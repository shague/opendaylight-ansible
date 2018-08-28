/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.northbound.api;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class AnsibleTopology {
    public static final TopologyId ANSIBLE_TOPOLOGY_ID = new TopologyId("ansible:1");

    private AnsibleTopology() {}

    public static final InstanceIdentifier<Topology> ANSIBLE_TOPOLOGY_PATH =
        InstanceIdentifier.create(NetworkTopology.class).child(Topology.class, new TopologyKey(ANSIBLE_TOPOLOGY_ID));

    public static final InstanceIdentifier<Node> ANSIBLE_NODE_PATH =
        InstanceIdentifier.create(NetworkTopology.class)
            .child(Topology.class, new TopologyKey(ANSIBLE_TOPOLOGY_ID))
            .child(Node.class);


}
