/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.northbound;

import static org.opendaylight.ansible.mdsalutils.Datastore.CONFIGURATION;
import static org.opendaylight.ansible.mdsalutils.Datastore.OPERATIONAL;
import static org.opendaylight.ansible.northbound.api.AnsibleTopology.ANSIBLE_TOPOLOGY_ID;
import static org.opendaylight.ansible.northbound.api.AnsibleTopology.ANSIBLE_TOPOLOGY_PATH;

import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.ansible.mdsalutils.Datastore;
import org.opendaylight.ansible.mdsalutils.RetryingManagedNewTransactionRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NorthboundProvider {
    private static final Logger LOG = LoggerFactory.getLogger(NorthboundProvider.class);
    private final RetryingManagedNewTransactionRunner txRunner;

    @Inject
    public NorthboundProvider(final DataBroker dataBroker) {
        LOG.info("constructor");
        txRunner = new RetryingManagedNewTransactionRunner(dataBroker, 3);
        initializeAnsibleTopology(CONFIGURATION);
        initializeAnsibleTopology(OPERATIONAL);
    }

    private void initializeAnsibleTopology(Class<? extends Datastore> datastoreType) {
        TopologyBuilder tpb = new TopologyBuilder();
        tpb.setTopologyId(ANSIBLE_TOPOLOGY_ID);
        try {
            txRunner.callWithNewWriteOnlyTransactionAndSubmit(datastoreType,
                tx -> tx.put(ANSIBLE_TOPOLOGY_PATH, tpb.build())).get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("NorthboundProvider error initializing ansible topology", e);
        }
    }
}
