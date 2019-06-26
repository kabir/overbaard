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

package org.overbaard.jira.api;

import org.jboss.dmr.ModelNode;

import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.user.ApplicationUser;

public interface JiraFacade {
    /**
     * Gets the board configurations
     * @param user the logged in user
     * @return the boards that the user is allowed to configure
     */
    String getBoardConfigurations(ApplicationUser user);

    /**
     * Creates a new or saves a board configuration
     * @param user the logged in user
     * @param id the id of the board
     * @param config the configuration to save
     */
    void saveBoardConfiguration(ApplicationUser user, int id, ModelNode config);

    /**
     * Creates a new or saves a board configuration
     * @param user the logged in user
     * @param id the id of the board
     * @param config the configuration to save
     */
    void saveBoardTemplateConfiguration(ApplicationUser user, int id, ModelNode config);

    /**
     * Deletes a board from the storage
     * @param user the logged in user
     * @param id the id of the board
     */
    void deleteBoardConfiguration(ApplicationUser user, int id);

    /**
     * Deletes a board template from the storage
     * @param user the logged in user
     * @param id the id of the board
     */
    void deleteBoardTemplateConfiguration(ApplicationUser user, int id);

    /**
     * Creates or updates a board config based on a template
     * @param user the currently
     * @param templateId the id of the template
     * @param boardId the id of the board. If creating a new board use {@code -1}
     * @param config the configuration to save
     */
    void saveBoardForTemplateConfiguration(ApplicationUser user, int templateId, int boardId, ModelNode config);

    /**
     * Gets the boards visible to the user.
     * @param user
     * @return the json of the boards
     */
    String getBoardsForDisplay(ApplicationUser user);

    /**
     * Gets a board for displaying to the user
     * @param user the user
     * @param backlog if {@true} we will include issues belonging to the backlog states
     * @param code the board code
     * @return the board's json
     * @throws SearchException
     */
    String getBoardJson(ApplicationUser user, boolean backlog, String code) throws SearchException;

    /**
     * Get the name of a board from its code
     * @param user the user
     * @param boardCode the board code
     * @return json containing the name of the board
     */
    String getBoardName(ApplicationUser user, String boardCode) throws SearchException;


    /**
     * Gets the changes for a board. The client passes in their view id, and the delta is passed back to the client in
     * json format so they can apply it to their own model.
     *
     * @param user the logged in user
     * @param backlog if {@true} we will include issues belonging to the backlog states
     * @param code the board code
     * @param viewId the view id of the client.
     * @return the json containing the changes
     */
    String getChangesJson(ApplicationUser user, boolean backlog, String code, int viewId) throws SearchException;

    /**
     * Saves the id of the custom field that Jira Agile uses for its 'Rank'.
     *
     * @param user the logged in user
     * @param idNode an object containing the id
     */
    void saveCustomFieldIds(ApplicationUser user, ModelNode idNode);

    /**
     * Loads the Overbård version from the manifest file
     *
     * @return the version string
     */
    String getOverbaardVersion();

    /**
     * Loads a board's json configuration for editing
     * @param user the logged in user
     * @param boardId the id of the board
     * @return the board's json
     */
    String getBoardJsonForConfig(ApplicationUser user, int boardId);

    /**
     * Loads a board template's json configuration for editing
     * @param user the logged in user
     * @param templateId the id of the board template
     * @return the board template's json
     */
    String getBoardTemplateJsonForConfig(ApplicationUser user, int templateId);


    /**
     * Gets any help text entered for a board's states
     *
     * @param user the logged in user
     * @param boardCode the code for the board
     * @return json containing the state help texts
     */
    String getStateHelpTexts(ApplicationUser user, String boardCode);

    /**
     * Logs a user access
     *
     * @param user the logged in user
     * @param boardCode the board code
     */
    void logUserAccess(ApplicationUser user, String boardCode, String userAgent);

    /**
     * Loads the list of accesses
     *
     * @param user the currently logged in user
     * @return json of the user accesses
     */
    String getUserAccessJson(ApplicationUser user);

    /**
     * Updates an issue's parallel task value
     * @param user the currently logged in used
     * @param boardCode
     * @param issueKey
     * @param groupIndex the index of the parallel task group within the issue's projects config
     * @param taskIndex the index of the task within the group
     * @param optionIndex the index of the option within the task
     */
    void updateParallelTaskForIssue(ApplicationUser user, String boardCode, String issueKey, int groupIndex, int taskIndex, int optionIndex) throws SearchException;

    /**
     * Get configuration history. This returns a full list
     *
     * @param user the user
     * @param restRootUrl
     * @return JSON containing the config history
     */
    String getBoardConfigurationHistory(ApplicationUser user, String restRootUrl, Integer cfgId, Integer fromId);

    String getBoardConfigurationHistoryEntry(ApplicationUser user, Integer historyEntryId);
}