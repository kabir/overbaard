package org.overbaard.jira.impl.activeobjects;

import net.java.ao.Entity;
import net.java.ao.schema.NotNull;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Unique;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public interface BoardCfgTemplate extends Entity {
    /**
     * The name of the 'category' implied by this template that will appear in the overview lists of boards
     * @return the name
     */
    @NotNull
    @Unique
    String getName();
    void setName(String name);

    @NotNull
    @StringLength(StringLength.UNLIMITED)
    String getConfigJson();
    void setConfigJson(String json);

}
