/*******************************************************************************
 * Copyright (c) 2013, 2014 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.core.client;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Behaviour operation that stops the refresh job prior to executing the
 * operation, and restarts it afterward.
 * 
 */
public abstract class BehaviourOperation implements ICloudFoundryOperation {

	protected final CloudFoundryServerBehaviour behaviour;

	public BehaviourOperation(CloudFoundryServerBehaviour behaviour) {
		this.behaviour = behaviour;
	}

	public void run(IProgressMonitor monitor) throws CoreException {
		performOperation(monitor);
		// Only trigger a refresh IF the operation succeeded.
		refresh(monitor);
	}



	/**
	 * Gets invoked after the operation completes. Does not get called if an
	 * operation failed.
	 * @param monitor
	 * @throws CoreException
	 */
	protected void refresh(IProgressMonitor monitor) throws CoreException {
		behaviour.getRefreshHandler().fireRefreshEvent(monitor);
	}

	protected abstract void performOperation(IProgressMonitor monitor) throws CoreException;

}