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
package ut.org.overbaard.jira;

import static org.jboss.dmr.ModelType.LIST;
import static org.overbaard.jira.impl.Constants.AFFECTS_VERSIONS;
import static org.overbaard.jira.impl.Constants.ASSIGNEE;
import static org.overbaard.jira.impl.Constants.ASSIGNEES;
import static org.overbaard.jira.impl.Constants.AVATAR;
import static org.overbaard.jira.impl.Constants.BLACKLIST;
import static org.overbaard.jira.impl.Constants.CHANGES;
import static org.overbaard.jira.impl.Constants.CLEAR_AFFECTS_VERSIONS;
import static org.overbaard.jira.impl.Constants.CLEAR_COMPONENTS;
import static org.overbaard.jira.impl.Constants.CLEAR_FIX_VERSIONS;
import static org.overbaard.jira.impl.Constants.CLEAR_LABELS;
import static org.overbaard.jira.impl.Constants.COMPONENTS;
import static org.overbaard.jira.impl.Constants.CUSTOM;
import static org.overbaard.jira.impl.Constants.DELETE;
import static org.overbaard.jira.impl.Constants.EMAIL;
import static org.overbaard.jira.impl.Constants.FIX_VERSIONS;
import static org.overbaard.jira.impl.Constants.INDEX;
import static org.overbaard.jira.impl.Constants.ISSUES;
import static org.overbaard.jira.impl.Constants.ISSUE_TYPES;
import static org.overbaard.jira.impl.Constants.KEY;
import static org.overbaard.jira.impl.Constants.LABELS;
import static org.overbaard.jira.impl.Constants.NAME;
import static org.overbaard.jira.impl.Constants.NEW;
import static org.overbaard.jira.impl.Constants.PARALLEL_TASKS;
import static org.overbaard.jira.impl.Constants.PRIORITIES;
import static org.overbaard.jira.impl.Constants.PRIORITY;
import static org.overbaard.jira.impl.Constants.PROJECTS;
import static org.overbaard.jira.impl.Constants.RANK;
import static org.overbaard.jira.impl.Constants.REMOVED_ISSUES;
import static org.overbaard.jira.impl.Constants.STATES;
import static org.overbaard.jira.impl.Constants.SUMMARY;
import static org.overbaard.jira.impl.Constants.TYPE;
import static org.overbaard.jira.impl.Constants.UNASSIGNED;
import static org.overbaard.jira.impl.Constants.VALUE;
import static org.overbaard.jira.impl.Constants.VIEW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.overbaard.jira.impl.BoardManagerBuilder;
import org.overbaard.jira.impl.OverbaardIssueEvent;
import org.overbaard.jira.impl.board.ProjectParallelTaskOptionsLoaderBuilder;
import org.overbaard.jira.impl.config.ParallelTaskGroupPosition;

import com.atlassian.jira.issue.search.SearchException;

/**
 * Tests the output of what happens when changes are made to the board issues.
 * {@link BoardManagerTest} tests the layout of the board on the server, and how it is serialized to the client on first load/full refresh.
 *
 * @author Kabir Khan
 */
public class BoardChangeRegistryTest extends AbstractBoardTest {

    private void setupInitialBoard(String cfgResource) throws Exception {
        setupInitialBoard(cfgResource, null);
    }

    private void setupInitialBoard(String cfgResource, AdditionalSetup additionalSetup) throws Exception {
        initializeMocks(cfgResource, additionalSetup);
        setupIssues(additionalSetup);
    }

    @Before
    public void setupIssues() throws Exception {
        super.initializeMocks();
        setupIssues(null);
    }

    public void setupIssues(AdditionalSetup additionalSetup) throws SearchException {
        if (additionalSetup == null || !additionalSetup.skipStandardIssues()) {
            issueRegistry.issueBuilder("TDP", "task", "highest", "One", "TDP-A")
                    .assignee("kabir").affectsVersions("A1").components("C1").labels("L1").fixVersions("F1").buildAndRegister();     //1
            issueRegistry.issueBuilder("TDP", "task", "high", "Two", "TDP-B")
                    .assignee("kabir").affectsVersions("A2").components("C2").labels("L2").fixVersions("F2").buildAndRegister();     //2
            issueRegistry.issueBuilder("TDP", "task", "low", "Three", "TDP-C")
                    .assignee("kabir").buildAndRegister();                      //3
            issueRegistry.issueBuilder("TDP", "task", "lowest", "Four", "TDP-D")
                    .assignee("brian").buildAndRegister();                      //4
            issueRegistry.issueBuilder("TDP", "task", "highest", "Five", "TDP-A")
                    .assignee("kabir").buildAndRegister();                      //5
            issueRegistry.issueBuilder("TDP", "bug", "high", "Six", "TDP-B")
                    .assignee("kabir").buildAndRegister();                      //6
            issueRegistry.issueBuilder("TDP", "feature", "low", "Seven", "TDP-C")
                    .affectsVersions("A1").components("C1").labels("L1").fixVersions("F1").buildAndRegister();                      //7

            issueRegistry.issueBuilder("TBG", "task", "highest", "One", "TBG-X")
                    .assignee("kabir").affectsVersions("A3").components("C3").labels("L3").fixVersions("F3").buildAndRegister();     //1
            issueRegistry.issueBuilder("TBG", "bug", "high", "Two", "TBG-Y")
                    .assignee("kabir").buildAndRegister();                      //2
            issueRegistry.issueBuilder("TBG", "feature", "low", "Three", "TBG-X")
                    .buildAndRegister();                                        //3
        }

        if (additionalSetup != null) {
            additionalSetup.setupIssues();
        }

        checkViewId(0);
        checkNoIssueChanges(0, 0);
    }

    @Test
    public void testFullRefreshOnTooHighView() throws Exception {
        String json = boardManager.getChangesJson(userManager.getUserByKey("kabir"), false, "TST", 1);
        ModelNode changes = ModelNode.fromJSONString(json);

        Assert.assertFalse(changes.hasDefined(CHANGES));
        Assert.assertFalse(changes.hasDefined("blacklist"));
        //Check that the top-level fields of the board are there
        Assert.assertTrue(changes.hasDefined(VIEW));
        Assert.assertTrue(changes.hasDefined("assignees"));
        Assert.assertTrue(changes.hasDefined("priorities"));
        Assert.assertTrue(changes.hasDefined("issue-types"));
        Assert.assertTrue(changes.hasDefined("projects"));
        Assert.assertTrue(changes.hasDefined(ISSUES));

        //TODO should check the same when passing in an old view id that has been reaped after being too old
    }

    @Test
    public void testDeleteIssues() throws Exception {
        OverbaardIssueEvent delete = OverbaardIssueEvent.createDeleteEvent("TDP-3", "TDP");
        boardManager.handleEvent(delete, nextRankedIssueUtil);
        ModelNode changesNode = getChangesJson(0, 1);
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-3");

        delete = OverbaardIssueEvent.createDeleteEvent("TDP-7", "TDP");
        boardManager.handleEvent(delete, nextRankedIssueUtil);
        changesNode = getChangesJson(0, 2);
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-3", "TDP-7");


        delete = OverbaardIssueEvent.createDeleteEvent("TBG-1", "TBG");
        boardManager.handleEvent(delete, nextRankedIssueUtil);
        changesNode = getChangesJson(0, 3);
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-3", "TDP-7", "TBG-1");
        changesNode = getChangesJson(1, 3);
        checkDeletes(changesNode, "TDP-7", "TBG-1");
        changesNode = getChangesJson(2, 3);
        checkDeletes(changesNode, "TBG-1");
    }

    @Test
    public void testCreateIssuesAssignees() throws Exception {
        //Add an issue which does not bring in new expectedAssignees
        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.BUG, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-D")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        ModelNode changesNode = getChangesJson(0, 1, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode, new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir"));

        //Now add an issue which brings in new expectedAssignees
        create = createEventBuilder("TBG-4", IssueType.FEATURE, Priority.LOW, "Four")
                .assignee("jason")
                .state("TBG-X")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        //0 -> 2
        changesNode = getChangesJson(0, 2, new NewAssigneesChecker("jason"),
                new NewRankChecker().rank((7), "TDP-8").rank(3, "TBG-4"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir"),
                new AddIssueData("TBG-4", IssueType.FEATURE, Priority.LOW, "Four", "TBG-X", "jason"));
        //1 -> 2
        changesNode = getChangesJson(1, 2, new NewAssigneesChecker("jason"),
                new NewRankChecker().rank(3, "TBG-4"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TBG-4", IssueType.FEATURE, Priority.LOW, "Four", "TBG-X", "jason"));


        //Add another one not bringing in new expectedAssignees
        create = createEventBuilder("TDP-9", IssueType.BUG, Priority.HIGH, "Nine")
                .state("TDP-D")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        //0 -> 3
        changesNode = getChangesJson(0, 3, new NewAssigneesChecker("jason"),
                new NewRankChecker().rank(7, "TDP-8").rank(3, "TBG-4").rank(8, "TDP-9"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir"),
                new AddIssueData("TBG-4", IssueType.FEATURE, Priority.LOW, "Four", "TBG-X", "jason"),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", null));
        //1 -> 3
        changesNode = getChangesJson(1, 3, new NewAssigneesChecker("jason"),
                new NewRankChecker().rank(3, "TBG-4").rank(8, "TDP-9"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TBG-4", IssueType.FEATURE, Priority.LOW, "Four", "TBG-X", "jason"),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", null));
        //2 -> 3
        changesNode = getChangesJson(2, 3, new NewRankChecker().rank(8, "TDP-9"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", null));
    }

    @Test
    public void testCreateIssuesMultiSelectNameOnlyValues() throws Exception {
        //Add an issue which does not bring in new components, labels or fix versions
        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.BUG, Priority.HIGH, "Eight")
                .assignee("kabir")
                .affectsVersions("A1", "A2")
                .components("C1", "C2")
                .labels("L1", "L2")
                .fixVersions("F1", "F2")
                .state("TDP-D")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        ModelNode changesNode = getChangesJson(0, 1, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir")
                        .affectsVersions("A5", "A6").affectsVersions("A1", "A2").components("C1", "C2").labels("L1", "L2").fixVersions("F1", "F2"));

        //Now add an issue which brings in new components, labels and fix versions
        create = createEventBuilder("TBG-4", IssueType.FEATURE, Priority.LOW, "Four")
                .assignee("kabir")
                .affectsVersions("A5", "A6")
                .components("C5", "C6")
                .labels("L5", "L6")
                .fixVersions("F5", "F6")
                .state("TBG-X")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        //0 -> 2
        changesNode = getChangesJson(0, 2,
                new NewAffectsVersionsChecker("A5", "A6"),
                new NewComponentsChecker("C5", "C6"),
                new NewLabelsChecker("L5", "L6"),
                new NewFixVersionsChecker("F5", "F6"),
                new NewRankChecker().rank(7, "TDP-8").rank(3, "TBG-4"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir")
                        .affectsVersions("A1", "A2").components("C1", "C2").labels("L1", "L2").fixVersions("F1", "F2"),
                new AddIssueData("TBG-4", IssueType.FEATURE, Priority.LOW, "Four", "TBG-X", "kabir")
                        .affectsVersions("A5", "A6").components("C5", "C6").labels("L5", "L6").fixVersions("F5", "F6"));
        //1 -> 2
        changesNode = getChangesJson(1, 2,
                new NewAffectsVersionsChecker("A5", "A6"),
                new NewComponentsChecker("C5", "C6"),
                new NewLabelsChecker("L5", "L6"),
                new NewFixVersionsChecker("F5", "F6"),
                new NewRankChecker().rank(3, "TBG-4"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TBG-4", IssueType.FEATURE, Priority.LOW, "Four", "TBG-X", "kabir")
                        .affectsVersions("A5", "A6").components("C5", "C6").labels("L5", "L6").fixVersions("F5", "F6"));


        //Add another one not bringing in new components, labels of fix versions
        create = createEventBuilder("TDP-9", IssueType.BUG, Priority.HIGH, "Nine")
                .state("TDP-D")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        //0 -> 3
        changesNode = getChangesJson(0, 3,
                new NewAffectsVersionsChecker("A5", "A6"),
                new NewComponentsChecker("C5", "C6"),
                new NewLabelsChecker("L5", "L6"),
                new NewFixVersionsChecker("F5", "F6"),
                new NewRankChecker().rank(7, "TDP-8").rank(3, "TBG-4").rank(8, "TDP-9"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir")
                        .affectsVersions("A1", "A2").components("C1", "C2").labels("L1", "L2").fixVersions("F1", "F2"),
                new AddIssueData("TBG-4", IssueType.FEATURE, Priority.LOW, "Four", "TBG-X", "kabir")
                        .affectsVersions("A5", "A6").components("C5", "C6").labels("L5", "L6").fixVersions("F5", "F6"),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", null));
        //1 -> 3
        changesNode = getChangesJson(1, 3,
                new NewAffectsVersionsChecker("A5", "A6"),
                new NewComponentsChecker("C5", "C6"),
                new NewLabelsChecker("L5", "L6"),
                new NewFixVersionsChecker("F5", "F6"),
                new NewRankChecker().rank(3, "TBG-4").rank(8, "TDP-9"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TBG-4", IssueType.FEATURE, Priority.LOW, "Four", "TBG-X", "kabir")
                        .affectsVersions("A5", "A6").components("C5", "C6").labels("L5", "L6").fixVersions("F5", "F6"),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", null));
        //2 -> 3
        changesNode = getChangesJson(2, 3, new NewRankChecker().rank(8, "TDP-9"));
        checkDeletes(changesNode);
        checkUpdates(changesNode);
        checkAdds(changesNode,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", null));
    }

    @Test
    public void testCreateIssuesCustomFields() throws Exception {
        final Long testerId = 121212121212L;
        final Long documenterId = 121212121213L;
        final Long testedById = 121212121214L;
        setupInitialBoard("config/board-custom.json", new AbstractAdditionalSetup() {
            @Override
            public void initialise(BoardManagerBuilder boardManagerBuilder) {
                ProjectParallelTaskOptionsLoaderBuilder loader = new ProjectParallelTaskOptionsLoaderBuilder();
                loader
                        .addCustomFieldOption("TDP", testedById, "QE", "QE")
                        .addCustomFieldOption("TDP", testedById, "Engineering", "Engineering");
                boardManagerBuilder.setProjectCustomFieldOptionsLoader(loader.build());
            }
        });

        //Create an issue which does not bring in custom fields
        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.BUG, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-D")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes, new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir"));

        //Create an issue which brings in a custom field
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(testerId, "jason");
        customFieldValues.put(testedById, "QE");
        create = createEventBuilder("TDP-9", IssueType.BUG, Priority.HIGH, "Nine")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        //0 -> 2
        changes = getChangesJson(0, 2, new NewCustomFieldChecker().testers("jason"),
                new NewRankChecker().rank(7, "TDP-8").rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(TesterChecker.NONE, DocumenterChecker.NONE, TestedByChecker.NONE),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(new TesterChecker("jason"), DocumenterChecker.NONE, new TestedByChecker("QE")));
        //1 -> 2
        changes = getChangesJson(1, 2, new NewCustomFieldChecker().testers("jason"),
                new NewRankChecker().rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(new TesterChecker("jason"), DocumenterChecker.NONE, new TestedByChecker("QE")));

        //Create an issue which brings in a custom field and reuses one of the existing ones
        customFieldValues = new HashMap<>();
        customFieldValues.put(testerId, "jason");
        customFieldValues.put(documenterId, "kabir");
        customFieldValues.put(testedById, "Engineering");
        create = createEventBuilder("TDP-10", IssueType.BUG, Priority.HIGH, "Ten")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        //0 -> 3
        changes = getChangesJson(0, 3, new NewCustomFieldChecker().testers("jason").documenters("kabir"),
                new NewRankChecker().rank(7, "TDP-8").rank(8, "TDP-9").rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(TesterChecker.NONE, DocumenterChecker.NONE),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(new TesterChecker("jason"), DocumenterChecker.NONE, new TestedByChecker("QE")),
                new AddIssueData("TDP-10", IssueType.BUG, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(new TesterChecker("jason"), new DocumenterChecker("kabir"), new TestedByChecker("Engineering")));
        //1 -> 3
        changes = getChangesJson(1, 3, new NewCustomFieldChecker().testers("jason").documenters("kabir"),
                new NewRankChecker().rank(8, "TDP-9").rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(new TesterChecker("jason"), DocumenterChecker.NONE, new TestedByChecker("QE")),
                new AddIssueData("TDP-10", IssueType.BUG, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(new TesterChecker("jason"), new DocumenterChecker("kabir"), new TestedByChecker("Engineering")));
        //2 -> 3
        changes = getChangesJson(2, 3, new NewCustomFieldChecker().documenters("kabir"), new NewRankChecker().rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-10", IssueType.BUG, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(new TesterChecker("jason"), new DocumenterChecker("kabir"), new TestedByChecker("Engineering")));

        //Create an issue which brings in no custom fields
        create = createEventBuilder("TDP-11", IssueType.BUG, Priority.HIGH, "Eleven")
                .assignee("kabir")
                .state("TDP-D")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        //0 -> 4
        changes = getChangesJson(0, 4, new NewCustomFieldChecker().testers("jason").documenters("kabir"),
                new NewRankChecker().rank(7, "TDP-8").rank(8, "TDP-9").rank(9, "TDP-10").rank(10, "TDP-11"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(TesterChecker.NONE, DocumenterChecker.NONE, TestedByChecker.NONE),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(new TesterChecker("jason"), DocumenterChecker.NONE, new TestedByChecker("QE")),
                new AddIssueData("TDP-10", IssueType.BUG, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(new TesterChecker("jason"), new DocumenterChecker("kabir"), new TestedByChecker("Engineering")),
                new AddIssueData("TDP-11", IssueType.BUG, Priority.HIGH, "Eleven", "TDP-D", "kabir").checkers(TesterChecker.NONE, DocumenterChecker.NONE, TestedByChecker.NONE));
        //1 -> 4
        changes = getChangesJson(1, 4, new NewCustomFieldChecker().testers("jason").documenters("kabir"),
                new NewRankChecker().rank(8, "TDP-9").rank(9, "TDP-10").rank(10, "TDP-11"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(new TesterChecker("jason"), DocumenterChecker.NONE, new TestedByChecker("QE")),
                new AddIssueData("TDP-10", IssueType.BUG, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(new TesterChecker("jason"), new DocumenterChecker("kabir"), new TestedByChecker("Engineering")),
                new AddIssueData("TDP-11", IssueType.BUG, Priority.HIGH, "Eleven", "TDP-D", "kabir").checkers(TesterChecker.NONE, DocumenterChecker.NONE, TestedByChecker.NONE));

        //2 -> 4
        changes = getChangesJson(2, 4, new NewCustomFieldChecker().documenters("kabir"),
                new NewRankChecker().rank(9, "TDP-10").rank(10, "TDP-11"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-10", IssueType.BUG, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(new TesterChecker("jason"), new DocumenterChecker("kabir"), new TestedByChecker("Engineering")),
                new AddIssueData("TDP-11", IssueType.BUG, Priority.HIGH, "Eleven", "TDP-D", "kabir").checkers(TesterChecker.NONE, DocumenterChecker.NONE, TestedByChecker.NONE));
        //3 -> 4
        changes = getChangesJson(3, 4, new NewRankChecker().rank(10, "TDP-11"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-11", IssueType.BUG, Priority.HIGH, "Eleven", "TDP-D", "kabir").checkers(TesterChecker.NONE, DocumenterChecker.NONE, TestedByChecker.NONE));
    }

    @Test
    public void testUpdateSameIssueNoNewData() throws Exception {
        //Do a noop update
        OverbaardIssueEvent update = updateEventBuilder("TDP-7").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = checkNoIssueChanges(0, 0);

        update = updateEventBuilder("TDP-7").summary("Seven-1").buildAndRegister();

        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 1);
        //Check expectedAssignees and deletes extra well here so we don't have to in the other tests
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").summary("Seven-1"));
        checkAdds(changes);

        update = updateEventBuilder("TDP-7").issueType(IssueType.BUG).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        //0 -> 2
        changes = getChangesJson(0, 2);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").type(IssueType.BUG).summary("Seven-1"));
        checkAdds(changes);
        //1 -> 2
        changes = getChangesJson(1, 2);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").type(IssueType.BUG));
        checkAdds(changes);


        update = updateEventBuilder("TDP-7").priority(Priority.HIGHEST).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        //0 -> 3
        changes = getChangesJson(0, 3);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").type(IssueType.BUG).priority(Priority.HIGHEST).summary("Seven-1"));
        checkAdds(changes);
        //1 -> 3
        changes = getChangesJson(1, 3);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").type(IssueType.BUG).priority(Priority.HIGHEST));
        checkAdds(changes);
        //2 -> 3
        changes = getChangesJson(2, 3);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").priority(Priority.HIGHEST));
        checkAdds(changes);
    }


    @Test
    public void testUpdateSameIssueAssignees() throws Exception {
        //Do an update not bringing in any new data
        OverbaardIssueEvent update = updateEventBuilder("TDP-7").assignee("kabir").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").assignee("kabir"));
        checkAdds(changes);

        update = updateEventBuilder("TDP-7").unassign().buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 2);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").unassigned());
        checkAdds(changes);
        changes = getChangesJson(1, 2);
        checkUpdates(changes, new UpdateIssueData("TDP-7").unassigned());

        update = updateEventBuilder("TDP-7").assignee("jason").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        //0->3
        changes = getChangesJson(0, 3, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").assignee("jason"));
        checkAdds(changes);
        //1 -> 3
        changes = getChangesJson(1, 3, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").assignee("jason"));
        checkAdds(changes);
        //2 -> 3
        changes = getChangesJson(2, 3, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").assignee("jason"));
        checkAdds(changes);

        update = updateEventBuilder("TDP-7").assignee("brian").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        //0->4
        changes = getChangesJson(0, 4, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").assignee("brian"));
        checkAdds(changes);
        //1 -> 4
        changes = getChangesJson(1, 4, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").assignee("brian"));
        checkAdds(changes);
        //2 -> 4
        changes = getChangesJson(2, 4, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").assignee("brian"));
        checkAdds(changes);
        //3 -> 4
        changes = getChangesJson(3, 4);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").assignee("brian"));
        checkAdds(changes);
    }

    @Test
    public void testUpdateSameIssueMultiSelectNameOnlyValues() throws Exception {
        //Do an update not bringing in any new data
        OverbaardIssueEvent update = updateEventBuilder("TDP-7")
                .affectsVersions("A1").components("C1").labels("L1").fixVersions("F1").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").affectsVersions("A1").components("C1").labels("L1").fixVersions("F1"));
        checkAdds(changes);

        update = updateEventBuilder("TDP-7").clearAffectsVersions().clearComponents().clearLabels().clearFixVersions().buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        for (int i = 0; i <= 1; i++) {
            changes = getChangesJson(i, 2);
            checkDeletes(changes);
            checkUpdates(changes, new UpdateIssueData("TDP-7").clearedAffectsVersions().clearedComponents().clearedLabels().clearedFixVersions());
            checkAdds(changes);
        }
        update = updateEventBuilder("TDP-7").affectsVersions("A-10").components("C-10").labels("L-10").fixVersions("F-10").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        for (int i = 0; i <= 2; i++) {
            changes = getChangesJson(i, 3,
                    new NewAffectsVersionsChecker("A-10"),
                    new NewComponentsChecker("C-10"),
                    new NewLabelsChecker("L-10"),
                    new NewFixVersionsChecker("F-10"));
            checkDeletes(changes);
            checkUpdates(changes, new UpdateIssueData("TDP-7").affectsVersions("A-10").components("C-10").labels("L-10").fixVersions("F-10"));
            checkAdds(changes);
        }

        update = updateEventBuilder("TDP-7").affectsVersions("A1", "A2").components("C1", "C2").labels("L1", "L2").fixVersions("F1", "F2").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        //0. 1 and 2 -> 4
        for (int i = 0; i <= 2; i++) {
            changes = getChangesJson(i, 4,
                    new NewAffectsVersionsChecker("A-10"),
                    new NewComponentsChecker("C-10"),
                    new NewLabelsChecker("L-10"),
                    new NewFixVersionsChecker("F-10"));
            checkDeletes(changes);
            checkUpdates(changes, new UpdateIssueData("TDP-7").affectsVersions("A1", "A2").components("C1", "C2").labels("L1", "L2").fixVersions("F1", "F2"));
            checkAdds(changes);
        }
        //3 -> 4
        changes = getChangesJson(3, 4);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").affectsVersions("A1", "A2").components("C1", "C2").labels("L1", "L2").fixVersions("F1", "F2"));
        checkAdds(changes);
    }

    @Test
    public void testUpdateSameIssueCustomFields() throws Exception {
        final Long testerId = 121212121212L;
        final Long documenterId = 121212121213L;
        final Long testedById = 121212121214L;
        setupInitialBoard("config/board-custom.json", new AbstractAdditionalSetup() {
            @Override
            public void initialise(BoardManagerBuilder boardManagerBuilder) {
                ProjectParallelTaskOptionsLoaderBuilder loader = new ProjectParallelTaskOptionsLoaderBuilder();
                loader
                        .addCustomFieldOption("TDP", testedById, "QE", "QE")
                        .addCustomFieldOption("TDP", testedById, "Engineering", "Engineering");
                boardManagerBuilder.setProjectCustomFieldOptionsLoader(loader.build());
            }

            @Override
            public void setupIssues() {
                //Make sure that 'kabir' is in the list of custom fields
                issueRegistry.setCustomField("TDP-1", testerId, userManager.getUserByKey("brian"));
                issueRegistry.setCustomField("TDP-1", documenterId, userManager.getUserByKey("stuart"));
                issueRegistry.setCustomField("TDP-1", testedById, userManager.getUserByKey("Engineering"));
            }
        });

        //Do an update not bringing in any new data (for 'tested by' we always have the full list)
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(testerId, "brian");
        customFieldValues.put(documenterId, "stuart");
        customFieldValues.put(testedById, "QE");
        OverbaardIssueEvent update = updateEventBuilder("TDP-7").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("brian"), new DocumenterChecker("stuart"), new TestedByChecker("QE")));

        //Clear one of the custom fields
        customFieldValues = new HashMap<>();
        customFieldValues.put(testerId, "");
        update = updateEventBuilder("TDP-7").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 2);
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(TesterChecker.UNDEFINED, new DocumenterChecker("stuart"), new TestedByChecker("QE")));
        changes = getChangesJson(1, 2);
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(TesterChecker.UNDEFINED, DocumenterChecker.NONE, TestedByChecker.NONE));

        //Clear the other custom fields
        customFieldValues = new HashMap<>();
        customFieldValues.put(documenterId, "");
        customFieldValues.put(testedById, "");
        update = updateEventBuilder("TDP-7").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 3);
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(TesterChecker.UNDEFINED, DocumenterChecker.UNDEFINED, TestedByChecker.UNDEFINED));
        changes = getChangesJson(1, 3);
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(TesterChecker.UNDEFINED, DocumenterChecker.UNDEFINED, TestedByChecker.UNDEFINED));
        changes = getChangesJson(2, 3);
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(TesterChecker.NONE, DocumenterChecker.UNDEFINED, TestedByChecker.UNDEFINED));

        //Now add custom fields bringing in new data
        customFieldValues = new HashMap<>();
        customFieldValues.put(testerId, "james");
        customFieldValues.put(documenterId, "jason");
        customFieldValues.put(testedById, "QE"); // Note that for this lookup field, we already have the full list so there is no new data
        update = updateEventBuilder("TDP-7").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 4, new NewCustomFieldChecker().testers("james").documenters("jason"));
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("james"), new DocumenterChecker("jason"), new TestedByChecker("QE")));
        changes = getChangesJson(1, 4, new NewCustomFieldChecker().testers("james").documenters("jason"));
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("james"), new DocumenterChecker("jason"), new TestedByChecker("QE")));
        changes = getChangesJson(2, 4, new NewCustomFieldChecker().testers("james").documenters("jason"));
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("james"), new DocumenterChecker("jason"), new TestedByChecker("QE")));
        changes = getChangesJson(3, 4, new NewCustomFieldChecker().testers("james").documenters("jason"));
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("james"), new DocumenterChecker("jason"), new TestedByChecker("QE")));

        //Update other custom fields bringing in new data
        customFieldValues = new HashMap<>();
        customFieldValues.put(testerId, "jason");
        customFieldValues.put(documenterId, "james");
        update = updateEventBuilder("TDP-7").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 5, new NewCustomFieldChecker().testers("james", "jason").documenters("james", "jason"));
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("jason"), new DocumenterChecker("james")));
        changes = getChangesJson(1, 5, new NewCustomFieldChecker().testers("james", "jason").documenters("james", "jason"));
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("jason"), new DocumenterChecker("james")));
        changes = getChangesJson(2, 5, new NewCustomFieldChecker().testers("james", "jason").documenters("james", "jason"));
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("jason"), new DocumenterChecker("james")));
        changes = getChangesJson(3, 5, new NewCustomFieldChecker().testers("james", "jason").documenters("james", "jason"));
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("jason"), new DocumenterChecker("james")));
        changes = getChangesJson(4, 5, new NewCustomFieldChecker().testers("jason").documenters("james"));
        checkUpdates(changes, new UpdateIssueData("TDP-7").checkers(new TesterChecker("jason"), new DocumenterChecker("james")));
    }


    @Test
    public void testCreateIssuesParallelTasks() throws Exception {
        setupInitialBoard("config/board-parallel-tasks.json", new ParallelTaskInit());

        //Create an issue with parallel tasks
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "IP");
        customFieldValues.put(ParallelTaskInit.downstreamId, "D");
        customFieldValues.put(ParallelTaskInit.documentationId, "TD");
        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.BUG, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes, new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(new ParallelTaskGroupsValueChecker(true, new int[]{1, 2}, new int[]{0})));

        //Create another issue with no parallel tasks, we should default to the default value
        customFieldValues = new HashMap<>();
        create = createEventBuilder("TDP-9", IssueType.BUG, Priority.HIGH, "Nine")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        changes = getChangesJson(1, 2, new NewRankChecker().rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes, new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(new ParallelTaskGroupsValueChecker(true, new int[]{0, 0}, new int[]{0})));

        //Create another issue with null parallel tasks, we should default to the default value
        customFieldValues = null;
        create = createEventBuilder("TDP-10", IssueType.BUG, Priority.HIGH, "Ten")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        changes = getChangesJson(2, 3, new NewRankChecker().rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes, new AddIssueData("TDP-10", IssueType.BUG, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(new ParallelTaskGroupsValueChecker(true, new int[]{0, 0}, new int[]{0})));

        //Create an issue in a project with no parallel tasks set up
        create = createEventBuilder("TBG-4", IssueType.FEATURE, Priority.LOW, "Four")
                .state("TBG-X")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        changes = getChangesJson(3, 4, new NewRankChecker().rank(3, "TBG-4"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes, new AddIssueData("TBG-4", IssueType.FEATURE, Priority.LOW, "Four", "TBG-X", null).checkers(ParallelTaskGroupsValueChecker.NONE));
    }

    @Test
    public void testUpdateIssueParallelTasks() throws Exception {
        setupInitialBoard("config/board-parallel-tasks.json", new ParallelTaskInit());

        //Update an issue with parallel tasks
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "M");
        OverbaardIssueEvent update = updateEventBuilder("TDP-1").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkUpdates(changes, new UpdateIssueData("TDP-1").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, -1}, new int[]{-1})));

        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "IP");
        customFieldValues.put(ParallelTaskInit.downstreamId, "D");
        customFieldValues.put(ParallelTaskInit.documentationId, "TD");
        update = updateEventBuilder("TDP-2").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 2);
        checkUpdates(changes,
                new UpdateIssueData("TDP-1").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, -1}, new int[]{-1})),
                new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{1, 2}, new int[]{0})));
        changes = getChangesJson(1, 2);
        checkUpdates(changes,
                new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{1, 2}, new int[]{0})));


        //Update an issue which does not have parallel task fields
        update = updateEventBuilder("TBG-1").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        checkViewId(2);
        update = updateEventBuilder("TBG-1").issueType(IssueType.BUG).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 3);
        checkUpdates(changes,
                new UpdateIssueData("TDP-1").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, -1}, new int[]{-1})),
                new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{1, 2}, new int[]{0})),
                new UpdateIssueData("TBG-1").type(IssueType.BUG).checkers(ParallelTaskGroupsValueChecker.NONE));
        changes = getChangesJson(1, 3);
        checkUpdates(changes,
                new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{1, 2}, new int[]{0})),
                new UpdateIssueData("TBG-1").type(IssueType.BUG).checkers(ParallelTaskGroupsValueChecker.NONE));
        changes = getChangesJson(2, 3);
        checkUpdates(changes,
                new UpdateIssueData("TBG-1").type(IssueType.BUG).checkers(ParallelTaskGroupsValueChecker.NONE));
    }

    @Test
    public void testCreateAndUpdateIssuesParallelTasks() throws Exception {
        setupInitialBoard("config/board-parallel-tasks.json", new ParallelTaskInit());

        //Create an issue with parallel tasks
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "IP");
        customFieldValues.put(ParallelTaskInit.downstreamId, "D");
        customFieldValues.put(ParallelTaskInit.documentationId, "TD");
        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.BUG, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes, new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(new ParallelTaskGroupsValueChecker(true, new int[]{1, 2}, new int[]{0})));

        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "M");
        customFieldValues.put(ParallelTaskInit.downstreamId, "TD");
        customFieldValues.put(ParallelTaskInit.documentationId, "W");
        OverbaardIssueEvent update = updateEventBuilder("TDP-8").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 2, new NewRankChecker().rank(7, "TDP-8"));
        checkAdds(changes, new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(new ParallelTaskGroupsValueChecker(true, new int[]{2, 0}, new int[]{1})));
        checkDeletes(changes);
        checkUpdates(changes);
        changes = getChangesJson(1, 2);
        checkAdds(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-8").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, 0}, new int[]{1})));
        checkDeletes(changes);
    }

    @Test
    public void testUpdateSeveralIssues() throws Exception {
        OverbaardIssueEvent update = updateEventBuilder("TDP-7").summary("Seven-1").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkDeletes(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-7").summary("Seven-1"));

        update = updateEventBuilder("TBG-3").issueType(IssueType.BUG).assignee("kabir").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 2);
        checkDeletes(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-7").summary("Seven-1"),
                new UpdateIssueData("TBG-3").type(IssueType.BUG).assignee("kabir"));
        checkAdds(changes);

        //////////////////////////////////////////////////////////////////////////////////////
        //Create, update and delete TDP-8 to make sure that does not affect the others

        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.BUG, Priority.HIGH, "Nine")
                .state("TDP-D")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        //0 -> 3
        changes = getChangesJson(0, 3, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-7").summary("Seven-1"),
                new UpdateIssueData("TBG-3").type(IssueType.BUG).assignee("kabir"));
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", null));

        //1 -> 3
        changes = getChangesJson(1, 3, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes,
                new UpdateIssueData("TBG-3").type(IssueType.BUG).assignee("kabir"));
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", null));
        //2 -> 3
        changes = getChangesJson(2, 3, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", null));

        //This should appear as an add for change sets including its previous create, and an update for change
        //sets not including the create
        update = updateEventBuilder("TDP-8").assignee("jason").state("TDP-C").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        //0 -> 4
        changes = getChangesJson(0, 4, new NewAssigneesChecker("jason"), new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-7").summary("Seven-1"),
                new UpdateIssueData("TBG-3").type(IssueType.BUG).assignee("kabir"));
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Nine", "TDP-C", "jason"));
        //1 -> 4
        changes = getChangesJson(1, 4, new NewAssigneesChecker("jason"), new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes,
                new UpdateIssueData("TBG-3").type(IssueType.BUG).assignee("kabir"));
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Nine", "TDP-C", "jason"));
        //2 -> 4
        changes = getChangesJson(2, 4, new NewAssigneesChecker("jason"), new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Nine", "TDP-C", "jason"));
        //3 -> 4
        changes = getChangesJson(3, 4, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-8").state("TDP-C").assignee("jason"));
        checkAdds(changes);

        //This will not appear in change sets including the create, it becomes a noop
        OverbaardIssueEvent delete = OverbaardIssueEvent.createDeleteEvent("TDP-8", "TDP");
        boardManager.handleEvent(delete, nextRankedIssueUtil);
        //0 -> 5
        changes = getChangesJson(0, 5, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-7").summary("Seven-1"),
                new UpdateIssueData("TBG-3").type(IssueType.BUG).assignee("kabir"));
        checkAdds(changes);
        //1 -> 5
        changes = getChangesJson(1, 5, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes,
                new UpdateIssueData("TBG-3").type(IssueType.BUG).assignee("kabir"));
        checkAdds(changes);
        //2 -> 5
        changes = getChangesJson(2, 5, new NewAssigneesChecker("jason"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes);
        //3 -> 5
        changes = getChangesJson(3, 5, new NewAssigneesChecker("jason"));
        checkDeletes(changes, "TDP-8");
        checkUpdates(changes);
        checkAdds(changes);
        //4 -> 5
        changes = getChangesJson(4, 5);
        checkDeletes(changes, "TDP-8");
        checkUpdates(changes);
        checkAdds(changes);
    }

    @Test
    public void testCreateAndDeleteIssueWithNewData() throws Exception {
        final Long testerId = 121212121212L;
        final Long documenterId = 121212121213L;
        final Long testedById = 121212121214L;
        setupInitialBoard("config/board-custom.json", new AbstractAdditionalSetup() {
            @Override
            public void initialise(BoardManagerBuilder boardManagerBuilder) {
                // Just initialise the lookups here since they are needed for the board configuration use. But we
                // don't need to test anything for the testedBy field here (it won't bring in new data)
                ProjectParallelTaskOptionsLoaderBuilder loader = new ProjectParallelTaskOptionsLoaderBuilder();
                loader
                        .addCustomFieldOption("TDP", testedById, "QE", "QE")
                        .addCustomFieldOption("TDP", testedById, "Engineering", "Engineering");
                boardManagerBuilder.setProjectCustomFieldOptionsLoader(loader.build());
            }
        });
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(testerId, "brian");
        customFieldValues.put(documenterId, "stuart");

        OverbaardIssueEvent event = createEventBuilder("TDP-8", IssueType.BUG, Priority.HIGH, "Eight")
                .assignee("jason")
                .labels("L-X", "L-Y")
                .state("TDP-A")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);

        ModelNode changes = getChangesJson(0, 1, new NewAssigneesChecker("jason"),
                new NewLabelsChecker("L-X", "L-Y"),
                new NewCustomFieldChecker().testers("brian").documenters("stuart"),
                new NewRankChecker().rank(7, "TDP-8"));
        checkAdds(changes, new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-A", "jason")
                .labels("L-X", "L-Y").checkers(new TesterChecker("brian"), new DocumenterChecker("stuart")));
        checkUpdates(changes);
        checkDeletes(changes);


        event = OverbaardIssueEvent.createDeleteEvent("TDP-8", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);

        //Although we have deleted the issue introducing the new assignee and components, we should still send those
        //down since they will exist on the server now, so if another issue changes to use those the clients will need a copy.
        changes = getChangesJson(0, 2, new NewAssigneesChecker("jason"),
                new NewLabelsChecker("L-X", "L-Y"),
                new NewCustomFieldChecker().testers("brian").documenters("stuart"));
        checkAdds(changes);
        checkUpdates(changes);
        checkDeletes(changes);
    }

    @Test
    public void testBlacklistBadCreatedState() throws SearchException {
        OverbaardIssueEvent event = createEventBuilder("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight")
                .state("BadState")
                .buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkNoIssueChanges(0, 1, new NewBlackListChecker().states("BadState").keys("TDP-8"));

        event = updateEventBuilder("TDP-8").summary("Eight-1").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = updateEventBuilder("TDP-8").issueType("NewBadType").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = OverbaardIssueEvent.createDeleteEvent("TDP-8", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkNoIssueChanges(0, 2, new NewBlackListChecker().states("BadState").removedKeys("TDP-8"));
        checkNoIssueChanges(1, 2, new NewBlackListChecker().removedKeys("TDP-8"));
    }

    @Test
    public void testBlacklistBadUpdatedState() throws SearchException {
        OverbaardIssueEvent event = updateEventBuilder("TDP-7").state("BadState").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkNoIssueChanges(0, 1, new NewBlackListChecker().states("BadState").keys("TDP-7"));

        event = updateEventBuilder("TDP-7").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = updateEventBuilder("TDP-7").issueType("NewBadType").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = OverbaardIssueEvent.createDeleteEvent("TDP-7", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkNoIssueChanges(0, 2, new NewBlackListChecker().states("BadState").removedKeys("TDP-7"));
        checkNoIssueChanges(1, 2, new NewBlackListChecker().removedKeys("TDP-7"));
    }

    @Test
    public void testBlacklistBadCreatedIssueType() throws SearchException {
        OverbaardIssueEvent event = createEventBuilder("TDP-8", "BadType", Priority.HIGH.name, "Eight")
                .state("TDP-C")
                .buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkNoIssueChanges(0, 1, new NewBlackListChecker().types("BadType").keys("TDP-8"));

        event = updateEventBuilder("TDP-8").summary("Eight-1").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = updateEventBuilder("TDP-8").issueType("NewBadType").buildAndRegister();

        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = OverbaardIssueEvent.createDeleteEvent("TDP-8", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkNoIssueChanges(0, 2, new NewBlackListChecker().types("BadType").removedKeys("TDP-8"));
        checkNoIssueChanges(1, 2, new NewBlackListChecker().removedKeys("TDP-8"));
    }

    @Test
    public void testBlacklistBadUpdatedIssueType() throws SearchException {
        OverbaardIssueEvent event = updateEventBuilder("TDP-7")
                .issueType("BadType")
                .buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        ModelNode changes = checkNoIssueChanges(0, 1, new NewBlackListChecker().types("BadType").keys("TDP-7"));

        event = updateEventBuilder("TDP-7").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = updateEventBuilder("TDP-7").issueType("NewBadType").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = OverbaardIssueEvent.createDeleteEvent("TDP-7", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkNoIssueChanges(0, 2, new NewBlackListChecker().types("BadType").removedKeys("TDP-7"));
        checkNoIssueChanges(1, 2, new NewBlackListChecker().removedKeys("TDP-7"));
    }

    @Test
    public void testBlacklistBadCreatedPriority() throws SearchException {
        OverbaardIssueEvent event = createEventBuilder("TDP-8", IssueType.FEATURE.name, "BadPriority", "Eight")
                .state("TDP-C")
                .buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        ModelNode changes = checkNoIssueChanges(0, 1, new NewBlackListChecker().priorities("BadPriority").keys("TDP-8"));

        event = updateEventBuilder("TDP-8").summary("Eight-1").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = updateEventBuilder("TDP-8").priority("NewBadPriority").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = OverbaardIssueEvent.createDeleteEvent("TDP-8", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkNoIssueChanges(0, 2, new NewBlackListChecker().priorities("BadPriority").removedKeys("TDP-8"));
        checkNoIssueChanges(1, 2, new NewBlackListChecker().removedKeys("TDP-8"));
    }

    @Test
    public void testBlacklistBadUpdatedPriority() throws SearchException {
        OverbaardIssueEvent event = updateEventBuilder("TDP-7")
                .priority("BadPriority")
                .buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        ModelNode changes = checkNoIssueChanges(0, 1, new NewBlackListChecker().priorities("BadPriority").keys("TDP-7"));

        event = updateEventBuilder("TDP-7").summary("Eight-1").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = updateEventBuilder("TDP-7").priority("NewBadPriority").buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkViewId(1); //No change, it was blacklisted already

        event = OverbaardIssueEvent.createDeleteEvent("TDP-7", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);
        checkNoIssueChanges(0, 2, new NewBlackListChecker().priorities("BadPriority").removedKeys("TDP-7"));
        checkNoIssueChanges(1, 2, new NewBlackListChecker().removedKeys("TDP-7"));
    }

    @Test
    public void testChangesToBackLogIssueWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent event = updateEventBuilder("TDP-1").priority(Priority.HIGH).buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").priority(Priority.HIGH));
        checkDeletes(backlogChanges);

        //Backlog not visible
        ModelNode nonBacklogChanges = checkNoIssueChanges(0, 1);
    }

    @Test
    public void testCreateIssueInBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        //Do a create in the backlog
        OverbaardIssueEvent event = createEventBuilder("TDP-8", IssueType.BUG, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-A")
                .buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true, new NewRankChecker().rank(7, "TDP-8"));
        checkAdds(backlogChanges, new AddIssueData("TDP-8", IssueType.BUG, Priority.HIGH, "Eight", "TDP-A", "kabir"));
        checkUpdates(backlogChanges);
        checkDeletes(backlogChanges);

        //Backlog not visible, the change is ignored
        checkNoIssueChanges(0, 1);
    }

    @Test
    public void testMoveIssueFromBacklogToBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent create = updateEventBuilder("TDP-1").state("TDP-B").buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-B"));
        checkDeletes(backlogChanges);

        //Backlog not visible
        //An issue moved from the backlog to the backlog will not show up as a change when the backlog is hidden
        ModelNode nonBacklogChanges = checkNoIssueChanges(0, 1);
    }

    @Test
    public void testMoveIssueFromBacklogToNonBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent create = updateEventBuilder("TDP-2").state("TDP-C").buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-2").state("TDP-C"));
        checkDeletes(backlogChanges);

        //Backlog not visible
        //An issue moved from the backlog to the non-backlog will appear as an add when the backlog is hidden
        ModelNode nonBacklogChanges = getChangesJson(0, 1, new NewRankChecker().rank(1, "TDP-2"));
        checkAdds(nonBacklogChanges,
                new AddIssueData("TDP-2", IssueType.TASK, Priority.HIGH, "Two", "TDP-C", "kabir")
                        .affectsVersions("A2").components("C2").labels("L2").fixVersions("F2"));
        checkDeletes(nonBacklogChanges);
        checkUpdates(nonBacklogChanges);
    }

    @Test
    public void testMoveIssueFromNonBacklogToBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent create = updateEventBuilder("TDP-3").state("TDP-B").buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-3").state("TDP-B"));
        checkDeletes(backlogChanges);

        //Backlog not visible
        //An issue moved from the non-backlog to the backlog will appear as a delete when the backlog is hidden
        ModelNode nonBacklogChanges = getChangesJson(0, 1);
        checkAdds(nonBacklogChanges);
        checkDeletes(nonBacklogChanges, "TDP-3");
        checkUpdates(nonBacklogChanges);
    }

    @Test
    public void testMoveIssueFromNonBacklogToNonBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent create = updateEventBuilder("TDP-3").state("TDP-D").buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-3").state("TDP-D"));
        checkDeletes(backlogChanges);

        //Backlog not visible
        //An issue moved from the non-backlog to the non-backlog will behave normally
        ModelNode nonBacklogChanges = getChangesJson(0, 1);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-3").state("TDP-D"));
        checkDeletes(backlogChanges);
    }

    @Test
    public void testBlacklistWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent create = updateEventBuilder("TDP-2").issueType("Bad Type").priority("Bad Priority").buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        //Backlog visible
        checkNoIssueChanges(0, 1, true, new NewBlackListChecker().types("Bad Type").priorities("Bad Priority").keys("TDP-2"));

        //Backlog invisible
        //Having something blacklisted is a configuration problem, so report this although this issue is in the backlog and not visible
        ModelNode nonBacklogChanges = getChangesJson(0, 1, true, new NewBlackListChecker().types("Bad Type").priorities("Bad Priority").keys("TDP-2"));
        checkNoIssueChanges(nonBacklogChanges);
    }

    @Test
    public void testNewAssigneesForNewIssueInBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.TASK, Priority.HIGH, "Eight")
                .assignee("jason")
                .state("TDP-A")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true, new NewRankChecker().rank(7, "TDP-8"), new NewAssigneesChecker("jason"));
        checkAdds(backlogChanges, new AddIssueData("TDP-8", IssueType.TASK, Priority.HIGH, "Eight", "TDP-A", "jason"));
        checkUpdates(backlogChanges);
        checkDeletes(backlogChanges);

        //Backlog invisible
        //Although the issue is hidden, pull down the new assignee. This is needed, since e.g. another visible issue might be
        //created using that assignee, and the server has no record of which clients have which assignee.
        checkNoIssueChanges(0, 1, new NewAssigneesChecker("jason"));
    }

    @Test
    public void testNewAssigneesForUpdatedIssueInBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent update = updateEventBuilder("TDP-1").assignee("jason").state("TDP-B").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true, new NewAssigneesChecker("jason"));
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-B").assignee("jason"));
        checkDeletes(backlogChanges);

        //Backlog invisible
        //Although the issue is hidden, pull down the new assignee. This is needed, since e.g. another visible issue might be
        //created using that assignee, and the server has no record of which clients have which assignee.
        ModelNode nonBacklogChanges = checkNoIssueChanges(0, 1, new NewAssigneesChecker("jason"));
    }

    @Test
    public void testNewComponentsForNewIssueInBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.TASK, Priority.HIGH, "Eight")
                .assignee("kabir")
                .components("C-X", "C-Y")
                .state("TDP-A")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true, new NewComponentsChecker("C-X", "C-Y"), new NewRankChecker().rank(7, "TDP-8"));
        checkAdds(backlogChanges, new AddIssueData("TDP-8", IssueType.TASK, Priority.HIGH, "Eight", "TDP-A", "kabir").components("C-X", "C-Y"));
        checkUpdates(backlogChanges);
        checkDeletes(backlogChanges);

        //Backlog invisible
        //Although the issue is hidden, pull down the new component. This is needed, since e.g. another visible issue might be
        //created using that component, and the server has no record of which clients have which component.
        checkNoIssueChanges(0, 1, new NewComponentsChecker("C-X", "C-Y"));
    }

    @Test
    public void testNewComponentsForUpdatedIssueInBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        OverbaardIssueEvent update = updateEventBuilder("TDP-1").
                components("C-X", "C-Y")
                .state("TDP-B")
                .buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true, new NewComponentsChecker("C-X", "C-Y"));
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-B").components("C-X", "C-Y"));
        checkDeletes(backlogChanges);

        //Backlog invisible
        //Although the issue is hidden, pull down the new component. This is needed, since e.g. another visible issue might be
        //created using that component, and the server has no record of which clients have which component.
        checkNoIssueChanges(0, 1, new NewComponentsChecker("C-X", "C-Y"));
    }

    @Test
    public void testNewCustomFieldsForNewIssueInBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");
        final Long testerId = 121212121212L;
        final Long documenterId = 121212121213L;

        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(testerId, "kabir");
        customFieldValues.put(documenterId, "stuart");

        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.TASK, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-A")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true, new NewCustomFieldChecker().testers("kabir").documenters("stuart"), new NewRankChecker().rank(7, "TDP-8"));
        checkAdds(backlogChanges, new AddIssueData("TDP-8", IssueType.TASK, Priority.HIGH, "Eight", "TDP-A", "kabir").checkers(new TesterChecker("kabir"), new DocumenterChecker("stuart")));
        checkUpdates(backlogChanges);
        checkDeletes(backlogChanges);

        //Backlog invisible
        //Although the issue is hidden, pull down the new assignee. This is needed, since e.g. another visible issue might be
        //created using that assignee, and the server has no record of which clients have which assignee.
        checkNoIssueChanges(0, 1, new NewCustomFieldChecker().testers("kabir").documenters("stuart"));
    }

    @Test
    public void testNewCustomFieldsForUpdatedIssueInBacklogWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        final Long testerId = 121212121212L;
        final Long documenterId = 121212121213L;

        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(testerId, "kabir");
        customFieldValues.put(documenterId, "stuart");

        OverbaardIssueEvent update = updateEventBuilder("TDP-1")
                .state("TDP-B")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true, new NewCustomFieldChecker().testers("kabir").documenters("stuart"));
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-B").checkers(new TesterChecker("kabir"), new DocumenterChecker("stuart")));
        checkDeletes(backlogChanges);

        //Backlog invisible
        //Although the issue is hidden, pull down the new assignee. This is needed, since e.g. another visible issue might be
        //created using that assignee, and the server has no record of which clients have which assignee.
        ModelNode nonBacklogChanges = checkNoIssueChanges(0, 1, new NewCustomFieldChecker().testers("kabir").documenters("stuart"));
    }

    @Test
    public void testIssueMovedThroughSeveralStatesWithBacklogStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        setupInitialBoard("config/board-tdp-backlog.json");

        //Move to a non-backlog state
        OverbaardIssueEvent update = updateEventBuilder("TDP-1").state("TDP-C").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        //Backlog visible
        ModelNode backlogChanges = getChangesJson(0, 1, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-C"));
        checkDeletes(backlogChanges);

        //Backlog invisible
        ModelNode nonBacklogChanges = getChangesJson(0, 1, new NewRankChecker().rank(0, "TDP-1"));
        //The move from the backlog to a normal state appears as an add
        checkAdds(nonBacklogChanges,
                new AddIssueData("TDP-1", IssueType.TASK, Priority.HIGHEST, "One", "TDP-C", "kabir")
                        .affectsVersions("A1").components("C1").labels("L1").fixVersions("F1"));
        checkUpdates(nonBacklogChanges);
        checkDeletes(nonBacklogChanges);

        //Move to another non-backlog state
        update = updateEventBuilder("TDP-1").state("TDP-D").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        //Backlog visible
        backlogChanges = getChangesJson(0, 2, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-D"));
        checkDeletes(backlogChanges);

        backlogChanges = getChangesJson(1, 2, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-D"));
        checkDeletes(backlogChanges);

        //Backlog invisible
        nonBacklogChanges = getChangesJson(0, 2, new NewRankChecker().rank(0, "TDP-1"));
        //The move from the backlog to a nornal state appears as an add
        checkAdds(nonBacklogChanges,
                new AddIssueData("TDP-1", IssueType.TASK, Priority.HIGHEST, "One", "TDP-D", "kabir")
                        .affectsVersions("A1").components("C1").labels("L1").fixVersions("F1"));
        checkUpdates(nonBacklogChanges);
        checkDeletes(nonBacklogChanges);

        nonBacklogChanges = getChangesJson(1, 2);
        //From a non-backlog to a non-backlog state appears as an update
        checkAdds(nonBacklogChanges);
        checkUpdates(nonBacklogChanges, new UpdateIssueData("TDP-1").state("TDP-D"));
        checkDeletes(nonBacklogChanges);

        //Move to a bl state
        update = updateEventBuilder("TDP-1").state("TDP-A").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        //Backlog visible
        backlogChanges = getChangesJson(0, 3, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-A"));
        checkDeletes(backlogChanges);

        backlogChanges = getChangesJson(1, 3, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-A"));
        checkDeletes(backlogChanges);

        backlogChanges = getChangesJson(2, 3, true);
        checkAdds(backlogChanges);
        checkUpdates(backlogChanges, new UpdateIssueData("TDP-1").state("TDP-A"));
        checkDeletes(backlogChanges);

        //Backlog invisible
        checkNoIssueChanges(0, 3);

        nonBacklogChanges = getChangesJson(1, 3);
        //The non-blacklog->blacklog move appears as a delete
        checkAdds(nonBacklogChanges);
        checkUpdates(nonBacklogChanges);
        checkDeletes(nonBacklogChanges, "TDP-1");

        nonBacklogChanges = getChangesJson(2, 3);
        //The non-blacklog->blacklog move appears as a delete
        checkAdds(nonBacklogChanges);
        checkUpdates(nonBacklogChanges);
        checkDeletes(nonBacklogChanges, "TDP-1");
    }

    @Test
    public void testMoveIssueWithDoneStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with done states set up
        setupInitialBoard("config/board-tdp-done.json");

        //Move an issue into a done state should appear as a delete
        OverbaardIssueEvent update = updateEventBuilder("TDP-1").state("TDP-D").buildAndRegister();
        searchCallback.searched = false;
        boardManager.handleEvent(update, nextRankedIssueUtil);
        Assert.assertFalse(searchCallback.searched);

        ModelNode changes = getChangesJson(0, 1);
        checkAdds(changes);
        checkUpdates(changes);
        checkDeletes(changes, "TDP-1");

        //Move an issue from a done state into a normal state will force a full refresh
        update = updateEventBuilder("TDP-4").state("TDP-A").buildAndRegister();
        searchCallback.searched = false;
        boardManager.handleEvent(update, nextRankedIssueUtil);
        Assert.assertTrue(searchCallback.searched);

        getChangesEnsuringFullRefresh(0);
    }

    @Test
    public void testMoveFromDoneToDoneWithDoneStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with done states set up
        setupInitialBoard("config/board-tdp-done.json");

        //Move an issue already in a done state into a done state should not appear as a change
        OverbaardIssueEvent update = updateEventBuilder("TDP-3").state("TDP-D").buildAndRegister();
        searchCallback.searched = false;
        boardManager.handleEvent(update, nextRankedIssueUtil);
        Assert.assertFalse(searchCallback.searched);

        //No changes
        ModelNode changes = getChangesJson(0, 0);
        checkAdds(changes);
        checkUpdates(changes);
        checkDeletes(changes);
    }

    @Test
    public void testComplexMoveFromDoneResultingInCreateWithDoneStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with done states set up
        setupInitialBoard("config/board-tdp-done.json");

        //Moving a done issue to a non-done state should cause a full refresh
        OverbaardIssueEvent update = updateEventBuilder("TDP-3").state("TDP-A").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        getChangesEnsuringFullRefresh(0);

        //Moving the issue back to a done state should appear as a delete
        update = updateEventBuilder("TDP-3").state("TDP-D").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkAdds(changes);
        checkUpdates(changes);
        checkDeletes(changes, "TDP-3");

        //Moving the issue back to a non-done state should cause a full refresh
        update = updateEventBuilder("TDP-3").state("TDP-A").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        getChangesEnsuringFullRefresh(0);
    }

    @Test
    public void testComplexMoveFromNonDoneResultingInCreateWithDoneStatesConfigured() throws Exception {
        //Override the default configuration set up by the @Before method to one with done states set up
        setupInitialBoard("config/board-tdp-done.json");

        //Moving a done issue to a done state should appear as a delete
        OverbaardIssueEvent update = updateEventBuilder("TDP-2").state("TDP-D").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkAdds(changes);
        checkUpdates(changes);
        checkDeletes(changes, "TDP-2");

        //Moving the issue back to a non-done state should cause a full refresh
        update = updateEventBuilder("TDP-2").state("TDP-A").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        getChangesEnsuringFullRefresh(0);


        //Moving the issue back to a done state should appear as a delete
        update = updateEventBuilder("TDP-2").state("TDP-C").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 1);
        checkAdds(changes);
        checkUpdates(changes);
        checkDeletes(changes, "TDP-2");
    }

    @Test
    public void testDeleteAndRankIssues() throws Exception {
        issueRegistry.deleteIssue("TDP-3");
        OverbaardIssueEvent event = OverbaardIssueEvent.createDeleteEvent("TDP-3", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);
        ModelNode changesNode = getChangesJson(0, 1);
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-3");

        issueRegistry.deleteIssue("TDP-7");
        event = OverbaardIssueEvent.createDeleteEvent("TDP-7", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);
        changesNode = getChangesJson(0, 2);
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-3", "TDP-7");

        issueRegistry.rerankIssue("TDP-1", null);
        event = updateEventBuilder("TDP-1").rank().buildAndRegister();
        boardManager.handleEvent(event, nextRankedIssueUtil);
        //0 -> 3
        changesNode = getChangesJson(0, 3, new NewRankChecker().rank(4, "TDP-1"));
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-3", "TDP-7");
        //1 -> 3
        changesNode = getChangesJson(1, 3, new NewRankChecker().rank(4, "TDP-1"));
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-7");
        //2 -> 3
        changesNode = getChangesJson(2, 3, new NewRankChecker().rank(4, "TDP-1"));
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode);

        //Now delete the reranked issue
        issueRegistry.deleteIssue("TDP-1");
        event = OverbaardIssueEvent.createDeleteEvent("TDP-1", "TDP");
        boardManager.handleEvent(event, nextRankedIssueUtil);
        //0 -> 4
        changesNode = getChangesJson(0, 4);
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-3", "TDP-7", "TDP-1");
        //1 -> 4
        changesNode = getChangesJson(1, 4);
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-7", "TDP-1");
        //2 -> 4
        changesNode = getChangesJson(2, 4);
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-1");
        //3 -> 4
        changesNode = getChangesJson(3, 4);
        checkAdds(changesNode);
        checkUpdates(changesNode);
        checkDeletes(changesNode, "TDP-1");
    }

    @Test
    public void testMoveIssueWithIssueTypeStateLinkOverrides() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        initializeMocks("config/board-issue-type-override-state-links.json");
        issueRegistry.issueBuilder("TDP", "task", "highest", "One", "TDP-A")
                .assignee("kabir").buildAndRegister();
        issueRegistry.issueBuilder("TDP", "bug", "highest", "Two", "TDP-D")
                .assignee("kabir").buildAndRegister();
        issueRegistry.issueBuilder("TDP", "feature", "highest", "Three", "TDP-B")
                .assignee("kabir").buildAndRegister();
        checkViewId(0);

        OverbaardIssueEvent update = updateEventBuilder("TDP-1").state("TDP-D").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        ModelNode changes = getChangesJson(0, 1, true);
        checkAdds(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-1").state("TDP-D"));
        checkDeletes(changes);

        update = updateEventBuilder("TDP-2").state("TDP-A").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        changes = getChangesJson(0, 2, true);
        checkAdds(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-1").state("TDP-D"),
                new UpdateIssueData("TDP-2").state("TDP-A"));
        checkDeletes(changes);
        changes = getChangesJson(1, 2, true);
        checkAdds(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-2").state("TDP-A"));
        checkDeletes(changes);

        // Move an issue that does not have a type with overridden states
        update = updateEventBuilder("TDP-3").state("TDP-C").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        changes = getChangesJson(0, 3, true);
        checkAdds(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-1").state("TDP-D"),
                new UpdateIssueData("TDP-2").state("TDP-A"),
                new UpdateIssueData("TDP-3").state("TDP-C"));
        checkDeletes(changes);
        changes = getChangesJson(1, 3, true);
        checkAdds(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-2").state("TDP-A"),
                new UpdateIssueData("TDP-3").state("TDP-C"));
        checkDeletes(changes);
        changes = getChangesJson(2, 3, true);
        checkAdds(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-3").state("TDP-C"));
        checkDeletes(changes);
    }

    @Test
    public void testCreateIssueWithIssueTypeStateLinkOverrides() throws Exception {
        initializeMocks("config/board-issue-type-override-state-links.json");
        checkViewId(0);

        OverbaardIssueEvent create = createEventBuilder("TDP-1", IssueType.TASK, Priority.HIGH, "One")
                .state("TDP-A")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        ModelNode changes =
                getChangesJson(0, 1, true,
                        new NewRankChecker().rank(0, "TDP-1"));
        checkAdds(changes,
                new AddIssueData("TDP-1", IssueType.TASK, Priority.HIGH, "One", "TDP-A", null));
        checkUpdates(changes);
        checkDeletes(changes);

        create = createEventBuilder("TDP-2", IssueType.BUG, Priority.HIGH, "Two")
                .state("TDP-D")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        changes = getChangesJson(0, 2, true,
                new NewRankChecker().rank(0, "TDP-1").rank(1, "TDP-2"));
        checkAdds(changes,
                new AddIssueData("TDP-1", IssueType.TASK, Priority.HIGH, "One", "TDP-A", null),
                new AddIssueData("TDP-2", IssueType.BUG, Priority.HIGH, "Two", "TDP-D", null));
        checkUpdates(changes);
        checkDeletes(changes);
        changes = getChangesJson(1, 2, true,
                new NewRankChecker().rank(1, "TDP-2"));
        checkAdds(changes,
                new AddIssueData("TDP-2", IssueType.BUG, Priority.HIGH, "Two", "TDP-D", null));
        checkUpdates(changes);
        checkDeletes(changes);

        create = createEventBuilder("TDP-3", IssueType.FEATURE, Priority.HIGH, "Three")
                .state("TDP-C")
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);

        changes = getChangesJson(0, 3, true,
                new NewRankChecker().rank(0, "TDP-1").rank(1, "TDP-2").rank(2, "TDP-3"));
        checkAdds(changes,
                new AddIssueData("TDP-1", IssueType.TASK, Priority.HIGH, "One", "TDP-A", null),
                new AddIssueData("TDP-2", IssueType.BUG, Priority.HIGH, "Two", "TDP-D", null),
                new AddIssueData("TDP-3", IssueType.FEATURE, Priority.HIGH, "Three", "TDP-C", null));
        checkUpdates(changes);
        checkDeletes(changes);
        changes = getChangesJson(1, 3, true,
                new NewRankChecker().rank(1, "TDP-2").rank(2, "TDP-3"));
        checkAdds(changes,
                new AddIssueData("TDP-2", IssueType.BUG, Priority.HIGH, "Two", "TDP-D", null),
                new AddIssueData("TDP-3", IssueType.FEATURE, Priority.HIGH, "Three", "TDP-C", null));
        changes = getChangesJson(2, 3, true,
                new NewRankChecker().rank(2, "TDP-3"));
        checkAdds(changes,
                new AddIssueData("TDP-3", IssueType.FEATURE, Priority.HIGH, "Three", "TDP-C", null));
    }

    @Test
    public void testMoveIssueWithIssueTypeStateLinkOverridesBlacklist() throws Exception {
        //Override the default configuration set up by the @Before method to one with backlog states set up
        initializeMocks("config/board-issue-type-override-state-links.json");
        issueRegistry.issueBuilder("TDP", "task", "highest", "One", "TDP-A")
                .assignee("kabir").buildAndRegister();
        issueRegistry.issueBuilder("TDP", "bug", "highest", "Two", "TDP-D")
                .assignee("kabir").buildAndRegister();
        issueRegistry.issueBuilder("TDP", "feature", "highest", "Three", "TDP-B")
                .assignee("kabir").buildAndRegister();
        checkViewId(0);

        OverbaardIssueEvent update = updateEventBuilder("TDP-1").state("TDP-D").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        ModelNode changes = getChangesJson(0, 1, true);
        checkAdds(changes);
        checkUpdates(changes, new UpdateIssueData("TDP-1").state("TDP-D"));
        checkDeletes(changes);

        update = updateEventBuilder("TDP-2").state("TDP-C").buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);

        changes = getChangesJson(0, 2, true, new NewBlackListChecker().keys("TDP-2").states("TDP-C"));
        checkAdds(changes);
        checkUpdates(changes,
                new UpdateIssueData("TDP-1").state("TDP-D"));
        checkDeletes(changes);
        changes = getChangesJson(1, 2, true, new NewBlackListChecker().keys("TDP-2").states("TDP-C"));
        checkAdds(changes);
        checkUpdates(changes);
        checkDeletes(changes);
    }

    @Test
    public void testCreateIssuesParallelTasksWithIssueTypeOverrides() throws Exception {
        setupInitialBoard("config/board-issue-type-override-parallel-tasks.json", new ParallelTaskInit(true));

        //Create an issue with parallel tasks using a type which does not have overrides
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "IP");
        customFieldValues.put(ParallelTaskInit.downstreamId, "D");
        customFieldValues.put(ParallelTaskInit.documentationId, "TD");
        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{1, 2}, new int[]{0})));

        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "NSb");
        customFieldValues.put(ParallelTaskInit.downstreamId, "IPb");
        customFieldValues.put(ParallelTaskInit.documentationId, "Pb");
        create = createEventBuilder("TDP-9", IssueType.BUG, Priority.HIGH, "Nine")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        changes = getChangesJson(0, 2,
                new NewRankChecker().rank(7, "TDP-8").rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{1, 2}, new int[]{0})),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2}, new int[]{0, 1})));
        changes = getChangesJson(1, 2, new NewRankChecker().rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2}, new int[]{0, 1})));

        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "IPt");
        customFieldValues.put(ParallelTaskInit.downstreamId, "Dt");
        customFieldValues.put(ParallelTaskInit.documentationId, "TDt");
        create = createEventBuilder("TDP-10", IssueType.TASK, Priority.HIGH, "Ten")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        changes = getChangesJson(0, 3,
                new NewRankChecker().rank(7, "TDP-8").rank(8, "TDP-9").rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{1, 2}, new int[]{0})),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2}, new int[]{0, 1})),
                new AddIssueData("TDP-10", IssueType.TASK, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{0}, new int[]{1, 2})));
        changes = getChangesJson(1, 3,
                new NewRankChecker().rank(8, "TDP-9").rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2}, new int[]{0, 1})),
                new AddIssueData("TDP-10", IssueType.TASK, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{0}, new int[]{1, 2})));
        changes = getChangesJson(2, 3,
                new NewRankChecker().rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-10", IssueType.TASK, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{0}, new int[]{1, 2})));
    }

    @Test
    public void testUpdateIssueParallelTasksWithIssueTypeOverrides() throws Exception {
        // Set up the issues manually here as the standard test setup doesn't match our purposes too
        setupInitialBoard("config/board-issue-type-override-parallel-tasks.json", new ParallelTaskInit(true, true) {
            @Override
            public void setupIssues() {
                issueRegistry.issueBuilder("TDP", "feature", "high", "One", "TDP-A")
                        .assignee("kabir").buildAndRegister();      //1
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.upstreamId, "IP");
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.downstreamId, "IP");
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.documentationId, "W");
                issueRegistry.issueBuilder("TDP", "bug", "high", "Two", "TDP-B")
                        .assignee("kabir").buildAndRegister();      //2
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.upstreamId, "Mb");
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.downstreamId, "Db");
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.documentationId, "Pb");
                issueRegistry.issueBuilder("TDP", "task", "high", "Three", "TDP-C")
                        .assignee("kabir").buildAndRegister();      //3
                issueRegistry.setParallelTaskField("TDP-3", BoardManagerTest.ParallelTaskInit.upstreamId, "NSt");
                issueRegistry.setParallelTaskField("TDP-3", BoardManagerTest.ParallelTaskInit.downstreamId, "TDt");
                issueRegistry.setParallelTaskField("TDP-3", BoardManagerTest.ParallelTaskInit.documentationId, "TDt");
            }
        });


        //Update an issue with no parallel task overrides for the issue type
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "M");
        OverbaardIssueEvent update = updateEventBuilder("TDP-1").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkUpdates(changes, new UpdateIssueData("TDP-1").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, -1}, new int[]{-1})));

        // Update an issue with parallel task overrides for the issue type
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "NSb");
        update = updateEventBuilder("TDP-2").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(1, 2);
        checkUpdates(changes, new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{-1}, new int[]{0, -1})));
        changes = getChangesJson(0, 2);
        checkUpdates(changes,
                new UpdateIssueData("TDP-1").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, -1}, new int[]{-1})),
                new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{-1}, new int[]{0, -1})));

        // Update another issue with parallel task overrides for the issue type
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.documentationId, "Pt");
        customFieldValues.put(ParallelTaskInit.downstreamId, "IPt");
        update = updateEventBuilder("TDP-3").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(2, 3);
        checkUpdates(changes, new UpdateIssueData("TDP-3").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2}, new int[]{-1, 1})));
        changes = getChangesJson(1, 3);
        checkUpdates(changes,
                new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{-1}, new int[]{0, -1})),
                new UpdateIssueData("TDP-3").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2}, new int[]{-1, 1})));
        changes = getChangesJson(0, 3);
        checkUpdates(changes,
                new UpdateIssueData("TDP-1").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, -1}, new int[]{-1})),
                new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{-1}, new int[]{0, -1})),
                new UpdateIssueData("TDP-3").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2}, new int[]{-1, 1})));
    }

    @Test
    public void testCreateIssuesParallelTasksWithJustIssueTypeOverrides() throws Exception {
        setupInitialBoard("config/board-issue-type-overrides-only-parallel-tasks.json", new ParallelTaskInit(true));

        //Create an issue and set the parallel task custom fields. The fields should have no effect though, as this issue type
        //is not set up to use parallel tasks
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "IP");
        customFieldValues.put(ParallelTaskInit.downstreamId, "D");
        customFieldValues.put(ParallelTaskInit.documentationId, "TD");
        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        ParallelTaskGroupsValueChecker.NONE));

        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.downstreamId, "IPb");
        customFieldValues.put(ParallelTaskInit.documentationId, "Pb");
        create = createEventBuilder("TDP-9", IssueType.BUG, Priority.HIGH, "Nine")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        changes = getChangesJson(0, 2,
                new NewRankChecker().rank(7, "TDP-8").rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        ParallelTaskGroupsValueChecker.NONE),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2}, new int[]{0, 1})));
        changes = getChangesJson(1, 2, new NewRankChecker().rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2}, new int[]{0, 1})));
    }

    @Test
    public void testUpdateIssueParallelTasksWithJustIssueTypeOverrides() throws Exception {
        // Set up the issues manually here as the standard test setup doesn't match our purposes too
        setupInitialBoard("config/board-issue-type-overrides-only-parallel-tasks.json", new ParallelTaskInit(true, true) {
            @Override
            public void setupIssues() {
                issueRegistry.issueBuilder("TDP", "feature", "high", "One", "TDP-A")
                        .assignee("kabir").buildAndRegister();      //1
                // Set the custom field values in the issue although this issue type is not configured to use PTs
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.upstreamId, "IP");
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.downstreamId, "IP");
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.documentationId, "W");
                issueRegistry.issueBuilder("TDP", "bug", "high", "Two", "TDP-B")
                        .assignee("kabir").buildAndRegister();      //2
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.upstreamId, "Mb");
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.downstreamId, "Db");
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.documentationId, "Pb");
            }
        });


        //Update an issue with no parallel task overrides for the issue type
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "M");
        OverbaardIssueEvent update = updateEventBuilder("TDP-1").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        // As the update to the PT custom field is irrelevant to this issue type the viewId is not changed
        getChangesJson(0, 0);

        // Update an issue with parallel task overrides for the issue type
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "NSb");
        update = updateEventBuilder("TDP-2").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkUpdates(changes, new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{-1}, new int[]{0, -1})));
    }

    @Test
    public void testCreateIssuesParallelTasksWithEmptyIssueTypeOverrides() throws Exception {
        setupInitialBoard("config/board-issue-type-empty-overrides-parallel-tasks.json", new ParallelTaskInit(true));

        //Create an issue with parallel tasks using a type which uses the project PT config
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "IP");
        customFieldValues.put(ParallelTaskInit.downstreamId, "D");
        customFieldValues.put(ParallelTaskInit.documentationId, "TD");
        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{1, 2}, new int[]{0})));

        // Now create an issue with a type which has empty PT overrides
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "NSb");
        customFieldValues.put(ParallelTaskInit.downstreamId, "IPb");
        customFieldValues.put(ParallelTaskInit.documentationId, "Pb");
        create = createEventBuilder("TDP-9", IssueType.BUG, Priority.HIGH, "Nine")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        changes = getChangesJson(0, 2,
                new NewRankChecker().rank(7, "TDP-8").rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{1, 2}, new int[]{0})),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        ParallelTaskGroupsValueChecker.NONE));
        changes = getChangesJson(1, 2, new NewRankChecker().rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        ParallelTaskGroupsValueChecker.NONE));
    }

    @Test
    public void testUpdateIssueParallelTasksWithEmptyIssueTypeOverrides() throws Exception {
        // Set up the issues manually here as the standard test setup doesn't match our purposes too
        setupInitialBoard("config/board-issue-type-empty-overrides-parallel-tasks.json", new ParallelTaskInit(true, true) {
            @Override
            public void setupIssues() {
                issueRegistry.issueBuilder("TDP", "feature", "high", "One", "TDP-A")
                        .assignee("kabir").buildAndRegister();      //1
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.upstreamId, "IP");
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.downstreamId, "IP");
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.documentationId, "W");
                issueRegistry.issueBuilder("TDP", "bug", "high", "Two", "TDP-B")
                        .assignee("kabir").buildAndRegister();      //2
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.upstreamId, "Mb");
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.downstreamId, "Db");
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.documentationId, "Pb");
            }
        });


        //Update an issue with no parallel task overrides for the issue type
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "M");
        OverbaardIssueEvent update = updateEventBuilder("TDP-1").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkUpdates(changes, new UpdateIssueData("TDP-1").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, -1}, new int[]{-1})));

        //Update an issue which has empty PT overrides for the type
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "NSb");
        update = updateEventBuilder("TDP-2").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        // Although we set the custom field id here it is ignored as the issue type is overridden to not use parallel tasks
        // so there is no effect on the board and the view id stays the same
        getChangesJson(1, 1);
    }

    @Test
    public void testCreateIssuesParallelTasksWithNonIntersectingIssueTypeOverrides() throws Exception {
        setupInitialBoard("config/board-issue-type-overrides-parallel-tasks-varying.json", new ParallelTaskInit(true));

        //Create an issue with parallel tasks using a type which uses the project PT config, use some irrelevant ones
        //and some defaults
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "IP");
        customFieldValues.put(ParallelTaskInit.documentationId, "TD");
        OverbaardIssueEvent create = createEventBuilder("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1, new NewRankChecker().rank(7, "TDP-8"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{1, 0})));

        // Create an issue with a type which has PT overrides, set some irrelevant PTs too
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.downstreamId, "IPb");
        customFieldValues.put(ParallelTaskInit.documentationId, "Pb");
        create = createEventBuilder("TDP-9", IssueType.BUG, Priority.HIGH, "Nine")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        changes = getChangesJson(0, 2,
                new NewRankChecker().rank(7, "TDP-8").rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{1, 0})),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2})));
        changes = getChangesJson(1, 2, new NewRankChecker().rank(8, "TDP-9"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2})));

        // Create an issue with a type which has empty PT overrides, set some irrelevant PTs too
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.downstreamId, "IPb");
        customFieldValues.put(ParallelTaskInit.documentationId, "Pb");
        create = createEventBuilder("TDP-10", IssueType.TASK, Priority.HIGH, "Ten")
                .assignee("kabir")
                .state("TDP-D")
                .customFieldValues(customFieldValues)
                .buildAndRegister();
        boardManager.handleEvent(create, nextRankedIssueUtil);
        changes = getChangesJson(0, 3,
                new NewRankChecker().rank(7, "TDP-8").rank(8, "TDP-9").rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-8", IssueType.FEATURE, Priority.HIGH, "Eight", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{1, 0})),
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2})),
                new AddIssueData("TDP-10", IssueType.TASK, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(
                        ParallelTaskGroupsValueChecker.NONE));
        changes = getChangesJson(1, 3, new NewRankChecker().rank(8, "TDP-9").rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-9", IssueType.BUG, Priority.HIGH, "Nine", "TDP-D", "kabir").checkers(
                        new ParallelTaskGroupsValueChecker(true, new int[]{2})),
                new AddIssueData("TDP-10", IssueType.TASK, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(
                        ParallelTaskGroupsValueChecker.NONE));
        changes = getChangesJson(2, 3, new NewRankChecker().rank(9, "TDP-10"));
        checkDeletes(changes);
        checkUpdates(changes);
        checkAdds(changes,
                new AddIssueData("TDP-10", IssueType.TASK, Priority.HIGH, "Ten", "TDP-D", "kabir").checkers(
                        ParallelTaskGroupsValueChecker.NONE));
    }

    @Test
    public void testUpdateIssueParallelTasksWithNonIntersectingIssueTypeOverrides() throws Exception {
        // Set up the issues manually here as the standard test setup doesn't match our purposes too well
        setupInitialBoard("config/board-issue-type-overrides-parallel-tasks-varying.json", new ParallelTaskInit(true, true) {
            @Override
            public void setupIssues() {
                issueRegistry.issueBuilder("TDP", "feature", "high", "One", "TDP-A")
                        .assignee("kabir").buildAndRegister();      //1
                // Set some defaults and some irrelevant ones
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.upstreamId, "IP");
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.documentationId, "W");
                issueRegistry.issueBuilder("TDP", "bug", "high", "Two", "TDP-B")
                        .assignee("kabir").buildAndRegister();      //2
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.documentationId, "Pb");
                issueRegistry.issueBuilder("TDP", "task", "high", "Two", "TDP-B")
                        .assignee("kabir").buildAndRegister();      //3
                // Set some irrelevant ones
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.upstreamId, "Mb");
                issueRegistry.setParallelTaskField("TDP-2", BoardManagerTest.ParallelTaskInit.documentationId, "Pb");
            }
        });

        ////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Update an issue using the project PTs
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "M");
        customFieldValues.put(ParallelTaskInit.downstreamId, "IP");
        OverbaardIssueEvent update = updateEventBuilder("TDP-1").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkUpdates(changes, new UpdateIssueData("TDP-1").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, 1})));

        //Update an irrelevant PT
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.documentationId, "P");
        update = updateEventBuilder("TDP-1").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        //view id should be unchanged
        getChangesJson(0, 1);

        ////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Update an issue using overridden PTs
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.documentationId, "TDb");
        update = updateEventBuilder("TDP-2").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        changes = getChangesJson(0, 2);
        checkUpdates(changes,
                new UpdateIssueData("TDP-1").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2, 1})),
                new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{0})));
        changes = getChangesJson(1, 2);
        checkUpdates(changes, new UpdateIssueData("TDP-2").checkers(new ParallelTaskGroupsValueChecker(false, new int[]{0})));

        //Update an irrelevant PT
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.upstreamId, "IP");
        update = updateEventBuilder("TDP-2").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        //view id should be unchanged
        getChangesJson(1, 2);

        ////////////////////////////////////////////////////////////////////////////////////////////////////////
        //Update an issue using empty PTs
        customFieldValues = new HashMap<>();
        customFieldValues.put(ParallelTaskInit.documentationId, "TDt");
        customFieldValues.put(ParallelTaskInit.upstreamId, "TDt");
        update = updateEventBuilder("TDP-3").customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        //view id should be unchanged
        changes = getChangesJson(0, 2);
    }

    @Test
    public void testUpdateIssueTypeWithIssueTypeParallelTaskOverrides() throws Exception {
        // The idea here is to change between issue types which have the normal PTs to the ones which have overrides and vice versa
        // Set up the issues manually here as the standard test setup doesn't match our purposes too well
        setupInitialBoard("config/board-issue-type-overrides-parallel-tasks-varying.json", new ParallelTaskInit(true, true) {
            @Override
            public void setupIssues() {
                issueRegistry.issueBuilder("TDP", "bug", "high", "One", "TDP-A")
                        .assignee("kabir").buildAndRegister();      //1
                // Set a mixture of default PT values and ones valid for the issue type
                issueRegistry.setParallelTaskField("TDP-1", BoardManagerTest.ParallelTaskInit.documentationId, "Pb");
            }
        });

        // Change to another issue type with different PTs
        //When we change the type the event listener sets all the PT fields again
        Map<Long, String> customFieldValues = new HashMap<>();
        customFieldValues.put(BoardManagerTest.ParallelTaskInit.upstreamId, "IP");
        customFieldValues.put(BoardManagerTest.ParallelTaskInit.downstreamId, "D");
        OverbaardIssueEvent update = updateEventBuilder("TDP-1").issueType(IssueType.FEATURE).customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        ModelNode changes = getChangesJson(0, 1);
        checkUpdates(changes, new UpdateIssueData("TDP-1").type(IssueType.FEATURE).checkers(new ParallelTaskGroupsValueChecker(false, new int[]{1, 2})));


        // Change to another issue type with different PTs
        customFieldValues = new HashMap<>();
        customFieldValues.put(BoardManagerTest.ParallelTaskInit.documentationId, "Wb");
        update = updateEventBuilder("TDP-1").issueType(IssueType.BUG).customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        int expectedViewId = 2;
        for (int i = 0; i < expectedViewId; i++) {
            changes = getChangesJson(0, expectedViewId);
            checkUpdates(changes, new UpdateIssueData("TDP-1").type(IssueType.BUG).checkers(new ParallelTaskGroupsValueChecker(false, new int[]{1})));
        }

        // Change to an issue type which has no parallel tasks, and pass in an empty map
        customFieldValues = new HashMap<>();
        customFieldValues.put(BoardManagerTest.ParallelTaskInit.documentationId, "Wb");
        update = updateEventBuilder("TDP-1").issueType(IssueType.TASK).customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        expectedViewId = 3;
        for (int i = 0; i < expectedViewId; i++) {
            changes = getChangesJson(0, expectedViewId);
            checkUpdates(changes, new UpdateIssueData("TDP-1").type(IssueType.TASK).checkers(ParallelTaskGroupsValueChecker.NONE));
        }

        // Change to another issue type with different PTs
        customFieldValues = new HashMap<>();
        customFieldValues.put(BoardManagerTest.ParallelTaskInit.documentationId, "Pb");
        update = updateEventBuilder("TDP-1").issueType(IssueType.BUG).customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        expectedViewId = 4;
        for (int i = 0; i < expectedViewId; i++) {
            changes = getChangesJson(0, expectedViewId);
            checkUpdates(changes, new UpdateIssueData("TDP-1").type(IssueType.BUG).checkers(new ParallelTaskGroupsValueChecker(false, new int[]{2})));
        }

        // Change to an issue type which has no parallel tasks, and pass in some irrelevant parallel task values
        // (as the event listener populates that with values for all boards, and each board should only deal with the relevant ones)
        customFieldValues = new HashMap<>();
        customFieldValues.put(BoardManagerTest.ParallelTaskInit.documentationId, "Wb");
        customFieldValues.put(BoardManagerTest.ParallelTaskInit.upstreamId, "IPb");
        customFieldValues.put(BoardManagerTest.ParallelTaskInit.downstreamId, "Db");
        update = updateEventBuilder("TDP-1").issueType(IssueType.TASK).customFieldValues(customFieldValues).buildAndRegister();
        boardManager.handleEvent(update, nextRankedIssueUtil);
        expectedViewId = 5;
        for (int i = 0; i < expectedViewId; i++) {
            changes = getChangesJson(0, expectedViewId);
            checkUpdates(changes, new UpdateIssueData("TDP-1").type(IssueType.TASK).checkers(ParallelTaskGroupsValueChecker.NONE));
        }

    }

    private ModelNode checkNoIssueChanges(int fromView, int expectedView, NewChecker...checkers) throws SearchException {
        return checkNoIssueChanges(fromView, expectedView, false, checkers);
    }


    private ModelNode checkNoIssueChanges(int fromView, int expectedView, boolean backlog, NewChecker...checkers) throws SearchException {
        ModelNode changesNode = getChangesJson(fromView, expectedView, backlog, checkers);
        checkNoIssueChanges(changesNode);
        return changesNode;
    }

    private void checkNoIssueChanges(ModelNode changesNode) throws SearchException {
        Assert.assertFalse(changesNode.hasDefined(CHANGES, ISSUES));
    }

    private void checkDeletes(ModelNode changesNode, String...expectedKeys) throws SearchException {
        Assert.assertEquals(1, changesNode.keys().size());
        if (expectedKeys.length == 0) {
            Assert.assertFalse(changesNode.get(CHANGES, ISSUES).hasDefined(DELETE));
        } else {
            Set<String> expectedKeysSet = new HashSet<>(Arrays.asList(expectedKeys));
            List<ModelNode> list = changesNode.get(CHANGES, ISSUES, DELETE).asList();
            Assert.assertEquals(expectedKeys.length, list.size());
            for (ModelNode node : list) {
                Assert.assertTrue(expectedKeysSet.contains(node.asString()));
            }
        }
    }

    private void checkAdds(ModelNode changesNode, AddIssueData...expectedIssues) throws SearchException {
        Assert.assertEquals(1, changesNode.keys().size());
        if (expectedIssues.length == 0) {
            Assert.assertFalse(changesNode.get(CHANGES, ISSUES).hasDefined(NEW));
        } else {
            Map<String, AddIssueData> expectedIssuesMap = new HashMap<>();
            Arrays.asList(expectedIssues).forEach(ei -> expectedIssuesMap.put(ei.key, ei));

            List<ModelNode> list = changesNode.get(CHANGES, ISSUES, NEW).asList();
            Assert.assertEquals(expectedIssuesMap.size(), list.size());

            for (ModelNode issue : list) {
                final String key = issue.get(KEY).asString();
                AddIssueData expected = expectedIssuesMap.get(key);
                Assert.assertNotNull(expected);
                Assert.assertEquals(expected.type.name, nullOrString(issue.get(TYPE)));
                Assert.assertEquals(expected.priority.name, nullOrString(issue.get(PRIORITY)));
                Assert.assertEquals(expected.summary, nullOrString(issue.get(SUMMARY)));
                Assert.assertEquals(expected.assignee, nullOrString(issue.get(ASSIGNEE)));
                Assert.assertEquals(expected.state, nullOrString(issue.get("state")));
                checkIssueComponents(expected.components, issue);
                checkIssueLabels(expected.labels, issue);
                checkIssueFixVersions(expected.fixVersions, issue);
                checkIssueAffectsVersions(expected.affectsVersions, issue);
                runIssueCheckers(issue, expected);
            }
        }
    }

    private void checkUpdates(ModelNode changesNode, UpdateIssueData...expectedIssues) throws SearchException {

        Assert.assertEquals(1, changesNode.keys().size());
        if (expectedIssues.length == 0) {
            Assert.assertFalse(changesNode.get(CHANGES, ISSUES).hasDefined("update"));
        } else {
            Map<String, UpdateIssueData> expectedIssuesMap = new HashMap<>();
            Arrays.asList(expectedIssues).forEach(ei -> expectedIssuesMap.put(ei.key, ei));
            List<ModelNode> list = changesNode.get(CHANGES, ISSUES, "update").asList();
            Assert.assertEquals(expectedIssuesMap.size(), list.size());
            for (ModelNode issue : list) {
                final String key = issue.get(KEY).asString();
                UpdateIssueData expected = expectedIssuesMap.get(key);
                Assert.assertNotNull(expected);
                Assert.assertEquals(expected.type == null ? null : expected.type.name,
                        nullOrString(issue.get(TYPE)));
                Assert.assertEquals(expected.priority == null ? null : expected.priority.name,
                        nullOrString(issue.get(PRIORITY)));
                Assert.assertEquals(expected.summary, nullOrString(issue.get(SUMMARY)));
                Assert.assertEquals(expected.assignee, nullOrString(issue.get(ASSIGNEE)));
                Assert.assertEquals(expected.state, nullOrString(issue.get("state")));
                if (expected.unassigned) {
                    Assert.assertTrue(issue.get(UNASSIGNED).asBoolean());
                } else {
                    Assert.assertFalse(issue.has(UNASSIGNED));

                }

                checkIssueComponents(expected.components, issue);
                checkClearedComponents(expected, issue);
                checkIssueLabels(expected.labels, issue);
                checkClearedLabels(expected, issue);
                checkIssueFixVersions(expected.fixVersions, issue);
                checkClearedFixVersions(expected, issue);
                checkIssueAffectsVersions(expected.affectsVersions, issue);
                checkClearedAffectsVersions(expected, issue);

                runIssueCheckers(issue, expected);
            }
        }
    }

    private void checkClearedComponents(UpdateIssueData expected, ModelNode issue) {
        checkClearedMultiSelectNameOnlyValue(expected.clearedComponents, issue, CLEAR_COMPONENTS);
    }

    private void checkClearedLabels(UpdateIssueData expected, ModelNode issue) {
        checkClearedMultiSelectNameOnlyValue(expected.clearedLabels, issue, CLEAR_LABELS);
    }

    private void checkClearedFixVersions(UpdateIssueData expected, ModelNode issue) {
        checkClearedMultiSelectNameOnlyValue(expected.clearedFixVersions, issue, CLEAR_FIX_VERSIONS);
    }

    private void checkClearedAffectsVersions(UpdateIssueData expected, ModelNode issue) {
        checkClearedMultiSelectNameOnlyValue(expected.clearedAffectsVersions, issue, CLEAR_AFFECTS_VERSIONS);
    }

    private void checkClearedMultiSelectNameOnlyValue(boolean expected, ModelNode issue, String name) {
        if (expected) {
            Assert.assertTrue(issue.get(name).asBoolean());
        } else {
            Assert.assertFalse(issue.has(name));
        }
    }

    private void checkIssueComponents(String[] expectedComponents, ModelNode issue) {
        checkIssueMultiSelectValues(expectedComponents, issue, COMPONENTS);
    }

    private void checkIssueLabels(String[] expectedComponents, ModelNode issue) {
        checkIssueMultiSelectValues(expectedComponents, issue, LABELS);
    }

    private void checkIssueFixVersions(String[] expectedComponents, ModelNode issue) {
        checkIssueMultiSelectValues(expectedComponents, issue, FIX_VERSIONS);
    }

    private void checkIssueAffectsVersions(String[] expectedAffectsVersions, ModelNode issue) {
        checkIssueMultiSelectValues(expectedAffectsVersions, issue, AFFECTS_VERSIONS);
    }



    private void checkIssueMultiSelectValues(String[] expectedValues, ModelNode issue, String name) {
        if (expectedValues == null || expectedValues.length == 0) {
            Assert.assertFalse(issue.hasDefined(name));
        } else {
            List<ModelNode> issueValues = issue.get(name).asList();
            Assert.assertEquals(expectedValues.length, issueValues.size());
            Set<String> expected = new HashSet<>(Arrays.asList(expectedValues));
            for (ModelNode value : issueValues) {
                Assert.assertTrue(expected.contains(value.asString()));
            }
        }
    }

    private void runIssueCheckers(ModelNode issue, IssueData expected) {
        Map<Class<? extends IssueChecker>, IssueChecker> checkerMap = new HashMap<>();
        //Default to the none checkers, unless the user overrides
        checkerMap.put(TesterChecker.class, TesterChecker.NONE);
        checkerMap.put(DocumenterChecker.class, DocumenterChecker.NONE);
        checkerMap.put(ParallelTaskGroupsValueChecker.class, ParallelTaskGroupsValueChecker.NONE);
        boolean hasCustom = false;
        for (IssueChecker checker : expected.issueCheckers) {
            Class<? extends IssueChecker> clazz = checker.getClass();
            if (clazz == TesterChecker.class && checker != TesterChecker.NONE
                    || clazz == DocumenterChecker.class && checker != DocumenterChecker.NONE) {
                hasCustom = true;
            }
            checkerMap.put(clazz, checker);
        }

        Assert.assertEquals(hasCustom, issue.hasDefined(CUSTOM));

        for (IssueChecker checker : checkerMap.values()) {
            checker.check(issue);
        }
    }

    String nullOrString(ModelNode node) {
        if (node.isDefined()) {
            return node.asString();
        }
        return null;
    }

    private void checkViewId(int expectedViewId) throws SearchException {
        String json = boardManager.getBoardJson(userManager.getUserByKey("kabir"), false, "TST");
        Assert.assertNotNull(json);
        ModelNode boardNode = ModelNode.fromJSONString(json);
        Assert.assertEquals(expectedViewId, boardNode.get(VIEW).asInt());
    }


    private ModelNode getChangesJson(int fromView, int expectedView, NewChecker...checkers) throws SearchException {
        return getChangesJson(fromView, expectedView, false, checkers);
    }

    private ModelNode getChangesJson(int fromView, int expectedView, boolean backlog, NewChecker...checkers) throws SearchException {
        String json = boardManager.getChangesJson(userManager.getUserByKey("kabir"), backlog, "TST", fromView);
        ModelNode changesNode = ModelNode.fromJSONString(json);
        Assert.assertEquals(expectedView, changesNode.get(CHANGES, VIEW).asInt());

        Assert.assertEquals(1, changesNode.keys().size());

        Map<Class<? extends NewChecker>, NewChecker> checkersMap = new HashMap();

        //Assume that we check for none if no checker has been passed in for a field
        checkersMap.put(NewAssigneesChecker.class, NewAssigneesChecker.NONE);
        checkersMap.put(NewComponentsChecker.class, NewComponentsChecker.NONE);
        checkersMap.put(NewLabelsChecker.class, NewLabelsChecker.NONE);
        checkersMap.put(NewFixVersionsChecker.class, NewFixVersionsChecker.NONE);
        checkersMap.put(NewAffectsVersionsChecker.class, NewAffectsVersionsChecker.NONE);
        checkersMap.put(NewCustomFieldChecker.class, NewCustomFieldChecker.NONE);
        checkersMap.put(NewRankChecker.class, NewRankChecker.NONE);
        checkersMap.put(NewBlackListChecker.class, NewBlackListChecker.NONE);
        for (NewChecker checker : checkers) {
            checkersMap.put(checker.getClass(), checker);
        }

        for (NewChecker checker : checkersMap.values()) {
            checker.check(changesNode);
        }

        return changesNode;
    }

    private ModelNode getChangesEnsuringFullRefresh(int fromView) throws SearchException {
        String json = boardManager.getChangesJson(userManager.getUserByKey("kabir"), true, "TST", fromView);
        ModelNode fullRefreshNode = ModelNode.fromJSONString(json);
        Assert.assertEquals(0, fullRefreshNode.get(VIEW).asInt());

        //Make sure we have the top-level attributes at least
        Assert.assertTrue(fullRefreshNode.hasDefined(STATES));
        Assert.assertTrue(fullRefreshNode.hasDefined(PRIORITIES));
        Assert.assertTrue(fullRefreshNode.hasDefined(ISSUE_TYPES));
        Assert.assertTrue(fullRefreshNode.hasDefined(ASSIGNEES));
        Assert.assertTrue(fullRefreshNode.hasDefined(COMPONENTS));
        Assert.assertTrue(fullRefreshNode.hasDefined(PROJECTS));
        Assert.assertTrue(fullRefreshNode.hasDefined(ISSUES));

        return fullRefreshNode;
    }

    private abstract static class IssueData {
        String key;
        IssueType type;
        Priority priority;
        String summary;
        String state;
        String assignee;
        boolean unassigned;
        String[] components;
        String[] labels;
        String[] fixVersions;
        String[] affectsVersions;
        IssueChecker[] issueCheckers;

        protected IssueData(String key, IssueType type, Priority priority, String summary, String state, String assignee) {
            this(key);
            this.type = type;
            this.priority = priority;
            this.summary = summary;
            this.assignee = assignee;
            this.state = state;
        }

        protected IssueData(String key) {
            this.key = key;
            this.issueCheckers = new IssueChecker[0];
        }
    }

    static class AddIssueData extends IssueData {
        AddIssueData(String key, IssueType type, Priority priority, String summary, String state, String assignee) {
            super(key, type, priority, summary, state, assignee);
        }

        AddIssueData components(String... components) {
            this.components = components;
            return this;
        }

        AddIssueData labels(String... labels) {
            this.labels = labels;
            return this;
        }

        AddIssueData fixVersions(String... fixVersions) {
            this.fixVersions = fixVersions;
            return this;
        }

        AddIssueData affectsVersions(String... affectsVersions) {
            this.affectsVersions = affectsVersions;
            return this;
        }

        AddIssueData checkers(IssueChecker... checkers) {
            this.issueCheckers = checkers;
            return this;
        }
    }

    static class UpdateIssueData extends IssueData {
        boolean clearedComponents;
        boolean clearedLabels;
        boolean clearedFixVersions;
        boolean clearedAffectsVersions;

        UpdateIssueData(String key) {
            super(key);
        }

        UpdateIssueData type(IssueType type) {
            this.type = type;
            return this;
        }

        UpdateIssueData priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        UpdateIssueData summary(String summary) {
            this.summary = summary;
            return this;
        }

        UpdateIssueData state(String state) {
            this.state = state;
            return this;
        }

        UpdateIssueData assignee(String assignee) {
            Assert.assertFalse(this.unassigned);
            this.assignee = assignee;
            return this;
        }

        UpdateIssueData unassigned() {
            Assert.assertNull(this.assignee);
            this.unassigned = true;
            return this;
        }

        UpdateIssueData components(String... components) {
            Assert.assertFalse(this.clearedComponents);
            this.components = components;
            return this;
        }

        UpdateIssueData clearedComponents(){
            Assert.assertNull(this.components);
            this.clearedComponents = true;
            return this;
        }

        UpdateIssueData labels(String... labels) {
            Assert.assertFalse(this.clearedLabels);
            this.labels = labels;
            return this;
        }

        UpdateIssueData clearedLabels() {
            Assert.assertNull(this.labels);
            this.clearedLabels = true;
            return this;
        }

        UpdateIssueData fixVersions(String... fixVersions) {
            Assert.assertFalse(this.clearedFixVersions);
            this.fixVersions = fixVersions;
            return this;
        }

        UpdateIssueData clearedFixVersions() {
            Assert.assertNull(this.fixVersions);
            this.clearedFixVersions = true;
            return this;
        }

        UpdateIssueData affectsVersions(String... affectsVersions) {
            Assert.assertFalse(this.clearedAffectsVersions);
            this.affectsVersions = affectsVersions;
            return this;
        }

        UpdateIssueData clearedAffectsVersions() {
            Assert.assertNull(this.affectsVersions);
            this.clearedAffectsVersions = true;
            return this;
        }

        UpdateIssueData checkers(IssueChecker... checkers) {
            Assert.assertNull(this.fixVersions);
            this.issueCheckers = checkers;
            return this;
        }
    }

    abstract static class CustomFieldChecker implements IssueChecker {
        private final String fieldName;
        private final String key;
        private final boolean undefined;

        public CustomFieldChecker(String fieldName, String key, boolean undefined) {
            this.fieldName = fieldName;
            this.key = key;
            this.undefined = undefined;
        }

        @Override
        public void check(ModelNode issue) {
            if (key != null) {
                Assert.assertTrue(issue.hasDefined(CUSTOM, fieldName));
                Assert.assertEquals(key, issue.get(CUSTOM, fieldName).asString());
            } else {
                if (undefined) {
                    Assert.assertTrue(issue.has(CUSTOM, fieldName));
                }
                Assert.assertFalse(issue.hasDefined(CUSTOM, fieldName));
            }
        }
    }

    static class TesterChecker extends CustomFieldChecker {
        /** Use to check that there is no tester defined */
        static final TesterChecker NONE = new TesterChecker(null);
        /** Use to check that there is a tester field but it is undefined (used when sending update events clearing the tester) */
        static final TesterChecker UNDEFINED = new TesterChecker(true);

        public TesterChecker(String key) {
            super("Tester", key, false);
        }

        private TesterChecker(boolean undefined) {
            super("Tester", null, undefined);
        }
    }

    static class DocumenterChecker extends CustomFieldChecker {
        /** Use to check that there is no documenter defined */
        static final DocumenterChecker NONE = new DocumenterChecker(null);
        /** Use to check that there is a tester field but it is undefined (used when sending update events clearing the tester) */
        static final DocumenterChecker UNDEFINED = new DocumenterChecker(true);

        public DocumenterChecker(String key) {
            super("Documenter", key, false);
        }

        private DocumenterChecker(boolean undefined) {
            super("Documenter", null, undefined);
        }
    }

    static class TestedByChecker extends CustomFieldChecker {
        /** Use to check that there is no tested by defined */
        static final TestedByChecker NONE = new TestedByChecker(null);
        /** Use to check that there is a tested by field but it is undefined (used when sending update events clearing the tester) */
        static final TestedByChecker UNDEFINED = new TestedByChecker(true);

        public TestedByChecker(String key) {
            super("Tested By", key, false);
        }

        private TestedByChecker(boolean undefined) {
            super("Tested By", null, undefined);
        }
    }



    static class ParallelTaskGroupsValueChecker implements IssueChecker {
        static final ParallelTaskGroupsValueChecker NONE = new ParallelTaskGroupsValueChecker(true, null);

        private final boolean add;
        private final int[][] expected;

        public ParallelTaskGroupsValueChecker(boolean add, int[]... expected) {
            this.add = add;
            this.expected = expected;
        }

        @Override
        public void check(ModelNode issue) {
            if (expected == null) {
                Assert.assertFalse(issue.hasDefined(PARALLEL_TASKS));
            } else {
                Assert.assertTrue(issue.hasDefined(PARALLEL_TASKS));
                ModelNode tasksNode = issue.get(PARALLEL_TASKS);
                if (add) {
                    List<ModelNode> values = tasksNode.asList();
                    Assert.assertEquals(expected.length, values.size());
                    for (int i = 0 ; i < expected.length ; i++) {
                        List<ModelNode> group = values.get(i).asList();
                        Assert.assertEquals(expected[i].length, group.size());
                        for (int j = 0 ; j < expected[i].length ; j++){
                            Assert.assertEquals(expected[i][j], group.get(j).asInt());
                        }
                    }
                } else {
                    Map<ParallelTaskGroupPosition, Integer> map = new HashMap<>();
                    for (String groupKey : tasksNode.keys()) {
                        ModelNode groupNode = tasksNode.get(groupKey);
                        for (String taskKey : groupNode.keys()) {
                            ParallelTaskGroupPosition position =
                                    new ParallelTaskGroupPosition(Integer.valueOf(groupKey), Integer.valueOf(taskKey));
                            map.put(position, groupNode.get(taskKey).asInt());
                        }
                    }

                    for (int groupIndex = 0 ; groupIndex < expected.length ; groupIndex++) {
                        for (int taskIndex = 0 ; taskIndex < expected[groupIndex].length ; taskIndex++) {
                            int expectedValue = expected[groupIndex][taskIndex];
                            Integer actualValue = map.get(new ParallelTaskGroupPosition(groupIndex, taskIndex));
                            if (expectedValue >= 0) {
                                Assert.assertEquals(expectedValue, actualValue.intValue());
                            } else {
                                Assert.assertNull(actualValue);
                            }
                        }
                    }

                }
            }
        }
    }


    interface AdditionalSetup extends AdditionalBuilderInit {
        boolean skipStandardIssues();
        void setupIssues();
    }

    private abstract static class AbstractAdditionalSetup implements AdditionalSetup {
        @Override
        public boolean skipStandardIssues() {
            return false;
        }

        @Override
        public void setupIssues() {

        }

        @Override
        public void initialise(BoardManagerBuilder boardManagerBuilder) {

        }
    }


    private static class RankChange {
        final int index;
        final String key;

        public RankChange(int index, String key) {
            this.index = index;
            this.key = key;
        }

        public int getIndex() {
            return index;
        }

        public String getKey() {
            return key;
        }
    }

    /**
     * Checker to check added fields sent to the client during a change
     */
    private interface NewChecker {
        void check(ModelNode changes);
    }

    private static class NewAssigneesChecker implements NewChecker {
        static final NewAssigneesChecker NONE = new NewAssigneesChecker();

        private final String[] expectedAssignees;

        NewAssigneesChecker(String... expectedAssignees) {
            this.expectedAssignees = expectedAssignees;
        }

        @Override
        public void check(ModelNode changes) {
            Assert.assertEquals(1, changes.keys().size());
            if (expectedAssignees.length == 0) {
                Assert.assertFalse(changes.get(CHANGES).hasDefined(ASSIGNEES));
            } else {
                List<ModelNode> list = changes.get(CHANGES, ASSIGNEES).asList();
                Assert.assertEquals(expectedAssignees.length, list.size());
                Set<String> assignees = new HashSet<>(Arrays.asList(expectedAssignees));
                for (ModelNode assigneeNode : list) {
                    String key = assigneeNode.get(KEY).asString();
                    Assert.assertTrue(assignees.contains(key));
                    Assert.assertEquals(key + "@example.com", assigneeNode.get(EMAIL).asString());
                    Assert.assertEquals("/avatars/" + key + ".png", assigneeNode.get(AVATAR).asString());
                    String displayName = assigneeNode.get(NAME).toString().toLowerCase();
                    Assert.assertTrue(displayName.length() > key.length());
                    Assert.assertTrue(displayName.contains(key));
                }
            }
        }
    }

    private abstract static class NewMultiSelectNameOnlyValueChecker implements NewChecker {
        private final String name;
        private final String[] expectedValues;

        protected NewMultiSelectNameOnlyValueChecker(String name, String[] expectedValues) {
            this.name = name;
            this.expectedValues = expectedValues;
        }

        @Override
        public void check(ModelNode changes) {
            Assert.assertEquals(1, changes.keys().size());
            if (expectedValues.length == 0) {
                Assert.assertFalse(changes.get(CHANGES).hasDefined(name));
            } else {
                List<ModelNode> list = changes.get(CHANGES, name).asList();
                Assert.assertEquals(expectedValues.length, list.size());
                Set<String> components = new HashSet<>(Arrays.asList(expectedValues));
                for (ModelNode componentNode : list) {
                    Assert.assertTrue(components.contains(componentNode.asString()));
                }
            }
        }
    }

    private static class NewComponentsChecker extends NewMultiSelectNameOnlyValueChecker {
        static final NewComponentsChecker NONE = new NewComponentsChecker();

        NewComponentsChecker(String...expectedComponents) {
            super(COMPONENTS, expectedComponents);
        }
    }

    private static class NewLabelsChecker extends NewMultiSelectNameOnlyValueChecker {
        static final NewLabelsChecker NONE = new NewLabelsChecker();

        NewLabelsChecker(String...expectedLabels) {
            super(LABELS, expectedLabels);
        }
    }

    private static class NewFixVersionsChecker extends NewMultiSelectNameOnlyValueChecker {
        static final NewFixVersionsChecker NONE = new NewFixVersionsChecker();

        NewFixVersionsChecker(String... expectedFixVersions) {
            super(FIX_VERSIONS, expectedFixVersions);
        }
    }

    private static class NewAffectsVersionsChecker extends NewMultiSelectNameOnlyValueChecker {
        static final NewAffectsVersionsChecker NONE = new NewAffectsVersionsChecker();

        NewAffectsVersionsChecker(String... expectedAffectsVersions) {
            super(AFFECTS_VERSIONS, expectedAffectsVersions);
        }
    }

    private static class NewCustomFieldChecker implements NewChecker {
        static final NewCustomFieldChecker NONE = new NewCustomFieldChecker();

        private String[] expectedTesters;
        private String[] expectedDocumenters;

        NewCustomFieldChecker() {
        }

        NewCustomFieldChecker testers(String...expectedTesters) {
            this.expectedTesters = expectedTesters;
            return this;
        }

        NewCustomFieldChecker documenters(String...expectedDocumenters) {
            this.expectedDocumenters = expectedDocumenters;
            return this;
        }

        @Override
        public void check(ModelNode changes) {
            String[] expectedTesters = emptyIfNull(this.expectedTesters);
            String[] expectedDocumenters = emptyIfNull(this.expectedDocumenters);
            if (expectedTesters.length == 0 && expectedDocumenters.length == 0) {
                Assert.assertFalse(changes.hasDefined(CHANGES, CUSTOM));

            } else {
                ModelNode customNode = changes.get(CHANGES, CUSTOM);
                checkValues(customNode, "Tester", expectedTesters);
                checkValues(customNode, "Documenter", expectedDocumenters);
            }
        }

        private void checkValues(ModelNode customNode, String name, String[] expectedValues) {
            ModelNode valueNode = customNode.get(name);
            if (expectedValues.length == 0) {
                Assert.assertFalse(valueNode.isDefined());
                return;
            }
            Set<String> expected = new HashSet<>(Arrays.asList(expectedValues));
            Assert.assertTrue(valueNode.isDefined());
            List<ModelNode> values = valueNode.asList();
            Assert.assertEquals(expected.size(), values.size());

            for (ModelNode user : values) {
                String key = user.get(KEY).asString();
                Assert.assertTrue(expected.remove(key));
                Assert.assertTrue(user.get(VALUE).asString().toLowerCase().startsWith(key));
            }
        }
    }

    private static class NewRankChecker implements NewChecker {
        static final NewRankChecker NONE = new NewRankChecker();
        private List<RankChange> rankChanges = new ArrayList<>();

        NewRankChecker() {
        }

        NewRankChecker rank(int index, String key) {
            rankChanges.add(new RankChange(index, key));
            return this;
        }

        @Override
        public void check(ModelNode changes) {
            if (rankChanges.size() == 0) {
                Assert.assertFalse(changes.hasDefined(CHANGES, RANK));
            } else {
                Map<String, List<RankChange>> rankChangesMap = new HashMap<>();
                for (RankChange rankChange : rankChanges) {
                    int index = rankChange.getKey().indexOf("-");
                    String projectCode = rankChange.getKey().substring(0, index);
                    List<RankChange> list = rankChangesMap.computeIfAbsent(projectCode, k -> new ArrayList<RankChange>());
                    list.add(rankChange);
                }
                Assert.assertTrue(changes.hasDefined(CHANGES, RANK));
                ModelNode rankNode = changes.get(CHANGES, RANK);
                Assert.assertEquals(rankChangesMap.size(), rankNode.keys().size());
                for (String projectCode : rankChangesMap.keySet()) {
                    ModelNode projectNode = rankNode.get(projectCode);
                    Assert.assertEquals(LIST, projectNode.getType());
                    List<ModelNode> listNode = projectNode.asList();
                    List<RankChange> rankList = rankChangesMap.get(projectCode);
                    Assert.assertEquals(rankList.size(), listNode.size());
                    for (int i = 0 ; i < rankList.size() ; i++) {
                        ModelNode rankChangeNode = listNode.get(i);
                        RankChange rankChange = rankList.get(i);
                        Assert.assertEquals(rankChange.getIndex(), rankChangeNode.get(INDEX).asInt());
                        Assert.assertEquals(rankChange.getKey(), rankChangeNode.get(KEY).asString());
                    }
                }
            }
        }
    }

    private static class NewBlackListChecker implements NewChecker {
        static final NewBlackListChecker NONE = new NewBlackListChecker();
        private String[] states;
        private String[] issueTypes;
        private String[] priorities;
        private String[] issueKeys;
        private String[] removedIssueKeys;

        NewBlackListChecker() {
        }

        NewBlackListChecker states(String... states) {
            this.states = states;
            return this;
        }

        NewBlackListChecker types(String... issueTypes) {
            this.issueTypes = issueTypes;
            return this;
        }

        NewBlackListChecker priorities(String... priorities) {
            this.priorities = priorities;
            return this;
        }

        NewBlackListChecker keys(String... issueKeys) {
            this.issueKeys = issueKeys;
            return this;
        }

        NewBlackListChecker removedKeys(String... removedIssueKeys) {
            this.removedIssueKeys = removedIssueKeys;
            return this;
        }

        @Override
        public void check(ModelNode changes) {
            ModelNode blacklistNode = changes.get(CHANGES, BLACKLIST);
            String[] states = emptyIfNull(this.states);
            String[] issueTypes = emptyIfNull(this.issueTypes);
            String[] priorities = emptyIfNull(this.priorities);
            String[] issueKeys = emptyIfNull(this.issueKeys);
            String[] removedIssueKeys = emptyIfNull(this.removedIssueKeys);

            if (states.length == 0 && issueTypes.length == 0 && priorities.length == 0 && issueKeys.length == 0 && removedIssueKeys.length == 0) {
                Assert.assertFalse(blacklistNode.isDefined());
            } else {
                checkEntries(blacklistNode, STATES, states);
                checkEntries(blacklistNode, ISSUE_TYPES, issueTypes);
                checkEntries(blacklistNode, PRIORITIES, priorities);
                checkEntries(blacklistNode, ISSUES, issueKeys);
                checkEntries(blacklistNode, REMOVED_ISSUES, removedIssueKeys);
            }
        }

        private void checkEntries(ModelNode parent, String key, String... entries) {
            if (entries.length == 0) {
                Assert.assertFalse(parent.hasDefined(key));
            } else {
                List<ModelNode> list = parent.get(key).asList();
                Assert.assertEquals(entries.length, list.size());
                Set<String> entrySet = list.stream().map(node -> node.asString()).collect(Collectors.toSet());
                for (String entry : entries) {
                    Assert.assertTrue(entrySet.contains(entry));
                }
            }
        }
    }


    static class ParallelTaskInit implements AdditionalSetup {
        static final Long upstreamId = 121212121212L;
        static final Long downstreamId = 121212121213L;
        static final Long documentationId = 121212121214L;

        final boolean overrides;
        final boolean skipStandardIssues;

        public ParallelTaskInit() {
            this(false);
        }


        public ParallelTaskInit(boolean overrides) {
            this(overrides, false);
        }

        public ParallelTaskInit(boolean overrides, boolean skipStandardIssues) {
            this.overrides = overrides;
            this.skipStandardIssues = skipStandardIssues;
        }

        @Override
        public boolean skipStandardIssues() {
            return skipStandardIssues;
        }

        @Override
        public void setupIssues() {
        }

        @Override
        public void initialise(BoardManagerBuilder boardManagerBuilder) {
            ProjectParallelTaskOptionsLoaderBuilder loader = new ProjectParallelTaskOptionsLoaderBuilder();
            loader
                    .addParallelTaskOption("TDP", upstreamId, "NS", "Not Started")
                    .addParallelTaskOption("TDP", upstreamId, "IP", "In Progress")
                    .addParallelTaskOption("TDP", upstreamId, "M", "Merged")
                    .addParallelTaskOption("TDP", downstreamId, "TD", "TODO")
                    .addParallelTaskOption("TDP", downstreamId, "IP", "In Progress")
                    .addParallelTaskOption("TDP", downstreamId, "D", "Done")
                    .addParallelTaskOption("TDP", documentationId, "TD", "TODO")
                    .addParallelTaskOption("TDP", documentationId, "W", "Writing")
                    .addParallelTaskOption("TDP", documentationId, "P", "Published");
            if (overrides) {
                addOverrides(loader, "bug");
                addOverrides(loader, "task");
            }

            boardManagerBuilder.setProjectCustomFieldOptionsLoader(loader.build());
        }

        private void addOverrides(ProjectParallelTaskOptionsLoaderBuilder loader, String issueType) {
            String suffix = issueType.substring(0, 1).toLowerCase();
            loader
                    .addParallelTaskOption("TDP", issueType, upstreamId, "NS" + suffix, "Not Started - " + issueType)
                    .addParallelTaskOption("TDP", issueType, upstreamId, "IP" + suffix, "In Progress - " + issueType)
                    .addParallelTaskOption("TDP", issueType, upstreamId, "M" + suffix, "Merged - " + issueType)
                    .addParallelTaskOption("TDP", issueType, downstreamId, "TD" + suffix, "TODO - " + issueType)
                    .addParallelTaskOption("TDP", issueType, downstreamId, "IP" + suffix, "In Progress - " + issueType)
                    .addParallelTaskOption("TDP", issueType, downstreamId, "D" + suffix, "Done - " + issueType)
                    .addParallelTaskOption("TDP", issueType, documentationId, "TD" + suffix, "TODO - " + issueType)
                    .addParallelTaskOption("TDP", issueType, documentationId, "W" + suffix, "Writing - " + issueType)
                    .addParallelTaskOption("TDP", issueType, documentationId, "P" + suffix, "Published - " + issueType);
        }
    }
}
