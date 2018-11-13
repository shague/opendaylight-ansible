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
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.Status.DeleteInProgress;
import static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.Status.InProgress;

import ch.vorburger.exec.ManagedProcessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.l3vpn.svc.fields.VpnServices;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.l3vpn.svc.fields.sites.Site;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.l3vpn.svc.fields.sites.site.site.network.accesses.SiteNetworkAccess;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.l3vpn.svc.rev170502.l3vpn.svc.fields.vpn.services.VpnService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.l3vpn.svc.aug.rev170502.PeAugmentation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.l3vpn.svc.aug.rev170502.VrfAugmentation;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.l3vpn.svc.aug.rev170502.VrfName;
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
    private Map<String, String> routeTargets = new HashMap<>();

    private enum State {
        PRESENT,
        ABSENT;

        @Override
        public String toString() {
            return super.toString().toLowerCase(Locale.ENGLISH);
        }
    }

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
                    Status status = configureVpn(State.PRESENT);
                    if (status != IetfL3vpnSvcUtils.getStatus(tx)) {
                        IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, status);
                    }
                    LOG.info("VPN is being created");
                } else if (IetfL3vpnSvcUtils.getStatus(tx) == DeleteInProgress) {
                    LOG.info("Deleting VPN");
                    Status status = configureVpn(State.ABSENT);
                    if (status != IetfL3vpnSvcUtils.getStatus(tx)) {
                        IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, status);
                    }
                    LOG.info("VPN is being deleted");
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
        txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx -> {
            try {
                if (IetfL3vpnSvcUtils.getStatus(tx) == InProgress) {
                    LOG.info("Updating VPN");
                    Status status = configureVpn(State.PRESENT);
                    if (status != IetfL3vpnSvcUtils.getStatus(tx)) {
                        IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, status);
                    }
                    LOG.info("VPN is being updated");
                } else if (IetfL3vpnSvcUtils.getStatus(tx) == DeleteInProgress) {
                    LOG.info("Deleting VPN");
                    Status status = configureVpn(State.ABSENT);
                    if (status != IetfL3vpnSvcUtils.getStatus(tx)) {
                        IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, status);
                    }
                    LOG.info("VPN is being deleted");
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

    private Status configureVpn(State state) throws AnsibleCommandException, L3vpnConfigException {
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

        // Find VRF name
        VpnServices vpnServices = l3vpnSvc.getVpnServices();
        if (vpnServices == null) {
            LOG.error("L3 VPN services config is missing");
            throw new L3vpnConfigException("L3 VPN services config is missing");
        }
        List<VpnService> vpnServiceList = vpnServices.getVpnService();
        if (vpnServiceList == null || vpnServiceList.isEmpty()) {
            LOG.error("L3 VPN missing vpn service config");
            throw new L3vpnConfigException("L3 VPN missing vpn service config");
        }
        // Assume one vpn service specified for demo
        VrfAugmentation vrfAug = vpnServiceList.get(0).augmentation(VrfAugmentation.class);

        List<Site> siteList = sites.getSite();
        if (siteList == null || siteList.isEmpty()) {
            LOG.error("L3 VPN config missing sites configuration");
            throw new L3vpnConfigException("L3 VPN config missing site configuration");
        }

        determineRouteTargets(vrfAug);

        for (Site site: siteList) {
            if (site == null) {
                LOG.warn("Null site found in L3 VPN config...ignoring");
                continue;
            }
            LOG.info("Examining site {}", site.toString());
            // TODO(trozet): Add method to check here if this site has a vpn attachment to the requested vpnservice
            cmdFutures.add(configureSite(site, vrfAug, state));
        }

        l3vpnCommands = Futures.successfulAsList(cmdFutures);
        l3vpnCommands.addListener(() -> {
            try {
                if (l3vpnCommands.get().contains(null)) {
                    txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx ->
                            IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, Status.Failed)
                    );
                } else {
                    txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx ->
                            IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, Status.Complete)
                    );
                }
            } catch (InterruptedException | ExecutionException e) {
                txRunner.callWithNewReadWriteTransactionAndSubmit(OPERATIONAL, tx ->
                    IetfL3vpnSvcUtils.putStatusL3VPN(tx, COMMIT_VERSION, Status.Failed)
                );
            }
        }, executor);

        if (state == State.ABSENT) {
            return Status.DeleteInProgress;
        } else {
            return Status.InProgress;
        }
    }

    private ListenableFuture<Uuid> configureSite(Site site, VrfAugmentation vrfAug, State state) throws
            AnsibleCommandException, L3vpnConfigException {
        // Find management info for Ansible
        final PeAugmentation sitePeAccess;
        List<SiteNetworkAccess> access = site.getSiteNetworkAccesses().getSiteNetworkAccess();
        final List<String> roleVars;
        final List<String> ansibleVars;
        final String deviceType;
        final String provider;
        final String networkOS;
        final Uuid siteCmdId;
        final String role = "ansible-network.l3vpn";

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
        ObjectMapper objectMapper = new ObjectMapper();
        String roleSiteVar;
        try {
            roleSiteVar = objectMapper.writeValueAsString(Arrays.asList(new L3vpnSiteConfig(access.get(0))));
        } catch (JsonProcessingException e) {
            throw new L3vpnConfigException("Unable to translate L3 Site Config into Ansible Config: " + e);
        }
        Long peAs = sitePeAccess.getPeBgpAs();
        if (peAs == null) {
            throw new L3vpnConfigException("Must specify Autonomous System value in pe-bgp-as");
        }
        // Find RD
        String rd;
        try {
            rd = vrfAug.getRouteDistinguisher().getAs().toString() + ":" + vrfAug.getRouteDistinguisher().getAsIndex()
                    .toString();
        } catch (NullPointerException e) {
            rd = randomRdRt();
        }
        VrfName vrfName = vrfAug.getVrfName();
        if (vrfName == null) {
            throw new L3vpnConfigException("vrf-name must be provided as part of vpn-service configuration");
        }
        // Find RT import
        String importRt;
        try {
            importRt = vrfAug.getImportRouteTargets().getRouteTarget().getAs().toString() + ":"
                    + vrfAug.getImportRouteTargets().getRouteTarget().getAsIndex().toString();
        } catch (NullPointerException e) {
            importRt = routeTargets.get(vrfName.getValue());
        }

        roleVars = Arrays.asList(
                "l3vpn_state=" + state.toString(),
                "l3vpn_name=" + vrfName.getValue(),
                "l3vpn_rd=" + rd,
                "l3vpn_rt_import=" + importRt,
                "l3vpn_rt_export=" + routeTargets.get(vrfName.getValue()),
                "l3vpn_bgp_as=" + peAs.toString(),
                "l3vpn_sites=" + roleSiteVar
                );

        LOG.info("Role vars are: {}", roleVars);
        Ipv4Address peMgmtIp = sitePeAccess.getPeMgmtIp();
        if (peMgmtIp == null) {
            LOG.error("Missing site management IP address for site: {}", site);
            throw new L3vpnConfigException("Site management IP address is missing");
        }
        String connectionType = "network_cli";
        deviceType = sitePeAccess.getDeviceType();
        if (deviceType == null) {
            LOG.error("Missing device type for site: {}", site);
            throw new L3vpnConfigException("Device type not specified for site");
        }
        if (deviceType.toLowerCase(Locale.ENGLISH).contains("cisco")) {
            provider = "ansible-network.cisco_ios";
            networkOS = "ios";
        } else if (deviceType.toLowerCase(Locale.ENGLISH).contains("arista")) {
            provider = "ansible-network.arista_eos";
            networkOS = "eos";
        } else if (deviceType.toLowerCase(Locale.ENGLISH).contains("juniper")) {
<<<<<<< HEAD
            provider = "ansible-network.juniper_junos";
            networkOS = "junos";
            connectionType = "netconf";
=======
            provider = "juniper.junos";
            networkOS = "junos";
>>>>>>> aa4b1533c74ddb6a0789380f9eab9eb717c3aef4
        } else {
            provider = "";
            networkOS = "";
        }
        ansibleVars = Arrays.asList(
                "ansible_user=" + sitePeAccess.getUsername(),
                "ansible_ssh_pass=" + sitePeAccess.getPassword(),
                "ansible_connection=" + connectionType,
                "ansible_network_os=" + networkOS,
                "ansible_network_provider=" + provider,
                "inventory_hostname=" + access.get(0).getSiteNetworkAccessId().getValue(),
                "inventory_hostname_short=" + access.get(0).getSiteNetworkAccessId().getValue()
                );
        // Call Ansible Command
        LOG.info("Executing VPN site configuration for host {}", peMgmtIp.getValue());
        try {
            siteCmdId = ansibleCommandService.runAnsibleRole(peMgmtIp.getValue(), null, role, roleVars,
                    ansibleVars);
            ansibleCommandService.initCommandStatus(siteCmdId);
        } catch (ManagedProcessException | IOException e) {
            LOG.error("Failed to configure site {}, error: {}", site, e);
            throw new AnsibleCommandException("Failed to configure site: " + site + "error: " + e);
        }
        return  executor.submit(() -> {
            while (!ansibleCommandService.isAnsibleComplete(siteCmdId)) {}
            if (ansibleCommandService.ansibleCommandSucceeded(siteCmdId)) {
                return siteCmdId;
            } else {
                return null;
            }
        });
    }

    private String randomRdRt() {
        Random rand = new Random();
        return String.valueOf(rand.nextInt(100) + 1) + ":" + String.valueOf(rand.nextInt(100) + 1);
    }

    private void determineRouteTargets(VrfAugmentation vrfAug) {
        String rt;
        try {
            rt = vrfAug.getExportRouteTargets().getRouteTarget().getAs().toString() + ":" + vrfAug
                    .getExportRouteTargets().getRouteTarget().getAsIndex().toString();
        } catch (NullPointerException e) {
            rt = randomRdRt();
        }
        routeTargets.put(vrfAug.getVrfName().getValue(), rt);
    }
}
