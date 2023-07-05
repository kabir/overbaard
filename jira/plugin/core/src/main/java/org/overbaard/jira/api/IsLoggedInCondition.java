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

import com.atlassian.jira.plugin.webfragment.JiraWebContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.PluginParseException;
import com.atlassian.plugin.web.Condition;

import java.util.Map;

/**
 * Reimplementation of com.atlassian.jira.plugin.webfragment.conditions.UserLoggedInCondition since Jira seems to be
 * having some problems loading up the main conditions. Using UserLoggedInCondition, I bump into:
 * https://answers.atlassian.com/questions/32978766/jira-7-plugin---no-conditions-available-for-web-items
 *
 * @author Kabir Khan
 */
public class IsLoggedInCondition implements Condition {

    @Override
    public void init(Map<String, String> map) throws PluginParseException {
    }

    @Override
    public boolean shouldDisplay(Map<String, Object> context) {
//        ApplicationUser appUser = (ApplicationUser)context.get("user");
//        if(appUser == null) {
//            String username = (String)context.get("username");
//            appUser = ComponentAccessor.getUserUtil().getUserObject(username);
//        }
//
        // The getUserObject() method used above seems deleted in Jira 9. Take the following from the
        // UserLoggedInCondition mentioned in the class comment
        ApplicationUser appUser = JiraWebContext.from(context).getUser().orElse(null);
        return appUser != null;
    }
}
