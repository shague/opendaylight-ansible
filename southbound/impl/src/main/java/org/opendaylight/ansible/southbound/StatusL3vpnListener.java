/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.ansible.southbound;

import static org.opendaylight.ansible.mdsalutils.Datastore.OPERATIONAL;
import static org.opendaylight.ansible.northbound.api.IetfL3vpnSvcUtils.STATUS_L3VPN_PATH;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.Status.InProgress;

import ch.vorburger.exec.ManagedProcessException;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.ansible.mdsalutils.RetryingManagedNewTransactionRunner;
import org.opendaylight.ansible.mdsalutils.SingleTransactionDataBroker;
import org.opendaylight.ansible.northbound.api.IetfL3vpnSvcUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.serviceutils.tools.mdsal.listener.AbstractSyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.L3vpnSvc;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.Status;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.StatusL3vpnProvider;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.l3vpn.svc.fields.Sites;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.l3vpn.svc.fields.sites.Site;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.l3vpn.svc.fields.sites.site.site.network.accesses.SiteNetworkAccess;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.l3vpn.svc.aug.rev170502.PeAugmentation;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.ops4j.pax.cdi.api.OsgiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StatusL3vpnListener extends AbstractSyncDataTreeChangeListener<StatusL3vpnProvider> {
    private static final Logger LOG = LoggerFactory.getLogger(StatusL3vpnListener.class);
    private final RetryingManagedNewTransactionRunner txRunner;
    private final DataBroker dataBroker;
    private static final String COMMIT_VERSION = "123";
    private final AnsibleCommandServiceImpl ansibleCommandService;
    private org.opendaylight.yang.gen.v1.urn.opendaylight.ansible.command.rev180821.Status ansibleStatus;
    private ListeningExecutorService executor;
    private static ListenableFuture<List<Uuid>> l3vpnCommands;

    @Inject
    public StatusL3vpnListener(@OsgiService final DataBroker dataBroker,
                               AnsibleCommandServiceImpl ansibleCommandService) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, STATUS_L3VPN_PATH);
        this.txRunner = new RetryingManagedNewTransactionRunner(dataBroker, 3);
        this.dataBroker = dataBroker;
        this.ansibleCommandService = ansibleCommandService;
        executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(5));
    }

    @Override
    public void add(@Nonnull InstanceIdentifier<StatusL3vpnProvider> instanceIdentifier,
                    @Nonnull StatusL3vpnProvider statusL3vpn) {
        LOG.info("add: id: {}\nstatusL3vpn: {}", instanceIdentifier, statusL3vpn);
        txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
            try {
                if (IetfL3vpnSvcUtils.getStatus(tx) == InProgress) {
                    LOG.info("Creating VPN");
                    IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, createVpn());
                    LOG.info("VPN is being created");
                }
            } catch (ExecutionException e) {
                LOG.error("Unable to determine status of L3VPN");
                IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, Status.Failed);
            } catch (AnsibleCommandException e) {
                LOG.error("L3 VPN provisioning failed. Ansible southbound Command failed: {}", e.getMessage());
                IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, Status.Failed);
            }

        });
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
        // ain't nobody got time to for this
    }

    private Status createVpn() throws AnsibleCommandException, L3vpnConfigException {
        Optional<L3vpnSvc> optL3vpnSvc;
        L3vpnSvc l3vpnSvc;
        try {
            optL3vpnSvc = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    InstanceIdentifier.create(L3vpnSvc.class));

        } catch (ReadFailedException e) {
            LOG.error("Unable to read L3 VPN config: {}", e.getMessage());
            return Status.Failed;
        }
        if (! optL3vpnSvc.isPresent()) {
            LOG.error("L3 VPN config is missing");
            throw new L3vpnConfigException("L3 VPN configuration is missing");
        }
        l3vpnSvc = optL3vpnSvc.get();
        // TODO(trozet): check if sites really belong to Ansible in non-demo implementation
        // Kick off ansible processes in parallel, then use Futures to callback when it is all done

        List<ListenableFuture<Uuid>> cmdFutures = new ArrayList<>();
        Sites sites = l3vpnSvc.getSites();
        if (sites == null) {
            LOG.error("L3 VPN config missing sites configuration");
            throw new L3vpnConfigException("L3 VPN config missing sites");
        }
        List<Site> siteList = sites.getSite();
        if (siteList == null || siteList.isEmpty()) {
            LOG.error("L3 VPN config missing sites configuration");
            throw new L3vpnConfigException("L3 VPN config missing site configuration");
        }

        for (Site site: siteList) {
            if (site == null) {
                LOG.warn("Null site found in L3 VPN config...ignoring");
                continue;
            }
            LOG.info("Examining site {}", site.toString());
            cmdFutures.add(configureSite(site));
        }

        l3vpnCommands = Futures.successfulAsList(cmdFutures);
        l3vpnCommands.addListener(() -> {
            try {
                l3vpnCommands.get().forEach(uuid -> {
                    if (uuid == null) {
                        txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx ->
                            IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, Status.Failed)
                        );
                        return;
                    }
                });
                txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx ->
                    IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, Status.Complete)
                );
            } catch (InterruptedException | ExecutionException e) {
                txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx ->
                    IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, Status.Failed)
                );
            }
        }, executor);
        return Status.InProgress;
    }

    private ListenableFuture<Uuid> configureSite(Site site) throws AnsibleCommandException, L3vpnConfigException {
        // Find management info for Ansible
        final PeAugmentation sitePeAccess;
        List<SiteNetworkAccess> access = site.getSiteNetworkAccesses().getSiteNetworkAccess();
        final List<String> roleVars;
        final List<String> ansibleVars;
        final String deviceType;
        final String provider;
        final String networkOS;
        final Uuid siteCmdId;

        LOG.info("Configuring site: {}", site.toString());
        if (access == null || access.isEmpty()) {
            LOG.error("Site Network Access information is missing for site {}", site);
            throw new L3vpnConfigException("Missing site information");
        }
        // Assume only 1 access
        LOG.info("Site Network Access list is: {}", access.toString());
        sitePeAccess = access.get(0).augmentation(PeAugmentation.class);
        LOG.info("Site access is: " + sitePeAccess.toString());
        // Find role vars VPN configuration
        // TODO(trozet): search for these values once we know what we need, for now hardcode
        roleVars = Arrays.asList("dummy=value", "dummy2=value2");

        LOG.info("Role vars are: {}", roleVars);
        Ipv4Address peMgmtIp = sitePeAccess.getPeMgmtIp();
        if (peMgmtIp == null) {
            LOG.error("Missing site management IP address for site: {}", site);
            throw new L3vpnConfigException("Site management IP address is missing");
        }
        deviceType = sitePeAccess.getDeviceType();
        if (deviceType == null) {
            LOG.error("Missing device type for site: {}", site);
            throw new L3vpnConfigException("Device type not specified for site");
        }
        if (deviceType.toLowerCase(Locale.ENGLISH).contains("cisco")) {
            provider = "ansible-network.cisco_ios";
            networkOS = "ios";
        } else {
            provider = "ansible-network.arista_eos";
            networkOS = "eos";
        }
        ansibleVars = Arrays.asList(
                "ansible_user=" + sitePeAccess.getUsername(),
                "ansible_ssh_pass=" + sitePeAccess.getPassword(),
                "ansible_connection=network_cli",
                "ansible_network_os=" + networkOS,
                "ansible_network_provider=" + provider
                );
        // Call Ansible Command
        LOG.info("Executing VPN site configuration for host {}", peMgmtIp.getValue());
        try {
            siteCmdId = ansibleCommandService.runAnsibleRole(peMgmtIp.getValue(), null, provider, roleVars,
                    ansibleVars);
            ansibleCommandService.initCommandStatus(siteCmdId);
        } catch (ManagedProcessException | IOException e) {
            LOG.error("Failed to configure site {}, error: {}", site, e);
            throw new AnsibleCommandException("Failed to configure site: " + site + "error: " + e);
        }
        return  executor.submit(() -> {
            while (!ansibleCommandService.ansibleCommandSucceeded(siteCmdId)) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    LOG.warn("Polling command {} was interrupted", siteCmdId);
                }
            }
            return siteCmdId;
        });
    }

}
