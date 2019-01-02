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

import static org.overbaard.jira.impl.Constants.BOARD;
import static org.overbaard.jira.impl.Constants.BOARDS;
import static org.overbaard.jira.impl.Constants.BOARD_ID;
import static org.overbaard.jira.impl.Constants.CAN_EDIT_CUSTOM_FIELDS;
import static org.overbaard.jira.impl.Constants.CHANGED_BY;
import static org.overbaard.jira.impl.Constants.CHANGE_TYPE;
import static org.overbaard.jira.impl.Constants.CODE;
import static org.overbaard.jira.impl.Constants.CONFIG;
import static org.overbaard.jira.impl.Constants.EDIT;
import static org.overbaard.jira.impl.Constants.ENTRIES;
import static org.overbaard.jira.impl.Constants.EPIC_LINK_CUSTOM_FIELD_ID;
import static org.overbaard.jira.impl.Constants.EPIC_NAME_CUSTOM_FIELD_ID;
import static org.overbaard.jira.impl.Constants.ID;
import static org.overbaard.jira.impl.Constants.NAME;
import static org.overbaard.jira.impl.Constants.OWNER;
import static org.overbaard.jira.impl.Constants.PROJECTS;
import static org.overbaard.jira.impl.Constants.RANK_CUSTOM_FIELD_ID;
import static org.overbaard.jira.impl.Constants.TIME;
import static org.overbaard.jira.impl.Constants.TEMPLATES;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;

import org.jboss.dmr.ModelNode;
import org.overbaard.jira.OverbaardPermissionException;
import org.overbaard.jira.OverbaardValidationException;
import org.overbaard.jira.api.BoardConfigurationManager;
import org.overbaard.jira.impl.activeobjects.BoardCfg;
import org.overbaard.jira.impl.activeobjects.BoardCfgHistory;
import org.overbaard.jira.impl.activeobjects.BoardCfgTemplate;
import org.overbaard.jira.impl.activeobjects.Setting;
import org.overbaard.jira.impl.config.BoardConfig;
import org.overbaard.jira.impl.config.BoardProjectConfig;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.jira.permission.GlobalPermissionKey;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.GlobalPermissionManager;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.security.plugin.ProjectPermissionKey;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.transaction.TransactionCallback;

import net.java.ao.DBParam;
import net.java.ao.Query;

/**
 * @author Kabir Khan
 */
@Named("overbaardBoardConfigurationManager")
public class BoardConfigurationManagerImpl implements BoardConfigurationManager {

    private static final int HISTORY_LIMIT = 50;

    private volatile Map<String, BoardConfig> boardConfigs = new ConcurrentHashMap<>();

    private final JiraInjectables jiraInjectables;

    /** Custom field ids */
    private volatile long rankCustomFieldId = -1;
    private volatile long epicLinkCustomFieldId = -1;
    private volatile long epicNameCustomFieldId = -1;

    @Inject
    public BoardConfigurationManagerImpl(JiraInjectables jiraInjectables) {
        this.jiraInjectables = jiraInjectables;
    }

    @Override
    public String getBoardConfigurations(ApplicationUser user) {
        Set<BoardCfg> configs = loadBoardConfigs();
        ModelNode boardsList = new ModelNode();
        boardsList.setEmptyList();
        for (BoardCfg config : configs) {
            if (config.getBoardCfgTemplate() == null) {
                // Don't include the boards based on templates
                ModelNode configNode = createBoardModelNode(user, true, config);
                if (configNode != null) {
                    boardsList.add(configNode);
                }
            }
        }

        Set<BoardCfgTemplate> templates = loadBoardTemplates();
        ModelNode templatesList = new ModelNode();
        templatesList.setEmptyList();
        for (BoardCfgTemplate template : templates) {
            ModelNode templateNode = createTemplateModelNode(user, true, template);
            if (templateNode != null) {
                templatesList.add(templateNode);
            }
        }

        //Add a few more fields
        ModelNode config = new ModelNode();
        config.get(BOARDS).set(boardsList);
        config.get(TEMPLATES).set(templatesList);

        config.get(CAN_EDIT_CUSTOM_FIELDS).set(canEditCustomFields(user));
        config.get(RANK_CUSTOM_FIELD_ID).set(getRankCustomFieldId());
        config.get(EPIC_LINK_CUSTOM_FIELD_ID).set(getEpicLinkCustomFieldId());
        config.get(EPIC_NAME_CUSTOM_FIELD_ID).set(getEpicNameCustomFieldId());

        return config.toJSONString(true);
    }

    @Override
    public String getBoardJsonConfig(ApplicationUser user, int boardId) {
        // Note that for this path (basically for the configuration page) we don't validate the loaded
        // config. Otherwise if we break compatibility, people will not be able to fix it.
        BoardCfg cfg = jiraInjectables.getActiveObjects().executeInTransaction(new TransactionCallback<BoardCfg>(){
            @Override
            public BoardCfg doInTransaction() {
                return jiraInjectables.getActiveObjects().get(BoardCfg.class, boardId);
            }
        });
        ModelNode configJson = ModelNode.fromJSONString(cfg.getConfigJson());
        return configJson.toJSONString(true);
    }

    @Override
    public String getBoardTemplateJsonConfig(ApplicationUser user, int templateId) {
        // Note that for this path (basically for the configuration page) we don't validate the loaded
        // config. Otherwise if we break compatibility, people will not be able to fix it.
        BoardCfgTemplate template = jiraInjectables.getActiveObjects().executeInTransaction(new TransactionCallback<BoardCfgTemplate>() {
            @Override
            public BoardCfgTemplate doInTransaction() {
                return jiraInjectables.getActiveObjects().get(BoardCfgTemplate.class, templateId);
            }
        });

        ModelNode configJson = ModelNode.fromJSONString(template.getConfigJson());
        ModelNode boards = new ModelNode();
        for (BoardCfg board : template.getBoardCfgs()) {
            ModelNode boardNode = new ModelNode();
            boardNode.get(ID).set(board.getID());
            boardNode.get(BOARD).set(ModelNode.fromJSONString(board.getConfigJson()));
            boards.add(boardNode);
        }
        configJson.get(BOARDS).set(boards);
        return configJson.toJSONString(true);
    }

    @Override
    public BoardConfig getBoardConfigForBoardDisplay(ApplicationUser user, final String code) {
        BoardConfig boardConfig = getBoardConfig(code);

        if (boardConfig != null && !canViewBoard(user, boardConfig)) {
            throw new OverbaardPermissionException("Insufficient permissions to view board " +
                    boardConfig.getName() + " (" + code + ")");
        }
        return boardConfig;
    }

    @Override
    public BoardConfig getBoardConfig(final String code) {
        BoardConfig boardConfig =  boardConfigs.get(code);
        if (boardConfig == null) {
            final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();
            BoardCfg[] cfgs = activeObjects.executeInTransaction(new TransactionCallback<BoardCfg[]>(){
                @Override
                public BoardCfg[] doInTransaction() {
                    return activeObjects.find(BoardCfg.class, Query.select().where("code = ?", code));
                }
            });

            if (cfgs != null && cfgs.length == 1) {
                BoardCfg cfg = cfgs[0];
                boardConfig = BoardConfig.loadAndValidate(jiraInjectables, cfg.getID(),
                        cfg.getOwningUser(), cfg.getConfigJson(), getRankCustomFieldId(),
                        getEpicLinkCustomFieldId(), getEpicNameCustomFieldId());

                BoardConfig old = boardConfigs.putIfAbsent(code, boardConfig);
                if (old != null) {
                    boardConfig = old;
                }
            }
        }
        return boardConfig;
    }

    @Override
    public BoardConfig saveBoard(ApplicationUser user, final int id, final ModelNode config) {
        //Validate it, and serialize it so that the order of fields is always the same
        final BoardConfig boardConfig;
        final ModelNode validConfig;
        try {
            boardConfig = BoardConfig.loadAndValidate(jiraInjectables, id,
                    user.getKey(), false, config, getRankCustomFieldId(),
                    getEpicLinkCustomFieldId(), getEpicNameCustomFieldId());
            validConfig = boardConfig.serializeModelNodeForConfig();
        } catch (Exception e) {
            throw new OverbaardValidationException("Invalid data: " + e.getMessage());
        }

        final String code = config.get(CODE).asString();
        final String name = config.get(NAME).asString();

        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();

        activeObjects.executeInTransaction(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction() {
                if (!canEditBoard(user, validConfig)) {
                    if (id >= 0) {
                        throw new OverbaardPermissionException("Insufficient permissions to edit board '" +
                                validConfig.get(NAME) + "' (" + id + ")");
                    } else {
                        throw new OverbaardPermissionException("Insufficient permissions to create board '" +
                                validConfig.get(NAME) + "'");
                    }
                }

                final String historyAction;
                final BoardCfg cfg;
                if (id >= 0) {
                    cfg = activeObjects.get(BoardCfg.class, id);
                    cfg.setCode(code);
                    cfg.setName(name);
                    cfg.setOwningUserKey(user.getKey());
                    cfg.setConfigJson(validConfig.toJSONString(true));
                    cfg.save();
                    historyAction = "U";
                } else {
                    cfg = activeObjects.create(
                            BoardCfg.class,
                            new DBParam("CODE", code),
                            new DBParam("NAME", name),
                            new DBParam("OWNING_USER", user.getKey()),
                            //Compact the json before saving it
                            new DBParam("CONFIG_JSON", validConfig.toJSONString(true)));
                    cfg.save();
                    historyAction = "C";
                }

                // Add a history entry
                try {
                    BoardCfgHistory history = activeObjects.create(
                            BoardCfgHistory.class,
                            new DBParam("CODE", code),
                            new DBParam("NAME", name),
                            new DBParam("OWNING_USER", user.getKey()),
                            new DBParam("CHANGING_USER", user.getKey()),
                            new DBParam("CONFIG_JSON", validConfig.toJSONString(true)),
                            new DBParam("BOARD_CFG_ID", cfg.getID()),
                            new DBParam("MODIFIED", new Date()),
                            new DBParam("ACTION", historyAction)
                    );
                    history.save();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (id >= 0) {
                    boardConfigs.remove(code);
                }
                return null;
            }
        });
        return boardConfig;
    }

    @Override
    public BoardConfig saveBoardTemplate(ApplicationUser user, final int id, final ModelNode config) {
        //Validate it, and serialize it so that the order of fields is always the same
        final BoardConfig boardConfig;
        final ModelNode validConfig;
        try {
            boardConfig = BoardConfig.loadAndValidate(jiraInjectables, id,
                    user.getKey(), true, config, getRankCustomFieldId(), getEpicLinkCustomFieldId(), getEpicNameCustomFieldId());

            validConfig = boardConfig.serializeModelNodeForConfig();
        } catch (Exception e) {
            throw new OverbaardValidationException("Invalid data: " + e.getMessage());
        }

        final String code = config.get(CODE).asString();
        final String name = config.get(NAME).asString();

        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();

        activeObjects.executeInTransaction(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction() {
                if (!canEditBoard(user, validConfig)) {
                    if (id >= 0) {
                        throw new OverbaardPermissionException("Insufficient permissions to edit board '" +
                                validConfig.get(NAME) + "' (" + id + ")");
                    } else {
                        throw new OverbaardPermissionException("Insufficient permissions to create board '" +
                                validConfig.get(NAME) + "'");
                    }
                }

                if (id >= 0) {
                    final BoardCfgTemplate cfg = activeObjects.get(BoardCfgTemplate.class, id);
                    cfg.setName(name);
                    cfg.setConfigJson(validConfig.toJSONString(true));
                    cfg.save();
                } else {
                    final BoardCfgTemplate cfg = activeObjects.create(
                            BoardCfgTemplate.class,
                            new DBParam("NAME", name),
                            //Compact the json before saving it
                            new DBParam("CONFIG_JSON", validConfig.toJSONString(true)));
                    cfg.save();
                }
                if (id >= 0) {
                    boardConfigs.remove(code);
                }
                return null;
            }
        });
        return boardConfig;
    }

    @Override
    public void saveBoardForTemplate(ApplicationUser user, int templateId, int boardId, ModelNode config) {

        BoardCfgTemplate template = jiraInjectables.getActiveObjects().executeInTransaction(new TransactionCallback<BoardCfgTemplate>() {
            @Override
            public BoardCfgTemplate doInTransaction() {
                return jiraInjectables.getActiveObjects().get(BoardCfgTemplate.class, templateId);
            }
        });

        //Validate it, and serialize it so that the order of fields is always the same
        final ModelNode mergedConfig;
        final ModelNode validConfig;
        try {
            BoardConfig mergedCfg =
                    BoardConfig.loadAndValidateBoardForTemplate(
                            jiraInjectables, boardId, user.getKey(),
                            ModelNode.fromJSONString(template.getConfigJson()), config,
                            rankCustomFieldId, epicLinkCustomFieldId, epicNameCustomFieldId);
            mergedConfig = mergedCfg.serializeModelNodeForConfig();
            validConfig = BoardConfig.serializeBoardForTemplateConfig(config);

        } catch (Exception e) {
            throw new OverbaardValidationException("Invalid data: " + e.getMessage());
        }


        final String code = config.get(CODE).asString();
        final String name = config.get(NAME).asString();

        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();

        activeObjects.executeInTransaction(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction() {
                if (!canEditBoard(user, mergedConfig)) {
                    if (boardId >= 0) {
                        throw new OverbaardPermissionException("Insufficient permissions to edit board '" +
                                name + "' (" + boardId + ") for template");
                    } else {
                        throw new OverbaardPermissionException("Insufficient permissions to create board '" +
                                name + "' for template");
                    }
                }

                final BoardCfgTemplate templateCfg = activeObjects.get(BoardCfgTemplate.class, templateId);
                final BoardCfg boardCfg;
                if (boardId >= 0) {
                    boardCfg = activeObjects.get(BoardCfg.class, boardId);
                } else {
                    boardCfg = activeObjects.create(
                            BoardCfg.class,
                            new DBParam("CODE", code),
                            new DBParam("NAME", name),
                            new DBParam("OWNING_USER", user.getKey()),
                            //Compact the json before saving it
                            new DBParam("CONFIG_JSON", validConfig.toJSONString(true)));
                }
                boardCfg.setBoardCfgTemplate(templateCfg);
                boardCfg.save();
                return null;
            }
        });
    }

    @Override
    public String deleteBoard(ApplicationUser user, int id) {
        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();

        final String code = activeObjects.executeInTransaction(new TransactionCallback<String>() {
            @Override
            public String doInTransaction() {
                BoardCfg cfg = activeObjects.get(BoardCfg.class, id);
                if (cfg == null) {
                    return null;
                }
                final String code = cfg.getCode();
                final ModelNode boardConfig = ModelNode.fromJSONString(cfg.getConfigJson());
                if (!canEditBoard(user, boardConfig)) {
                    throw new OverbaardPermissionException("Insufficient permissions to delete board '" +
                            boardConfig.get(NAME) + "' (" + id + ")");
                }
                activeObjects.delete(cfg);

                // Add a history entry
                BoardCfgHistory history = activeObjects.create(
                        BoardCfgHistory.class,
                        new DBParam("CODE", code),
                        new DBParam("NAME", cfg.getName()),
                        new DBParam("OWNING_USER", cfg.getOwningUser()),
                        new DBParam("CHANGING_USER", user.getKey()),
                        new DBParam("CONFIG_JSON", cfg.getConfigJson()),
                        new DBParam("BOARD_CFG_ID", cfg.getID()),
                        new DBParam("MODIFIED", new Date()),
                        new DBParam("ACTION", "D")
                );
                history.save();


                return code;
            }
        });
        if (code != null) {
            boardConfigs.remove(code);
        }
        return code;
    }

    @Override
    public void deleteBoardTemplate(ApplicationUser user, int id) {
        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();

        activeObjects.executeInTransaction(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction() {
                BoardCfgTemplate cfg = activeObjects.get(BoardCfgTemplate.class, id);
                if (cfg == null) {
                    return null;
                }
                final ModelNode boardConfig = ModelNode.fromJSONString(cfg.getConfigJson());
                if (!canEditBoard(user, boardConfig)) {
                    throw new OverbaardPermissionException("Insufficient permissions to delete board template'" +
                            boardConfig.get(NAME) + "' (" + id + ")");
                }
                activeObjects.delete(cfg);
                return null;
            }
        });
    }

    @Override
    public List<String> getBoardCodesForProjectCode(String projectCode) {
        //For now just iterate
        List<String> boardCodes = new ArrayList<>();
        for (Map.Entry<String, BoardConfig> entry : boardConfigs.entrySet()) {
            if (entry.getValue().getBoardProject(projectCode) != null) {
                boardCodes.add(entry.getKey());
            }
        }
        return boardCodes;
    }

    @Override
    public void saveCustomFieldIds(ApplicationUser user, ModelNode idsNode) {
        if (!canEditCustomFields(user)) {
            throw new OverbaardPermissionException("Only Jira Administrators can edit the custom field id");
        }

        for (String customFieldKey : idsNode.keys()) {
            try {
                idsNode.get(customFieldKey).asInt();
            } catch (Exception e) {
                throw new OverbaardValidationException(customFieldKey + " needs to be a number");
            }
        }

        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();

        activeObjects.executeInTransaction(new TransactionCallback<Void>() {
            @Override
            public Void doInTransaction() {

                for (String customFieldKey : idsNode.keys()) {
                    String customFieldId = idsNode.get(customFieldKey).asString();

                    Setting[] settings =  activeObjects.find(Setting.class, Query.select().where("name = ?", customFieldKey));
                    if (settings.length == 0) {
                        //Insert
                        final Setting setting = activeObjects.create(
                                Setting.class,
                                new DBParam("NAME", customFieldKey),
                                new DBParam("VALUE", customFieldId));
                        setting.save();
                    } else {
                        //update
                        Setting setting = settings[0];
                        setting.setValue(customFieldId);
                        setting.save();
                    }

                    // Set these to -1 so that they are reloaded. This is the safest if the Tx fails
                    switch (customFieldKey) {
                        case RANK_CUSTOM_FIELD_ID:
                            rankCustomFieldId = -1;
                            break;
                        case EPIC_LINK_CUSTOM_FIELD_ID:
                            epicLinkCustomFieldId = -1;
                            break;
                        case EPIC_NAME_CUSTOM_FIELD_ID:
                            epicNameCustomFieldId = -1;
                    }
                }
                return null;
            }
        });

    }

    @Override
    public String getStateHelpTextsJson(ApplicationUser user, String boardCode) {
        BoardConfig cfg = getBoardConfigForBoardDisplay(user, boardCode);
        Map<String, String> helpTexts = cfg.getStateHelpTexts();
        ModelNode output = new ModelNode();
        output.setEmptyObject();
        for (Map.Entry<String, String> entry : helpTexts.entrySet()) {
            output.get(entry.getKey()).set(entry.getValue());
        }
        return output.toJSONString(true);
    }

    private Set<BoardCfg> loadBoardConfigs() {
        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();

        return activeObjects.executeInTransaction(new TransactionCallback<Set<BoardCfg>>(){
            @Override
            public Set<BoardCfg> doInTransaction() {
                Set<BoardCfg> configs = new TreeSet<>((o1, o2) -> {
                    return o1.getName().compareTo(o2.getName());
                });
                for (BoardCfg boardCfg : activeObjects.find(BoardCfg.class)) {
                    configs.add(boardCfg);

                }
                return configs;
            }
        });
    }

    private Set<BoardCfgTemplate> loadBoardTemplates() {
        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();

        return activeObjects.executeInTransaction(new TransactionCallback<Set<BoardCfgTemplate>>(){
            @Override
            public Set<BoardCfgTemplate> doInTransaction() {
                Set<BoardCfgTemplate> configs = new TreeSet<>((o1, o2) -> {
                    return o1.getName().compareTo(o2.getName());
                });
                for (BoardCfgTemplate boardCfg : activeObjects.find(BoardCfgTemplate.class)) {
                    configs.add(boardCfg);

                }
                return configs;
            }
        });
    }

    @Override
    public long getRankCustomFieldId() {
        long id = this.rankCustomFieldId;
        if (id < 0) {
            id = loadCustomFieldId(RANK_CUSTOM_FIELD_ID);
            this.rankCustomFieldId = id;
        }
        return id;
    }

    public long getEpicLinkCustomFieldId() {
        long id = this.epicLinkCustomFieldId;
        if (id < 0) {
            id = loadCustomFieldId(EPIC_LINK_CUSTOM_FIELD_ID);
            this.epicLinkCustomFieldId = id;
        }
        return id;
    }

    public long getEpicNameCustomFieldId() {
        long id = this.epicNameCustomFieldId;
        if (id < 0) {
            id = loadCustomFieldId(EPIC_NAME_CUSTOM_FIELD_ID);
            this.epicNameCustomFieldId = id;
        }
        return id;
    }

    private long loadCustomFieldId(String name) {
        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();

        Setting[] settings = activeObjects.executeInTransaction(new TransactionCallback<Setting[]>() {
            @Override
            public Setting[] doInTransaction() {
                return activeObjects.find(Setting.class, Query.select().where("name = ?", name));
            }
        });
        if (settings.length == 1) {
            return Long.valueOf(settings[0].getValue());
        }
        return -1;
    }

    @Override
    public String getBoardConfigurationHistoryJson(ApplicationUser user, String restRootUrl, Integer cfgId, Integer fromId) {
        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();
        // Unfortunately select distinct doesn't work, so just do a paged thing
        Query query = Query.select()
                .order("MODIFIED DESC");

        StringBuilder whereClause = new StringBuilder();
        List<Object> whereParams = new ArrayList<>();
        if (fromId != null && fromId > 0) {
            whereClause.append("ID < ?");
            whereParams.add(fromId);
        }
        if (cfgId != null) {
            if (whereClause.length() > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append("BOARD_CFG_ID = ?");
            whereParams.add(cfgId);
        }
        if (whereClause.length() > 0) {
            query.where(whereClause.toString(), whereParams.toArray());
        }
        query = query.limit(HISTORY_LIMIT);
        final Query theQuery = query;


        BoardCfgHistory[] history = activeObjects.executeInTransaction(new TransactionCallback<BoardCfgHistory[]>() {
            @Override
            public BoardCfgHistory[] doInTransaction() {
                return activeObjects.find(BoardCfgHistory.class, theQuery);
            }
        });

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd\'T\'HH:mmZ");
        ModelNode historyList = new ModelNode().setEmptyList();
        int startId = fromId == null ? 0 : fromId + 1;
        int endId = 0;
        for (BoardCfgHistory h : history) {
            if (startId == 0) {
                startId = h.getID();
            }
            endId = h.getID();

            ModelNode entry = new ModelNode();
            entry.get(ID).set(h.getID());
            entry.get(CODE).set(h.getCode());
            entry.get(BOARD_ID).set(h.getBoardCfgId());
            entry.get(CHANGED_BY).set(h.getChangingUser());
            entry.get(CHANGE_TYPE).set(formatChangeType(h));
            entry.get(TIME).set(formatDate(h.getModified()));
            entry.get("url").set(restRootUrl + "/" + h.getID());
            historyList.add(entry);
        }

        ModelNode result = new ModelNode();
        if (history.length == HISTORY_LIMIT) {
            String nextPath = "?from=" + endId;
            if (cfgId != null) {
                nextPath += "&" + BOARD_ID +"=" + cfgId;
            }
            result.get("next").set(restRootUrl + nextPath);
        }
        result.get(ENTRIES).set(historyList);

        return result.toJSONString(false);
    }

    @Override
    public String getBoardConfigurationHistoryEntry(ApplicationUser user, Integer historyEntryId) {
        final ActiveObjects activeObjects = jiraInjectables.getActiveObjects();
        BoardCfgHistory history = activeObjects.executeInTransaction(new TransactionCallback<BoardCfgHistory[]>() {
            @Override
            public BoardCfgHistory[] doInTransaction() {
                return activeObjects.find(BoardCfgHistory.class, Query.select().where("ID = ?", historyEntryId));
            }
        })[0];

        ModelNode historyNode = new ModelNode();
        historyNode.get(ID).set(history.getID());
        historyNode.get(NAME).set(history.getName());
        historyNode.get(CODE).set(history.getCode());
        historyNode.get(OWNER).set(history.getOwningUser());
        historyNode.get(BOARD_ID).set(history.getBoardCfgId());
        historyNode.get(CHANGED_BY).set(history.getChangingUser());
        historyNode.get(CHANGE_TYPE).set(formatChangeType(history));
        historyNode.get(TIME).set(formatDate(history.getModified()));
        historyNode.get(CONFIG).set(ModelNode.fromJSONString(history.getConfigJson()));

        return historyNode.toJSONString(false);
    }

    private String formatDate(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd\'T\'HH:mmZ");
        return format.format(date);
    }

    private String formatChangeType(BoardCfgHistory h) {
        String changeType = "Updated";
        if (h.getAction().equals("D")) {
            changeType = "Deleted";
        } else if (h.getAction().equals("C")) {
            changeType = "Created";
        }
        return changeType;
    }

    private ModelNode createBoardModelNode(ApplicationUser user, boolean forConfig, BoardCfg cfg) {
        return createBoardOrTemplateModelNode(
                user, forConfig, cfg.getID(), cfg.getCode(), cfg.getName(), cfg.getConfigJson());
    }

    private ModelNode createTemplateModelNode(ApplicationUser user, boolean forConfig, BoardCfgTemplate template) {
        return createBoardOrTemplateModelNode(
                user, forConfig, template.getID(), null, template.getName(), template.getConfigJson());
    }

    private ModelNode createBoardOrTemplateModelNode(ApplicationUser user, boolean forConfig, int id, String code, String name, String json) {
        ModelNode configNode = new ModelNode();
        configNode.get(ID).set(id);
        if (code != null) {
            configNode.get(CODE).set(code);
        }
        configNode.get(NAME).set(name);
        ModelNode configJson = ModelNode.fromJSONString(json);
        if (forConfig) {
            if (canEditBoard(user, configJson)) {
                configNode.get(EDIT).set(true);
            }
            return configNode;
        } else {
            //A guess at what is needed to view the boards
            if (canViewBoard(user, configNode)) {
                return configNode;
            }
        }
        return null;
    }

    //Permission methods
    private boolean canEditBoard(ApplicationUser user, ModelNode boardConfig) {
        return hasPermissionBoard(user, boardConfig, ProjectPermissions.ADMINISTER_PROJECTS);
    }

    private boolean canViewBoard(ApplicationUser user, ModelNode boardConfig) {
        //A wild guess at a reasonable permission needed to view the boards
        return hasPermissionBoard(user, boardConfig, ProjectPermissions.TRANSITION_ISSUES);
    }

    private boolean canViewBoard(ApplicationUser user, BoardConfig boardConfig) {
        //A wild guess at a reasonable permission needed to view the boards
        return hasPermissionBoard(user, boardConfig, ProjectPermissions.TRANSITION_ISSUES);
    }

    private boolean canEditCustomFields(ApplicationUser user) {
        //Only Jira Administrators can tweak the custom field ids
        return isJiraAdministrator(user);
    }

    private boolean hasPermissionBoard(ApplicationUser user, BoardConfig boardConfig, ProjectPermissionKey... permissions) {
        for (BoardProjectConfig boardProject : boardConfig.getBoardProjects()) {
            if (!hasPermission(user, boardProject.getCode(), permissions)) {
                return false;
            }
        }
        return true;
    }


    private boolean hasPermissionBoard(ApplicationUser user, ModelNode boardConfig, ProjectPermissionKey...permissions) {
        if (isJiraAdministrator(user)) {
            return true;
        }
        if (!boardConfig.hasDefined(PROJECTS)) {
            //The project is empty, start checking once they add something
            return true;
        }
        for (ModelNode project : boardConfig.get(PROJECTS).asList()) {
            String projectCode = project.get(CODE).asString();
            if (!hasPermission(user, projectCode, permissions)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPermission(ApplicationUser user, String projectCode, ProjectPermissionKey[] permissions) {
        if (isJiraAdministrator(user)) {
            return true;
        }
        final ProjectManager projectManager = jiraInjectables.getProjectManager();
        final PermissionManager permissionManager = jiraInjectables.getPermissionManager();

        Project project = projectManager.getProjectByCurrentKey(projectCode);
        for (ProjectPermissionKey permission : permissions) {
            if (!permissionManager.hasPermission(permission, project, user)) {
                return false;
            }
        }
        return true;
    }

    private boolean isJiraAdministrator(ApplicationUser user) {
        final GlobalPermissionManager globalPermissionManager = jiraInjectables.getGlobalPermissionManager();

        return globalPermissionManager.hasPermission(GlobalPermissionKey.ADMINISTER, user);
    }
}
