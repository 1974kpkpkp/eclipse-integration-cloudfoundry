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
package org.cloudfoundry.ide.eclipse.internal.server.core.client;

import org.cloudfoundry.client.lib.domain.CloudEntity;
import org.cloudfoundry.client.lib.domain.CloudService;
import org.springframework.util.Assert;

/**
 * Represents a cloud service that is either not yet created or exists and is
 * bound to an application, but does not contain full information for the
 * service, as bound services typically only contain the service name.
 * 
 */
public class LocalCloudService extends CloudService {

	public LocalCloudService(String name) {
		Assert.notNull(name);
		setMeta(CloudEntity.Meta.defaultMeta());
		setName(name);
	}

}
