/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.northbound;

import com.google.common.util.concurrent.ListenableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.rev180821.AnsibleService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.rev180821.RunCommandInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.rev180821.RunCommandOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.rev180821.RunCommandOutputBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AnsibleServiceImpl implements AnsibleService {
    private static final Logger LOG = LoggerFactory.getLogger(AnsibleServiceImpl.class);

    @Inject
    public AnsibleServiceImpl() {
        LOG.info("constructor");
    }

    @Override
    public ListenableFuture<RpcResult<RunCommandOutput>> runCommand(RunCommandInput input) {
        LOG.info("We made it!");
        return RpcResultBuilder.success(new RunCommandOutputBuilder().setSaysomething("hi").build()).buildFuture();
    }
}
