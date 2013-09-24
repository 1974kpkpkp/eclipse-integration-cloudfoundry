/*******************************************************************************
 * Copyright (c) 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.ui.wizards;

import java.util.List;

import org.cloudfoundry.client.lib.domain.CloudService;
import org.cloudfoundry.client.lib.domain.Staging;
import org.cloudfoundry.ide.eclipse.internal.server.core.ApplicationAction;
import org.cloudfoundry.ide.eclipse.internal.server.core.client.ApplicationDeploymentInfo;

/**
 * 
 * Descriptor that contains all the necessary information to push an application
 * to a Cloud Foundry server, such as the application's name, URL, framework,
 * and runtime
 * <p/>
 * This descriptor is shared by all the pages in the application deployment
 * wizard. Some values are required, and must always be set in order to push the
 * application to the server
 * 
 */
public class ApplicationWizardDescriptor {

	private Staging staging;

	private ApplicationDeploymentInfo deploymentInfo;

	private ApplicationAction deploymentMode;

	private List<CloudService> createdCloudServices;

	private List<String> selectedServicesForBinding;

	public ApplicationWizardDescriptor() {

	}

	/**
	 * Required value. Must not be null
	 * @return non-null application framework
	 */
	public Staging getStaging() {
		return staging;
	}

	public void setStartCommand(String startCommand) {
		String buildpack = staging != null ? staging.getBuildpackUrl() : null;
		staging = new Staging(startCommand, buildpack);
	}

	public void setBuildpack(String buildpack) {
		String existingStartCommand = staging != null ? staging.getCommand() : null;
		staging = new Staging(existingStartCommand, buildpack);
	}

	/**
	 * Required value. Must not be null
	 * @return non-null application deployment info.
	 */
	public ApplicationDeploymentInfo getDeploymentInfo() {
		return deploymentInfo;
	}

	public void setDeploymentInfo(ApplicationDeploymentInfo deploymentInfo) {
		this.deploymentInfo = deploymentInfo;
	}

	/**
	 * Optional value. Indicates whether an application should be started
	 * automatically after being pushed. If null, the application will be pushed
	 * to the Cloud Foundry server, but not started automatically.
	 * @return Deployment mode if the application needs to be started after
	 * being pushed, or null if app should not be started
	 */
	public ApplicationAction getStartDeploymentMode() {
		return deploymentMode;
	}

	public void setStartDeploymentMode(ApplicationAction deploymentMode) {
		this.deploymentMode = deploymentMode;
	}

	/**
	 * Optional value. If a user does not create services in the Application
	 * wizard, return null or an empty list.
	 * @return Optional list of created services, or null/empty list if no
	 * services are to be created
	 */
	public List<CloudService> getCreatedCloudServices() {
		return createdCloudServices;
	}

	public void setCreatedCloudServices(List<CloudService> createdCloudServices) {
		this.createdCloudServices = createdCloudServices;
	}

	/**
	 * Optional value. If no services are bound to the application, return null
	 * or empty list.
	 * @return Optional list of bound services, or null/empty list if no
	 * services should be bound to the application
	 */
	public List<String> getSelectedServicesForBinding() {
		return selectedServicesForBinding;
	}

	public void setSelectedServicesForBinding(List<String> selectedServicesForBinding) {
		this.selectedServicesForBinding = selectedServicesForBinding;
	}

}
