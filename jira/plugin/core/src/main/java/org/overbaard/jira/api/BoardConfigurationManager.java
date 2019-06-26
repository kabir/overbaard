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

import java.util.List;

import org.jboss.dmr.ModelNode;
import org.overbaard.jira.OverbaardPermissionException;
import org.overbaard.jira.OverbaardValidationException;
import org.overbaard.jira.impl.config.BoardConfig;

import com.atlassian.jira.user.ApplicationUser;

/**
 * @author Kabir Khan
 */
public interface BoardConfigurationManager {
    /**
     * Gets all the boards. This does not do any attempt to validate the JSON, so that following a structure change
     * for the config, we can still go in and edit everything. It filters boards which the user can not see from
     * the view, but does not throw any permission exceptions.
     *
     * @param user      the logged in user
     * @return the json for the boards
     */
    String getBoardConfigurations(ApplicationUser user);

    /**
     * Saves a new board (if {@code id < 0}, or updates an exisiting one. Permissions are checked to see if the user
     * can update anything
     * @param user    the logged in user
     * @param id      the id of the board
     * @param config  the configuration
     * @throws OverbaardPermissionException if the user does not have the correct permissions
     * @throws OverbaardValidationException if the input is bad
     */
    BoardConfig saveBoard(ApplicationUser user, int id, ModelNode config);


    /**
     * Saves a new board template (if {@code id < 0}, or updates an exisiting one. Permissions are checked to see if the user
     * can update anything
     * @param user    the logged in user
     * @param id      the id of the board
     * @param config  the configuration
     * @throws OverbaardPermissionException if the user does not have the correct permissions
     * @throws OverbaardValidationException if the input is bad
     */
    BoardConfig saveBoardTemplate(ApplicationUser user, int id, ModelNode config);

    /**
     * Creates or updates a board config based on a template
     * @param user the currently
     * @param templateId the id of the template
     * @param boardId the id of the board. If creating a new board use {@code -1}
     * @param config the configuration to save
     */
    void saveBoardForTemplate(ApplicationUser user, int templateId, int boardId, ModelNode config);

    /**
     * Deletes a board. Permissions are checked to see if the user
     * can delete it
     * @param user    the logged in user
     * @param id      the id of the board
     * @return the code of the deleted board
     * @throws OverbaardPermissionException if the user does not have the correct permissions
     */
    String deleteBoard(ApplicationUser user, int id);

    /**
     * Deletes a board template. Permissions are checked to see if the user
     * can delete it
     * @param user    the logged in user
     * @param id      the id of the board template
     * @throws OverbaardPermissionException if the user does not have the correct permissions
     */
    void deleteBoardTemplate(ApplicationUser user, int id);

    /**
     * Loads the board configuration. Permissions are checked to see if the user
     * can update anything.
     * @param user the user
     * @param code the configuration code
     * @return the configuration
     * @throws OverbaardPermissionException if the user does not have the correct permissions
     * @throws OverbaardValidationException if the structure of the config is bad
     */
    BoardConfig getBoardConfigForBoardDisplay(ApplicationUser user, String code);

    /**
     * Loads the board configuration without checking permissions
     *
     * @param code the configuration code
     * @return the configuration
     * @throws OverbaardValidationException if the structure of the config is bad
     */
    BoardConfig getBoardConfig(String code);

    /**
     * Gets all the boards set up for a given project
     *
     * @param projectCode the project code
     * @return the board codes
     */
    List<String> getBoardCodesForProjectCode(String projectCode);

    /**
     * Saves the id of the custom field that Jira Agile uses for its 'Rank'.
     *
     * @param user the logged in user
     * @param idNode an object containing the id
     */
    void saveCustomFieldIds(ApplicationUser user, ModelNode idNode);

    String getBoardJsonConfig(ApplicationUser user, int boardId);

    String getBoardTemplateJsonConfig(ApplicationUser user, int templateId);

    String getStateHelpTextsJson(ApplicationUser user, String boardCode);

    long getRankCustomFieldId();

    String getBoardConfigurationHistoryJson(ApplicationUser user, String restRootUrl, Integer cfgId, Integer fromId);

    String getBoardConfigurationHistoryEntry(ApplicationUser user, Integer historyEntryId);
}

