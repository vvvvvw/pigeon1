/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.listener;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.config.ConfigManagerLoader;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.ClientSelector;
import com.dianping.pigeon.remoting.invoker.config.InvokerConfig;
import com.dianping.pigeon.remoting.invoker.domain.ConnectInfo;
import com.dianping.pigeon.remoting.invoker.exception.ServiceUnavailableException;
import com.dianping.pigeon.threadpool.DefaultThreadFactory;
import com.dianping.pigeon.util.CollectionUtils;
import com.dianping.pigeon.util.ThreadPoolUtils;

public class DefaultClusterListener implements ClusterListener {

	private static final Logger logger = LoggerLoader.getLogger(DefaultClusterListener.class);

	//key:服务url value：该服务url相关的client
	private Map<String, List<Client>> serviceClients = new ConcurrentHashMap<String, List<Client>>();

	//key: host:port value:对应的cllient
	private Map<String, Client> allClients = new ConcurrentHashMap<String, Client>();

	private HeartBeatListener heartbeatListener;

	private ReconnectListener reconnectListener;

	private ScheduledThreadPoolExecutor closeExecutor = new ScheduledThreadPoolExecutor(5, new DefaultThreadFactory(
			"Pigeon-Client-Cache-Close-ThreadPool"));

	private ClusterListenerManager clusterListenerManager = ClusterListenerManager.getInstance();

	public DefaultClusterListener(HeartBeatListener heartbeatListener, ReconnectListener reconnectListener,
			ProviderAvailableListener providerAvailableListener) {
		this.heartbeatListener = heartbeatListener;
		this.reconnectListener = reconnectListener;
		this.reconnectListener.setWorkingClients(serviceClients);
		this.heartbeatListener.setWorkingClients(serviceClients);
		providerAvailableListener.setWorkingClients(serviceClients);
	}

	public void clear() {
		serviceClients = new ConcurrentHashMap<String, List<Client>>();
		allClients = new ConcurrentHashMap<String, Client>();
	}

	public List<Client> getClientList(InvokerConfig<?> invokerConfig) {
		List<Client> clientList = this.serviceClients.get(invokerConfig.getUrl());
		if (CollectionUtils.isEmpty(clientList)) {
			throw new ServiceUnavailableException("no available provider for service:" + invokerConfig.getUrl()
					+ ", group:" + invokerConfig.getGroup() + ", env:"
					+ ConfigManagerLoader.getConfigManager().getEnv());
		}
		return clientList;
	}

	//添加
	public void addConnect(ConnectInfo connectInfo) {
		if (logger.isInfoEnabled()) {
			logger.info("[cluster-listener] add service provider:" + connectInfo);
		}
		Client client = this.allClients.get(connectInfo.getConnect());
		if (clientExisted(connectInfo)) {
			if (client != null) {
				//从serviceClients中获取到这个client，如果和并且关闭
				for (List<Client> clientList : serviceClients.values()) {
					int idx = clientList.indexOf(client);
					if (idx >= 0 && clientList.get(idx) != client) {
						closeClientInFuture(client);
					}
				}
			} else {
				//?如果在serviceClients中存在该connect，但是在allClients没有得到client的话，这直接返回？（什么情况下会出现这种情况？）
				return;
			}
		}

		if (client == null) {
			//根据clientinfo得到client
			client = ClientSelector.selectClient(connectInfo);
		}

		//如果在allClients中没有，存入allClients
		if (!this.allClients.containsKey(connectInfo.getConnect())) {
			this.allClients.put(connectInfo.getConnect(), client);
		}
		try {
			if (!client.isConnected()) {
				client.connect();
			}
			if (client.isConnected()) {
				for (Entry<String, Integer> sw : connectInfo.getServiceNames().entrySet()) {
					String serviceName = sw.getKey();
					List<Client> clientList = this.serviceClients.get(serviceName);
					if (clientList == null) {
						clientList = new ArrayList<Client>();
						this.serviceClients.put(serviceName, clientList);
					}
					if (!clientList.contains(client)) {
						clientList.add(client);
					}
				}
			} else {
				clusterListenerManager.removeConnect(client);
			}
		} catch (Throwable e) {
			logger.error("", e);
		}
	}

	//判断connectInfo是否已经在serviceClients中存在
	private boolean clientExisted(ConnectInfo connectInfo) {
		for (String serviceName : connectInfo.getServiceNames().keySet()) {
			List<Client> clientList = serviceClients.get(serviceName);
			if (clientList != null) {
				for (Client client : clientList) {
					if (client.getAddress().equals(connectInfo.getConnect())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public void removeConnect(Client client) {
		if (logger.isInfoEnabled()) {
			logger.info("[cluster-listener] remove service provider:" + client);
		}
		for (String serviceName : this.serviceClients.keySet()) {
			List<Client> clientList = this.serviceClients.get(serviceName);
			if (clientList != null && clientList.contains(client)) {
				clientList.remove(client);
			}
		}
	}

	//
	@Override
	public void doNotUse(String serviceName, String host, int port) {
		if (logger.isInfoEnabled()) {
			logger.info("[cluster-listener] do not use service provider:" + serviceName + ":" + host + ":" + port);
		}
		List<Client> cs = serviceClients.get(serviceName);
		List<Client> newCS = new ArrayList<Client>();
		if (cs != null && !cs.isEmpty()) {
			newCS.addAll(cs);
		}
		Client clientFound = null;
		//从serviceClients中移除
		for (Client client : cs) {
			if (client.getHost().equals(host) && client.getPort() == port) {
				newCS.remove(client);
				clientFound = client;
			}
		}
		serviceClients.put(serviceName, newCS);

		// 一个client可能对应多个serviceName，仅当client不被任何serviceName使用时才关闭
		if (clientFound != null) {
			if (!isClientInUse(clientFound)) {
				//从重连监听器中移除
				removeClientFromReconnectTask(clientFound);
				//冲allClients中移除
				allClients.remove(clientFound.getAddress());
				//关闭clientFound
				closeClientInFuture(clientFound);
			}
		}
	}

	// move to HeartTask?
	//如果clientToRemove已经在reconnectListener的closedClients列表中，则从closedClients移除？难道不在closedClients列表中就不移除了么？
	private void removeClientFromReconnectTask(Client clientToRemove) {
		Map<String, Client> closedClients = reconnectListener.getClosedClients();
		Set<String> keySet = closedClients.keySet();
		Iterator<String> iterator = keySet.iterator();
		while (iterator.hasNext()) {
			String connect = iterator.next();
			if (closedClients.get(connect).equals(clientToRemove)) {
				iterator.remove();
			}
		}
	}

	//如果在serviceClients存在，返回true；否则，返回false
	private boolean isClientInUse(Client clientToFind) {
		for (List<Client> clientList : serviceClients.values()) {
			if (clientList.contains(clientToFind)) {
				return true;
			}
		}
		return false;
	}

	//等待com.dianping.pigeon.invoker.closewaittime时间后，关闭client
	private void closeClientInFuture(final Client client) {

		Runnable command = new Runnable() {

			//关闭client，并且关闭相关联的channel
			@Override
			public void run() {
				client.close();
			}

		};

		try {
			String waitTimeStr = System.getProperty("com.dianping.pigeon.invoker.closewaittime");
			int waitTime = 3000;
			if (waitTimeStr != null) {
				try {
					waitTime = Integer.parseInt(waitTimeStr);
				} catch (RuntimeException e) {
					logger.error("error parsing com.dianping.pigeon.invoker.closewaittime", e);
				}
			}
			if (waitTime < 0) {
				waitTime = 3000;
			}
			closeExecutor.schedule(command, waitTime, TimeUnit.MILLISECONDS);
		} catch (Throwable e) {
			logger.error("error schedule task to close client", e);
		}
	}

	public void destroy() throws Exception {
		ThreadPoolUtils.shutdown(closeExecutor);
	}
}
