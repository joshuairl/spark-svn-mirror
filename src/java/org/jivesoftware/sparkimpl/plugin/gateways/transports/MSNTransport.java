/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Lesser Public License (LGPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.sparkimpl.plugin.gateways.transports;

import org.jivesoftware.resource.SparkRes;
import org.jivesoftware.resource.Res;

import javax.swing.Icon;

/**
 *
 */
public class MSNTransport implements Transport {

    private String serviceName;

    public MSNTransport(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getTitle() {
        return Res.getString("title.msn.registration");
    }

    public String getInstructions() {
        return Res.getString("message.enter.msn");
    }

    public Icon getIcon() {
        return SparkRes.getImageIcon(SparkRes.MSN_TRANSPORT_ACTIVE_IMAGE);
    }

    public Icon getInactiveIcon() {
        return SparkRes.getImageIcon(SparkRes.MSN_TRANSPORT_INACTIVE_IMAGE);
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getName(){
        return "MSN";
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
