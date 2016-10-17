/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.console.listener;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.config.ConfigManager;
import com.dianping.pigeon.console.exception.ServiceNotifyException;
import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.monitor.Monitor;
import com.dianping.pigeon.monitor.MonitorLogger;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.provider.config.ProviderConfig;
import com.dianping.pigeon.remoting.provider.listener.ServiceChangeListener;

public class DefaultServiceChangeListener implements ServiceChangeListener {

	private static final Logger logger = LoggerLoader.getLogger(DefaultServiceChangeListener.class);
   //从services文件夹下得到ConfigManager类
	private static ConfigManager configManager = ExtensionLoader.getExtension(ConfigManager.class);

	private Map<String, NotifyEvent> failedNotifyEvents = new ConcurrentHashMap<String, NotifyEvent>();
	//从services文件夹下得到Monitor类
	private static final MonitorLogger monitorLogger = ExtensionLoader.getExtension(Monitor.class).getLogger();

	public DefaultServiceChangeListener() {
	}

	public void destroy() throws Exception {
	}

	public Map<String, NotifyEvent> getFailedNotifyEvents() {
		return failedNotifyEvents;
	}

	private HttpClient getHttpClient() {
		HttpConnectionManager connectionManager = new MultiThreadedHttpConnectionManager(); //?这边怎么是一次申请一个HttpConnectionManager
		HttpConnectionManagerParams params = new HttpConnectionManagerParams();
		params.setMaxTotalConnections(300);
		params.setDefaultMaxConnectionsPerHost(50);
		params.setConnectionTimeout(3000);
		params.setTcpNoDelay(true);
		params.setSoTimeout(3000);
		params.setStaleCheckingEnabled(true);
		connectionManager.setParams(params);
		HttpClient httpClient = new HttpClient();
		httpClient.setHttpConnectionManager(connectionManager);

		return httpClient;
	}

	//发现服务发布了
	@Override
	public synchronized void notifyServicePublished(ProviderConfig<?> providerConfig) {
		logger.info("start to notify service published:" + providerConfig);
		notifyServiceChange("publish", providerConfig);
		logger.info("succeed to notify service published:" + providerConfig);
	}

	//访问http://${managerAddress}/service/{action}?env={env}&id=3&updatezk=false&service={providerConfig.getUrl()&group=
	// {group}&ip={ip}&port={providerConfig.getServerConfig().getActualPort()
	//如果appname不为空&app=configManager.getAppName()}
	public synchronized void notifyServiceChange(String action, ProviderConfig<?> providerConfig) {
		String managerAddress = configManager.getStringValue(Constants.KEY_MANAGER_ADDRESS,
				Constants.DEFAULT_MANAGER_ADDRESS);
		String env = providerConfig.getServerConfig().getEnv();
		if (StringUtils.isBlank(env)) {
			env = configManager.getEnv();
		}
		//得到参数ip,如果providerConfig中没有配置或者为空字符串，则configManager.getLocalIp()
		String ip = providerConfig.getServerConfig().getIp();
		if (StringUtils.isBlank(ip)) {
			ip = configManager.getLocalIp();
		}
		//得到参数group,如果providerConfig中没有配置或者为空字符串，则使用""
		String group = providerConfig.getServerConfig().getGroup();
		if (StringUtils.isBlank(group)) {
			group = Constants.DEFAULT_GROUP;
		}
		StringBuilder url = new StringBuilder();
		url.append("http://").append(managerAddress).append("/service/").append(action);
		url.append("?env=").append(env).append("&id=3&updatezk=false&service=");
		url.append(providerConfig.getUrl());
		url.append("&group=").append(group);
		url.append("&ip=").append(ip);
		url.append("&port=").append(providerConfig.getServerConfig().getActualPort());
		if (StringUtils.isNotBlank(configManager.getAppName())) {
			url.append("&app=").append(configManager.getAppName());
		}

		//从failedNotifyEvents移除
		failedNotifyEvents.remove(providerConfig.getUrl());
		boolean isSuccess = false;
		try {
			isSuccess = doNotify(url.toString());
		} catch (Throwable t) {
			logger.warn("error while notifying service change to url:" + url, t);
		}
		//如果不成功在加到failedNotifyEvents
		if (!isSuccess) {
			NotifyEvent event = new NotifyEvent();
			event.setNotifyUrl(url.toString());
			failedNotifyEvents.put(providerConfig.getUrl(), event);
		}
	}

	synchronized boolean doNotify(String url) {
		HttpClient httpClient = getHttpClient();
		GetMethod getMethod = null;
		String response = null;
		Throwable notifyException = null;
		logger.info("service change notify url:" + url);
		try {
			getMethod = new GetMethod(url);
			httpClient.executeMethod(getMethod);
			if (getMethod.getStatusCode() >= 300) {
				throw new IllegalStateException("Did not receive successful HTTP response: status code = "
						+ getMethod.getStatusCode() + ", status message = [" + getMethod.getStatusText() + "]");
			}
			InputStream inputStream = getMethod.getResponseBodyAsStream();
			BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
			StringBuilder sb = new StringBuilder();
			String str = null;
			while ((str = br.readLine()) != null) {
				sb.append(str);
			}
			response = sb.toString();
			br.close();
		} catch (Throwable t) {
			logger.warn("error while notifying service change to url:" + url, t);
			notifyException = t;
		} finally {
			if (getMethod != null) {
				getMethod.releaseConnection();
			}
		}
		boolean isSuccess = false;

		//响应以0开头则成功，否则报错
		if (response != null && response.startsWith("0")) {
			isSuccess = true;
		}
		if (!isSuccess) {
			logger.warn("error while notifying service change to url:" + url + ", response:" + response);
			monitorLogger.logError(new ServiceNotifyException("error while notifying service change to url:" + url
					+ ", response:" + response, notifyException));
		}
		return isSuccess;
	}

	//服务撤销
	@Override
	public synchronized void notifyServiceUnpublished(ProviderConfig<?> providerConfig) {
		logger.info("start to notify service unpublished:" + providerConfig);
		try {
			notifyServiceChange("unpublish", providerConfig);
			logger.info("succeed to notify service unpublished:" + providerConfig);
		} catch (Throwable t) {
			logger.warn(t.getMessage());
		}
	}

}
