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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.dmr.ModelNode;
import org.overbaard.jira.OverbaardLogger;
import org.overbaard.jira.impl.BoardManagerImpl;
import org.overbaard.jira.impl.Constants;
import org.overbaard.jira.impl.OverbaardIssueEvent;
import org.overbaard.jira.impl.board.MultiSelectNameOnlyValue.AffectsVersion;
import org.overbaard.jira.impl.board.MultiSelectNameOnlyValue.Component;
import org.overbaard.jira.impl.board.MultiSelectNameOnlyValue.FixVersion;
import org.overbaard.jira.impl.board.MultiSelectNameOnlyValue.Label;
import org.overbaard.jira.impl.config.ParallelTaskGroupPosition;


/**
 * Since Jira 6.4.x does not support web sockets, this maintains a queue of changes. A client connects and gets the full
 * board which has a current {@code view} sequence number. The client then polls every so often, and passes in its known
 * {@code view} sequence number. This class then returns the json of the delta between the client's sequence number and
 * the current {@code view} sequence number.
 *
 * @author Kabir Khan
 */
public class BoardChangeRegistry {

    //Look for items to clean up every 15 seconds
    private static final int CLEANUP_TICK_MS = 15000;

    //Delete items older than 90 seconds
    private static final int CLEANUP_AGE_SECONDS = 90000;

    private final BoardManagerImpl boardManager;
    private volatile Board board;
    private volatile boolean valid = true;

    //The time for the next cleanup
    private volatile long nextCleanup;

    private volatile List<BoardChange> changes = new ArrayList<>();

    private volatile int startView;
    private volatile int endView;

    public BoardChangeRegistry(BoardManagerImpl boardManager, Board board) {
        this.boardManager = boardManager;
        this.board = board;
        this.startView = board.getCurrentView();
        this.endView = startView;
        incrementNextCleanup();
    }

    public BoardChange.Builder addChange(int view, OverbaardIssueEvent event) {
        return new BoardChange.Builder(this, view, event);
    }

    //This gets called by the board change builder
    void registerChange(BoardChange boardChange) {
        //Do the cleanup here if needed, but....
        getChangesListCleaningUpIfNeeded();
        synchronized (this) {
            //....make sure we work on the instance variable when adding
            changes.add(boardChange);
            endView = boardChange.getView();
        }
    }

    //This gets called by the board manager after the board has been built
    public void setBoard(Board board) {
        this.board = board;
    }


    public ModelNode getChangesSince(boolean backlog, int sinceView) throws FullRefreshNeededException {
        //Get a snapshot of the changes
        if (sinceView > endView) {
            //Our board was probably reset since we last connected, so we need to send a full refresh instead
            throw new FullRefreshNeededException();
        }
        if (sinceView < startView) {
            //The client has taken too long to ask for changes
            throw new FullRefreshNeededException();
        }

        final Board board = this.board;
        final List<BoardChange> changes = getChangesListCleaningUpIfNeeded();
        final ChangeSetCollector collector = new ChangeSetCollector(backlog, board.getCurrentView());
        for (BoardChange change : changes) {
            if (change.getView() <= sinceView) {
                continue;
            }
            collector.addChange(change);
            if (change.getView() > board.getCurrentView()) {
                break;
            }
        }

        return collector.serialize(board);
    }

    private List<BoardChange> getChangesListCleaningUpIfNeeded() {
        final long current = System.currentTimeMillis();
        if (current < nextCleanup) {
            return changes;
        }
        final long expiryTime = current - CLEANUP_AGE_SECONDS;
        synchronized (this) {
            if (current < nextCleanup) {
                return changes;
            }

            int firstView = 0;
            if (changes.size() > 0 && changes.get(0).getTime() < expiryTime) {
                final List<BoardChange> changesCopy = new ArrayList<>();
                for (BoardChange change : changes) {
                    if (change.getTime() < expiryTime) {
                        continue;
                    }
                    if (firstView == 0) {
                        firstView = change.getView();
                    }
                    changesCopy.add(change);
                    endView = change.getView();
                }
                startView = firstView;
                changes = new CopyOnWriteArrayList<>(changesCopy);
            }
            incrementNextCleanup();
        }
        return changes;
    }

    private void incrementNextCleanup() {
        nextCleanup = System.currentTimeMillis() + CLEANUP_TICK_MS;
    }

    //Callback for the BoardIssue to convert itself to an IssueChange
    IssueChange createCreateIssueChange(
            Issue issue, Assignee assignee, String issueType, String priority, Set<Component> components,
            Set<Label> labels, Set<FixVersion> fixVersions, Set<AffectsVersion> affectsVersions) {
        IssueChange change = new IssueChange(issue.getProjectCode(), issue.getKey(), null);
        change.type = OverbaardIssueEvent.Type.CREATE;
        change.state = issue.getState();
        change.summary = issue.getSummary();
        change.assignee = assignee.getKey();
        change.issueType = issueType;
        change.priority = priority;

        if (components != null) {
            change.components = new HashSet<>();
            components.forEach(component -> change.components.add(component.getName()));
        }
        if (labels != null) {
            change.labels = new HashSet<>();
            labels.forEach(label -> change.labels.add(label.getName()));
        }
        if (fixVersions != null) {
            change.fixVersions = new HashSet<>();
            fixVersions.forEach(fixVersion -> change.fixVersions.add(fixVersion.getName()));
        }
        if (affectsVersions != null) {
            change.affectsVersions = new HashSet<>();
            affectsVersions.forEach(affectsVersion -> change.affectsVersions.add(affectsVersion.getName()));
        }
        return change;
    }

    public void forceRefresh() {
        OverbaardLogger.LOGGER.debug("Forcing refresh");
        boardManager.forceRefresh(board.getConfig().getCode());
    }

    public void invalidate() {
        valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    private static class NewReferenceCollector {
        private final Map<String, Assignee> newAssignees = new HashMap<>();
        private final Map<String, Component> newComponents = new HashMap<>();
        private final Map<String, Label> newLabels = new HashMap<>();
        private final Map<String, FixVersion> newFixVersions = new HashMap<>();
        private final Map<String, AffectsVersion> newAffectsVersions = new HashMap<>();
        private final Map<String, List<CustomFieldValue>> newCustomFieldValues = new HashMap<>();

        void addNewAssignee(Assignee assignee) {
            newAssignees.put(assignee.getKey(), assignee);
        }

        void addNewComponents(Set<Component> evtNewComponents) {
            evtNewComponents.forEach(c -> newComponents.put(c.getName(), c));
        }

        void addNewLabels(Set<Label> evtNewLabels) {
            evtNewLabels.forEach(l -> newLabels.put(l.getName(), l));
        }

        void addNewFixVersions(Set<FixVersion> evtNewFixVersions) {
            evtNewFixVersions.forEach(f -> newFixVersions.put(f.getName(), f));
        }

        void addNewAffectsVersions(Set<AffectsVersion> evtNewAffectsVersions) {
            evtNewAffectsVersions.forEach(a -> newAffectsVersions.put(a.getName(), a));
        }

        void addNewCustomFieldValues(Map<String, CustomFieldValue> newCustomFields) {
            newCustomFields.forEach((key, value) -> {
                List<CustomFieldValue> list = this.newCustomFieldValues.computeIfAbsent(key, k -> new ArrayList<CustomFieldValue>());
                list.add(value);
            });
        }

        Map<String, Assignee> getNewAssignees() {
            return newAssignees;
        }

        Map<String, Component> getNewComponents() {
            return newComponents;
        }

        public Map<String, Label> getNewLabels() {
            return newLabels;
        }

        public Map<String, FixVersion> getNewFixVersions() {
            return newFixVersions;
        }

        public Map<String, AffectsVersion> getNewAffectsVersions() {
            return newAffectsVersions;
        }

        Map<String, List<CustomFieldValue>> getNewCustomFieldValues() {
            return newCustomFieldValues;
        }

    }

    private class ChangeSetCollector {
        private final boolean backlog;
        private int view;
        private final Map<String, IssueChange> issueChanges = new HashMap<>();
        private final BlacklistChange blacklistChange = new BlacklistChange();
        private NewReferenceCollector newReferenceCollector = new NewReferenceCollector();

        public ChangeSetCollector(boolean backlog, int endView) {
            this.backlog = backlog;
            this.view = endView;
        }

        void addChange(BoardChange boardChange) {
            final String issueKey = boardChange.getEvent().getIssueKey();

            if (!boardChange.isBlacklistEvent()) {
                IssueChange issueChange = issueChanges.get(issueKey);
                if (issueChange == null) {
                    issueChange = IssueChange.create(newReferenceCollector, boardChange);
                    issueChanges.put(issueKey, issueChange);
                } else {
                    issueChange.merge(newReferenceCollector, boardChange);
                    if (issueChange.type == null) {
                        issueChanges.remove(issueChange.issueKey);
                    }
                }
            } else {
                blacklistChange.populate(boardChange);
            }


            if (boardChange.getView() > view) {
                view = boardChange.getView();
            }
        }

        ModelNode serialize(Board board) {
            ModelNode output = new ModelNode();
            ModelNode changes = output.get(Constants.CHANGES);
            changes.get(Constants.VIEW).set(view);

            Set<IssueChange> newIssues = new HashSet<>();
            Set<IssueChange> updatedIssues = new HashSet<>();
            Set<IssueChange> deletedIssues = new HashSet<>();
            Map<String, Set<String>> rerankedIssuesByProject = new HashMap();
            sortIssues(board, newIssues, updatedIssues, deletedIssues, rerankedIssuesByProject);

            ModelNode issues = new ModelNode();
            serializeIssues(issues, newIssues, updatedIssues, deletedIssues);
            if (issues.isDefined()) {
                changes.get(Constants.ISSUES).set(issues);
            }

            serializeAssignees(changes, newReferenceCollector.getNewAssignees());
            serializeMultiSelectNameOnlyValues(changes, Constants.COMPONENTS, newReferenceCollector.getNewComponents());
            serializeMultiSelectNameOnlyValues(changes, Constants.LABELS, newReferenceCollector.getNewLabels());
            serializeMultiSelectNameOnlyValues(changes, Constants.FIX_VERSIONS, newReferenceCollector.getNewFixVersions());
            serializeMultiSelectNameOnlyValues(changes, Constants.AFFECTS_VERSIONS, newReferenceCollector.getNewAffectsVersions());
            serializeCustomFieldValues(changes, newReferenceCollector.getNewCustomFieldValues());
            serializeBlacklist(changes);

            if (rerankedIssuesByProject.size() > 0) {

                for (Map.Entry<String, Set<String>> projectEntry : rerankedIssuesByProject.entrySet()) {

                    final Set<String> rerankedIssues = projectEntry.getValue();
                    final BoardProject project = board.getBoardProject(projectEntry.getKey());
                    final List<String> rankedIssueKeys = project.getRankedIssueKeys();

                    for (int i = 0; i < rankedIssueKeys.size() ; i++) {
                        final String issueKey = rankedIssueKeys.get(i);

                        if (rerankedIssues.contains(issueKey)) {
                            final ModelNode ranked = changes.get(Constants.RANK, projectEntry.getKey());

                            ModelNode rankEntry = new ModelNode();
                            rankEntry.get(Constants.INDEX).set(i);
                            rankEntry.get(Constants.KEY).set(issueKey);
                            ranked.add(rankEntry);
                        }
                    }
                }
            }
            return output;
        }

        private void serializeIssues(ModelNode parent, Set<IssueChange> newIssues, Set<IssueChange> updatedIssues, Set<IssueChange> deletedIssues) {
            serializeIssues(parent, Constants.NEW, newIssues);
            serializeIssues(parent, Constants.UPDATE, updatedIssues);
            serializeIssues(parent, Constants.DELETE, deletedIssues);
        }

        private void serializeIssues(ModelNode parent, String key, Set<IssueChange> issueChanges) {
            if (issueChanges.size() == 0) {
                return;
            }
            ModelNode issuesNode = parent.get(key);
            issueChanges.forEach(change -> issuesNode.add(change.serialize()));
        }

        private void serializeAssignees(ModelNode parent, Map<String, Assignee> newAssignees) {
            if (newAssignees.size() > 0) {
                ModelNode assignees = parent.get(Constants.ASSIGNEES);
                newAssignees.values().forEach(assignee -> assignee.serialize(assignees));
            }
        }

        private void serializeMultiSelectNameOnlyValues(ModelNode parent, String name, Map<String, ? extends MultiSelectNameOnlyValue> newComponents) {
            if (newComponents.size() > 0) {
                ModelNode components = parent.get(name);
                newComponents.values().forEach(component -> component.serialize(components));
            }
        }

        private void serializeCustomFieldValues(ModelNode parent, Map<String, List<CustomFieldValue>> newCustomFieldValues) {
            if (newCustomFieldValues.size() > 0) {
                ModelNode custom = parent.get(Constants.CUSTOM);
                newCustomFieldValues.forEach((key, list) -> list.forEach(value -> value.serializeRegistry(custom.get(key))));
            }
        }

        private void sortIssues(Board board, Set<IssueChange> newIssues, Set<IssueChange> updatedIssues,
                                Set<IssueChange> deletedIssues,
                                Map<String, Set<String>> rerankedIssuesByProject) {
            for (IssueChange change : issueChanges.values()) {
                boolean rank = false;
                if (change.type == OverbaardIssueEvent.Type.CREATE) {
                    if (backlog || change.backlogEndState != null && !change.backlogEndState) {
                        newIssues.add(change);
                        rank = true;
                    }
                } else if (change.type == OverbaardIssueEvent.Type.UPDATE) {
                    if (backlog ||
                            (!change.backlogStartState && change.backlogEndState != null && !change.backlogEndState)) {
                        updatedIssues.add(change);
                        rank = change.reranked;
                    } else if (change.backlogStartState && change.backlogEndState != null && !change.backlogEndState) {
                        //This is being moved from the backlog to the non-backlog with the backlog hidden. Treat this
                        //as an add for the client. We need to create a new IssueChange containing all the relevant data
                        //since an update only contains the changed data
                        IssueChange createWithAllData = board.createCreateIssueChange(BoardChangeRegistry.this, change.issueKey);
                        newIssues.add(createWithAllData);
                        rank = true;
                    } else if (!backlog && !change.backlogStartState && change.backlogEndState != null && change.backlogEndState) {
                        //This is being moved from the non-backlog to the backlog with the backlog hidden. Treat this
                        //as a delete for the client.
                        IssueChange delete = new IssueChange(change.projectCode, change.issueKey, null);
                        delete.type = OverbaardIssueEvent.Type.DELETE;
                        deletedIssues.add(delete);
                    } else {
                        rank = change.reranked;
                    }
                } else if (change.type == OverbaardIssueEvent.Type.DELETE) {
                    deletedIssues.add(change);
                }

                if (rank) {
                    Set<String> rerankedIssues =
                            rerankedIssuesByProject.computeIfAbsent(change.projectCode, k -> new HashSet<String>());
                    rerankedIssues.add(change.issueKey);
                }
            }
        }

        private void serializeBlacklist(ModelNode changes) {
            ModelNode blacklistNode = blacklistChange.serialize();
            if (blacklistNode.isDefined()) {
                changes.get(Constants.BLACKLIST).set(blacklistNode);
            }
        }
    }

    //Will all be called in one thread by ChangeSetCollector, so no need for thread safety
    static class IssueChange {
        private final String projectCode;
        private final String issueKey;
        private OverbaardIssueEvent.Type type;
        private boolean reranked;

        //Will be null if the issue was both created and updated
        private String issueType;
        private String priority;
        private String summary;
        private String assignee;
        private boolean unassigned;
        private Set<String> components;
        private boolean clearedComponents;
        private Set<String> labels;
        private boolean clearedLabels;
        private Set<String> fixVersions;
        private boolean clearedFixVersions;
        private Set<String> affectsVersions;
        private boolean clearedAffectsVersions;
        private String state;
        private Boolean backlogStartState;
        private Boolean backlogEndState;

        private Map<String, CustomFieldValue> customFieldValues;
        private Map<ParallelTaskGroupPosition, Integer> parallelTaskGroupValues;
        private Boolean clearedParallelTaskValues;

        private IssueChange(String projectCode, String issueKey, Boolean backlogState) {
            this.projectCode = projectCode;
            this.issueKey = issueKey;
            this.backlogStartState = backlogState;
        }

        static IssueChange create(NewReferenceCollector newReferenceCollector, BoardChange boardChange) {
            OverbaardIssueEvent event = boardChange.getEvent();
            IssueChange change = new IssueChange(event.getProjectCode(), event.getIssueKey(),  boardChange.getFromBacklogState());
            change.merge(newReferenceCollector, boardChange);

            return change;
        }

        static IssueChange create(Board board, Issue issue) {
            return new IssueChange(issue.getProjectCode(), issue.getKey(), false);
        }

        void merge(NewReferenceCollector newReferenceCollector, BoardChange boardChange) {
            mergeType(boardChange.getEvent());
            if (type == null) {
                //If the issue was both updated and deleted we return null
                return;
            }
            switch (type) {
                case CREATE:
                    reranked = true;
                case UPDATE:
                    if (!reranked) {
                        reranked = boardChange.getEvent().getDetails().isReranked();
                    }
                    mergeFields(boardChange, newReferenceCollector);
                    if (boardChange.getBacklogState() != null) {
                        backlogEndState = boardChange.getBacklogState();
                    }
                    break;
                case DELETE:
                    //No need to do anything, we will not serialize this issue's details
                    //Clear the state change details
                    break;
                default:
            }
        }

        void mergeFields(BoardChange boardChange, NewReferenceCollector newReferenceCollector) {
            OverbaardIssueEvent event = boardChange.getEvent();
            final OverbaardIssueEvent.Detail detail = event.getDetails();
            if (detail.getIssueType() != null) {
                issueType = detail.getIssueType();
            }
            if (detail.getPriority() != null) {
                priority = detail.getPriority();
            }
            if (detail.getSummary() != null) {
                summary = detail.getSummary();
            }
            if (detail.getAssignee() != null) {
                if (detail.getAssignee() == OverbaardIssueEvent.UNASSIGNED) {
                    assignee = null;
                    unassigned = true;
                } else {
                    assignee = detail.getAssignee().getName();
                    unassigned = false;
                    if (boardChange.getNewAssignee() != null) {
                        //We always add the new assignees, even if a later change might remove the need, since the board
                        //only tracks when an assignee is first referenced before creating the BoardChange entries.
                        //Later changes to the board might use this assignee, in which case this information will be needed
                        //on the client
                        newReferenceCollector.addNewAssignee(boardChange.getNewAssignee());
                    }
                }
            }
            if (detail.getComponents() != null) {
                if (detail.getComponents().isEmpty()) {
                    components = null;
                    clearedComponents = true;
                } else {
                    components = new HashSet<>(detail.getComponents().size());
                    detail.getComponents().forEach(comp -> components.add(comp.getName()));
                    clearedComponents = false;
                    if (boardChange.getNewComponents() != null) {
                        //We always add the new components, even if a later change might remove the need, since the board
                        //only tracks when an component is first referenced before creating the BoardChange entries
                        //Later changes to the board might use this component, in which case this information will be needed
                        //on the client
                        newReferenceCollector.addNewComponents(boardChange.getNewComponents());
                    }
                }
            }
            if (detail.getLabels() != null) {
                if (detail.getLabels().isEmpty()) {
                    labels = null;
                    clearedLabels = true;
                } else {
                    labels = new HashSet<>(detail.getLabels().size());
                    detail.getLabels().forEach(lbl -> labels.add(lbl.getLabel()));
                    clearedLabels = false;
                    if (boardChange.getNewLabels() != null) {
                        //We always add the new labels, even if a later change might remove the need, since the board
                        //only tracks when a label is first referenced before creating the BoardChange entries
                        //Later changes to the board might use this label, in which case this information will be needed
                        //on the client
                        newReferenceCollector.addNewLabels(boardChange.getNewLabels());
                    }
                }
            }
            if (detail.getFixVersions() != null) {
                if (detail.getFixVersions().isEmpty()) {
                    fixVersions = null;
                    clearedFixVersions = true;
                } else {
                    fixVersions = new HashSet<>(detail.getFixVersions().size());
                    detail.getFixVersions().forEach(fv-> fixVersions.add(fv.getName()));
                    clearedFixVersions = false;
                    if (boardChange.getNewFixVersions() != null) {
                        //We always add the new labels, even if a later change might remove the need, since the board
                        //only tracks when a label is first referenced before creating the BoardChange entries
                        //Later changes to the board might use this label, in which case this information will be needed
                        //on the client
                        newReferenceCollector.addNewFixVersions(boardChange.getNewFixVersions());
                    }
                }
            }
            if (detail.getAffectsVersions() != null) {
                if (detail.getAffectsVersions().isEmpty()) {
                    affectsVersions = null;
                    clearedAffectsVersions = true;
                } else {
                    affectsVersions = new HashSet<>(detail.getAffectsVersions().size());
                    detail.getAffectsVersions().forEach(fv-> affectsVersions.add(fv.getName()));
                    clearedAffectsVersions = false;
                    if (boardChange.getNewAffectsVersions() != null) {
                        //We always add the new labels, even if a later change might remove the need, since the board
                        //only tracks when a label is first referenced before creating the BoardChange entries
                        //Later changes to the board might use this label, in which case this information will be needed
                        //on the client
                        newReferenceCollector.addNewAffectsVersions(boardChange.getNewAffectsVersions());
                    }
                }
            }
            if (detail.getState() != null) {
                state = detail.getState();
            }
            if (boardChange.getCustomFieldValues() != null) {
                Map<String, CustomFieldValue> customFieldValues = boardChange.getCustomFieldValues();
                Map<String, CustomFieldValue> newCustomFieldValues = boardChange.getNewCustomFieldValues();
                if (this.customFieldValues == null) {
                    this.customFieldValues = new HashMap<>();
                }

                if (newCustomFieldValues != null) {
                    //We always add the new custom field values, even if a later change might remove the need, since the board
                    //only tracks when custom field is first referenced before creating the BoardChange entries.
                    //Later changes to the board might use these custom fields, in which case this information will be needed
                    //on the client
                    newReferenceCollector.addNewCustomFieldValues(newCustomFieldValues);
                }
                customFieldValues.entrySet().forEach(entry -> this.customFieldValues.put(entry.getKey(), entry.getValue()));
            }
            if (boardChange.getParallelTaskGroupValues() != null) {
                if (this.parallelTaskGroupValues == null) {
                    this.parallelTaskGroupValues = new HashMap<>();
                }
                parallelTaskGroupValues.putAll(boardChange.getParallelTaskGroupValues());
                clearedParallelTaskValues = false;
            }
            if (boardChange.isClearParallelTaskGroupValues()) {
                if (parallelTaskGroupValues != null) {
                    clearedParallelTaskValues = true;
                }
                parallelTaskGroupValues = null;
            }
        }

        void mergeType(OverbaardIssueEvent event) {
            OverbaardIssueEvent.Type evtType = event.getType();
            if (type == null) {
                type = evtType;
                return;
            }
            switch (type) {
                case CREATE:
                    //We were created as part of this change-set, so keep CREATE unless we were deleted
                    if (evtType == OverbaardIssueEvent.Type.DELETE) {
                        //We are deleting something created in this change set, so set null as a signal to remove it
                        type = null;
                    }
                    break;
                case UPDATE:
                    type = evtType;
                    break;
                case DELETE:
                    //If an issue was moved to a done state
                    break;
            }
        }

        public ModelNode serialize() {
            ModelNode output = new ModelNode();
            switch (type) {
                case CREATE:
                    output.get(Constants.KEY).set(issueKey);
                    output.get(Constants.TYPE).set(issueType);
                    output.get(Constants.PRIORITY).set(priority);
                    output.get(Constants.SUMMARY).set(summary);
                    if (assignee != null) {
                        output.get(Constants.ASSIGNEE).set(assignee);
                    }
                    if (components != null) {
                        components.forEach(component -> output.get(Constants.COMPONENTS).add(component));
                    }
                    if (labels != null) {
                        labels.forEach(label -> output.get(Constants.LABELS).add(label));
                    }
                    if (fixVersions != null) {
                        fixVersions.forEach(fixVersion -> output.get(Constants.FIX_VERSIONS).add(fixVersion));
                    }
                    if (affectsVersions != null) {
                        affectsVersions.forEach(affectsVersion -> output.get(Constants.AFFECTS_VERSIONS).add(affectsVersion));
                    }
                    if (customFieldValues != null) {
                        customFieldValues.forEach((key,value)-> output.get(Constants.CUSTOM, key).set(value.getKey()));
                    }
                    if (parallelTaskGroupValues != null) {
                        output.get(Constants.PARALLEL_TASKS).set(populateParallelTaskGroupsForCreate());
                    }
                    output.get(Constants.STATE).set(state);
                    break;
                case UPDATE:
                    output.get(Constants.KEY).set(issueKey);
                    if (issueType != null) {
                        output.get(Constants.TYPE).set(issueType);
                    }
                    if (priority != null) {
                        output.get(Constants.PRIORITY).set(priority);
                    }
                    if (summary != null) {
                        output.get(Constants.SUMMARY).set(summary);
                    }
                    if (assignee != null) {
                        output.get(Constants.ASSIGNEE).set(assignee);
                    }
                    if (components != null) {
                        components.forEach(component -> output.get(Constants.COMPONENTS).add(component));
                    }
                    if (labels != null) {
                        labels.forEach(label -> output.get(Constants.LABELS).add(label));
                    }
                    if (fixVersions != null) {
                        fixVersions.forEach(fixVersion -> output.get(Constants.FIX_VERSIONS).add(fixVersion));
                    }
                    if (affectsVersions != null) {
                        affectsVersions.forEach(affectsVersion -> output.get(Constants.AFFECTS_VERSIONS).add(affectsVersion));
                    }
                    if (customFieldValues != null) {
                        customFieldValues.forEach((key, value)-> {
                            ModelNode fieldKey = value == null ? new ModelNode() : new ModelNode(value.getKey());
                            output.get(Constants.CUSTOM, key).set(fieldKey);
                        });
                    }
                    if (parallelTaskGroupValues != null) {
                        parallelTaskGroupValues.forEach((pos, value) -> {
                            output.get(
                                    Constants.PARALLEL_TASKS,
                                    String.valueOf(pos.getGroupIndex()),
                                    String.valueOf(pos.getTaskIndex()))
                                    .set(value);
                        });
                    }
                    if (state != null) {
                        output.get(Constants.STATE).set(state);
                    }
                    if (unassigned) {
                        output.get(Constants.UNASSIGNED).set(true);
                    }
                    if (clearedComponents) {
                        output.get(Constants.CLEAR_COMPONENTS).set(true);
                    }
                    if (clearedLabels) {
                        output.get(Constants.CLEAR_LABELS).set(true);
                    }
                    if (clearedFixVersions) {
                        output.get(Constants.CLEAR_FIX_VERSIONS).set(true);
                    }
                    if (clearedAffectsVersions) {
                        output.get(Constants.CLEAR_AFFECTS_VERSIONS).set(true);
                    }

                    break;
                case DELETE:
                    //No more data needed
                    output.set(issueKey);
                    break;
            }
            return output;
        }

        private ModelNode populateParallelTaskGroupsForCreate() {
            TreeMap<ParallelTaskGroupPosition, Integer> sortedValues = new TreeMap<>(new Comparator<ParallelTaskGroupPosition>() {
                @Override
                public int compare(ParallelTaskGroupPosition o1, ParallelTaskGroupPosition o2) {
                    if (o1.getGroupIndex() == o2.getGroupIndex()) {
                        return o1.getTaskIndex() < o2.getTaskIndex() ? -1 : 1;
                    }
                    return o1.getGroupIndex() < o2.getGroupIndex() ? -1 : 1;
                }
            });
            sortedValues.putAll(parallelTaskGroupValues);

            ModelNode groupsTable = new ModelNode().setEmptyList();
            ModelNode currentGroup = null;
            int lastGroupIndex = -1;
            for (Map.Entry<ParallelTaskGroupPosition, Integer> entry : sortedValues.entrySet()) {
                ParallelTaskGroupPosition position = entry.getKey();
                if (position.getGroupIndex() != lastGroupIndex) {
                    if (currentGroup != null) {
                        groupsTable.add(currentGroup);
                    }
                    lastGroupIndex = position.getGroupIndex();
                    currentGroup = new ModelNode().setEmptyList();

                }
                currentGroup.add(entry.getValue());
            }
            if (currentGroup != null) {
                groupsTable.add(currentGroup);
            }
            return groupsTable;
        }
    }

    private static class BlacklistChange {
        Set<String> states;
        Set<String> issueTypes;
        Set<String> priorities;
        Set<String> issues;
        Set<String> removedIssues;

        private BlacklistChange() {
        }

        void populate(BoardChange change) {
            if (change.getAddedBlacklistState() != null) {
                if (states == null) {
                    states = new HashSet<>();
                }
                states.add(change.getAddedBlacklistState());
            }
            if (change.getAddedBlacklistIssueType() != null) {
                if (issueTypes == null) {
                    issueTypes = new HashSet<>();
                }
                issueTypes.add(change.getAddedBlacklistIssueType());
            }
            if (change.getAddedBlacklistPriority() != null) {
                if (priorities == null) {
                    priorities = new HashSet<>();
                }
                priorities.add(change.getAddedBlacklistPriority());
            }
            if (change.getAddedBlacklistIssue() != null) {
                if (issues == null) {
                    issues = new HashSet<>();
                }
                issues.add(change.getAddedBlacklistIssue());
            }
            if (change.getDeletedBlacklistIssue() != null) {
                if (issues != null) {
                    issues.remove(change.getDeletedBlacklistIssue());
                }
                //It was not added to this change set, so add it to the deleted issues set
                if (removedIssues == null) {
                    removedIssues = new HashSet<>();
                }
                removedIssues.add(change.getDeletedBlacklistIssue());
            }
        }

        ModelNode serialize() {
            ModelNode modelNode = new ModelNode();
            if (states != null) {
                states.forEach(state -> modelNode.get(Constants.STATES).add(state));
            }
            if (issueTypes != null) {
                issueTypes.forEach(type -> modelNode.get(Constants.ISSUE_TYPES).add(type));
            }
            if (priorities != null) {
                priorities.forEach(priority -> modelNode.get(Constants.PRIORITIES).add(priority));
            }
            if (issues != null && issues.size() > 0) { //Check size here as well since removes can happen in populate()
                issues.forEach(issue -> modelNode.get(Constants.ISSUES).add(issue));
            }
            if (removedIssues != null) {
                removedIssues.forEach(issue -> modelNode.get(Constants.REMOVED_ISSUES).add(issue));
            }

            return modelNode;
        }
    }

    public static class FullRefreshNeededException extends Exception {

    }
}
