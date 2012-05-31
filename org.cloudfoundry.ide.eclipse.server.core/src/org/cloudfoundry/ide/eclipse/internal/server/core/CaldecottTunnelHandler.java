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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.caldecott.TunnelException;
import org.cloudfoundry.caldecott.client.HttpTunnelFactory;
import org.cloudfoundry.caldecott.client.TunnelHelper;
import org.cloudfoundry.caldecott.client.TunnelServer;
import org.cloudfoundry.client.lib.CloudApplication;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.CloudService;
import org.cloudfoundry.client.lib.DeploymentInfo;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryServerBehaviour.Request;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.wst.server.core.IModule;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Primary handler for all Caldecott operations, like starting and stopping a
 * tunnel.
 * <p/>
 * If a Caldecott app is not yet deployed, this handler will automatically
 * deploy and start it.
 * <p/>
 * 
 * When creating a tunnel for a service that hasn't been bound, this handler
 * will automatically bind the service first to the Caldecott application before
 * attempting to create a tunnel.
 * 
 */
public class CaldecottTunnelHandler {

	public static final String LOCAL_HOST = "127.0.0.1";

	private final CloudFoundryServer cloudServer;

	public static final int BASE_PORT = 10000;

	public CaldecottTunnelHandler(CloudFoundryServer cloudServer) {
		this.cloudServer = cloudServer;
	}

	public boolean bindServiceToCaldecottApp(CloudApplication caldecottApp, String serviceName, IProgressMonitor monitor)
			throws CoreException {

		List<String> updateCaldecottServices = new ArrayList<String>();
		List<String> existingServices = caldecottApp.getServices();
		if (existingServices != null) {
			updateCaldecottServices.addAll(existingServices);
		}

		IModule caldecottModule = getCaldecottModule(monitor);

		if (!updateCaldecottServices.contains(serviceName)) {
			updateCaldecottServices.add(serviceName);
			CloudFoundryServerBehaviour behaviour = cloudServer.getBehaviour();
			behaviour.stopModule(new IModule[] { caldecottModule }, monitor);
			behaviour.updateServices(TunnelHelper.getTunnelAppName(), updateCaldecottServices, monitor);
			behaviour.startModule(new IModule[] { caldecottModule }, monitor);

			setDeploymentServices(serviceName, monitor);

			return caldecottApp.getServices().contains(serviceName);
		}
		else {
			return true;
		}

	}

	public static boolean isCaldecottApp(String appName) {
		return TunnelHelper.getTunnelAppName().equals(appName);
	}

	public synchronized CaldecottTunnelDescriptor startCaldecottTunnel(final String serviceName,
			final IProgressMonitor monitor) throws CoreException {

		final List<CaldecottTunnelDescriptor> tunnel = new ArrayList<CaldecottTunnelDescriptor>(1);

		cloudServer.getBehaviour().new Request<CaldecottTunnelDescriptor>() {

			@Override
			protected CaldecottTunnelDescriptor doRun(final CloudFoundryClient client, SubMonitor progress)
					throws CoreException {
				final CloudApplication caldecottApp = getCaldecottApp(progress);
				if (caldecottApp == null) {
					return null;
				}

				progress.setTaskName("Binding " + serviceName + " to Caldecott.");

				bindServiceToCaldecottApp(caldecottApp, serviceName, progress);

				// The application must be started before creating a tunnel
				int ticks = 1;
				long sleep = 5000;

				progress.setTaskName("Starting Caldecott application.");

				new WaitWithProgressJob<Boolean>(ticks, sleep) {

					@Override
					protected Boolean runInWait(IProgressMonitor monitor) {

						if (!caldecottApp.getState().equals(CloudApplication.AppState.STARTED)) {
							client.startApplication(caldecottApp.getName());
							// wait to check again the state of the app
							return null;
						}
						return true;
					}

				}.run(monitor);

				// First get an unused port, even if there may be an
				// existing tunnel, as deleting an existing tunnel
				// right away
				// may not necessarily free the port immediately on
				// the server side.
				int unusedPort = CloudFoundryPlugin.getCaldecottTunnelCache().getUnusedPort();

				CaldecottTunnelDescriptor oldDescriptor = CloudFoundryPlugin.getCaldecottTunnelCache().getDescriptor(
						cloudServer, serviceName);

				if (oldDescriptor != null) {
					try {
						stopCaldecottTunnel(serviceName);
					}
					catch (CoreException e) {
						CloudFoundryPlugin
								.logError(NLS
										.bind("Failed to stop existing tunnel for service {0} on port {1}. Attempting to create a new tunnel on a different port {2}.",
												new Object[] { serviceName, oldDescriptor.tunnelPort(), unusedPort }));
					}
				}

				InetSocketAddress local = new InetSocketAddress(LOCAL_HOST, unusedPort);

				String url = TunnelHelper.getTunnelUri(client);

				Map<String, String> info = getTunnelInfo(client, serviceName, progress);

				if (info == null) {
					CloudFoundryPlugin.logError(NLS.bind("Failed to obtain tunnel information for {0} on port {1}.",
							new Object[] { serviceName, unusedPort }));

					return null;
				}

				String host = info.get("hostname");
				int port = Integer.valueOf(info.get("port"));
				String auth = TunnelHelper.getTunnelAuth(client);
				String serviceUserName = info.get("username");
				String servicePassword = info.get("password");
				String dataBase = getServiceVendor(serviceName, progress);

				String name = info.get("vhost");
				if (name == null) {
					name = info.get("db") != null ? info.get("db") : info.get("name");
				}

				TunnelServer tunnelServer = new TunnelServer(local, new HttpTunnelFactory(url, host, port, auth),
						getTunnelServerThreadExecutor());

				progress.setTaskName("Starting tunnel for " + serviceName);

				tunnelServer.start();

				// Delete the old tunnel only after a new one has been created,
				// as to allow the port checker to assign a unused port
				if (oldDescriptor != null) {
					CloudFoundryPlugin.getCaldecottTunnelCache().removeDescriptor(cloudServer, serviceName);
				}

				CaldecottTunnelDescriptor descriptor = new CaldecottTunnelDescriptor(serviceUserName, servicePassword,
						name, serviceName, dataBase, tunnelServer, unusedPort);

				CloudFoundryPlugin.getCaldecottTunnelCache().addDescriptor(cloudServer, descriptor);
				tunnel.add(descriptor);

				CloudFoundryCallback callBack = CloudFoundryPlugin.getCallback();
				List<CaldecottTunnelDescriptor> descriptors = new ArrayList<CaldecottTunnelDescriptor>();
				descriptors.add(descriptor);

				// Update any UI that needs to be notified that a tunnel was
				// created
				callBack.displayCaldecottTunnelConnections(cloudServer, descriptors);

				return descriptor;
			}

		}.run(monitor);

		return tunnel.size() > 0 ? tunnel.get(0) : null;
	}

	/**
	 * Sets the new service in the existing deployment info for an existing
	 * Caldecott ApplicationModule, if and only if the application module
	 * already has a deployment info and does not yet contain the service.
	 * Returns true if the latter iff condition is met, false any other case.
	 * @param serviceName
	 * @param monitor
	 * @return
	 */
	protected boolean setDeploymentServices(String serviceName, IProgressMonitor monitor) {

		boolean serviceChanges = false;

		IModule module = getCaldecottModule(monitor);

		if (module instanceof ApplicationModule) {
			ApplicationModule appModule = (ApplicationModule) module;

			DeploymentInfo deploymentInfo = appModule.getLastDeploymentInfo();
			// Do NOT set a deployment info if one does not exist, as another
			// component of CF integration does it, only
			// add to the existing deployment info.
			if (deploymentInfo != null) {
				List<String> existingServices = deploymentInfo.getServices();
				List<String> updatedServices = new ArrayList<String>();
				if (existingServices != null) {
					updatedServices.addAll(existingServices);
				}

				if (!updatedServices.contains(serviceName)) {
					updatedServices.add(serviceName);
					deploymentInfo.setServices(updatedServices);
					serviceChanges = true;
				}
			}
		}

		return serviceChanges;
	}

	protected TaskExecutor getTunnelServerThreadExecutor() {
		int defaultPoolSize = 20;
		ThreadPoolTaskExecutor te = new ThreadPoolTaskExecutor();
		te.setCorePoolSize(defaultPoolSize);
		te.setMaxPoolSize(defaultPoolSize * 2);
		te.setQueueCapacity(100);
		return te;
	}

	protected String getServiceVendor(String serviceName, IProgressMonitor monitor) throws CoreException {
		List<CloudService> services = cloudServer.getBehaviour().getServices(monitor);
		if (services != null) {
			for (CloudService service : services) {
				if (serviceName.equals(service.getName())) {
					return service.getVendor();
				}
			}
		}
		return null;
	}

	public Map<String, String> getTunnelInfo(final CloudFoundryClient client, final String serviceName,
			IProgressMonitor monitor) {

		int ticks = 5;
		long sleepTime = 5000;
		monitor.setTaskName("Getting tunnel information for " + serviceName);
		Map<String, String> info = new WaitWithProgressJob<Map<String, String>>(ticks, sleepTime) {

			@Override
			protected Map<String, String> runInWait(IProgressMonitor monitor) {
				return TunnelHelper.getTunnelServiceInfo(client, serviceName);
			}
		}.run(monitor);

		if (info == null) {
			CloudFoundryPlugin.logError("Timeout trying to obtain tunnel information for: " + serviceName
					+ ". Please wait a few seconds before trying again.");
		}

		return info;
	}

	public synchronized CaldecottTunnelDescriptor stopAndDeleteCaldecottTunnel(String serviceName,
			IProgressMonitor monitor) throws CoreException {

		CaldecottTunnelDescriptor tunnelDescriptor = stopCaldecottTunnel(serviceName);
		if (tunnelDescriptor != null) {
			CloudFoundryPlugin.getCaldecottTunnelCache().removeDescriptor(cloudServer,
					tunnelDescriptor.getServiceName());
		}
		return tunnelDescriptor;

	}

	/**
	 * Stops and deletes all Caldecott tunnels for the given server.
	 * @param monitor
	 * @throws CoreException
	 */
	public synchronized void stopAndDeleteAllTunnels(IProgressMonitor monitor) throws CoreException {
		Collection<CaldecottTunnelDescriptor> descriptors = CloudFoundryPlugin.getCaldecottTunnelCache()
				.getDescriptors(cloudServer);
		if (descriptors != null) {
			for (CaldecottTunnelDescriptor desc : descriptors) {
				stopAndDeleteCaldecottTunnel(desc.getServiceName(), monitor);
			}
		}
	}

	public synchronized CaldecottTunnelDescriptor stopCaldecottTunnel(String serviceName) throws CoreException {

		CaldecottTunnelDescriptor tunnelDescriptor = CloudFoundryPlugin.getCaldecottTunnelCache().getDescriptor(
				cloudServer, serviceName);
		if (tunnelDescriptor != null) {
			tunnelDescriptor.getTunnelServer().stop();
		}
		return tunnelDescriptor;
	}

	public synchronized boolean hasCaldecottTunnels() {
		Collection<CaldecottTunnelDescriptor> descriptors = CloudFoundryPlugin.getCaldecottTunnelCache()
				.getDescriptors(cloudServer);
		return descriptors != null && descriptors.size() > 0;
	}

	/**
	 * Returns an a tunnel descriptor if the service currently is connected via
	 * a tunnel, or null if no open tunnel exists
	 * @param serviceName
	 * @return
	 */
	public synchronized CaldecottTunnelDescriptor getCaldecottTunnel(String serviceName) {

		return CloudFoundryPlugin.getCaldecottTunnelCache().getDescriptor(cloudServer, serviceName);
	}

	public synchronized boolean hasCaldecottTunnel(String serviceName) {
		return getCaldecottTunnel(serviceName) != null;
	}

	/**
	 * Retrieves the actual Caldecott Cloud Application from the server. It does
	 * not rely on webtools IModule. May be a long running operation and
	 * experience network I/O timeouts. SHould only be called when other
	 * potential long running operations are performed.
	 * @param client
	 * @param monitor
	 * @return
	 */
	public synchronized CloudApplication getCaldecottApp(IProgressMonitor monitor) throws CoreException {

		Request<CloudApplication> request = cloudServer.getBehaviour().new Request<CloudApplication>() {

			@Override
			protected CloudApplication doRun(CloudFoundryClient client, SubMonitor progress) throws CoreException {
				CloudApplication caldecottApp = null;
				try {

					try {
						caldecottApp = client.getApplication(TunnelHelper.getTunnelAppName());
					}
					catch (Throwable e) {
						// Ignore all first attempt.
					}

					if (caldecottApp == null) {
						deployCaldecottApp(progress);
					}

					try {
						caldecottApp = client.getApplication(TunnelHelper.getTunnelAppName());
					}
					catch (Exception e) {
						throw new CoreException(CloudFoundryPlugin.getErrorStatus(e));
					}

					if (caldecottApp != null && !caldecottApp.getState().equals(CloudApplication.AppState.STARTED)) {
						client.startApplication(caldecottApp.getName());
					}

				}
				catch (CoreException ce) {
					CloudFoundryPlugin.logError("Failed to deploy Caldecott app. Unable to create service tunnel.", ce);
				}
				return caldecottApp;
			}
		};
		return request.run(monitor);
	}

	protected void deployCaldecottApp(IProgressMonitor monitor) throws CoreException {

		Request<Boolean> request = cloudServer.getBehaviour().new Request<Boolean>() {

			@Override
			protected Boolean doRun(CloudFoundryClient client, SubMonitor progress) throws CoreException {
				progress.setTaskName("Deploying Caldecott application.");
				Thread t = Thread.currentThread();
				ClassLoader oldLoader = t.getContextClassLoader();
				boolean deployed = false;
				try {
					t.setContextClassLoader(CloudFoundryServerBehaviour.class.getClassLoader());
					TunnelHelper.deployTunnelApp(client);
					deployed = true;
				}
				catch (TunnelException te) {
					CloudFoundryPlugin.logError(te);
				}
				finally {
					t.setContextClassLoader(oldLoader);
				}

				// refresh the list of modules to create a module for the
				// deployed Caldecott App
				if (deployed) {
					cloudServer.getBehaviour().refreshModules(progress);
				}
				return deployed;
			}
		};
		request.run(monitor);

	}

	public synchronized IModule getCaldecottModule(IProgressMonitor monitor) {

		CloudApplication caldecottApp = null;
		Throwable error = null;
		// Deploy the application first, if it isn't deployed yet
		try {
			caldecottApp = getCaldecottApp(monitor);
		}
		catch (CoreException e) {
			error = e;
		}

		if (caldecottApp == null) {
			if (error != null) {
				CloudFoundryPlugin.logError("Failed to deploy Caldecott app. Check server connection.", error);
			}
			else {
				CloudFoundryPlugin.logError("Failed to deploy Caldecott app. Check server connection.");
			}
			return null;
		}
		else {
			IModule appModule = null;
			Collection<ApplicationModule> modules = cloudServer.getApplications();
			if (modules != null) {
				String caldecottAppName = TunnelHelper.getTunnelAppName();
				for (ApplicationModule module : modules) {
					if (caldecottAppName.equals(module.getApplicationId())) {
						appModule = module;
					}
				}
			}
			return appModule;
		}
	}

	abstract class WaitWithProgressJob<T> {

		private final int ticks;

		private final long sleepTime;

		public WaitWithProgressJob(int ticks, long sleepTime) {
			this.ticks = ticks;
			this.sleepTime = sleepTime;
		}

		/**
		 * Return null if the run operation failed to obtain a run result. Null
		 * value will cause the wait operation to wait for a specified amount of
		 * time. Returning a non-null result will stop any further waiting.
		 * @return
		 */
		abstract protected T runInWait(IProgressMonitor monitor);

		public T run(IProgressMonitor monitor) {

			Throwable t = null;

			T result = null;
			int i = 0;
			while (i < ticks && !monitor.isCanceled()) {
				try {
					result = runInWait(monitor);
				}
				catch (Throwable th) {
					t = th;
				}
				if (result == null) {

					try {
						Thread.sleep(sleepTime);
					}
					catch (InterruptedException e) {
						// Ignore and proceed
					}
				}
				else {
					break;
				}
				i++;
			}

			if (result == null && t != null) {
				CloudFoundryPlugin.logError(t);
			}
			return result;
		}
	}

}
