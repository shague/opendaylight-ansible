/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.northbound.api;

import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.northbound.rev180821.Commands;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.northbound.rev180821.commands.Command;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public final class AnsibleCommand {

    private AnsibleCommand() {}

    public static final InstanceIdentifier<Command> ANSIBLE_COMMAND_PATH =
        InstanceIdentifier.create(Commands.class).child(Command.class);

}
