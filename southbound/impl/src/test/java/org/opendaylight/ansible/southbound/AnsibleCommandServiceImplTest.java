/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.southbound;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.AnsibleCommandService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.RunAnsibleCommandInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.RunAnsibleCommandInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.RunAnsibleCommandOutput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnsibleCommandServiceImplTest {
    private static final Logger LOG = LoggerFactory.getLogger(AnsibleCommandServiceImplTest.class);
    public @Rule MethodRule guice = new GuiceRule(new AnsibleCommandTestModule());

    @Inject
    private AnsibleCommandService ansibleCommandService;

    @Before
    public void setUp() {
    }

    @Test
    public void testRunCommand() throws ExecutionException, InterruptedException {
        LOG.info("testRunCommand about to blow up");
        RunAnsibleCommandInput input = new RunAnsibleCommandInputBuilder().setDirectory("path")
            .setFile("file").setHost("localhost").build();
        Future<RpcResult<RunAnsibleCommandOutput>> output = ansibleCommandService.runAnsibleCommand(input);
        Assert.assertEquals(output.get().getResult().getStatus(), "OK");
    }
}
