/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.southbound;

import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.AnsibleCommandService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.RunAnsibleCommandInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.RunAnsibleCommandOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.RunAnsibleCommandOutputBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AnsibleCommandServiceImpl implements AnsibleCommandService {
    private static final Logger LOG = LoggerFactory.getLogger(AnsibleCommandServiceImpl.class);

    @Inject
    public AnsibleCommandServiceImpl() {
        LOG.info("constructor");
    }

    @Override
    public ListenableFuture<RpcResult<RunAnsibleCommandOutput>> runAnsibleCommand(RunAnsibleCommandInput input) {
        LOG.info("We made it!");
        return RpcResultBuilder.success(new RunAnsibleCommandOutputBuilder().setStatus("OK").build()).buildFuture();
    }
}
