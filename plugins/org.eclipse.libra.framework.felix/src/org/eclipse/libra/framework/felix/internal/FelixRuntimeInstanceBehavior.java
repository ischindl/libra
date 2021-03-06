/*******************************************************************************
 *   Copyright (c) 2010 Eteration A.S. and others.
 *   All rights reserved. This program and the accompanying materials
 *   are made available under the terms of the Eclipse Public License v1.0
 *   which accompanies this distribution, and is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 *  
 *   Contributors:
 *      Naci Dai and Murat Yener, Eteration A.S. - Initial API and implementation
 *******************************************************************************/
package org.eclipse.libra.framework.felix.internal;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.libra.framework.core.FrameworkInstanceConfiguration;
import org.eclipse.libra.framework.core.OSGIFrameworkInstanceBehaviorDelegate;
import org.eclipse.libra.framework.core.ProgressUtil;
import org.eclipse.libra.framework.core.Trace;
import org.eclipse.libra.framework.felix.FelixFrameworkInstance;
import org.eclipse.libra.framework.felix.IFelixFrameworkInstance;
import org.eclipse.libra.framework.felix.IFelixVersionHandler;
import org.eclipse.libra.framework.felix.Messages;
import org.eclipse.wst.server.core.IModule;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.model.ServerBehaviourDelegate;


public class FelixRuntimeInstanceBehavior extends
		OSGIFrameworkInstanceBehaviorDelegate {

	protected transient FelixConfigurationPublishHelper publishHelper = new FelixConfigurationPublishHelper(
			this);

	/**
	 * FelixRuntimeInstanceBehavior.
	 */
	public FelixRuntimeInstanceBehavior() {
		super();
	}
	
	public IFelixVersionHandler getFelixVersionHandler() {
		return getFelixRuntimeInstance().getFelixVersionHandler();
	}

	public FelixFrameworkInstance getFelixRuntimeInstance() {
		return (FelixFrameworkInstance) getServer().loadAdapter(FelixFrameworkInstance.class, null);
	}

	@Override
	public String getFrameworkClass() {
		return getFelixVersionHandler().getFrameworkClass();
	}

	@Override
	public String[] getFrameworkProgramArguments(boolean starting) {
		return getFelixVersionHandler().getFrameworkProgramArguments(
				getBaseDirectory(), getFrameworkInstance().isDebug(), starting);
	}

	@Override
	public String[] getExcludedFrameworkProgramArguments(boolean starting) {
		return getFelixVersionHandler().getExcludedFrameworkProgramArguments(
				getFrameworkInstance().isDebug(), starting);
	}

	@Override
	public String[] getFrameworkVMArguments() {
		IPath installPath = getServer().getRuntime().getLocation();
		// If installPath is relative, convert to canonical path and hope for
		// the best
		if (!installPath.isAbsolute()) {
			try {
				String installLoc = (new File(installPath.toOSString()))
						.getCanonicalPath();
				installPath = new Path(installLoc);
			} catch (IOException e) {
				// Ignore if there is a problem
			}
		}

		IPath deployPath = getBaseDirectory();
		// If deployPath is relative, convert to canonical path and hope for the
		// best
		if (!deployPath.isAbsolute()) {
			try {
				String deployLoc = (new File(deployPath.toOSString()))
						.getCanonicalPath();
				deployPath = new Path(deployLoc);
			} catch (IOException e) {
				// Ignore if there is a problem
			}
		}
		
		int jmxPort = ((IFelixFrameworkInstance)this.getFrameworkInstance()).getJMXPort();
		boolean jmxEnabled =  ((IFelixFrameworkInstance)this.getFrameworkInstance()).getJMXEnabled();
		return getFelixVersionHandler().getFrameworkVMArguments(installPath, null, deployPath, false, jmxEnabled, jmxPort);
	}
	
	@Override
	protected void publishServer(int kind, IProgressMonitor monitor)
			throws CoreException {
		if (getServer().getRuntime() == null)
			return;

		IPath confDir = getBaseDirectory();
		IStatus status = getFelixVersionHandler().prepareDeployDirectory(
				confDir);

		if (status != null && !status.isOK())
			throw new CoreException(status);

		IProgressMonitor monitor2 = ProgressUtil.getMonitorFor(monitor);
		monitor2.beginTask(Messages.publishServerTask, 600);

		// TODO OSAMI 1) Cleanup 2) Backup and Publish,

		// if (status != null && !status.isOK())
		// throw new CoreException(status);

		monitor2.done();

		setServerPublishState(IServer.PUBLISH_STATE_NONE);
	}

	@Override
	@SuppressWarnings("rawtypes")
	protected void publishModules(int kind, List modules, List deltaKind2,
			MultiStatus multi, IProgressMonitor monitor) {

		@SuppressWarnings("unchecked")
		final List<IModule[]> myModules = modules;

		IPath confDir = getBaseDirectory();

		FrameworkInstanceConfiguration felixConfiguration;
		
		try {
			felixConfiguration = getFelixRuntimeInstance().getFelixConfiguration();
			
			
			publishHelper.exportBundles(myModules, felixConfiguration, confDir);
			getFelixVersionHandler().prepareFrameworkConfigurationFile(confDir,
					publishHelper.getServerModules(myModules,"reference:file:", " " ),
					publishHelper.getTargetBundles(felixConfiguration,"reference:file:", " "));

		} catch (CoreException e) {
			Trace.trace(Trace.SEVERE, "Publishing failed", e);
		}
	}

	@Override
	protected void publishModule(int kind, int deltaKind, IModule[] moduleTree,
			IProgressMonitor monitor) throws CoreException {
		if (getServer().getServerState() != IServer.STATE_STOPPED) {
			if (deltaKind == ServerBehaviourDelegate.ADDED
					|| deltaKind == ServerBehaviourDelegate.REMOVED)
				setServerRestartState(true);
		}

		Properties p = loadModulePublishLocations();

		// TODO OSAMI PUBLISH

		setModulePublishState(moduleTree, IServer.PUBLISH_STATE_NONE);

		saveModulePublishLocations(p);
	}
}
