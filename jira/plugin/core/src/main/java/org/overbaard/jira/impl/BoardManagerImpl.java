/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.overbaard.jira.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import javax.inject.Inject;
import javax.inject.Named;

import org.jboss.dmr.ModelNode;
import org.overbaard.jira.OverbaardLogger;
import org.overbaard.jira.OverbaardValidationException;
import org.overbaard.jira.api.BoardConfigurationManager;
import org.overbaard.jira.api.BoardManager;
import org.overbaard.jira.api.NextRankedIssueUtil;
import org.overbaard.jira.api.ProjectCustomFieldOptionsLoader;
import org.overbaard.jira.impl.board.Board;
import org.overbaard.jira.impl.board.BoardChangeRegistry;
import org.overbaard.jira.impl.board.BoardProject;
import org.overbaard.jira.impl.board.CustomFieldValue;
import org.overbaard.jira.impl.board.SortedFieldOptions;
import org.overbaard.jira.impl.config.BoardConfig;
import org.overbaard.jira.impl.config.BoardProjectConfig;
import org.overbaard.jira.impl.config.CustomFieldConfig;
import org.overbaard.jira.impl.config.ParallelTaskCustomFieldConfig;
import org.overbaard.jira.impl.config.ProjectParallelTaskGroupsConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.event.type.EventDispatchOption;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.customfields.view.CustomFieldParams;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.user.ApplicationUser;

/**
 * The interface to the loaded boards
 *
 * @author Kabir Khan
 */
@Named("overbaardBoardManager")
public class BoardManagerImpl implements BoardManager, InitializingBean, DisposableBean {

    private static final int REFRESH_TIMEOUT_SECONDS = 5 * 60;

    private final JiraInjectables jiraInjectables;

    //Guarded by this
    private Map<String, Board> boards = new HashMap<>();
    //Guarded by this
    private Map<String, BoardChangeRegistry> boardChangeRegistries = new HashMap<>();

    private final BoardConfigurationManager boardConfigurationManager;

    private final ProjectCustomFieldOptionsLoader projectCustomFieldOptionsLoader;

    private final ExecutorService boardRefreshExecutor = Executors.newSingleThreadExecutor();

    private final Queue<RefreshEntry> boardRefreshQueue = new LinkedBlockingQueue<>();

    //Guarded by this
    private final Map<String, RefreshEntry> refreshEntries = new HashMap<>();

    @Inject
    public BoardManagerImpl(JiraInjectables jiraInjectables,
                            BoardConfigurationManager boardConfigurationManager,
                            ProjectCustomFieldOptionsLoader projectCustomFieldOptionsLoader) {
        this.jiraInjectables = jiraInjectables;
        this.boardConfigurationManager = boardConfigurationManager;
        this.projectCustomFieldOptionsLoader = projectCustomFieldOptionsLoader;
    }

    @Override
    public void updateParallelTaskForIssue(ApplicationUser user, String boardCode, String issueKey, int groupIndex, int taskIndex, int optionIndex) throws SearchException {
        //Don't do anything to any of the cached boards, the Jira event mechanism will trigger an event when we update
        // the issue, which in turn will end up in our event listener to update the caches for the active boards.

        final Board board = getBoard(user, boardCode);

        final IssueService issueService = jiraInjectables.getIssueService();
        final IssueService.IssueResult issueResult = issueService.getIssue(user, issueKey);
        final MutableIssue issue = issueResult.getIssue();
        if (issue == null) {
            throw new OverbaardValidationException("Could not find issue " + issueKey);
        }

        final String projectCode = issueKey.substring(0, issueKey.indexOf("-"));
        final String issueType = issue.getIssueType().getName();

        final ProjectParallelTaskGroupsConfig parallelTaskGroupsConfig =
                board.getConfig().getBoardProject(projectCode).getParallelTaskGroupsConfig(issueType);
        final ParallelTaskCustomFieldConfig taskFieldConfig = parallelTaskGroupsConfig.forPosition(groupIndex, taskIndex);
        final CustomField customField = taskFieldConfig.getJiraCustomField();

        final BoardProject boardProject = board.getBoardProject(projectCode);
        final SortedFieldOptions.ParallelTasks sortedParallelTaskFieldOptions =
                boardProject.getParallelTaskOptions().getOptions(issueType).get(taskFieldConfig.getName());
        final CustomFieldValue value = sortedParallelTaskFieldOptions.forIndex(optionIndex);

        final IssueInputParameters inputParameters = issueService.newIssueInputParameters();
        inputParameters.addCustomFieldValue(customField.getId(), value.getKey());

        IssueService.UpdateValidationResult validationResult = issueService.validateUpdate(user, issue.getId(), inputParameters);
        if (validationResult.getErrorCollection().hasAnyErrors()) {
            //Typically we see errors if custom fields are used in a field configuration scheme where some are
            //required, and the custom fields were added after the issue was added so any default value has not actually
            //been set.
            //This means that if we have say two parallel task custom fields configured, an update will only set one of them,
            //while the other one remains unset. Attempt to work around this be checking which fields appear in the error
            //message, set them to the initial value, and try again

            final Map<String, String> errors = new HashMap<>(validationResult.getErrorCollection().getErrors());
            final Set<String> found = new HashSet<>();
            for (String fieldName : errors.keySet()) {
                Object object = validationResult.getFieldValuesHolder().get(fieldName);
                if (object instanceof CustomFieldParams) {
                    final CustomFieldParams customFieldParams = (CustomFieldParams)object;
                    CustomField currentField = customFieldParams.getCustomField();
                    if (currentField.getId().equals(customField.getId())) {
                        //There were problems with the field we were looking for so skip it
                        continue;
                    }

                    //currentField is most likely not set, look for the field in the current configuration
                    ParallelTaskCustomFieldConfig parallelTaskField =
                            parallelTaskGroupsConfig.forPosition(groupIndex, taskIndex);
                    if (parallelTaskField != null) {
                        //Since the field is not set, guess the default value (i.e. the first one in the list)
                        //Later if this is not satisfactory, we can use the Jira SDK to figure
                        final SortedFieldOptions.ParallelTasks currentParallelTaskFieldOptions =
                                boardProject.getParallelTaskOptions().getInternalAdvanced().getOptionsForProject().get(parallelTaskField.getName());
                        CustomFieldValue defaultValue = currentParallelTaskFieldOptions.forIndex(0);

                        inputParameters.addCustomFieldValue(currentField.getId(), defaultValue.getKey());
                        found.add(fieldName);
                    }
                }
            }

            for (String key : found) {
                errors.remove(key);
            }

            if (errors.size() > 0) {
                throw new RuntimeException("Could not set the value for '" + taskFieldConfig.getCode() + "' due to the following problems: " + errors);
            }
            //Validate our new attempt at updating the issue
            validationResult = issueService.validateUpdate(user, issue.getId(), inputParameters);
            if (validationResult.getErrorCollection().hasAnyErrors()) {
                throw new RuntimeException("Error validating update of '" + taskFieldConfig.getCode() + "': " + validationResult.getErrorCollection().getErrors());
            }
        }

        //Update the issue
        IssueService.IssueResult updateResult = issueService.update(user, validationResult, EventDispatchOption.ISSUE_UPDATED, true);
        if (updateResult.getErrorCollection().hasAnyErrors()) {
            throw new RuntimeException("Error updating '" + taskFieldConfig.getCode() + "': " + updateResult.getErrorCollection().getErrors());
        }
    }

    @Override
    public String getBoardJson(ApplicationUser user, boolean backlog, String code) throws SearchException {
        Board board = getBoard(user, code);
        return board.serialize(jiraInjectables, backlog, user).toJSONString(true);
    }

    private Board getBoard(ApplicationUser user, String code) throws SearchException {
        Board board = boards.get(code);
        if (board == null) {
            synchronized (this) {
                board = boards.get(code);
                if (board == null) {

                    //Use the logged in user to check if we are allowed to view the board
                    //Also, we force a refresh of the board config since we're doing a full load of the board
                    //(either as a first load, or following a refresh)
                    final BoardConfig boardConfig = boardConfigurationManager.getBoardConfigForBoardDisplay(user, code, true);
                    /*
                    Use the board owner to load the board data. The board is only loaded once, and shared amongst all
                    users.
                    Since I was not 100% sure which permission to use to determine if a user can view the board in the
                    check done by getBoardConfigForBoardDisplay(), it feels less error-prone to use the user who created
                    the board (who needs the project admin permission) to load this data.
                    This user is only used to load board data; all changes will be done using the logged in user.
                    */

                    final ApplicationUser boardOwner = jiraInjectables.getJiraUserManager().getUserByKey(boardConfig.getOwningUserKey());
                    board = Board.builder(jiraInjectables, projectCustomFieldOptionsLoader, boardConfig, boardOwner).load().build();
                    OverbaardLogger.LOGGER.debug("Full refresh of board {}", code);
                    boards.put(code, board);
                    boardChangeRegistries.put(code, new BoardChangeRegistry(this, board));
                    final RefreshEntry refreshEntry = new RefreshEntry(code, REFRESH_TIMEOUT_SECONDS);
                    boardRefreshQueue.add(refreshEntry);
                    refreshEntries.put(code, refreshEntry);
                }
            }
        }
        return board;
    }

    @Override
    public String getBoardName(ApplicationUser user, String boardCode) throws SearchException {
        Board board = getBoard(user, boardCode);
        ModelNode nameNode = new ModelNode();
        nameNode.get(Constants.NAME).set(board.getConfig().getName());
        return nameNode.toJSONString(true);
    }

    @Override
    public void deleteBoard(ApplicationUser user, String code) {
        deleteBoard(code);
    }

    public void forceRefresh(String code) {
        deleteBoard(code);
    }

    private void deleteBoard(String code) {
        synchronized (this) {
            boards.remove(code);
            BoardChangeRegistry registry = boardChangeRegistries.remove(code);
            if (registry != null) {
                registry.invalidate();
            }
            RefreshEntry refreshEntry = refreshEntries.remove(code);
            if (refreshEntry != null) {
                refreshEntry.invalidate();
            }
        }
    }

    @Override
    public boolean hasBoardsForProjectCode(String projectCode) {
        List<String> boardCodes = boardConfigurationManager.getBoardCodesForProjectCode(projectCode);
        if (boardCodes.size() == 0) {
            return false;
        }
        synchronized (this) {
            for (String boardCode : boardCodes) {
                //There might be a config, but no board. So check if there is a board first.
                if (boards.get(boardCode) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Set<CustomFieldConfig> getCustomFieldsForUpdateEvent(String projectCode, String jiraCustomFieldName) {
        return processBoardConfigs(
                projectCode,
                //Don't use a lamba here, it breaks Jira
                new BiFunction<BoardConfig, Set<CustomFieldConfig>, Set<CustomFieldConfig>>() {
                    @Override
                    public Set<CustomFieldConfig> apply(BoardConfig boardConfig, Set<CustomFieldConfig> result) {
                        CustomFieldConfig config = boardConfig.getCustomFieldObjectForJiraName(jiraCustomFieldName);
                        if (config != null) {
                            if (result == null) {
                                result = new HashSet<>();
                            }
                            result.add(config);
                        }
                        return result;
                    }
                });
    }

    @Override
    public Set<CustomFieldConfig> getCustomFieldsForCreateEvent(String projectCode) {
        return processBoardConfigs(
                projectCode,
                //Don't use a lamba here, it breaks Jira
                new BiFunction<BoardConfig, Set<CustomFieldConfig>, Set<CustomFieldConfig>>() {
                    @Override
                    public Set<CustomFieldConfig> apply(BoardConfig boardConfig, Set<CustomFieldConfig> result) {
                        Set<CustomFieldConfig> configs = boardConfig.getCustomFieldConfigs();
                        if (configs.size() > 0) {
                            if (result == null) {
                                result = new HashSet<>();
                            }
                            result.addAll(configs);
                        }
                        return result;
                    }
                });
    }

    public Set<ParallelTaskCustomFieldConfig> getParallelTaskFieldsForUpdateEvent(String projectCode, String issueType, String jiraCustomFieldName) {
        return processBoardConfigs(
                projectCode,
                new BiFunction<BoardConfig, Set<ParallelTaskCustomFieldConfig>, Set<ParallelTaskCustomFieldConfig>>() {
                    @Override
                    public Set<ParallelTaskCustomFieldConfig> apply(BoardConfig boardConfig, Set<ParallelTaskCustomFieldConfig> result) {
                        BoardProjectConfig projectConfig = boardConfig.getBoardProject(projectCode);
                        if (projectConfig != null && projectConfig.getParallelTaskGroupsConfig(issueType) != null) {
                            ProjectParallelTaskGroupsConfig parallelTaskGroupsConfig = projectConfig.getParallelTaskGroupsConfig(issueType);
                            return new HashSet<>(parallelTaskGroupsConfig.getFieldConfigs());
                        }
                        return result;
                    }
                });
    }


    public Set<ParallelTaskCustomFieldConfig> getParallelTaskFieldsForCreateEvent(String projectCode, String issueType) {
        return processBoardConfigs(
                projectCode,
                new BiFunction<BoardConfig, Set<ParallelTaskCustomFieldConfig>, Set<ParallelTaskCustomFieldConfig>>() {
                    @Override
                    public Set<ParallelTaskCustomFieldConfig> apply(BoardConfig boardConfig, Set<ParallelTaskCustomFieldConfig> result) {
                        BoardProjectConfig projectConfig = boardConfig.getBoardProject(projectCode);
                        if (projectConfig != null && projectConfig.getParallelTaskGroupsConfig(issueType) != null) {
                            ProjectParallelTaskGroupsConfig parallelTaskGroupsConfig = projectConfig.getParallelTaskGroupsConfig(issueType);
                            return new HashSet<>(parallelTaskGroupsConfig.getFieldConfigs());
                        }
                        return result;
                    }
                });
    }

    private <T> Set<T> processBoardConfigs(String projectCode, BiFunction<BoardConfig, Set<T>, Set<T>> function) {
        List<String> boardCodes = boardConfigurationManager.getBoardCodesForProjectCode(projectCode);
        if (boardCodes.size() == 0) {
            return Collections.emptySet();
        }
        Set<String> activeBoards = new HashSet<>();
        synchronized (this) {
            for (String boardCode : boardCodes) {
                //There might be a config, but no board. So check if there is a board first.
                //There is a slight chance that a new board might pop up so we will miss this update, but it isn't a big
                //deal. It will come in during the next periodic full refresh.
                if (boards.get(boardCode) != null) {
                    activeBoards.add(boardCode);
                }
            }
        }
        Set<T> result = null;
        OverbaardLogger.LOGGER.trace("Active boards for project code {}: {}", projectCode, activeBoards);
        for (String boardCode : activeBoards) {
            BoardConfig boardConfig = null;
            try {
                boardConfig = boardConfigurationManager.getBoardConfig(boardCode);
                if (boardConfig != null) {
                    result = function.apply(boardConfig, result);
                }
            } catch (OverbaardValidationException e) {
                OverbaardLogger.LOGGER.error("Error loading custom fields {} {}", boardCode, e.getMessage());
            }
        }
        return result != null ? result : Collections.emptySet();
    }

    @Override
    public void handleEvent(OverbaardIssueEvent event, NextRankedIssueUtil nextRankedIssueUtil) {
        //Jira seems to only handle one event at a time, which is good

        List<String> boardCodes = boardConfigurationManager.getBoardCodesForProjectCode(event.getProjectCode());
        for (String boardCode : boardCodes) {
            final Board board;
            final BoardChangeRegistry changeRegistry;
            synchronized (this) {
                board = boards.get(boardCode);
                if (board == null) {
                    continue;
                }
                changeRegistry = boardChangeRegistries.get(boardCode);
            }
            final ApplicationUser boardOwner = jiraInjectables.getJiraUserManager().getUserByKey(board.getConfig().getOwningUserKey());
            try {
                OverbaardLogger.LOGGER.debug("BoardManagerImpl.handleEvent - Handling event on board {}", board.getConfig().getCode());
                Board newBoard = board.handleEvent(jiraInjectables, nextRankedIssueUtil, boardOwner, event, changeRegistry);
                if (newBoard == null) {
                    //The changes in the issue were not relevant
                    return;
                }
                synchronized (this) {
                    //An event ending up in forceRefresh() might have deleted the board and the change registry
                    //with the intent of forcing the next read to perform a full refresh
                    //We have the new board returned, but check if we need to recreate the registry
                    if (changeRegistry.isValid()) {
                        changeRegistry.setBoard(newBoard);
                        boards.put(boardCode, newBoard);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                //Last parameter is the exception (it does not match a {} entry)
                OverbaardLogger.LOGGER.error("BoardManagerImpl.handleEvent - Error handling event {} - {}", event.getIssueKey(), e.getMessage());

                //Last parameter is the exception (it does not match a {} entry)
                OverbaardLogger.LOGGER.debug("BoardManagerImpl.handleEvent - Error handling event {}", event.getIssueKey(), e);
            }
        }
    }

    @Override
    public String getChangesJson(ApplicationUser user, boolean backlog, String code, int viewId) throws SearchException {
        //Check we are allowed to view the board
        boardConfigurationManager.getBoardConfigForBoardDisplay(user, code);

        BoardChangeRegistry boardChangeRegistry;
        synchronized (this) {
            boardChangeRegistry = boardChangeRegistries.get(code);
        }

        if (boardChangeRegistry == null) {
            //There is config but no board, so do a full refresh
            return getBoardJson(user, backlog, code);
        }

        final ModelNode changes;
        try {
            changes = boardChangeRegistry.getChangesSince(backlog, viewId);
        } catch (BoardChangeRegistry.FullRefreshNeededException e) {
            return getBoardJson(user, backlog, code);
        }

        return changes.toJSONString(true);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        boardRefreshExecutor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(10000);

                        //Throw out all the 'expired' boards so that they are refreshed again
                        synchronized (BoardManagerImpl.this) {
                            RefreshEntry entry = boardRefreshQueue.peek();
                            while (entry != null && System.currentTimeMillis() > entry.endTime) {
                                //Remove the entry we peeked at
                                entry = boardRefreshQueue.poll();

                                if (entry.isValid()) {
                                    OverbaardLogger.LOGGER.debug("Periodic task deleting board " + entry.boardCode);
                                    //Remove the board, an attempt to read it will result in a new instance being fully loaded
                                    //and created
                                    boardChangeRegistries.remove(entry.boardCode);
                                    boards.remove(entry.boardCode);
                                    //When an attempt is made to get the board again, a new entry will be added to the  queue
                                }
                                entry = boardRefreshQueue.peek();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void destroy() throws Exception {
        boardRefreshExecutor.shutdownNow();
        boardRefreshExecutor.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static class RefreshEntry {
        private final String boardCode;
        private final long endTime;
        private volatile boolean valid = true;

        public RefreshEntry(String boardCode, int timeoutSeconds) {
            this.boardCode = boardCode;
            this.endTime = System.currentTimeMillis() + timeoutSeconds *1000;
        }

        void invalidate() {
            valid = false;
        }

        boolean isValid() {
            return valid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RefreshEntry that = (RefreshEntry) o;
            return boardCode == that.boardCode;
        }

        @Override
        public int hashCode() {
            return boardCode.hashCode();
        }
    }
}
