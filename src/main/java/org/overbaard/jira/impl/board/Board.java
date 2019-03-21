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
package org.overbaard.jira.impl.board;

import static org.overbaard.jira.impl.Constants.EPICS;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.dmr.ModelNode;
import org.overbaard.jira.OverbaardLogger;
import org.overbaard.jira.api.NextRankedIssueUtil;
import org.overbaard.jira.api.ProjectCustomFieldOptionsLoader;
import org.overbaard.jira.impl.Constants;
import org.overbaard.jira.impl.JiraInjectables;
import org.overbaard.jira.impl.OverbaardIssueEvent;
import org.overbaard.jira.impl.board.MultiSelectNameOnlyValue.AffectsVersion;
import org.overbaard.jira.impl.board.MultiSelectNameOnlyValue.Component;
import org.overbaard.jira.impl.board.MultiSelectNameOnlyValue.FixVersion;
import org.overbaard.jira.impl.board.MultiSelectNameOnlyValue.Label;
import org.overbaard.jira.impl.config.BoardConfig;
import org.overbaard.jira.impl.config.BoardProjectConfig;
import org.overbaard.jira.impl.config.CustomFieldConfig;
import org.overbaard.jira.impl.config.LinkedProjectConfig;
import org.overbaard.jira.impl.config.ParallelTaskGroupPosition;
import org.overbaard.jira.impl.util.IndexedMap;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.issue.link.IssueLinkManager;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;

/**
 * The data for a board.
 *
 * @author Kabir Khan
 */
public class Board {
    //This is incremented every time a change is made to the board
    final int currentView;

    private final BoardConfig boardConfig;

    //Map of assignees sorted by their display name
    private final IndexedMap<String, Assignee> sortedAssignees;
    private final IndexedMap<String, Component> sortedComponents;
    private final IndexedMap<String, Label> sortedLabels;
    private final IndexedMap<String, FixVersion> sortedFixVersions;
    private final IndexedMap<String, AffectsVersion> sortedAffectsVersions;
    private final Map<String, Issue> allIssues;
    private final Map<String, BoardProject> projects;
    private final Map<String, SortedCustomFieldValues> sortedCustomFieldValues;

    private final Blacklist blacklist;

    private Board(Board old, BoardConfig boardConfig,
                  IndexedMap<String, Assignee> sortedAssignees,
                  IndexedMap<String, Component> sortedComponents,
                  IndexedMap<String, Label> sortedLabels,
                  IndexedMap<String, FixVersion> sortedFixVersions,
                  IndexedMap<String, AffectsVersion> sortedAffectsVersions, Map<String, Issue> allIssues,
                  Map<String, BoardProject> projects,
                  Map<String, SortedCustomFieldValues> sortedCustomFieldValues,
                  Blacklist blacklist) {
        this.currentView = old == null ? 0 : old.currentView + 1;
        this.boardConfig = boardConfig;

        this.sortedAssignees = sortedAssignees;

        this.sortedComponents = sortedComponents;
        this.sortedLabels = sortedLabels;
        this.sortedFixVersions = sortedFixVersions;
        this.sortedAffectsVersions = sortedAffectsVersions;

        this.allIssues = allIssues;
        this.projects = projects;
        this.sortedCustomFieldValues = sortedCustomFieldValues;
        this.blacklist = blacklist;
    }

    public static Builder builder(JiraInjectables jiraInjectables,
                                  ProjectCustomFieldOptionsLoader projectCustomFieldOptionsLoader,
                                  BoardConfig boardConfig,
                                  ApplicationUser boardOwner) {
        return new Builder(jiraInjectables, projectCustomFieldOptionsLoader, boardConfig, boardOwner);
    }

    public Board handleEvent(JiraInjectables jiraInjectables, NextRankedIssueUtil nextRankedIssueUtil, ApplicationUser boardOwner, OverbaardIssueEvent event,
                             BoardChangeRegistry changeRegistry) throws SearchException {
        Updater boardUpdater = new Updater(jiraInjectables, this, boardOwner, changeRegistry);
        return boardUpdater.handleEvent(event, nextRankedIssueUtil);
    }

    public ModelNode serialize(JiraInjectables jiraInjectables, boolean backlog, ApplicationUser user) {
        ModelNode outputNode = new ModelNode();
        //Sort the assignees by name
        outputNode.get(Constants.VIEW).set(currentView);
        outputNode.get(Constants.CURRENT_USER).set(user.getKey());

        ModelNode assigneesNode = outputNode.get(Constants.ASSIGNEES);
        assigneesNode.setEmptyList();
        sortedAssignees.values().forEach(assignee -> assignee.serialize(assigneesNode));

        if (sortedComponents.size() > 0) {
            ModelNode componentsNode = outputNode.get(Constants.COMPONENTS);
            sortedComponents.values().forEach(component -> component.serialize(componentsNode));
        }
        if (sortedLabels.size() > 0) {
            ModelNode labelsNode = outputNode.get(Constants.LABELS);
            sortedLabels.values().forEach(label -> label.serialize(labelsNode));
        }
        if (sortedFixVersions.size() > 0) {
            ModelNode fixVersionsNode = outputNode.get(Constants.FIX_VERSIONS);
            sortedFixVersions.values().forEach(fixVersion -> fixVersion.serialize(fixVersionsNode));
        }
        if (sortedAffectsVersions.size() > 0) {
            ModelNode affectsVersionsNode = outputNode.get(Constants.AFFECTS_VERSIONS);
            sortedAffectsVersions.values().forEach(affectsVersion -> affectsVersion.serialize(affectsVersionsNode));
        }
        if (sortedCustomFieldValues.size() > 0) {
            ModelNode customNode = outputNode.get(Constants.CUSTOM);
            sortedCustomFieldValues.values().forEach(values -> values.serialize(customNode));
        }

        boardConfig.serializeModelNodeForBoard(outputNode);

        ModelNode allIssues = outputNode.get(Constants.ISSUES);
        this.allIssues.forEach((code, issue) -> {
            boolean relevant = true;
            if (!backlog) {
                relevant = !getBoardProject(issue.getProjectCode()).isBacklogState(issue.getIssueTypeName(), issue.getState());
            }
            if (relevant) {
                allIssues.get(code).set(issue.getModelNodeForFullRefresh(this));
            }
        });

        ModelNode mainProjectsParent = outputNode.get(Constants.PROJECTS, Constants.MAIN);

        Map<String, ModelNode> projectLookup = new HashMap<>();
        for (ModelNode project : mainProjectsParent.asList()) {
            projectLookup.put(project.get(Constants.CODE).asString(), project);
        }
        ModelNode epicsByProject = new ModelNode();
        for (Map.Entry<String, BoardProject> projectEntry : projects.entrySet()) {
            final String projectCode = projectEntry.getKey();
            ModelNode project = projectLookup.get(projectCode);
            projectEntry.getValue().serialize(jiraInjectables, this, project, user, backlog);


            ModelNode epicsNode = projectEntry.getValue().serializeEpics();
            if (epicsNode != null) {
                epicsByProject.get(projectEntry.getKey()).set(epicsNode);
            }
        }

        if (epicsByProject.isDefined()) {
            outputNode.get(EPICS).set(epicsByProject);
        }

        blacklist.serialize(outputNode);
        return outputNode;
    }

    public Issue getIssue(String key) {
        return allIssues.get(key);
    }

    public BoardConfig getConfig() {
        return boardConfig;
    }

    public int getProjectCode() {
        return boardConfig.getId();
    }

    public BoardProject getBoardProject(String code) {
        return projects.get(code);
    }

    public int getAssigneeIndex(Assignee assignee) {
        return sortedAssignees.getIndex(assignee.getKey());
    }

    public int getComponentIndex(Component component) {
        return sortedComponents.getIndex(component.getName());
    }

    public int getLabelIndex(Label label) {
        return sortedLabels.getIndex(label.getName());
    }

    public int getFixVersionIndex(FixVersion fixVersion) {
        return sortedFixVersions.getIndex(fixVersion.getName());
    }

    public int getAffectsVersionIndex(AffectsVersion affectsVersion) {
        return sortedAffectsVersions.getIndex(affectsVersion.getName());
    }

    public int getCustomFieldIndex(CustomFieldValue customFieldValue) {
        return sortedCustomFieldValues.get(customFieldValue.getCustomFieldName()).getCustomFieldIndex(customFieldValue);
    }

    private void updateBoardInProjects() {
        projects.values().forEach(project -> project.setBoard(this));
    }

    private static Assignee createAssignee(JiraInjectables jiraInjectables, ApplicationUser boardOwner, ApplicationUser assigneeUser) {
        URI avatarUrl = jiraInjectables.getAvatarService().getAvatarURL(boardOwner, assigneeUser, Avatar.Size.MEDIUM);
        Assignee assignee = Assignee.create(assigneeUser, avatarUrl.toString());
        return assignee;
    }

    public int getCurrentView() {
        return currentView;
    }

    BoardChangeRegistry.IssueChange createCreateIssueChange(BoardChangeRegistry registry, String issueKey) {
        Issue issue = allIssues.get(issueKey);
        return issue.convertToCreateIssueChange(registry, getConfig());
    }

    abstract static class Accessor {
        protected final JiraInjectables jiraInjectables;
        protected final BoardConfig boardConfig;
        protected final ApplicationUser boardOwner;


        Accessor(JiraInjectables jiraInjectables, BoardConfig boardConfig, ApplicationUser boardOwner) {
            this.jiraInjectables = jiraInjectables;
            this.boardConfig = boardConfig;
            this.boardOwner = boardOwner;
        }

        public ApplicationUser getBoardOwner() {
            return boardOwner;
        }

        Integer getIssueTypeIndexRecordingMissing(String issueKey, String issueTypeName) {
            final Integer issueTypeIndex = boardConfig.getIssueTypeIndex(issueTypeName);
            if (issueTypeIndex == null) {
                getBlacklist().addMissingIssueType(issueKey, issueTypeName);
            }
            return issueTypeIndex;
        }

        Integer getPriorityIndexRecordingMissing(String issueKey, String priorityName) {
            final Integer priorityIndex = boardConfig.getPriorityIndex(priorityName);
            if (priorityIndex == null) {
                getBlacklist().addMissingPriority(issueKey, priorityName);
            }
            return priorityIndex;
        }

        void addMissingState(String issueKey, String stateName) {
            getBlacklist().addMissingState(issueKey, stateName);
        }

        IssueLinkManager getIssueLinkManager() {
            return jiraInjectables.getIssueLinkManager();
        }


        public BoardProject.LinkedProjectContext getLinkedProjectContext(String linkedProjectCode) {
            LinkedProjectConfig projectCfg = boardConfig.getLinkedProjectConfig(linkedProjectCode);
            if (projectCfg == null) {
                return null;
            }
            return BoardProject.linkedProjectContext(this, projectCfg);
        }

        abstract Accessor addIssue(Issue issue);
        abstract Assignee getAssignee(ApplicationUser assigneeUser);
        abstract Issue getIssue(String issueKey);
        abstract Blacklist.Accessor getBlacklist();

        abstract CustomFieldValue getCustomFieldValue(CustomFieldConfig customField, Object fieldValue);

        abstract CustomFieldValue getCustomFieldValue(CustomFieldConfig customField, String key);

        abstract Set<Component> getComponents(Collection<ProjectComponent> componentObjects);

        abstract Set<Label> getLabels(Set<com.atlassian.jira.issue.label.Label> labels);

        abstract Set<FixVersion> getFixVersions(Collection<Version> fixVersions);

        abstract Set<AffectsVersion> getAffectsVersions(Collection<Version> affectsVersions);

        public List<String> getStateNames() {
            return boardConfig.getStateNames();
        }

        public BoardConfig getConfig() {
            return boardConfig;
        }

        public abstract void addBulkLoadedCustomFieldValue(CustomFieldConfig config, CustomFieldValue value);

    }

    private static Map<String, Assignee> sortAssignees(Map<String, Assignee> assignees) {
        return sortMapValues(
                assignees,
                Assignee::getDisplayName,
                Assignee::getKey);
    }

    private static <T extends MultiSelectNameOnlyValue> Map<String, T> sortMultiSelectNameOnlyValueMap(Map<String, T> values) {
        return sortMapValues(values, MultiSelectNameOnlyValue::getName, MultiSelectNameOnlyValue::getName);
    }

    private static <T> Map<String, T> sortMapValues(Map<String, T> unsorted, Function<T, String> displayNameExtractor, Function<T, String> keyExtractor) {
        List<T> values = new ArrayList<T>(unsorted.values());
        Comparator<T> comparator = Comparator.comparing(displayNameExtractor, String.CASE_INSENSITIVE_ORDER);
        Collections.sort(values, comparator);
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        values.forEach(value -> result.put(keyExtractor.apply(value), value));
        return result;
    }

    /**
     * Used to create a new board
     */
    public static class Builder extends Accessor {
        private final ProjectCustomFieldOptionsLoader projectCustomFieldOptionsLoader;
        private final Map<String, Assignee> assignees = new HashMap<>();
        private final Map<String, Component> components = new HashMap<>();
        private final Map<String, Label> labels = new HashMap<>();
        private final Map<String, FixVersion> fixVersions = new HashMap<>();
        private final Map<String, AffectsVersion> affectsVersions = new HashMap<>();
        private final Map<String, Issue> allIssues = new HashMap<>();
        private final Map<String, BoardProject.Builder> projects = new HashMap<>();
        private final Blacklist.Builder blacklist = new Blacklist.Builder();
        private final Map<Long, SortedCustomFieldValues.Builder> customFieldBuilders = new HashMap();

        public Builder(JiraInjectables jiraInjectables,
                       ProjectCustomFieldOptionsLoader projectCustomFieldOptionsLoader,
                       BoardConfig boardConfig, ApplicationUser boardOwner) {
            super(jiraInjectables, boardConfig, boardOwner);
            this.projectCustomFieldOptionsLoader = projectCustomFieldOptionsLoader;
        }

        public Builder load() throws SearchException {
            for (BoardProjectConfig boardProjectConfig : boardConfig.getBoardProjects()) {
                BoardProjectConfig project = boardConfig.getBoardProject(boardProjectConfig.getCode());
                BoardProject.Builder projectBuilder = BoardProject.builder(jiraInjectables, projectCustomFieldOptionsLoader, this, project, boardOwner);
                projectBuilder.load();
                projects.put(projectBuilder.getCode(), projectBuilder);
            }
            return this;
        }

        @Override
        public Accessor addIssue(Issue issue) {
            allIssues.put(issue.getKey(), issue);
            return this;
        }

        @Override
        Assignee getAssignee(ApplicationUser assigneeUser) {
            if (assigneeUser == null) {
                //Unassigned issue
                return null;
            }
            Assignee assignee = assignees.get(assigneeUser.getName());
            if (assignee != null) {
                return assignee;
            }
            assignee = createAssignee(jiraInjectables, boardOwner, assigneeUser);
            assignees.put(assigneeUser.getName(), assignee);
            return assignee;
        }

        @Override
        Set<Component> getComponents(Collection<ProjectComponent> componentObjects) {
            return getIssueMultiSelectNameValues(components, componentObjects,
                    projectComponent -> projectComponent.getName(),
                    name -> new Component(name));
        }

        @Override
        Set<Label> getLabels(Set<com.atlassian.jira.issue.label.Label> jiraLabels) {
            return getIssueMultiSelectNameValues(labels, jiraLabels,
                    jiraLabel -> jiraLabel.getLabel(),
                    name -> new Label(name));
        }

        @Override
        Set<FixVersion> getFixVersions(Collection<Version> jiraFixVersions) {
            return getIssueMultiSelectNameValues(fixVersions, jiraFixVersions,
                    jiraFixVersion -> jiraFixVersion.getName(),
                    name -> new FixVersion(name));
        }

        @Override
        Set<AffectsVersion> getAffectsVersions(Collection<Version> jiraAffectsVersions) {
            return getIssueMultiSelectNameValues(affectsVersions, jiraAffectsVersions,
                    jiraAffectsVersion -> jiraAffectsVersion.getName(),
                    name -> new AffectsVersion(name));
        }

        private <T, R extends MultiSelectNameOnlyValue> Set<R> getIssueMultiSelectNameValues (
                Map<String, R> builderMap,
                Collection<T> issueJiraObjects,
                Function<T, String> nameGetter,
                Function<String, R> valueFactory) {
            if (issueJiraObjects == null || issueJiraObjects.size() == 0) {
                //No values for issue
                return null;
            }
            final Set<R> ret = new LinkedHashSet<R>(issueJiraObjects.size());
            for (T issueJiraObject : issueJiraObjects) {
                final String name = nameGetter.apply(issueJiraObject);
                R value = builderMap.get(name);
                if (value == null) {
                    value = valueFactory.apply(name);
                    builderMap.put(name, value);
                }
                ret.add(value);
            }
            return ret;
        }

        @Override
        CustomFieldValue getCustomFieldValue(CustomFieldConfig customFieldConfig, Object fieldValue) {
            final SortedCustomFieldValues.Builder customFieldBuilder =
                    customFieldBuilders.computeIfAbsent(customFieldConfig.getId(), id -> new SortedCustomFieldValues.Builder(customFieldConfig));
            return customFieldBuilder.getCustomFieldValue(fieldValue);
        }

        @Override
        CustomFieldValue getCustomFieldValue(CustomFieldConfig customField, String key) {
            //Should not get called for this code path
            throw new IllegalStateException();
        }

        @Override
        Issue getIssue(String issueKey) {
            //Should not get called for this code path
            throw new IllegalStateException();
        }

        @Override
        public void addBulkLoadedCustomFieldValue(CustomFieldConfig customFieldConfig, CustomFieldValue value) {
            final SortedCustomFieldValues.Builder customFieldBuilder =
                    customFieldBuilders.computeIfAbsent(customFieldConfig.getId(), id -> new SortedCustomFieldValues.Builder(customFieldConfig));
            customFieldBuilder.addBulkLoadedCustomFieldValue(value);

        }

        @Override
        Blacklist.Accessor getBlacklist() {
            return blacklist;
        }

        public Board build() {
            Map<String, BoardProject> projects = new LinkedHashMap<>();

            this.projects.forEach((name, projectBuilder) -> {
                if (boardConfig.getBoardProject(name) != null) {
                    projects.put(name, projectBuilder.build());
                }
            });

            Map<String, SortedCustomFieldValues> sortedCustomFieldValues = new HashMap<>();
            this.customFieldBuilders.values().forEach(scfBuilder -> {
                SortedCustomFieldValues fieldValues = scfBuilder.build();
                sortedCustomFieldValues.put(fieldValues.getFieldName(), fieldValues);
            });

            Board board = new Board(
                    null, boardConfig,
                    new IndexedMap<>(sortAssignees(assignees)),
                    new IndexedMap<>(sortMultiSelectNameOnlyValueMap(components)),
                    new IndexedMap<>(sortMultiSelectNameOnlyValueMap(labels)),
                    new IndexedMap<>(sortMultiSelectNameOnlyValueMap(fixVersions)),
                    new IndexedMap<>(sortMultiSelectNameOnlyValueMap(affectsVersions)),
                    Collections.unmodifiableMap(allIssues),
                    Collections.unmodifiableMap(projects),
                    Collections.unmodifiableMap(sortedCustomFieldValues),
                    blacklist.build());
            projects.values().forEach(project -> project.setBoard(board));
            return board;
        }
    }

    /**
     * Used to update an already existing/loaded board
     */
    static class Updater extends Accessor {
        private final Board board;
        private final BoardChangeRegistry changeRegistry;
        private final Blacklist.Updater blacklist;

        //Will only be populated if a new assignee is brought in
        private Map<String, Assignee> assigneesCopy;
        //Will only be populated if new components are brought in
        private Map<String, Component> componentsCopy;
        //Will only be populated if new labels are brought in
        private Map<String, Label> labelsCopy;
        //Will only be populated if new fixVersions are brought in
        private Map<String, FixVersion> fixVersionsCopy;
        //Will only be populated if new affectsVersions are brought in
        private Map<String, AffectsVersion> affectsVersionsCopy;

        Map<String, Issue> allIssuesCopy;

        private Assignee newAssignee;
        private Set<Component> newComponents;
        private Set<Label> newLabels;
        private Set<FixVersion> newFixVersions;
        private Set<AffectsVersion> newAffectsVersions;
        private final Map<Long, SortedCustomFieldValues.Updater> customFieldUpdaters = new HashMap();

        Updater(JiraInjectables jiraInjectables, Board board, ApplicationUser boardOwner, BoardChangeRegistry changeRegistry) {
            super(jiraInjectables, board.getConfig(), boardOwner);
            this.board = board;
            this.changeRegistry = changeRegistry;
            this.blacklist = new Blacklist.Updater(board.blacklist);

        }

        Board handleEvent(OverbaardIssueEvent event, NextRankedIssueUtil nextRankedIssueUtil) throws SearchException {
            switch (event.getType()) {
                case DELETE:
                    return handleDeleteEvent(event);
                case CREATE:
                    return handleCreateOrUpdateIssue(event, nextRankedIssueUtil, true);
                case UPDATE:
                    return handleCreateOrUpdateIssue(event, nextRankedIssueUtil, false);
                default:
                    throw new IllegalArgumentException("Unknown event type " + event.getType());
            }
        }

        private Board handleDeleteEvent(OverbaardIssueEvent event) throws SearchException {
            OverbaardLogger.LOGGER.debug("Board.Updater.handleDeleteEvent - Handling delete event for {}", event.getIssueKey());
            final BoardProject project = board.projects.get(event.getProjectCode());
            if (project == null) {
                throw new IllegalArgumentException("Can't find project " + event.getProjectCode() +
                        " in board " + board.boardConfig.getId());
            }

            final Map<String, BoardProject> projectsCopy;
            final Map<String, Issue> allIssuesCopy;
            if (board.blacklist.isBlacklisted(event.getIssueKey())) {
                OverbaardLogger.LOGGER.debug("Board.Updater.handleDeleteEvent - Handling delete event for blacklisted issue {}", event.getIssueKey());
                //For a delete of an issue that has been blacklisted we simply remove the issue from the blacklist.
                //It is not part of any of the issue tables so just use the old projects
                projectsCopy = board.projects;
                allIssuesCopy = board.allIssues;

                //We still need to update the board somewhat though to include the new blacklist (we only remove the
                // issue and not the bad state/issue-type/priority)
                blacklist.deleteIssue(event.getIssueKey());
            } else {
                OverbaardLogger.LOGGER.debug("Board.Updater.handleDeleteEvent - Handling delete event for issue {}", event.getIssueKey());
                final Issue issue = board.allIssues.get(event.getIssueKey());
                if (issue == null) {
                    throw new IllegalArgumentException("Can't find issue to delete " + event.getIssueKey() +
                            " in board " + board.boardConfig.getId());
                }
                final BoardProject projectCopy = project.copyAndDeleteIssue(issue);
                projectsCopy = copyAndPut(board.projects, event.getProjectCode(), projectCopy, HashMap::new);

                Map<String, Issue> allIssues = new HashMap<>(board.allIssues);
                allIssues.remove(issue.getKey());
                allIssuesCopy = Collections.unmodifiableMap(allIssues);
            }

            Board boardCopy = new Board(board, board.boardConfig,
                    board.sortedAssignees,
                    board.sortedComponents,
                    board.sortedLabels,
                    board.sortedFixVersions,
                    board.sortedAffectsVersions,
                    Collections.unmodifiableMap(allIssuesCopy),
                    projectsCopy,
                    SortedCustomFieldValues.Updater.merge(customFieldUpdaters, board.sortedCustomFieldValues),
                    blacklist.build());
            boardCopy.updateBoardInProjects();

            //Register the event
            BoardChange.Builder changeBuilder = changeRegistry.addChange(boardCopy.currentView, event);
            if (blacklist.isUpdated()) {
                changeBuilder.deleteBlacklist(blacklist.getDeletedIssue());
            }
            changeBuilder.buildAndRegister();
            return boardCopy;
        }

        Board handleCreateOrUpdateIssue(OverbaardIssueEvent event, NextRankedIssueUtil nextRankedIssueUtil, boolean create) throws SearchException {

            OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - Handling create or update event for {}; create: {}", event.getIssueKey(), create);
            if (!create && board.blacklist.isBlacklisted(event.getIssueKey())) {
                //For an update of an issue that has been blacklisted we will not be able to figure out the state
                //So just return the original board
                OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - update event for blacklisted {} - returning original board", event.getIssueKey());
                return board;
            }

            final BoardProject project = board.projects.get(event.getProjectCode());
            if (project == null) {
                throw new IllegalArgumentException("Can't find project " + event.getProjectCode()
                        + " in board " + board.boardConfig.getId());
            }
            OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - using board project {}", project.getCode());

            OverbaardIssueEvent.Detail evtDetail = event.getDetails();

            boolean moveFromDone = false;
            if (!create) {
                OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - updating issue {}", event.getIssueKey());
                if (event.getDetails().getState() != null) {

                    //This was a move, work out if we are moving to a done state or to an old state
                    //Note that in theory if the issue type was changed the state calculation could be different
                    //for the new state, HOWEVER Jira doesn't seem to allow you to change the type if the new type uses
                    //a different workflow
                    boolean newDone = project.isDoneState(event.getDetails().getIssueType(), event.getDetails().getState());
                    boolean oldDone = project.isDoneState(event.getDetails().getIssueType(), event.getDetails().getOldState());

                    OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - possible state change from {} to {}. oldDone: {}; newDone: {}",
                            event.getDetails().getOldState(), event.getDetails().getState(), oldDone, newDone);

                    if (newDone && oldDone) {
                        //The whole move happened within the 'done' states, so ignore this update
                        OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - whole move happened in done states - return original");
                        return board;
                    }
                    if (newDone && !oldDone) {
                        //We are moving from a non-done to a 'done' state. Delete this issue from our cache
                        OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - Moving from non-done to done state, delete issue");
                        return handleDeleteEvent(OverbaardIssueEvent.createDeleteEvent(event.getIssueKey(), event.getProjectCode()));
                    }
                    moveFromDone = oldDone && !newDone;
                } else if (project.isDoneState(event.getDetails().getIssueType(), event.getDetails().getOldState())) {
                    OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue- ignoring done issue {}, state: {}. Return original",
                            event.getIssueKey(), event.getDetails().getOldState());
                    //This was not a move, so if the 'old state' (which is the current one) is a done state
                    //we should return since we ignore these 'done' issues
                    return board;
                }
            }

            OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - moveFromDone: {}", moveFromDone);

            //Might bring in a new assignee and/or component, need to add those first
            //Will populate assigneeCopy and newAssignee if we need to add the assignee
            final Assignee issueAssignee = getOrCreateIssueAssignee(evtDetail);
            final Set<Component> issueComponents = getOrCreateIssueComponents(evtDetail);
            final Set<Label> issueLabels = getOrCreateIssueLabels(evtDetail);
            final Set<FixVersion> issueFixVersions = getOrCreateIssueFixVersions(evtDetail);
            final Set<AffectsVersion> issueAffectsVersions = getOrCreateIssueAffectsVersions(evtDetail);

            final BoardProject.Updater projectUpdater = project.updater(jiraInjectables, nextRankedIssueUtil, this, boardOwner);
            final Map<String, CustomFieldValue> customFieldValues
                    = CustomFieldValue.loadCustomFieldValues(projectUpdater, evtDetail.getCustomFieldValues());

            final Issue existingIssue;
            final Issue newIssue;
            final Map<ParallelTaskGroupPosition, Integer> parallelTaskGroupValues;
            if (create) {
                OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue- create issue {}", event.getIssueKey());
                existingIssue = null;
                parallelTaskGroupValues =
                        CustomFieldValue.loadParallelTaskGroupValues(projectUpdater, evtDetail.getCustomFieldValues(), null, evtDetail.getIssueType());
                newIssue = projectUpdater.createIssue(event.getIssueKey(), evtDetail.getIssueType(),
                        evtDetail.getPriority(), evtDetail.getSummary(), issueAssignee,
                        issueComponents, issueLabels, issueFixVersions, issueAffectsVersions,
                        evtDetail.getState(), customFieldValues, parallelTaskGroupValues);
            } else {
                existingIssue = board.allIssues.get(event.getIssueKey());
                if (existingIssue == null) {
                    if (moveFromDone) {
                        OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue- Moving from done {}", event.getIssueKey());

                        //We are doing a state change from one of the 'done' states for which we do not cache issues,
                        //into a cached state. Load it up and add it to the board
                        newIssue = projectUpdater.loadSingleIssue(event.getIssueKey());

                        // A bit ugly, but further down we don't use value if we are moving from done
                        parallelTaskGroupValues = Collections.emptyMap();
                        if (newIssue == null) {
                            throw new IllegalArgumentException("Can't load issue that was updated from a 'done' state: " + event.getIssueKey() + " in board " + board.boardConfig.getId());
                        }
                    } else {
                        throw new IllegalArgumentException("Can't find issue to update " + event.getIssueKey() + " in board " + board.boardConfig.getId());
                    }
                } else {
                    OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue- Updating issue {}", event.getIssueKey());
                    parallelTaskGroupValues = CustomFieldValue.loadParallelTaskGroupValues(projectUpdater, evtDetail.getCustomFieldValues(), existingIssue, evtDetail.getIssueType());
                    newIssue = projectUpdater.updateIssue(existingIssue, evtDetail.getIssueType(),
                            evtDetail.getPriority(), evtDetail.getSummary(), issueAssignee,
                            issueComponents, issueLabels, issueFixVersions, issueAffectsVersions,
                            evtDetail.isReranked(), evtDetail.getState(), customFieldValues, parallelTaskGroupValues);
                }
            }


            //This will replace the old issue
            allIssuesCopy = newIssue != null ?
                    copyAndPut(board.allIssues, event.getIssueKey(), newIssue, HashMap::new) :
                    board.allIssues;

            OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - newIssue {}; updatedBlacklist {}; changedRankOrState {}",
                    newIssue, blacklist.isUpdated(), evtDetail.isReranked());

            if (newIssue != null || blacklist.isUpdated() || evtDetail.isReranked()) {
                //The project's issue tables will be updated if needed
                OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - Copying project {}", project.getCode());
                final BoardProject projectCopy = projectUpdater.build();
                final Map<String, BoardProject> projectsCopy = new HashMap<>(board.projects);
                projectsCopy.put(event.getProjectCode(), projectCopy);

                final Board boardCopy = new Board(board, board.boardConfig,
                        assigneesCopy == null ? board.sortedAssignees : new IndexedMap<>(sortAssignees(assigneesCopy)),
                        componentsCopy == null ? board.sortedComponents : new IndexedMap<>(sortMultiSelectNameOnlyValueMap(componentsCopy)),
                        labelsCopy == null ? board.sortedLabels : new IndexedMap<>(sortMultiSelectNameOnlyValueMap(labelsCopy)),
                        fixVersionsCopy == null ? board.sortedFixVersions : new IndexedMap<>(sortMultiSelectNameOnlyValueMap(fixVersionsCopy)),
                        affectsVersionsCopy == null ? board.sortedAffectsVersions : new IndexedMap<>(sortMultiSelectNameOnlyValueMap(affectsVersionsCopy)),
                        allIssuesCopy,
                        Collections.unmodifiableMap(projectsCopy),
                        SortedCustomFieldValues.Updater.merge(customFieldUpdaters, board.sortedCustomFieldValues),
                        blacklist.build());

                //Register the event
                boardCopy.updateBoardInProjects();

                if (moveFromDone) {
                    //We are making an issue visible again by moving it from a done state to a non-done state
                    //In this case invalidate. While it could be done, the change set becomes quite hard to keep
                    //track of if we think of moving to a done state as a delete, and moving out of a done state to
                    //a non-done state as a (re)create for e.g. the following scenarios:
                    //  non-done state -> done state (delete) == we have this now, and it becomes a delete
                    //  done state -> non-done state == a create
                    //  non-done state -> done state (delete) -> non-done state (recreate) == a noop (if no data changed), or an update
                    //  done state -> non-done state (create) -> done state == a noop
                    //
                    //To simplify things make moving an issue from a 'done' state to a normal state force a full board
                    //refresh for the clients. Issues being moved to 'done' is the norm. Issues being moved out from
                    //'done' to a prior state is not.
                    changeRegistry.forceRefresh();
                } else {
                    BoardChange.Builder changeBuilder = changeRegistry.addChange(boardCopy.currentView, event);

                    if (newAssignee != null) {
                        changeBuilder.addNewAssignee(newAssignee);
                    }
                    if (newComponents != null) {
                        changeBuilder.addNewComponents(newComponents);
                    }
                    if (newLabels != null) {
                        changeBuilder.addNewLabels(newLabels);
                    }
                    if (newFixVersions != null) {
                        changeBuilder.addNewFixVersions(newFixVersions);
                    }
                    if (newAffectsVersions != null) {
                        changeBuilder.addNewAffectsVersions(newAffectsVersions);
                    }
                    if (blacklist.isUpdated()) {
                        changeBuilder.addBlacklist(blacklist.getAddedState(), blacklist.getAddedIssueType(),
                                blacklist.getAddedPriority(), blacklist.getAddedIssue());
                    }
                    if (customFieldValues.size() > 0) {
                        changeBuilder.addCustomFieldValues(board.sortedCustomFieldValues, customFieldValues);
                    }

                    if (existingIssue != null) {
                        changeBuilder.setFromBacklogState(project.isBacklogState(existingIssue.getIssueTypeName(), existingIssue.getState()));
                    }
                    if (newIssue != null) {
                        changeBuilder.setBacklogState(project.isBacklogState(newIssue.getIssueTypeName(), newIssue.getState()));
                    }
                    if (parallelTaskGroupValues.size() > 0) {
                        changeBuilder.setParallelTaskGroupValues(parallelTaskGroupValues);
                    } else {
                        if (existingIssue != null && evtDetail.getIssueType() != null) {
                            changeBuilder.clearParallelTaskGroupValues();
                        }
                    }
                    OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - Registering change");
                    changeBuilder.buildAndRegister();
                }

                return boardCopy;
            }
            OverbaardLogger.LOGGER.debug("Board.Updater.handleCreateOrUpdateIssue - Returning null board");
            return null;
        }

        @Override
        Assignee getAssignee(ApplicationUser assigneeUser) {
            return getOrCreateIssueAssignee(assigneeUser);
        }

        @Override
        Set<Component> getComponents(Collection<ProjectComponent> componentObjects) {
            return getOrCreateIssueComponents(componentObjects);
        }

        @Override
        Set<Label> getLabels(Set<com.atlassian.jira.issue.label.Label> labels) {
            return getOrCreateIssueLabels(labels);
        }

        @Override
        Set<FixVersion> getFixVersions(Collection<Version> fixVersions) {
            return getOrCreateIssueFixVersions(fixVersions);
        }

        @Override
        Set<AffectsVersion> getAffectsVersions(Collection<Version> affectsVersions) {
            return getOrCreateIssueAffectsVersions(affectsVersions);
        }

        @Override
        CustomFieldValue getCustomFieldValue(CustomFieldConfig customFieldConfig, Object fieldValue) {
            //Should not get called for this code path
            throw new IllegalStateException();
        }

        @Override
        public void addBulkLoadedCustomFieldValue(CustomFieldConfig config, CustomFieldValue value) {
            //Should not get called for this code path
            throw new IllegalStateException();
        }

        @Override
        CustomFieldValue getCustomFieldValue(CustomFieldConfig customFieldConfig, String key) {
            SortedCustomFieldValues boardValues = board.sortedCustomFieldValues.get(customFieldConfig.getName());
            if (boardValues != null) {
                CustomFieldValue customFieldValue = boardValues.getCustomFieldValue(key);
                if (customFieldValue != null) {
                    return customFieldValue;
                }
            }

            final SortedCustomFieldValues.Updater customFieldUpdater =
                    customFieldUpdaters.computeIfAbsent(customFieldConfig.getId(),
                            id -> new SortedCustomFieldValues.Updater(
                                    customFieldConfig,
                                    board.sortedCustomFieldValues.get(customFieldConfig.getName())));

            return customFieldUpdater.getCustomFieldValue(jiraInjectables, key);
        }

        @Override
        Accessor addIssue(Issue issue) {
            //This should not happen for this code path
            throw new IllegalStateException();
        }

        @Override
        Issue getIssue(String issueKey) {
            return allIssuesCopy.get(issueKey);
        }

        @Override
        Blacklist.Accessor getBlacklist() {
            return blacklist;
        }

        private Assignee getOrCreateIssueAssignee(OverbaardIssueEvent.Detail evtDetail) {
            return getOrCreateIssueAssignee(evtDetail.getAssignee());
        }

        private Assignee getOrCreateIssueAssignee(ApplicationUser evtAssignee) {
            if (evtAssignee == null) {
                return null;
            } else if (evtAssignee == OverbaardIssueEvent.UNASSIGNED) {
                return Assignee.UNASSIGNED;
            } else {
                Assignee assignee = board.sortedAssignees.get(evtAssignee.getName());
                if (assignee == null) {
                    assignee = Board.createAssignee(jiraInjectables, boardOwner, evtAssignee);
                    newAssignee = assignee;
                    assigneesCopy = copyAndPut(board.sortedAssignees.map(), evtAssignee.getName(), assignee, HashMap::new);
                }
                return assignee;
            }
        }

        private Set<Component> getOrCreateIssueComponents(OverbaardIssueEvent.Detail evtDetail) {
            return getOrCreateIssueComponents(evtDetail.getComponents());
        }

        private Set<Component> getOrCreateIssueComponents(Collection<ProjectComponent> evtComponents) {
            return getOrCreateIssueMultiSelectNameValues(
                    evtComponents,
                    ProjectComponent::getName,
                    name -> new Component(name),
                    () -> componentsCopy == null ? board.sortedComponents.map() : componentsCopy,
                    () -> {
                        if (componentsCopy == null) {
                            componentsCopy = new HashMap<String, Component>(board.sortedComponents.map());
                        }
                        return componentsCopy;
                    },
                    () -> {
                        if (newComponents == null) {
                            newComponents = new HashSet<Component>();
                        }
                        return newComponents;
                    });
        }

        private Set<Label> getOrCreateIssueLabels(OverbaardIssueEvent.Detail evtDetail) {
            return getOrCreateIssueLabels(evtDetail.getLabels());
        }

        private Set<Label> getOrCreateIssueLabels(Collection<com.atlassian.jira.issue.label.Label> evtLabels) {
            return getOrCreateIssueMultiSelectNameValues(
                    evtLabels,
                    com.atlassian.jira.issue.label.Label::getLabel,
                    name -> new Label(name),
                    () -> labelsCopy == null ? board.sortedLabels.map() : labelsCopy,
                    () -> {
                        if (labelsCopy == null) {
                            labelsCopy = new HashMap<String, Label>(board.sortedLabels.map());
                        }
                        return labelsCopy;
                    },
                    () -> {
                        if (newLabels == null) {
                            newLabels = new HashSet<Label>();
                        }
                        return newLabels;
                    });
        }

        private Set<FixVersion> getOrCreateIssueFixVersions(OverbaardIssueEvent.Detail evtDetail) {
            return getOrCreateIssueFixVersions(evtDetail.getFixVersions());
        }

        private Set<FixVersion> getOrCreateIssueFixVersions(Collection<com.atlassian.jira.project.version.Version> evtFixVersions) {
            return getOrCreateIssueMultiSelectNameValues(
                    evtFixVersions,
                    com.atlassian.jira.project.version.Version::getName,
                    name -> new FixVersion(name),
                    () -> fixVersionsCopy == null ? board.sortedFixVersions.map() : fixVersionsCopy,
                    () -> {
                        if (fixVersionsCopy == null) {
                            fixVersionsCopy = new HashMap<String, FixVersion>(board.sortedFixVersions.map());
                        }
                        return fixVersionsCopy;
                    },
                    () -> {
                        if (newFixVersions == null) {
                            newFixVersions = new HashSet<FixVersion>();
                        }
                        return newFixVersions;
                    });
        }

        private Set<AffectsVersion> getOrCreateIssueAffectsVersions(OverbaardIssueEvent.Detail evtDetail) {
            return getOrCreateIssueAffectsVersions(evtDetail.getAffectsVersions());
        }

        private Set<AffectsVersion> getOrCreateIssueAffectsVersions(Collection<com.atlassian.jira.project.version.Version> evtAffectsVersions) {
            return getOrCreateIssueMultiSelectNameValues(
                    evtAffectsVersions,
                    com.atlassian.jira.project.version.Version::getName,
                    name -> new AffectsVersion(name),
                    () -> affectsVersionsCopy == null ? board.sortedAffectsVersions.map() : affectsVersionsCopy,
                    () -> {
                        if (affectsVersionsCopy == null) {
                            affectsVersionsCopy = new HashMap<String, AffectsVersion>(board.sortedAffectsVersions.map());
                        }
                        return affectsVersionsCopy;
                    },
                    () -> {
                        if (newAffectsVersions == null) {
                            newAffectsVersions = new HashSet<AffectsVersion>();
                        }
                        return newAffectsVersions;
                    });
        }


        private <T, R extends MultiSelectNameOnlyValue> Set<R> getOrCreateIssueMultiSelectNameValues(
                Collection<T> jiraEventValues,
                Function<T, String> nameExtractor,
                Function<String, R> newValueCreator,
                Supplier<Map<String, R>> valueMapSupplier,
                Supplier<Map<String, R>> copyMapSupplier,
                Supplier<Set<R>> newSetSupplier) {
            if (jiraEventValues == null) {
                return null;
            } else if (jiraEventValues.isEmpty()) {
                return Collections.emptySet();
            } else {
                Set<R> values = new HashSet<>();
                for (T jiraEventValue : jiraEventValues) {
                    Map<String, R> valueMap = valueMapSupplier.get();
                    final String name = nameExtractor.apply(jiraEventValue);
                    R value = valueMap.get(name);
                    if (value == null) {
                        value = newValueCreator.apply(name);
                        Map<String, R> copyMap = copyMapSupplier.get();
                        copyMap.put(name, value);
                        newSetSupplier.get().add(value);
                    }
                    values.add(value);
                }
                return values;
            }
        }

        private <K, V> Map<K, V> copyAndPut(Map<K, V> map, K key, V value, Supplier<Map<K, V>> supplier) {
            OverbaardLogger.LOGGER.debug("Board.Updater.copyAndPut - Overwrite {}", key);
            Map<K, V> copy = supplier.get();
            copy.putAll(map);
            copy.put(key, value);
            return Collections.unmodifiableMap(copy);
        }
    }
}
