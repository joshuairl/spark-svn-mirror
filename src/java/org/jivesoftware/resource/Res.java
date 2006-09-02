/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */

package org.jivesoftware.resource;

import java.text.MessageFormat;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Use for Spark Internationalization.
 *
 * @author Derek DeMoro
 */
public class Res {
    private static PropertyResourceBundle prb;

    private Res() {

    }

    static ClassLoader cl = Res.class.getClassLoader();

    static {
        prb = (PropertyResourceBundle)ResourceBundle.getBundle("i18n/spark_i18n");
    }

    public static final String getString(String propertyName) {
        return prb.getString(propertyName);
    }

    public static final String getString(String propertyName, Object... obj) {
        String str = prb.getString(propertyName);
        if (str == null) {
            return null;
        }



        return MessageFormat.format(str, obj);
    }

    public static PropertyResourceBundle getBundle() {
        return prb;
    }
}