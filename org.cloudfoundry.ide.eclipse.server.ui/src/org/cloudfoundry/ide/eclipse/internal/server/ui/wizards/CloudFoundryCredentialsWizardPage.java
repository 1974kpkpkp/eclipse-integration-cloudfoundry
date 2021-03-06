/*******************************************************************************
 * Copyright (c) 2012, 2014 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.ui.wizards;

import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryServer;
import org.cloudfoundry.ide.eclipse.internal.server.ui.CloudServerSpaceDelegate;
import org.cloudfoundry.ide.eclipse.internal.server.ui.ServerWizardValidator;
import org.cloudfoundry.ide.eclipse.internal.server.ui.editor.CloudFoundryCredentialsPart;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

/**
 * Credentials wizard page used to prompt users for credentials in case an
 * EXISTING server instance can no longer connect with the existing credentials.
 * This wizard page is not used in the new server wizard when creating a new
 * server instance.
 * @author Christian Dupuis
 * @author Leo Dos Santos
 * @author Steffen Pingel
 * @author Terry Denney
 */
public class CloudFoundryCredentialsWizardPage extends WizardPage {

	private final CloudFoundryCredentialsPart credentialsPart;

	private CloudServerSpaceDelegate cloudServerSpaceDelegate;

	private WizardChangeListener wizardUpdateHandler;

	private ServerWizardValidator validator;

	protected CloudFoundryCredentialsWizardPage(CloudFoundryServer server) {
		super(server.getServer().getName() + " Credentials");
		cloudServerSpaceDelegate = new CloudServerSpaceDelegate(server);
		wizardUpdateHandler = new WizardPageChangeListener(this);
		validator = new ServerWizardValidator(server, cloudServerSpaceDelegate);
		credentialsPart = new CloudFoundryCredentialsPart(server, validator, wizardUpdateHandler, this);
	}

	public void createControl(Composite parent) {
		Control control = credentialsPart.createPart(parent);
		setControl(control);
	}

	@Override
	public boolean isPageComplete() {
		return validator.validate(false, getContainer()).getStatus().isOK();
	}

	public CloudServerSpaceDelegate getServerSpaceDelegate() {
		return cloudServerSpaceDelegate;
	}

	public boolean canFlipToNextPage() {
		// There should only be a next page for the spaces page if there is a
		// cloud space descriptor set
		return isPageComplete() && getNextPage() != null;
	}

}
