/*******************************************************************************
 * Copyright (c) 2012 - 2013 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.cloudfoundry.ide.eclipse.internal.server.ui;

import java.util.List;

import org.cloudfoundry.client.lib.domain.CloudEntity;
import org.cloudfoundry.client.lib.domain.CloudOrganization;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.cloudfoundry.ide.eclipse.internal.server.core.CloudFoundryServer;
import org.cloudfoundry.ide.eclipse.internal.server.core.spaces.CloudSpacesDescriptor;
import org.cloudfoundry.ide.eclipse.internal.server.ui.editor.CloudSpaceChangeHandler;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.wst.server.ui.wizard.IWizardHandle;

public class CloudSpacesSelectionPart {

	private static final String DEFAULT_DESCRIPTION = "Selected an organization and space";

	private TreeViewer orgsSpacesViewer;

	private CloudSpaceChangeHandler spaceChangeHandler;

	public CloudSpacesSelectionPart(CloudSpaceChangeHandler spaceChangeHandler, CloudFoundryServer cloudServer,
			WizardPage wizardPage) {
		this.spaceChangeHandler = spaceChangeHandler;

		String serverTypeId = cloudServer.getServer().getServerType().getId();

		wizardPage.setTitle("Organizations and Spaces");
		wizardPage.setDescription(DEFAULT_DESCRIPTION);
		ImageDescriptor banner = CloudFoundryImages.getWizardBanner(serverTypeId);
		if (banner != null) {
			wizardPage.setImageDescriptor(banner);
		}
	}

	public CloudSpacesSelectionPart(CloudSpaceChangeHandler spaceChangeHandler, CloudFoundryServer cloudServer,
			IWizardHandle wizardHandle) {
		this.spaceChangeHandler = spaceChangeHandler;

		String serverTypeId = cloudServer.getServer().getServerType().getId();

		wizardHandle.setTitle("Organizations and Spaces");
		wizardHandle.setDescription(DEFAULT_DESCRIPTION);
		ImageDescriptor banner = CloudFoundryImages.getWizardBanner(serverTypeId);
		if (banner != null) {
			wizardHandle.setImageDescriptor(banner);
		}
	}

	public Composite createComposite(Composite parent) {
		Composite tableArea = new Composite(parent, SWT.NONE);
		GridLayoutFactory.fillDefaults().numColumns(1).equalWidth(true).applyTo(tableArea);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(tableArea);

		Composite orgTableComposite = new Composite(tableArea, SWT.NONE);
		GridLayoutFactory.fillDefaults().numColumns(1).applyTo(orgTableComposite);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(orgTableComposite);

		Label orgLabel = new Label(orgTableComposite, SWT.NONE);
		GridDataFactory.fillDefaults().grab(false, false).applyTo(orgLabel);
		orgLabel.setText("Organizations and Spaces:");

		Tree orgTable = new Tree(orgTableComposite, SWT.BORDER | SWT.SINGLE);

		GridDataFactory.fillDefaults().grab(true, true).applyTo(orgTable);

		orgsSpacesViewer = new TreeViewer(orgTable);

		orgsSpacesViewer.setContentProvider(new TableContentProvider());
		orgsSpacesViewer.setLabelProvider(new SpacesLabelProvider());
		orgsSpacesViewer.setSorter(new SpacesSorter());

		orgsSpacesViewer.addSelectionChangedListener(new ISelectionChangedListener() {

			public void selectionChanged(SelectionChangedEvent event) {
				refresh();
			}
		});

		setInput();
		return tableArea;
	}

	protected void setInput() {
		if (spaceChangeHandler != null && orgsSpacesViewer != null) {
			List<CloudOrganization> orgInput = spaceChangeHandler.getCurrentSpacesDescriptor() != null ? spaceChangeHandler
					.getCurrentSpacesDescriptor().getOrgs() : null;
			if (orgInput != null && orgInput.size() > 0) {
				CloudOrganization[] organizationInput = orgInput.toArray(new CloudOrganization[orgInput.size()]);
				orgsSpacesViewer.setInput(organizationInput);

				// Expand all first, so that child elements can be selected
				orgsSpacesViewer.setExpandedElements(organizationInput);

				CloudSpace selectedSpace = spaceChangeHandler.getCurrentSpacesDescriptor().getDefaultCloudSpace();
				if (selectedSpace != null) {

					// First set the default cloud space as the selected space
					setSpaceSelection(selectedSpace);

					// Now set the cloud space in the tree
					Tree tree = orgsSpacesViewer.getTree();
					TreeItem[] orgItems = tree.getItems();
					if (orgItems != null) {
						TreeItem orgItem = null;

						// Find the tree item corresponding to the cloud space's
						// org
						for (TreeItem item : orgItems) {
							Object treeObj = item.getData();
							if (treeObj instanceof CloudOrganization
									&& ((CloudOrganization) treeObj).getName().equals(
											selectedSpace.getOrganization().getName())) {
								orgItem = item;
								break;

							}
						}

						if (orgItem != null) {
							TreeItem[] children = orgItem.getItems();
							if (children != null) {
								for (TreeItem childItem : children) {
									Object treeObj = childItem.getData();
									if (treeObj instanceof CloudSpace
											&& ((CloudSpace) treeObj).getName().equals(selectedSpace.getName())) {
										tree.select(childItem);
										break;
									}
								}
							}
						}
					}
				}
			}
		}
	}

	protected void setSpaceSelection(CloudSpace selectedSpace) {
		if (spaceChangeHandler != null) {
			spaceChangeHandler.setSelectedSpace(selectedSpace);
		}
	}

	protected void refresh() {
		if (orgsSpacesViewer != null) {

			Tree tree = orgsSpacesViewer.getTree();
			TreeItem[] selectedItems = tree.getSelection();
			if (selectedItems != null && selectedItems.length > 0) {
				// It's a single selection tree, so only get the first selection
				Object selectedObj = selectedItems[0].getData();
				if (selectedObj instanceof CloudSpace) {
					setSpaceSelection((CloudSpace) selectedObj);
				}
				else if (selectedObj instanceof CloudOrganization) {
					setSpaceSelection(null);
				}
			}
		}
	}

	static class SpacesSorter extends ViewerSorter {

		public SpacesSorter() {

		}

		public int compare(Viewer viewer, Object e1, Object e2) {
			if (e1 instanceof CloudEntity && e1 instanceof CloudEntity) {
				String name1 = ((CloudEntity) e1).getName();
				String name2 = ((CloudEntity) e2).getName();
				return name1.compareTo(name2);
			}

			return super.compare(viewer, e1, e2);
		}

	}

	class TableContentProvider implements ITreeContentProvider {
		private Object[] elements;

		public TableContentProvider() {
		}

		public void dispose() {
		}

		public Object[] getChildren(Object parentElement) {
			if (parentElement instanceof CloudOrganization && spaceChangeHandler != null) {
				CloudSpacesDescriptor spaceDescriptor = spaceChangeHandler.getCurrentSpacesDescriptor();
				if (spaceDescriptor != null) {
					List<CloudSpace> spaces = spaceDescriptor.getOrgSpaces(((CloudOrganization) parentElement)
							.getName());
					if (spaces != null) {
						return spaces.toArray(new CloudSpace[spaces.size()]);
					}
				}
			}
			return null;
		}

		public Object[] getElements(Object inputElement) {
			return elements;
		}

		public Object getParent(Object element) {
			return null;
		}

		public boolean hasChildren(Object element) {
			return false;
		}

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			if (newInput instanceof Object[]) {
				elements = (Object[]) newInput;
			}
		}
	}

	static class SpacesLabelProvider extends LabelProvider {

		public SpacesLabelProvider() {

		}

		public String getText(Object element) {
			if (element instanceof CloudEntity) {
				CloudEntity cloudEntity = (CloudEntity) element;
				return cloudEntity.getName();
			}
			return super.getText(element);
		}

	}

}
