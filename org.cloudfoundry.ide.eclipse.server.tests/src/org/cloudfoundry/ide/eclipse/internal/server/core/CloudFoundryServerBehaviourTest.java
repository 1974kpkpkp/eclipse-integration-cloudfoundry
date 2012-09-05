/*******************************************************************************
 * Copyright (c) 2012 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.core;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.ide.eclipse.server.tests.util.CloudFoundryTestFixture;
import org.cloudfoundry.ide.eclipse.server.tests.util.CloudFoundryTestFixture.Harness;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IServer;
import org.junit.Assert;

/**
 * @author Steffen Pingel
 */
public class CloudFoundryServerBehaviourTest extends AbstractCloudFoundryTest {

	// TODO: Disable until proxies are supported in CF Java client 0.8.0
	// public void testCreateApplicationInvalidProxy() throws Exception {
	// // ensure valid session
	// getClient();
	//
	// // save proxy settings
	// IProxyService proxyService =
	// CloudFoundryPlugin.getDefault().getProxyService();
	// boolean systemProxiesEnabled = proxyService.isSystemProxiesEnabled();
	// boolean proxiesEnabled = proxyService.isProxiesEnabled();
	// IProxyData[] oldData = proxyService.getProxyData();
	//
	// try {
	// // set invalid proxy
	// proxyService.setSystemProxiesEnabled(false);
	// proxyService.setProxiesEnabled(true);
	// IProxyData[] data = proxyService.getProxyData();
	// data[0].setHost("invalid.proxy.test");
	// data[0].setPort(8080);
	// proxyService.setProxyData(data);
	//
	// try {
	// List<String> uris = new ArrayList<String>();
	// uris.add("test-proxy-upload.cloudfoundry.com");
	// CloudFoundryOperations client = getClient();
	// client.createApplication("test", DeploymentConstants.SPRING, 128, uris,
	// new ArrayList<String>());
	// fail("Expected ResourceAccessException due to invalid proxy configuration");
	// }
	// catch (Exception e) {
	// assertTrue("Expected ResourceAccessException, got: " + e, e instanceof
	// ResourceAccessException);
	// assertEquals("invalid.proxy.test", e.getCause().getMessage());
	// }
	// }
	// finally {
	// // restore proxy settings
	// proxyService.setSystemProxiesEnabled(systemProxiesEnabled);
	// proxyService.setProxiesEnabled(proxiesEnabled);
	// proxyService.setProxyData(oldData);
	// }
	// }

	public void testConnect() throws Exception {
		serverBehavior.connect(null);
		assertEquals(IServer.STATE_STARTED, server.getServerState());
		assertEquals(Collections.emptyList(), Arrays.asList(server.getModules()));
	}

	// XXX this test fails on the build server for an unknown reason
	public void testStartModule() throws Exception {
		harness.createProjectAndAddModule("dynamic-webapp");

		IModule[] modules = server.getModules();
		assertEquals("Expected dynamic-webapp module, got " + Arrays.toString(modules), 1, modules.length);
		int moduleState = server.getModulePublishState(modules);
		assertEquals(IServer.PUBLISH_STATE_UNKNOWN, moduleState);

		serverBehavior.deployOrStartModule(modules, true, null);
		moduleState = server.getModuleState(modules);
		assertEquals(IServer.STATE_STARTED, moduleState);
		moduleState = server.getModulePublishState(modules);
		// assertEquals(IServer.PUBLISH_STATE_UNKNOWN, moduleState);

		ApplicationModule appModule = cloudServer.getApplication(modules[0]);
		List<String> uris = appModule.getApplication().getUris();
		assertEquals(Collections.singletonList(harness.getUrl("dynamic-webapp")), uris);

		// wait 1s until app is actually started
		URI uri = new URI("http://" + harness.getUrl("dynamic-webapp") + "/index.html");
		assertEquals("Hello World.", getContent(uri));
	}

	// XXX this test fails on the build server for an unknown reason
	public void testStartModuleInvalidToken() throws Exception {
		harness.createProjectAndAddModule("dynamic-webapp");

		IModule[] modules = server.getModules();
		assertEquals("Expected dynamic-webapp module, got " + Arrays.toString(modules), 1, modules.length);
		int moduleState = server.getModulePublishState(modules);
		assertEquals(IServer.PUBLISH_STATE_UNKNOWN, moduleState);

		try {
			serverBehavior.resetClient();
			getClient("invalid");
		}
		catch (Exception e) {
			assertEquals("403 Error requesting access token.", e.getMessage());
		}

		try {
			serverBehavior.deployOrStartModule(modules, true, null);

		}
		catch (Throwable e) {
			assertEquals("Operation not permitted (403 Forbidden)", e.getMessage());
		}

		// Set the client again
		serverBehavior.resetClient();
		getClient();

		// moduleState = server.getModuleState(modules);
		// assertEquals(IServer.STATE_STARTED, moduleState);
		// moduleState = server.getModulePublishState(modules);
		// // assertEquals(IServer.PUBLISH_STATE_UNKNOWN, moduleState);
		//
		// ApplicationModule appModule = cloudServer.getApplication(modules[0]);
		// List<String> uris = appModule.getApplication().getUris();
		// assertEquals(Collections.singletonList(harness.getUrl("dynamic-webapp")),
		// uris);
		//
		// // wait 1s until app is actually started
		// URI uri = new URI("http://" + harness.getUrl("dynamic-webapp") +
		// "/index.html");
		// assertEquals("Hello World.", getContent(uri));
	}

	public void testStartModuleInvalidPassword() throws Exception {

		harness.createProjectAndAddModule("dynamic-webapp");

		IModule[] modules = server.getModules();
		assertEquals("Expected dynamic-webapp module, got " + Arrays.toString(modules), 1, modules.length);
		int moduleState = server.getModulePublishState(modules);
		assertEquals(IServer.PUBLISH_STATE_UNKNOWN, moduleState);

		try {
			serverBehavior.resetClient();
			CloudFoundryServer cloudServer = (CloudFoundryServer) server.loadAdapter(CloudFoundryServer.class, null);

			String userName = cloudServer.getUsername();
			CloudCredentials credentials = new CloudCredentials(userName, "invalid-password");
			getClient(credentials);

			serverBehavior.deployOrStartModule(modules, true, null);
			fail("Expected CoreException due to invalid password");
		}
		catch (Throwable e) {
			assertEquals("403 Error requesting access token.", e.getMessage());
		}

		// Set the client again
		serverBehavior.resetClient();
		getClient();

	}

	public void testDeleteModuleExternally() throws Exception {
		harness.createProjectAndAddModule("dynamic-webapp");

		IModule[] modules = server.getModules();
		serverBehavior.deployOrStartModule(modules, true, null);

		// wait 1s until app is actually started
		URI uri = new URI("http://" + harness.getUrl("dynamic-webapp") + "/index.html");
		assertEquals("Hello World.", getContent(uri));

		List<CloudApplication> applications = serverBehavior.getApplications(new NullProgressMonitor());
		boolean found = false;

		for (CloudApplication application : applications) {
			if (application.getName().equals("dynamic-webapp")) {
				found = true;
				break;
			}
		}
		assertTrue(found);

		CloudFoundryOperations client = CloudFoundryPlugin
				.getDefault()
				.getCloudFoundryClientFactory()
				.getCloudFoundryClient(CloudFoundryTestFixture.VCLOUDLABS.getUsername(),
						CloudFoundryTestFixture.VCLOUDLABS.getPassword(), CloudFoundryTestFixture.VCLOUDLABS.getUrl());
		client.login();
		client.deleteApplication("dynamic-webapp");

		serverBehavior.refreshModules(new NullProgressMonitor());
		applications = serverBehavior.getApplications(new NullProgressMonitor());
		found = false;

		for (CloudApplication application : applications) {
			if (application.getName().equals("dynamic-webapp")) {
				found = true;
				break;
			}
		}
		assertFalse(found);
	}

	public void testStartModuleWithDifferentId() throws Exception {
		harness = CloudFoundryTestFixture.current("dynamic-webapp-test").harness();
		server = harness.createServer();
		cloudServer = (CloudFoundryServer) server.loadAdapter(CloudFoundryServer.class, null);
		serverBehavior = (CloudFoundryServerBehaviour) server.loadAdapter(CloudFoundryServerBehaviour.class, null);

		harness.createProjectAndAddModule("dynamic-webapp");

		IModule[] modules = server.getModules();
		assertEquals("Expected dynamic-webapp module, got " + Arrays.toString(modules), 1, modules.length);
		int moduleState = server.getModulePublishState(modules);
		assertEquals(IServer.PUBLISH_STATE_UNKNOWN, moduleState);

		serverBehavior.deployOrStartModule(modules, true, null);
		moduleState = server.getModuleState(modules);
		assertEquals(IServer.STATE_STARTED, moduleState);
		moduleState = server.getModulePublishState(modules);
		// assertEquals(IServer.PUBLISH_STATE_UNKNOWN, moduleState);

		ApplicationModule appModule = cloudServer.getApplication(modules[0]);
		List<String> uris = appModule.getApplication().getUris();
		assertEquals(Collections.singletonList(harness.getUrl("dynamic-webapp")), uris);

		// wait 1s until app is actually started
		URI uri = new URI("http://" + harness.getUrl("dynamic-webapp") + "/index.html");
		assertEquals("Hello World.", getContent(uri));

		serverBehavior.refreshModules(new NullProgressMonitor());
		List<CloudApplication> applications = serverBehavior.getApplications(new NullProgressMonitor());
		boolean found = false;

		for (CloudApplication application : applications) {
			if (application.getName().equals("dynamic-webapp-test")) {
				found = true;
				break;
			}
		}
		assertTrue(found);
	}

	public void testStartModuleWithUsedUrl() throws Exception {
		getClient().deleteAllApplications();

		harness.createProjectAndAddModule("dynamic-webapp");

		IModule[] modules = server.getModules();
		serverBehavior.deployOrStartModule(modules, true, null);

		// wait 1s until app is actually started
		URI uri = new URI("http://" + harness.getUrl("dynamic-webapp") + "/index.html");
		assertEquals("Hello World.", getContent(uri));

		ApplicationModule module = cloudServer.getApplication(modules[0]);
		Assert.assertNull(module.getErrorMessage());

		harness = CloudFoundryTestFixture.current("dynamic-webapp-with-appclient-module",
				"dynamic-webapp.cloudfoundry.com").harness();
		server = harness.createServer();
		cloudServer = (CloudFoundryServer) server.loadAdapter(CloudFoundryServer.class, null);
		serverBehavior = (CloudFoundryServerBehaviour) server.loadAdapter(CloudFoundryServerBehaviour.class, null);

		harness.createProjectAndAddModule("dynamic-webapp-with-appclient-module");
		modules = server.getModules();

		// FIXME: once we verify what the proper behavior is, we should fail
		// appropriately
		// try {
		// serverBehavior.deployOrStartModule(modules, true, null);
		// Assert.fail("Expects CoreException due to duplicate URL");
		// }
		// catch (CoreException e) {
		// }
		//
		// module = cloudServer.getApplication(modules[0]);
		// Assert.assertNotNull(module.getErrorMessage());
		// try {
		// serverBehavior.getClient().deleteApplication("dynamic-webapp-with-appclient-module");
		// }
		// catch (Exception e) {
		//
		// }
	}

	// This case should never pass since the wizard should guard against
	// duplicate ID
	// public void testStartModuleWithDuplicatedId() throws Exception {
	// harness =
	// CloudFoundryTestFixture.current("dynamic-webapp-test").harness();
	// server = harness.createServer();
	// cloudServer = (CloudFoundryServer)
	// server.loadAdapter(CloudFoundryServer.class, null);
	// serverBehavior = (CloudFoundryServerBehaviour)
	// server.loadAdapter(CloudFoundryServerBehaviour.class, null);
	//
	// harness.createProjectAndAddModule("dynamic-webapp");
	//
	// IModule[] modules = server.getModules();
	// serverBehavior.deployOrStartModule(modules, true, null);
	//
	// // wait 1s until app is actually started
	// URI uri = new URI("http://" + harness.getUrl("dynamic-webapp") +
	// "/index.html");
	// assertEquals("Hello World.", getContent(uri));
	//
	// serverBehavior.refreshModules(new NullProgressMonitor());
	// List<CloudApplication> applications = serverBehavior.getApplications(new
	// NullProgressMonitor());
	// boolean found = false;
	//
	// for (CloudApplication application : applications) {
	// if (application.getName().equals("dynamic-webapp-test")) {
	// found = true;
	// }
	// }
	//
	// Assert.assertTrue(found);
	//
	// harness.createProjectAndAddModule("dynamic-webapp-with-appclient-module");
	//
	// modules = server.getModules();
	// serverBehavior.deployOrStartModule(modules, true, null);
	//
	// // wait 1s until app is actually started
	// uri = new URI("http://" +
	// harness.getUrl("dynamic-webapp-with-appclient-module") + "/index.html");
	// assertEquals("Hello World.", getContent(uri));
	//
	// serverBehavior.refreshModules(new NullProgressMonitor());
	// applications = serverBehavior.getApplications(new NullProgressMonitor());
	// found = false;
	//
	// for (CloudApplication application : applications) {
	// if (application.getName().equals("dynamic-webapp-test")) {
	// found = true;
	// }
	// }
	//
	// Assert.assertTrue(found);
	// }

	@Override
	protected Harness createHarness() {
		return CloudFoundryTestFixture.current().harness();
	}

}
