/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.monitor.Monitor;
import com.dianping.pigeon.registry.config.RegistryConfigLoader;
import com.dianping.pigeon.remoting.common.codec.SerializerFactory;
import com.dianping.pigeon.remoting.common.status.Phase;
import com.dianping.pigeon.remoting.common.status.StatusContainer;
import com.dianping.pigeon.remoting.invoker.process.InvokerProcessHandlerFactory;
import com.dianping.pigeon.remoting.invoker.process.ResponseProcessorFactory;
import com.dianping.pigeon.remoting.invoker.process.statistics.InvokerCapacityBucket;
import com.dianping.pigeon.remoting.invoker.process.statistics.InvokerStatisticsHolder;
import com.dianping.pigeon.remoting.invoker.route.balance.LoadBalanceManager;
import com.dianping.pigeon.remoting.invoker.route.statistics.CapacityBucket;
import com.dianping.pigeon.remoting.invoker.route.statistics.ServiceStatisticsHolder;
import com.dianping.pigeon.remoting.invoker.service.ServiceInvocationRepository;
import com.dianping.pigeon.util.VersionUtils;

public final class InvokerBootStrap {

	private static final Logger logger = LoggerLoader.getLogger(InvokerBootStrap.class);

	private static volatile boolean isStartup = false;

	public static boolean isStartup() {
		return isStartup;
	}

	public static void startup() {
		if (!isStartup) {
			synchronized (InvokerBootStrap.class) {
				if (!isStartup) {
					RegistryConfigLoader.init();
					ServiceInvocationRepository.getInstance().init();
					InvokerProcessHandlerFactory.init();
					SerializerFactory.init();
					LoadBalanceManager.init();
					ResponseProcessorFactory.selectProcessor();
					InvokerStatisticsHolder.class.getSimpleName(); //？这4条语句有什么用？
					InvokerCapacityBucket.class.getSimpleName();
					ServiceStatisticsHolder.class.getSimpleName();
					CapacityBucket.class.getSimpleName();
					Monitor monitor = ExtensionLoader.getExtension(Monitor.class);
					if (monitor != null) {
						monitor.init();
					}
					isStartup = true;
					StatusContainer.setPhase(Phase.INVOKER_READY);
					logger.warn("pigeon client[version:" + VersionUtils.VERSION + "] has been started");
				}
			}
		}
	}

	public static void shutdown() {
		if (isStartup) {
			synchronized (InvokerBootStrap.class) {
				if (isStartup) {
					try {
						ClientManager.getInstance().destroy();
					} catch (Throwable e) {
					}
					try {
						ServiceInvocationRepository.getInstance().destroy();
					} catch (Throwable e) {
					}
					try {
						ResponseProcessorFactory.stop();
					} catch (Throwable e) {
					}
					try {
						LoadBalanceManager.destroy();
					} catch (Throwable e) {
					}
					isStartup = false;
					if (logger.isInfoEnabled()) {
						logger.info("pigeon client[version:" + VersionUtils.VERSION + "] has been shutdown");
					}
				}
			}
		}
	}

}
