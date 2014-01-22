/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.ui.console;

import org.eclipse.swt.SWT;

/**
 * 
 * Local std error content for the Eclipse console. Intention is to write
 * local content to the console using the
 * {@link #write(String, org.eclipse.core.runtime.IProgressMonitor)}
 * <p/>
 * To fetch std error content from a remote server (e.g. a std  log file), use
 * {@link FileConsoleContent} instead.
 */
public class AppStdErrorConsoleContent extends StdConsoleContent {

	public AppStdErrorConsoleContent() {
		super(SWT.COLOR_RED);
	}

	public IContentType getConsoleType() {
		return StdContentType.STD_ERROR;
	}

}
