/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */

package org.jivesoftware.spark.ui;

import javax.swing.Icon;
import javax.swing.JComponent;

/**
 *
 */
public interface ContainerComponent {

    public abstract String getTabTitle();

    public abstract Icon getTabIcon();

    public abstract JComponent getGUI();

    public abstract String getToolTipDescription();

    public abstract void closing();
}
