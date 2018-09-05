/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.northbound;

import static org.opendaylight.ansible.northbound.api.IetfL3vpnSvcUtils.STATUS_L3VPN_PATH;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.StatusL3vpnProvider;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.ops4j.pax.cdi.api.OsgiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StatusL3vpnListener extends AbstractSyncDataTreeChangeListener<StatusL3vpnProvider> {
    private static final Logger LOG = LoggerFactory.getLogger(StatusL3vpnListener.class);

    @Inject
    public StatusL3vpnListener(@OsgiService final DataBroker dataBroker) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, STATUS_L3VPN_PATH);
        LOG.info("constructor");
    }

    @Override
    public void add(@Nonnull InstanceIdentifier<StatusL3vpnProvider> instanceIdentifier,
                    @Nonnull StatusL3vpnProvider statusL3vpn) {
        LOG.info("add: id: {}\nstatusL3vpn: {}", instanceIdentifier, statusL3vpn);
    }

    @Override
    public void remove(@Nonnull InstanceIdentifier<StatusL3vpnProvider> instanceIdentifier,
                       @Nonnull StatusL3vpnProvider statusL3vpn) {
        LOG.info("remove: id: {}\nstatusL3vpn: {}", instanceIdentifier, statusL3vpn);
    }

    @Override
    public void update(@Nonnull InstanceIdentifier<StatusL3vpnProvider> instanceIdentifier,
                       @Nonnull StatusL3vpnProvider oldStatusL3vpn, @Nonnull StatusL3vpnProvider newStatusL3vpn) {
        LOG.info("update: id: {}\nold: {}\nnew: {}", instanceIdentifier, oldStatusL3vpn, newStatusL3vpn);
    }
}
