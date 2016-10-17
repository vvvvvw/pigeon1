/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;
import org.springframework.util.CollectionUtils;

import com.dianping.pigeon.domain.phase.Disposable;
import com.dianping.pigeon.registry.listener.RegistryEventListener;
import com.dianping.pigeon.registry.listener.ServiceProviderChangeEvent;
import com.dianping.pigeon.registry.listener.ServiceProviderChangeListener;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.domain.ConnectInfo;

public class ClusterListenerManager implements Disposable {

	private static final Logger logger = LoggerLoader.getLogger(ClusterListenerManager.class);

	public static final String PLACEHOLDER = ":";

	private List<ClusterListener> listeners = new ArrayList<ClusterListener>();

	private ServiceProviderChangeListener providerChangeListener = new InnerServiceProviderChangeListener();

	//key: host:port
	private Map<String, ConnectInfo> connectInfoMap = new ConcurrentHashMap<String, ConnectInfo>();

	private static ClusterListenerManager instance = new ClusterListenerManager();

	public static ClusterListenerManager getInstance() {
		return instance;
	}

	private ClusterListenerManager() {
		RegistryEventListener.addListener(providerChangeListener);
	}

	//添加连接，并调用注册的ClusterListener的addConnect()方法
	public synchronized void addConnect(ConnectInfo cmd) {
		ConnectInfo connectInfo = this.connectInfoMap.get(cmd.getConnect());
		if (connectInfo == null) {
			this.connectInfoMap.put(cmd.getConnect(), cmd);
		} else {
			connectInfo.addServiceNames(cmd.getServiceNames());
			if (CollectionUtils.isEmpty(cmd.getServiceNames())) {
				if (logger.isInfoEnabled()) {
					logger.info("[cluster-listener-mgr] add services from:" + connectInfo);
				}
				cmd.addServiceNames(connectInfo.getServiceNames());
			}
		}
		for (ClusterListener listener : listeners) {
			listener.addConnect(cmd);
		}
	}

	public synchronized void removeConnect(Client client) {
		String connect = client.getConnectInfo().getConnect();
		ConnectInfo cmd = this.connectInfoMap.get(connect); //?不从connectInfoMap中移除么，还是说在连接被移除时，会不断调用RegistryEventListener的providerRemoved方法直到从connectInfoMap中删除？
		if (cmd != null) {
			for (ClusterListener listener : listeners) {
				listener.removeConnect(client);
			}
		}
	}

	public void addListener(ClusterListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void destroy() throws Exception {
		RegistryEventListener.removeListener(providerChangeListener);
	}

	class InnerServiceProviderChangeListener implements ServiceProviderChangeListener {
		@Override
		public void hostWeightChanged(ServiceProviderChangeEvent event) {
		}

		@Override
		public void providerAdded(ServiceProviderChangeEvent event) {
		}

		//当否一个服务被移除时，修改connectInfoMap，并调用所有注册的ClusterListener的doNotUse函数
		@Override
		public void providerRemoved(ServiceProviderChangeEvent event) {
			// addConnect的逆操作
			String connect = event.getHost() + ":" + event.getPort();
			if (logger.isInfoEnabled()) {
				logger.info("[cluster-listener-mgr] remove:" + connect + " from " + event.getServiceName());
			}
			ConnectInfo cmd = connectInfoMap.get(connect);
			if (cmd != null) {
				cmd.getServiceNames().remove(event.getServiceName());
				if (cmd.getServiceNames().size() == 0) {
					connectInfoMap.remove(connect);
				}
			}
			for (ClusterListener listener : listeners) {
				listener.doNotUse(event.getServiceName(), event.getHost(), event.getPort());
			}
		}
	}
}
