/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.northbound;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.rev180821.AnsibleService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.rev180821.RunCommandInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.rev180821.RunCommandInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.rev180821.RunCommandOutput;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnsibleServiceImplTest {
    private static final Logger LOG = LoggerFactory.getLogger(AnsibleServiceImplTest.class);
    public @Rule MethodRule guice = new GuiceRule(new AnsibleTestModule());

    @Inject
    private AnsibleService ansibleService;

    @Before
    public void setUp() {
    }

    @Test
    public void testRunCommand() throws ExecutionException, InterruptedException {
        LOG.info("testRunCommand about to blow up");
        RunCommandInput input = new RunCommandInputBuilder().setDirectory("path")
            .setFile("file").setHost("localhost").build();
        Future<RpcResult<RunCommandOutput>> output = ansibleService.runCommand(input);
        Assert.assertEquals(output.get().getResult().getSaysomething(), "hi");
    }
}
