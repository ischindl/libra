/*******************************************************************************
 * Copyright (c) 2011 SAP AG
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     SAP AG - initial API and implementation
 *******************************************************************************/
package org.eclipse.libra.framework.editor.integration.admin.osgijmx;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.libra.framework.editor.core.IOSGiFrameworkAdmin;
import org.eclipse.libra.framework.editor.core.model.IBundle;
import org.eclipse.libra.framework.editor.integration.internal.IntegrationPlugin;
import org.eclipse.libra.framework.editor.integration.internal.admin.osgijmx.Bundle;
import org.eclipse.libra.framework.editor.integration.internal.admin.osgijmx.PackageExport;
import org.eclipse.libra.framework.editor.integration.internal.admin.osgijmx.PackageImport;
import org.eclipse.libra.framework.editor.integration.internal.admin.osgijmx.PackagesData;
import org.eclipse.libra.framework.editor.integration.internal.admin.osgijmx.ServiceReference;
import org.eclipse.libra.framework.editor.integration.internal.admin.osgijmx.ServicesData;
import org.osgi.framework.Constants;
import org.osgi.jmx.framework.BundleStateMBean;
import org.osgi.jmx.framework.FrameworkMBean;
import org.osgi.jmx.framework.PackageStateMBean;
import org.osgi.jmx.framework.ServiceStateMBean;

/**
 * @author Kaloyan Raev
 */
public abstract class AbstractOSGiJMXFrameworkAdmin implements IOSGiFrameworkAdmin {
	
	protected abstract String getHost() throws CoreException;
	
	protected abstract String getPort() throws CoreException;
	
	public Map<Long, IBundle> getBundles(IProgressMonitor monitor) throws CoreException {
		Map<Long, IBundle> map = new HashMap<Long, IBundle>();
		
		try {
			MBeanServerConnection connection = getMBeanServerConnection();
			BundleStateMBean bundleStateMBean = getBundleStateMBean(connection);
			TabularData bundlesData = bundleStateMBean.listBundles();
			PackageStateMBean packageStateMBean = getPackageStateMBean(connection);
			PackagesData packagesData = new PackagesData(packageStateMBean);
			ServiceStateMBean serviceStateMBean = getServiceStateMBean(connection);
			ServicesData servicesData = new ServicesData(serviceStateMBean);
			
			Set<?> keys = bundlesData.keySet();
			for (Object key : keys) {
				// TODO Weird cast & get?
				CompositeData bundleInfo = bundlesData.get(((Collection<?>) key).toArray());
				String id = bundleInfo.get(BundleStateMBean.IDENTIFIER).toString();
				String symbolicName = bundleInfo.get(BundleStateMBean.SYMBOLIC_NAME).toString();
				String version = bundleInfo.get(BundleStateMBean.VERSION).toString();
				String state = bundleInfo.get(BundleStateMBean.STATE).toString();
				String location = bundleInfo.get(BundleStateMBean.LOCATION).toString();
				Bundle bundle = new Bundle(id, symbolicName, version, state, location);
				
				TabularData headers = (TabularData) bundleInfo.get(BundleStateMBean.HEADERS);
				Set<?> headerKeys = headers.keySet();
				for (Object headerKey : headerKeys) {
					// TODO Weird cast & get?
					CompositeData headerCData = headers.get(((Collection<?>) headerKey).toArray()); 
					String hKey = (String) headerCData.get(BundleStateMBean.KEY);
					String hValue = (String) headerCData.get(BundleStateMBean.VALUE);
					bundle.addHeader(hKey, hValue);
				}
				
				String[] exportedPackages = (String[]) bundleInfo.get(BundleStateMBean.EXPORTED_PACKAGES);
				for (String epStr : exportedPackages) {
					int column = epStr.indexOf(';');
					String packageName = epStr.substring(0, column);
					String packageVersion = epStr.substring(column + 1, epStr.length());
					bundle.addPackageExport(new PackageExport(packageName, packageVersion));
				}
				
				String[] importedPackages = (String[]) bundleInfo.get(BundleStateMBean.IMPORTED_PACKAGES);
				for (String ipStr : importedPackages) {
					int column = ipStr.indexOf(';');
					String packageName = ipStr.substring(0, column);
					String packageVersion = ipStr.substring(column + 1, ipStr.length());
					String exportingBundleId = packagesData.getExportingBundleId(packageName, packageVersion).toString();
					bundle.addPackageImport(new PackageImport(packageName, packageVersion, exportingBundleId));
				}
				
				Long[] registeredServices = (Long[]) bundleInfo.get(BundleStateMBean.REGISTERED_SERVICES);
				for (Long regService : registeredServices) {
					ServicesData.ServiceInfo serviceInfo = servicesData.getService(regService);
					ServiceReference sr = new ServiceReference(ServiceReference.Type.REGISTERED, serviceInfo.getBundleId(), serviceInfo.getObjectClass());
					sr.addProperty(Constants.SERVICE_ID, serviceInfo.getServiceId().toString());
					for (Long usingBundleId : serviceInfo.getUsingBundles()) {
						sr.addUsingBundle(usingBundleId);
					}
					bundle.addRegisteredService(sr);
				}
				
				Long[] servicesInUse = (Long[]) bundleInfo.get(BundleStateMBean.SERVICES_IN_USE);
				for (Long serviceInUse : servicesInUse) {
					ServicesData.ServiceInfo serviceInfo = servicesData.getService(serviceInUse);
					ServiceReference sr = new ServiceReference(ServiceReference.Type.IN_USE, serviceInfo.getBundleId(), serviceInfo.getObjectClass());
					sr.addProperty(Constants.SERVICE_ID, serviceInfo.getServiceId().toString());
					for (Long usingBundleId : serviceInfo.getUsingBundles()) {
						sr.addUsingBundle(usingBundleId);
					}
					bundle.addUsingService(sr); 
				}
				
				map.put(Long.valueOf(id), bundle);
			}
		} catch (UndeclaredThrowableException e) {
			if (e.getCause() instanceof InstanceNotFoundException) {
				throw new CoreException(IntegrationPlugin.newErrorStatus("MBean not found: " + e.getCause().getMessage(), e.getCause()));
			}
			throw new CoreException(IntegrationPlugin.newErrorStatus(e));
		} catch (Exception e) {
			throw new CoreException(IntegrationPlugin.newErrorStatus(e));
		}
		
		return map;
	}

	public void startBundle(long bundleId) throws CoreException {
		try {
			MBeanServerConnection connection = getMBeanServerConnection();
			FrameworkMBean mbean = getFrameworkMBean(connection);
			mbean.startBundle(bundleId);
		} catch (Exception e) {
			throw new CoreException(IntegrationPlugin.newErrorStatus(e));
		}
	}

	public void stopBundle(long bundleId) throws CoreException {
		try {
			MBeanServerConnection connection = getMBeanServerConnection();
			FrameworkMBean mbean = getFrameworkMBean(connection);
			mbean.stopBundle(bundleId);
		} catch (Exception e) {
			throw new CoreException(IntegrationPlugin.newErrorStatus(e));
		}
	}

	public void refreshBundle(long bundleId) throws CoreException {
		try {
			MBeanServerConnection connection = getMBeanServerConnection();
			FrameworkMBean mbean = getFrameworkMBean(connection);
			mbean.refreshBundle(bundleId);
		} catch (Exception e) {
			throw new CoreException(IntegrationPlugin.newErrorStatus(e));
		}
	}

	public void updateBundle(long bundleId) throws CoreException {

		try {
			MBeanServerConnection connection = getMBeanServerConnection();
			FrameworkMBean mbean = getFrameworkMBean(connection);
			mbean.updateBundle(bundleId);
		} catch (Exception e) {
			throw new CoreException(IntegrationPlugin.newErrorStatus(e));
		}
	}
	
	protected MBeanServerConnection getMBeanServerConnection() throws IOException, CoreException {
		JMXServiceURL url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + getHost() + ":" + getPort() + "/jmxrmi"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		JMXConnector connector = JMXConnectorFactory.connect(url);
		return connector.getMBeanServerConnection();
	}
	
	protected BundleStateMBean getBundleStateMBean(MBeanServerConnection connection) throws MalformedObjectNameException {
		ObjectName objectName = new ObjectName("osgi.core:type=bundleState,version=1.5"); //$NON-NLS-1$
		return JMX.newMBeanProxy(connection, objectName, BundleStateMBean.class);
	}
	
	protected PackageStateMBean getPackageStateMBean(MBeanServerConnection connection) throws MalformedObjectNameException {
		ObjectName objectName = new ObjectName("osgi.core:type=packageState,version=1.5"); //$NON-NLS-1$
		return JMX.newMBeanProxy(connection, objectName, PackageStateMBean.class);
	}
	
	protected ServiceStateMBean getServiceStateMBean(MBeanServerConnection connection) throws MalformedObjectNameException {
		ObjectName objectName = new ObjectName("osgi.core:type=serviceState,version=1.5"); //$NON-NLS-1$
		return JMX.newMBeanProxy(connection, objectName, ServiceStateMBean.class);
	}
	
	protected FrameworkMBean getFrameworkMBean(MBeanServerConnection connection) throws MalformedObjectNameException {
		ObjectName objectName = new ObjectName("osgi.core:type=framework,version=1.5"); //$NON-NLS-1$
		return JMX.newMBeanProxy(connection, objectName, FrameworkMBean.class);
	}

}
