/*******************************************************************************
 * Copyright (c) 2012 - 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.core.tunnel;

import java.util.List;

/**
 * 
 * Using getters and setters with no-argument constructors for JSON
 * serialisation
 * 
 */
public class ServerService {

	private List<ServiceCommand> commands;

	private ServiceInfo serviceInfo;

	public ServerService() {

	}

	public List<ServiceCommand> getCommands() {
		return commands;
	}

	public void setCommands(List<ServiceCommand> commands) {
		this.commands = commands;
	}

	public ServiceInfo getServiceInfo() {
		return serviceInfo;
	}

	public void setServiceInfo(ServiceInfo serviceInfo) {
		this.serviceInfo = serviceInfo;
	}

}
