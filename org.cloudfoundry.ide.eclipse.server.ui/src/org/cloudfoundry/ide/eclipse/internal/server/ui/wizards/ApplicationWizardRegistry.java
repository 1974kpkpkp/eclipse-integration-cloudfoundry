/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.ui.wizards;

import java.util.HashMap;
import java.util.Map;

import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryPlugin;
import org.cloudfoundry.ide.eclipse.internal.server.core.application.ApplicationProvider;
import org.cloudfoundry.ide.eclipse.internal.server.core.application.ApplicationRegistry;
import org.cloudfoundry.ide.eclipse.internal.server.core.application.IApplicationDelegate;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;
import org.eclipse.wst.server.core.IModule;

public class ApplicationWizardRegistry {

	private static Map<String, ApplicationWizardProvider> wizardProviders;

	private static final String APPLICATION_DELEGATE_EXT_ELEMENT = "applicationWizardDelegate";

	public static String EXTENSION_POINT = "org.cloudfoundry.ide.eclipse.server.ui.applicationWizard";

	public static ApplicationWizardDelegate getWizardProvider(IModule module) {
		// See if there is a corresponding application delegate
		ApplicationProvider applicationProvider = ApplicationRegistry.getApplicationProvider(module);
		return getWizardDelegate(applicationProvider);

	}

	/**
	 * Gets the corresponding application wizard delegate that matches the
	 * provider ID specified by the given application provider, or null if it
	 * could not find.
	 * @param applicationProvider
	 * @return application wizard delegate that matches the provider ID, or
	 * null.
	 */
	protected static ApplicationWizardDelegate getWizardDelegate(ApplicationProvider applicationProvider) {
		if (applicationProvider == null) {
			return null;
		}
		if (wizardProviders == null) {
			load();
		}
		ApplicationWizardProvider wizardProvider = wizardProviders.get(applicationProvider.getProviderID());
		if (wizardProvider != null) {
			return wizardProvider.getDelegate(applicationProvider.getDelegate());
		}

		return null;
	}

	public static IApplicationWizardDelegate getDefaultJavaWebWizardDelegate() {
		ApplicationProvider javaWebProvider = ApplicationRegistry.getDefaultJavaWebApplicationProvider();
		return getWizardDelegate(javaWebProvider);
	}

	private static void load() {
		wizardProviders = new HashMap<String, ApplicationWizardProvider>();
		IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(EXTENSION_POINT);

		if (extensionPoint == null) {
			CloudFoundryPlugin.logError("Failed to load Cloud Foundry application wizard providers from: "
					+ EXTENSION_POINT);
		}
		else {
			for (IExtension extension : extensionPoint.getExtensions()) {
				for (IConfigurationElement config : extension.getConfigurationElements()) {

					if (APPLICATION_DELEGATE_EXT_ELEMENT.equals(config.getName())) {
						ApplicationWizardProvider wizardProvider = new ApplicationWizardProvider(config,
								EXTENSION_POINT);
						String providerID = wizardProvider.getProviderID();
						if (providerID == null) {
							CloudFoundryPlugin
									.logError("Failed to load application wizard provider from extension point: "
											+ EXTENSION_POINT + ". Missing provider ID.");
						}
						else {
							wizardProviders.put(providerID, wizardProvider);
						}
					}
				}
			}
		}
	}

}
