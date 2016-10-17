/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.registry;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.config.ConfigConstants;
import com.dianping.pigeon.config.ConfigManager;
import com.dianping.pigeon.domain.HostInfo;
import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.monitor.MonitorLogger;
import com.dianping.pigeon.registry.config.DefaultRegistryConfigManager;
import com.dianping.pigeon.registry.config.RegistryConfigManager;
import com.dianping.pigeon.registry.exception.RegistryException;
import com.dianping.pigeon.registry.listener.RegistryEventListener;
import com.dianping.pigeon.registry.listener.ServerInfoListener;
import com.dianping.pigeon.registry.util.Constants;
import com.dianping.pigeon.registry.util.Utils;

public class RegistryManager {

	private static final Logger logger = LoggerLoader.getLogger(RegistryManager.class);

	private Properties props = new Properties();

	private static volatile boolean isInit = false;

	private static Throwable initializeException = null;

	private static RegistryManager instance = new RegistryManager();

	private static RegistryConfigManager registryConfigManager = new DefaultRegistryConfigManager();

	private static Registry registry = ExtensionLoader.getExtension(Registry.class);

	//key：服务url value：主机
	private static Map<String, Set<HostInfo>> referencedServiceAddresses = new ConcurrentHashMap<String, Set<HostInfo>>();

	//key:服务器地址 value:服务器信息（包括ip、端口、权重等）
	private static Map<String, HostInfo> referencedAddresses = new ConcurrentHashMap<String, HostInfo>();

	private static ConfigManager configManager = ExtensionLoader.getExtension(ConfigManager.class);

	private static ConcurrentHashMap<String, Object> registeredServices = new ConcurrentHashMap<String, Object>();

	MonitorLogger monitorLogger = ExtensionLoader.getExtension(MonitorLogger.class);

	private RegistryManager() {
	}

	public static boolean isInitialized() {
		return isInit;
	}

	public static Throwable getInitializeException() {
		return initializeException;
	}

	//如果registryConfigManager.getRegistryConfig()中的pigeon.registry.type不为local，使用registry初始化
	//使用registryConfigManager.getRegistryConfig()中的值初始化pros属性
	//并添加new InnerServerInfoListener()(这是一个ServerInfoListener监听器)
	public static RegistryManager getInstance() {
		if (!isInit) {
			synchronized (RegistryManager.class) {
				if (!isInit) {
					instance.init(registryConfigManager.getRegistryConfig());
					initializeException = null;
					RegistryEventListener.addListener(new InnerServerInfoListener());
					isInit = true;
				}
			}
		}
		return instance;
	}

	private void init(Properties properties) {
		instance.setProperties(properties);
		String registryType = properties.getProperty(Constants.KEY_REGISTRY_TYPE);
		if (!Constants.REGISTRY_TYPE_LOCAL.equalsIgnoreCase(registryType)) {
			if (registry != null) {
				try {
					registry.init(properties);
				} catch (Throwable t) {
					initializeException = t;
					throw new RuntimeException(t);
				}
			}
		} else {
		}
	}

	public Registry getRegistry() {
		return registry;
	}

	public void setProperty(String key, String value) {
		// 如果是dev环境，可以把当前配置加载进去
		props.put(key, value);
	}

	public void setProperties(Properties props) {
		this.props.putAll(props);
	}

	public Set<String> getReferencedServices() {
		return referencedServiceAddresses.keySet();
	}

	public Set<String> getRegisteredServices() {
		return registeredServices.keySet();
	}

	public boolean isReferencedService(String serviceName, String group) {
		return referencedServiceAddresses.containsKey(serviceName);
	}

	public List<String> getServiceAddressList(String serviceName, String group) throws RegistryException {
		String serviceAddress = getServiceAddress(serviceName, group);
		return Utils.getAddressList(serviceName, serviceAddress);
	}

	//根据serviceName和group获取pros中的值，如果没有获取到
	//如果环境为dev或alpha，configManager.getLocalStringValue(Utils.escapeServiceName(serviceKey))，如果还是为null，configManager.getLocalStringValue(serviceKey)
	//在否则，调用registry.getServiceAddress(serviceName, group)返回
	public String getServiceAddress(String serviceName, String group) throws RegistryException {
		String serviceKey = getServiceKey(serviceName, group);
		if (props.containsKey(serviceKey)) {
			if (logger.isInfoEnabled()) {
				logger.info("get service address from local properties, service name:" + serviceName + "  address:"
						+ props.getProperty(serviceKey));
			}
			return props.getProperty(serviceKey);
		}
		if (ConfigConstants.ENV_DEV.equalsIgnoreCase(configManager.getEnv())
				|| ConfigConstants.ENV_ALPHA.equalsIgnoreCase(configManager.getEnv())) {
			String addr = configManager.getLocalStringValue(Utils.escapeServiceName(serviceKey));
			if (addr == null) {
				try {
					addr = configManager.getLocalStringValue(serviceKey);
				} catch (Throwable e) {
				}
			}
			if (!StringUtils.isBlank(addr)) {
				if (logger.isInfoEnabled()) {
					logger.info("get service address from local properties, service name:" + serviceName + "  address:"
							+ addr);
				}
				return addr;
			}
		}
		if (registry != null) {
			String addr = registry.getServiceAddress(serviceName, group);
			return addr;
		}

		return null;
	}
	//如果group为null或空字符串，返回serviceName
	//否则，返回serviceName?group
	private String getServiceKey(String serviceName, String group) {
		if (StringUtils.isBlank(group)) {
			return serviceName;
		} else {
			return serviceName + "?group=" + group;
		}
	}

	//返回referencedAddresses.get(serverAddress)中的权值，否则返回Constants.DEFAULT_WEIGHT，默认为1
	public int getServiceWeightFromCache(String serverAddress) {
		HostInfo hostInfo = referencedAddresses.get(serverAddress);
		if (hostInfo != null) {
			return hostInfo.getWeight();
		}
		return Constants.DEFAULT_WEIGHT;
	}


	//1.返回referencedAddresses中的权重
	//2.否则调用registry.getServerWeight(serverAddress)返回权值
	//3.返回Constants.DEFAULT_WEIGHT
	//如果readCache为true，执行1，2,3；否则，执行2,3
	public int getServiceWeight(String serverAddress, boolean readCache) {
		if (readCache) {
			HostInfo hostInfo = referencedAddresses.get(serverAddress);
			if (hostInfo != null) {
				return hostInfo.getWeight();
			}
		}
		int weight = Constants.DEFAULT_WEIGHT;
		if (registry != null) {
			try {
				weight = registry.getServerWeight(serverAddress);
				HostInfo hostInfo = referencedAddresses.get(serverAddress);// ？这一步不是白写的么，这边是不是写错了，应该是referencedAddresses.set（new hostinfo）吧
				if (hostInfo != null) {
					hostInfo.setWeight(weight);
				}
			} catch (Throwable e) {
				logger.error("failed to get weight for " + serverAddress, e);
			}
		}
		return weight;
	}

	//返回服务权值，读本地referencedAddresses
	public int getServiceWeight(String serverAddress) {
		return getServiceWeight(serverAddress, true);
	}

	/*
	 * Update service weight in local cache. Will not update to registry center.
	 */
	//更新referencedAddresses中hostinfo权重，不更新注册中心
	public void setServiceWeight(String serviceAddress, int weight) {
		HostInfo hostInfo = referencedAddresses.get(serviceAddress);
		if (hostInfo == null) {
			if (!serviceAddress.startsWith(configManager.getLocalIp())) {
				logger.warn("weight not found for address:" + serviceAddress);
			}
			return;
		}
		hostInfo.setWeight(weight);
		logger.info("set " + serviceAddress + " weight to " + weight);
	}

	//使用rigistry注册，如果registeredServices不存在该serviceName，存入serviceAddress
	public void registerService(String serviceName, String group, String serviceAddress, int weight)
			throws RegistryException {
		if (registry != null) {
			registry.registerService(serviceName, group, serviceAddress, weight);
			registeredServices.putIfAbsent(serviceName, serviceAddress);
			monitorLogger.logEvent("PigeonService.register", serviceName, "weight=" + weight + "&group=" + group);
		}
	}

	//向注册中心设置Server权重
	//？为什么不更新本地缓存referencedAddresses中的权重
	public void setServerWeight(String serverAddress, int weight) throws RegistryException {
		if (registry != null) {
			registry.setServerWeight(serverAddress, weight);
			monitorLogger.logEvent("PigeonService.weight", weight + "", "");
		}
	}


	public void unregisterService(String serviceName, String serviceAddress) throws RegistryException {
		unregisterService(serviceName, Constants.DEFAULT_GROUP, serviceAddress);
	}

	//调用registry.unregisterService(serviceName, group, serviceAddress)注销服务，并在registeredServices上清除服务
	public void unregisterService(String serviceName, String group, String serviceAddress) throws RegistryException {
		if (registry != null) {
			registry.unregisterService(serviceName, group, serviceAddress);
			registeredServices.remove(serviceName);
			monitorLogger.logEvent("PigeonService.unregister", serviceName, "group=" + group);
		}
	}
	//检验权重，并将serviceName，host, port加入referencedServiceAddresses和referencedAddresses
	//同时向hostInfo中传入registry.getServerApp(hostInfo.getConnect())和 registry.getServerVersion(hostInfo.getConnect())
	public void addServiceAddress(String serviceName, String host, int port, int weight) {
		Utils.validateWeight(host, port, weight);

		HostInfo hostInfo = new HostInfo(host, port, weight);

		Set<HostInfo> hostInfos = referencedServiceAddresses.get(serviceName);
		if (hostInfos == null) {
			hostInfos = new HashSet<HostInfo>();
			referencedServiceAddresses.put(serviceName, hostInfos);
		}
		hostInfos.add(hostInfo);

		if (!referencedAddresses.containsKey(hostInfo.getConnect())) {
			referencedAddresses.put(hostInfo.getConnect(), hostInfo);
			if (registry != null) {
				String app = registry.getServerApp(hostInfo.getConnect());
				hostInfo.setApp(app);
				String version = registry.getServerVersion(hostInfo.getConnect());
				hostInfo.setVersion(version);
			}
		}
	}

	//从referencedServiceAddresses去除标识该serviceName和hostInfo的记录
	//如果在referencedServiceAddresses中已经没有这个连接，从referencedAddresses中去除
	public void removeServiceAddress(String serviceName, HostInfo hostInfo) {
		Set<HostInfo> hostInfos = referencedServiceAddresses.get(serviceName);
		if (hostInfos == null || !hostInfos.contains(hostInfo)) {
			logger.warn("address:" + hostInfo + " is not in address list of service " + serviceName);
			return;
		}
		hostInfos.remove(hostInfo);
		logger.info("removed address:" + hostInfo + " from service:" + serviceName);

		// If server is not referencd any more, remove from server list
		if (!isAddressReferenced(hostInfo)) {
			referencedAddresses.remove(hostInfo.getConnect());
		}
	}

	//返回hostInfo是否在referencedServiceAddresses
	private boolean isAddressReferenced(HostInfo hostInfo) {
		for (Set<HostInfo> hostInfos : referencedServiceAddresses.values()) {
			if (hostInfos.contains(hostInfo))
				return true;
		}
		return false;
	}

	//返回提供服务的地址
	public Set<HostInfo> getReferencedServiceAddresses(String serviceName) {
		Set<HostInfo> hostInfos = referencedServiceAddresses.get(serviceName);
		if (hostInfos == null || hostInfos.size() == 0) {
			logger.warn("empty address list for service:" + serviceName);
		}
		return hostInfos;
	}

	//返回所有referencedServiceAddresses
	public Map<String, Set<HostInfo>> getAllReferencedServiceAddresses() {
		return referencedServiceAddresses;
	}

	//从referencedAddresses中得到app的值
	//否则，调用registry.getServerApp(serverAddress)并存入referencedAddresses
	public String getServerApp(String serverAddress) {
		HostInfo hostInfo = referencedAddresses.get(serverAddress);
		String app = null;
		if (hostInfo != null) {
			app = hostInfo.getApp();
			if (app == null && registry != null) {
				app = registry.getServerApp(serverAddress);
				hostInfo.setApp(app);
			}
			return app;
		}
		return "";
	}

	//调用registry.setServerApp(serverAddress, app)
	public void setServerApp(String serverAddress, String app) {
		if (registry != null) {
			registry.setServerApp(serverAddress, app);
		}
	}
	//调用registry.unregisterServerApp(serverAddress)
	public void unregisterServerApp(String serverAddress) {
		if (registry != null) {
			registry.unregisterServerApp(serverAddress);
		}
	}
	//registry.setServerVersion(serverAddress, version)
	public void setServerVersion(String serverAddress, String version) {
		if (registry != null) {
			registry.setServerVersion(serverAddress, version);
		}
	}

	//向referencedAddresses查询的version，如果为null，向注册中心查询并设置到referencedAddresses中
	public String getServerVersion(String serverAddress) {
		HostInfo hostInfo = referencedAddresses.get(serverAddress);
		String version = null;
		if (hostInfo != null) {
			version = hostInfo.getVersion();
			if (version == null && registry != null) {
				version = registry.getServerVersion(serverAddress);
				hostInfo.setVersion(version);
			}
			return version;
		}
		return null;
	}

	//向注册中心注销serverAddress版本号
	public void unregisterServerVersion(String serverAddress) {
		if (registry != null) {
			registry.unregisterServerVersion(serverAddress);
		}
	}

	static class InnerServerInfoListener implements ServerInfoListener {

		//将新的app名字设置到hostinfo中
		@Override
		public void onServerAppChange(String serverAddress, String app) {
			HostInfo hostInfo = referencedAddresses.get(serverAddress);
			if (hostInfo != null) {
				hostInfo.setApp(app);
			}
		}

		//将新的version设置到hostinfo中
		@Override
		public void onServerVersionChange(String serverAddress, String version) {
			HostInfo hostInfo = referencedAddresses.get(serverAddress);
			if (hostInfo != null) {
				hostInfo.setVersion(version);
			}
		}
	}
}
