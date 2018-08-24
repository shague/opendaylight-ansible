/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.northbound;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.ansible.mdsalutils.Datastore.CONFIGURATION;
import static org.opendaylight.ansible.northbound.api.AnsibleTopology.ANSIBLE_TOPOLOGY_ID;
import static org.opendaylight.ansible.northbound.api.AnsibleTopology.ANSIBLE_TOPOLOGY_PATH;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.ansible.mdsalutils.Datastore.Configuration;
import org.opendaylight.ansible.mdsalutils.RetryingManagedNewTransactionRunner;
import org.opendaylight.ansible.mdsalutils.SingleTransactionDataBroker;
import org.opendaylight.ansible.mdsalutils.TypedReadTransaction;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.test.ConstantSchemaAbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NorthboundProviderTest extends ConstantSchemaAbstractDataBrokerTest {
    private static final Logger LOG = LoggerFactory.getLogger(NorthboundProviderTest.class);
    private DataBroker dataBroker;

    @Before
    public void setUp() {
        dataBroker = getDataBroker();
        NorthboundProvider northboundProvider = new NorthboundProvider(dataBroker);
    }

    @Test
    public void testInitializeAnsibleTopology() {
        ReadOnlyTransaction transaction = dataBroker.newReadOnlyTransaction();
        CheckedFuture<Optional<Topology>, ReadFailedException> ansibleTopology =
            transaction.read(LogicalDatastoreType.CONFIGURATION, ANSIBLE_TOPOLOGY_PATH);
        try {
            assertTrue(ansibleTopology.get().isPresent());
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("NorthboundProvider error reading ansible topology", e);
        }
    }

    @Test
    public void testInitializeAnsibleTopology2() {
        try {
            Optional<Topology> ansibleTopology = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, ANSIBLE_TOPOLOGY_PATH);
            assertTrue(ansibleTopology.isPresent());
            assertNotNull(ansibleTopology.get());
        } catch (ReadFailedException e) {
            LOG.error("NorthboundProvider error reading ansible topology", e);
        }

        RetryingManagedNewTransactionRunner txRunner =
            new RetryingManagedNewTransactionRunner(dataBroker, 3);
        txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
            Optional<Topology> ansibleTopology = tx.read(ANSIBLE_TOPOLOGY_PATH).get();
            assertTrue(ansibleTopology.isPresent());
            assertNotNull(ansibleTopology.get());
        });
    }
}
