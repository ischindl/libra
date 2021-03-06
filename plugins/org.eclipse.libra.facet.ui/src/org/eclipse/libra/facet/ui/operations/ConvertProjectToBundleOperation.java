/*******************************************************************************
 * Copyright (c) 2010, 2011 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kaloyan Raev (SAP AG) - initial API and implementation
 *******************************************************************************/
package org.eclipse.libra.facet.ui.operations;

import static org.eclipse.libra.facet.OSGiBundleFacetUtils.OSGI_BUNDLE_FACET_42;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;


public class ConvertProjectToBundleOperation extends WorkspaceModifyOperation {
	
	protected IProject fProject;
	
	public ConvertProjectToBundleOperation(IProject project) {
		this.fProject = project;
	}

	@Override
	protected void execute(IProgressMonitor monitor) throws CoreException,
			InvocationTargetException, InterruptedException {
		IFacetedProject fproj = ProjectFacetsManager.create(fProject, true, monitor);
		fproj.installProjectFacet(OSGI_BUNDLE_FACET_42, null, monitor);
	}

}
