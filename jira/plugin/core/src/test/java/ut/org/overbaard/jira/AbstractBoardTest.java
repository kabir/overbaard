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

import static org.overbaard.jira.impl.Constants.EPIC_LINK_CUSTOM_FIELD_ID;
import static org.overbaard.jira.impl.Constants.EPIC_NAME_CUSTOM_FIELD_ID;
import static org.overbaard.jira.impl.Constants.RANK_CUSTOM_FIELD_ID;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Rule;
import org.overbaard.jira.api.BoardConfigurationManager;
import org.overbaard.jira.api.BoardManager;
import org.overbaard.jira.api.NextRankedIssueUtil;
import org.overbaard.jira.impl.BoardConfigurationManagerBuilder;
import org.overbaard.jira.impl.BoardManagerBuilder;
import org.overbaard.jira.impl.ConfigurationManagerInjectables;
import org.overbaard.jira.impl.OverbaardIssueEvent;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.component.ProjectComponent;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.label.Label;
import com.atlassian.jira.issue.link.IssueLinkManager;
import com.atlassian.jira.junit.rules.MockitoContainer;
import com.atlassian.jira.junit.rules.MockitoMocksInContainer;
import com.atlassian.jira.mock.component.MockComponentWorker;
import com.atlassian.jira.project.version.Version;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;

import ut.org.overbaard.jira.mock.CustomFieldManagerBuilder;
import ut.org.overbaard.jira.mock.IssueLinkManagerBuilder;
import ut.org.overbaard.jira.mock.IssueRegistry;
import ut.org.overbaard.jira.mock.MockLabel;
import ut.org.overbaard.jira.mock.MockProjectComponent;
import ut.org.overbaard.jira.mock.MockVersion;
import ut.org.overbaard.jira.mock.SearchServiceBuilder;
import ut.org.overbaard.jira.mock.UserManagerBuilder;

/**
 * @author Kabir Khan
 */
public abstract class AbstractBoardTest {

    @Rule
    public MockitoContainer mockitoContainer = MockitoMocksInContainer.rule(this);

    protected BoardManager boardManager;
    protected BoardConfigurationManager boardConfigurationManager;
    protected UserManager userManager;
    protected IssueRegistry issueRegistry;
    protected NextRankedIssueUtil nextRankedIssueUtil;
    protected SearchCallback searchCallback = new SearchCallback();

    public void initializeMocks() throws Exception {
        initializeMocks("config/board-tdp.json");
    }

    protected void initializeMocks(String cfgResource) throws Exception {
        initializeMocks(cfgResource, null);
    }

    protected void initializeMocks(String cfgResource, AdditionalBuilderInit init) throws Exception {
        BoardConfigurationManagerBuilder builder = new BoardConfigurationManagerBuilder();
        this.boardConfigurationManager = builder
                .addConfigActiveObjectsFromFile(cfgResource)
                .addSettingActiveObject(RANK_CUSTOM_FIELD_ID, TestConstants.RANK_CUSTOM_FIELD_ID.toString())
                .addSettingActiveObject(EPIC_LINK_CUSTOM_FIELD_ID, TestConstants.EPIC_LINK_CUSTOM_FIELD_ID.toString())
                .addSettingActiveObject(EPIC_NAME_CUSTOM_FIELD_ID, TestConstants.EPIC_NAME_CUSTOM_FIELD_ID.toString())
                .setCustomFieldManager(CustomFieldManagerBuilder.loadFromResource(cfgResource))
                .build();
        initializeMocks(this.boardConfigurationManager, builder, init);
    }

    protected void initializeMocks(ModelNode config, AdditionalBuilderInit init) throws Exception {
        BoardConfigurationManagerBuilder builder = new BoardConfigurationManagerBuilder();
        this.boardConfigurationManager = builder
                .addConfigActiveObjectsFromModel(config)
                .addSettingActiveObject(RANK_CUSTOM_FIELD_ID, TestConstants.RANK_CUSTOM_FIELD_ID.toString())
                .addSettingActiveObject(EPIC_LINK_CUSTOM_FIELD_ID, TestConstants.EPIC_LINK_CUSTOM_FIELD_ID.toString())
                .addSettingActiveObject(EPIC_NAME_CUSTOM_FIELD_ID, TestConstants.EPIC_NAME_CUSTOM_FIELD_ID.toString())
                .setCustomFieldManager(CustomFieldManagerBuilder.loadFromModel(config))
                .build();
        initializeMocks(this.boardConfigurationManager, builder, init);
    }

    private void initializeMocks(BoardConfigurationManager cfgManager, ConfigurationManagerInjectables configurationManagerInjectables, AdditionalBuilderInit init) throws Exception {
        MockComponentWorker worker = new MockComponentWorker();
        userManager = new UserManagerBuilder()
                .addDefaultUsers()
                .build(worker);

        IssueRegistry issueRegistry = new IssueRegistry(userManager);
        this.issueRegistry = issueRegistry;
        this.nextRankedIssueUtil = issueRegistry;

        SearchService searchService = new SearchServiceBuilder(worker)
                .setIssueRegistry(issueRegistry)
                .setSearchCallback(searchCallback)
                .build();
        IssueLinkManager issueLinkManager = new IssueLinkManagerBuilder().build();
        worker.init();

        BoardManagerBuilder boardManagerBuilder = new BoardManagerBuilder(cfgManager, configurationManagerInjectables)
                .setUserManager(userManager)
                .setSearchService(searchService)
                .setIssueLinkManager(issueLinkManager)
                .setNextRankedIssueUtil(nextRankedIssueUtil);

        if (init != null) {
            init.initialise(boardManagerBuilder);
        }
        boardManager = boardManagerBuilder.build();
    }



    protected CreateEventBuilder createEventBuilder(String issueKey, IssueType issueType, Priority priority, String summary) {
        return new CreateEventBuilder(issueKey, issueType == null ? null : issueType.name, priority == null ? null : priority.name, summary);
    }

    protected CreateEventBuilder createEventBuilder(String issueKey, String issueType, String priority, String summary) {
        return new CreateEventBuilder(issueKey, issueType, priority, summary);
    }

    protected UpdateEventBuilder updateEventBuilder(String issueKey) {
        return new UpdateEventBuilder(issueKey);
    }

    protected static String[] emptyIfNull(String[] arr) {
        if (arr == null) {
            return new String[0];
        }
        return arr;
    }

    protected enum IssueType {
        TASK(0),
        BUG(1),
        FEATURE(2);

        final int index;
        final String name;

        IssueType(int index) {
            this.index = index;
            this.name = super.name().toLowerCase();
        }
    }

    protected enum Priority {
        HIGHEST(0),
        HIGH(1),
        LOW(2),
        LOWEST(3);

        final int index;
        final String name;

        Priority(int index) {
            this.index = index;
            this.name = super.name().toLowerCase();
        }
    }

    protected static class SearchCallback implements SearchServiceBuilder.SearchCallback {
        public boolean searched = false;
        @Override
        public void searching() {
            searched = true;
        }
    }

    interface AdditionalBuilderInit {
        void initialise(BoardManagerBuilder boardManagerBuilder);
    }

    /**
     * Checker to check each individual issue
     */
    interface IssueChecker {
        void check(ModelNode issue);
    }

    private class EventBuilderState {
        private final String issueKey;
        private final String projectCode;
        private String oldIssueType;
        private String issueType;
        private String priority;
        private String summary;
        private String username;
        private ApplicationUser user;
        private Set<ProjectComponent> components;
        private Set<Label> labels;
        private Set<Version> fixVersions;
        private Set<Version> affectsVersions;
        private String state;
        private Map<Long, String> customFieldValues;

        EventBuilderState(String issueKey) {
            this.issueKey = issueKey;
            projectCode = issueKey.substring(0, issueKey.indexOf("-"));
        }

        void assignee(String username) {
            this.username = username;
            this.user = userManager.getUserByKey(username);
        }

        void components(String... componentNames) {
            components = MockProjectComponent.createProjectComponents(componentNames);
        }

        void labels(String... labelNames) {
            labels = MockLabel.createLabels(labelNames);
        }

        void fixVersions(String... fixVersionNames) {
            this.fixVersions = MockVersion.createVersions(fixVersionNames);
        }

        void affectsVersions(String... affectsVersionNames) {
            this.affectsVersions = MockVersion.createVersions(affectsVersionNames);
        }
    }

    class CreateEventBuilder {
        private final EventBuilderState delegate;

        private CreateEventBuilder(String issueKey, String issueType, String priority, String summary) {
            delegate = new EventBuilderState(issueKey);
            delegate.issueType = issueType;
            delegate.priority = priority;
            delegate.summary = summary;
        }

        CreateEventBuilder assignee(String username) {
            delegate.assignee(username);
            return this;
        }

        CreateEventBuilder components(String...componentNames) {
            delegate.components(componentNames);
            return this;
        }

        CreateEventBuilder labels(String... labelNames) {
            delegate.labels(labelNames);
            return this;
        }

        CreateEventBuilder fixVersions(String... fixVersionName) {
            delegate.fixVersions(fixVersionName);
            return this;
        }

        CreateEventBuilder affectsVersions(String... affectsVersionNames) {
            delegate.affectsVersions(affectsVersionNames);
            return this;
        }

        CreateEventBuilder state(String state) {
            delegate.state = state;
            return this;
        }

        CreateEventBuilder customFieldValues(Map<Long, String> customFieldValues) {
            delegate.customFieldValues = customFieldValues;
            return this;
        }

        OverbaardIssueEvent buildAndRegister() {
            OverbaardIssueEvent create = OverbaardIssueEvent.createCreateEvent(
                    delegate.issueKey,
                    delegate.projectCode,
                    delegate.issueType,
                    delegate.priority,
                    delegate.summary,
                    delegate.user,
                    delegate.components,
                    delegate.labels,
                    delegate.fixVersions,
                    delegate.affectsVersions,
                    delegate.state,
                    delegate.customFieldValues);

            IssueRegistry.CreateIssueBuilder builder = issueRegistry.issueBuilder(delegate.projectCode, delegate.issueType, delegate.priority, delegate.summary, delegate.state);
            if (delegate.username != null) {
                builder.assignee(delegate.user);
            }
            if (delegate.components != null) {
                builder.components(delegate.components);
            }
            if (delegate.labels != null) {
                builder.labels(delegate.labels);
            }
            if (delegate.fixVersions != null) {
                builder.fixVersions(delegate.fixVersions);
            }
            builder.buildAndRegister();

            if (delegate.customFieldValues != null) {
                for (Map.Entry<Long, String> entry : delegate.customFieldValues.entrySet()) {
                    issueRegistry.setCustomField(delegate.issueKey, entry.getKey(), entry.getValue());
                }
            }
            return create;
        }
    }

    class UpdateEventBuilder  {
        private final EventBuilderState delegate;

        private boolean unassigned;
        private boolean clearComponents;
        private boolean clearLabels;
        private boolean clearFixVersions;
        private boolean clearAffectsVersions;
        private boolean rank;

        UpdateEventBuilder(String issueKey) {
            delegate = new EventBuilderState(issueKey);
        }

        UpdateEventBuilder issueType(IssueType issueType) {
            delegate.issueType = issueType.name;
            return this;
        }

        UpdateEventBuilder issueType(String issueType) {
            delegate.issueType = issueType;
            return this;
        }

        UpdateEventBuilder priority(Priority priority) {
            delegate.priority = priority.name;
            return this;
        }

        UpdateEventBuilder priority(String priority) {
            delegate.priority = priority;
            return this;
        }

        UpdateEventBuilder summary(String summary) {
            delegate.summary = summary;
            return this;
        }

        UpdateEventBuilder assignee(String username) {
            delegate.assignee(username);
            return this;
        }

        UpdateEventBuilder unassign() {
            this.unassigned = true;
            return this;
        }

        UpdateEventBuilder components(String...componentNames) {
            delegate.components(componentNames);
            return this;
        }

        UpdateEventBuilder labels(String... labelNames) {
            delegate.labels(labelNames);
            return this;
        }

        UpdateEventBuilder fixVersions(String... fixVersionName) {
            delegate.fixVersions(fixVersionName);
            return this;
        }

        UpdateEventBuilder affectsVersions(String... affectsVersionName) {
            delegate.affectsVersions(affectsVersionName);
            return this;
        }

        UpdateEventBuilder clearComponents() {
            clearComponents = true;
            return this;
        }

        UpdateEventBuilder clearLabels() {
            clearLabels = true;
            return this;
        }

        UpdateEventBuilder clearFixVersions() {
            clearFixVersions = true;
            return this;
        }

        UpdateEventBuilder clearAffectsVersions() {
            clearAffectsVersions = true;
            return this;
        }

        UpdateEventBuilder state(String state) {
            delegate.state = state;
            return this;
        }

        UpdateEventBuilder customFieldValues(Map<Long, String> customFieldValues) {
            delegate.customFieldValues = customFieldValues;
            return this;
        }

        UpdateEventBuilder rank() {
            rank = true;
            return this;
        }

        OverbaardIssueEvent buildAndRegister() {
            Assert.assertFalse(delegate.username != null && unassigned);
            if (unassigned) {
                delegate.user = OverbaardIssueEvent.UNASSIGNED;
            }
            if (clearComponents) {
                Assert.assertNull(delegate.components);
                delegate.components = Collections.emptySet();
            }
            if (clearLabels) {
                Assert.assertNull(delegate.labels);
                delegate.labels = Collections.emptySet();
            }
            if (clearFixVersions) {
                Assert.assertNull(delegate.fixVersions);
                delegate.fixVersions = Collections.emptySet();
            }
            if (clearAffectsVersions) {
                Assert.assertNull(delegate.affectsVersions);
                delegate.affectsVersions = Collections.emptySet();
            }
            Issue issue = issueRegistry.getIssue(delegate.issueKey);
            OverbaardIssueEvent update = OverbaardIssueEvent.createUpdateEvent(
                    delegate.issueKey,
                    delegate.projectCode,
                    delegate.issueType,
                    delegate.priority,
                    delegate.summary,
                    delegate.user,
                    delegate.components,
                    delegate.labels,
                    delegate.fixVersions,
                    delegate.affectsVersions,
                    issue.getStatusObject().getName(),
                    delegate.state,
                    rank,
                    delegate.customFieldValues);

            issueRegistry.updateIssue(
                    delegate.issueKey,
                    delegate.issueType,
                    delegate.priority,
                    delegate.summary,
                    delegate.username,
                    delegate.components,
                    delegate.labels,
                    delegate.fixVersions,
                    delegate.affectsVersions,
                    delegate.state);
            if (delegate.customFieldValues != null) {
                for (Map.Entry<Long, String> entry : delegate.customFieldValues.entrySet()) {
                    issueRegistry.setCustomField(delegate.issueKey, entry.getKey(), entry.getValue());
                }
            }
            return update;
        }
    }
}
