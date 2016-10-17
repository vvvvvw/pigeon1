/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.provider.config;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.dianping.pigeon.config.ConfigManager;
import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.util.LangUtils;

//pigeon服务器的设置，包括是端口号，http端口号，pool大小（目前还不知道pool是不是线程池，队列是不是线程池的队列）
public class ServerConfig {

	private static ConfigManager configManager = ExtensionLoader.getExtension(ConfigManager.class);
	public static final int DEFAULT_PORT = getDefaultPort();
	public static final int DEFAULT_HTTP_PORT = 4080;
	private int port = configManager.getIntValue("pigeon.server.defaultport", DEFAULT_PORT);
	private int httpPort = configManager.getIntValue("pigeon.httpserver.defaultport", DEFAULT_HTTP_PORT);
	private boolean autoSelectPort = true;
	private boolean enableTest = configManager
			.getBooleanValue(Constants.KEY_TEST_ENABLE, Constants.DEFAULT_TEST_ENABLE);
	private int corePoolSize = configManager.getIntValue(Constants.KEY_PROVIDER_COREPOOLSIZE,
			Constants.DEFAULT_PROVIDER_COREPOOLSIZE);
	private int maxPoolSize = configManager.getIntValue(Constants.KEY_PROVIDER_MAXPOOLSIZE,
			Constants.DEFAULT_PROVIDER_MAXPOOLSIZE);
	private int workQueueSize = configManager.getIntValue(Constants.KEY_PROVIDER_WORKQUEUESIZE,
			Constants.DEFAULT_PROVIDER_WORKQUEUESIZE);
	private String group = configManager.getGroup();
	private String protocol = Constants.PROTOCOL_DEFAULT;
	private String env;
	private String ip;
	private int actualPort = port;

	public ServerConfig() {
	}

	//得到应用默认的端口号
	public static int getDefaultPort() {
		int port = 4040;
		try {
			String app = configManager.getAppName();
			if (StringUtils.isNotBlank(app)) {
				port = LangUtils.hash(app, 6000, 2000);
			}
		} catch (Throwable t) {
		}
		return port;
	}

	public int getActualPort() {
		return actualPort;
	}

	public void setActualPort(int actualPort) {
		this.actualPort = actualPort;
	}

	public String getEnv() {
		return env;
	}

	public void setEnv(String env) {
		this.env = env;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public boolean isEnableTest() {
		return enableTest;
	}

	public void setEnableTest(boolean enableTest) {
		this.enableTest = enableTest;
	}

	public boolean isAutoSelectPort() {
		return autoSelectPort;
	}

	public void setAutoSelectPort(boolean autoSelectPort) {
		this.autoSelectPort = autoSelectPort;
	}

	public int getHttpPort() {
		return httpPort;
	}

	public void setHttpPort(int httpPort) {
		this.httpPort = httpPort;
	}

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		if (!StringUtils.isBlank(group)) {
			this.group = group;
		}
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	//CorePoolSize小于等于0，返回1，否则，返回CorePoolSize
	public int getCorePoolSize() {
		if (corePoolSize <= 0) {
			corePoolSize = 1;
		}
		return corePoolSize;
	}

	public void setCorePoolSize(int corePoolSize) {
		this.corePoolSize = corePoolSize;
	}

	//MaxPoolSize小于等于0，返回5，否则，返回maxPoolSize
	public int getMaxPoolSize() {
		if (maxPoolSize <= 0) {
			maxPoolSize = 5;
		}
		return maxPoolSize;
	}

	public void setMaxPoolSize(int maxPoolSize) {
		this.maxPoolSize = maxPoolSize;
	}

	public int getWorkQueueSize() {
		return workQueueSize;
	}

	public void setWorkQueueSize(int workQueueSize) {
		this.workQueueSize = workQueueSize;
	}

	//反射获取当前类各种值的字符串
	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this, ToStringStyle.SHORT_PREFIX_STYLE);
	}
}
