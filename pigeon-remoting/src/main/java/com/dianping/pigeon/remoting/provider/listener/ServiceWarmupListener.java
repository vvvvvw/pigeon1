/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.provider.listener;

import java.util.Map;
import java.util.Map.Entry;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.config.ConfigManager;
import com.dianping.pigeon.config.ConfigManagerLoader;
import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.remoting.common.status.Phase;
import com.dianping.pigeon.remoting.common.status.StatusContainer;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.provider.config.ProviderConfig;
import com.dianping.pigeon.remoting.provider.service.ServiceProviderFactory;

public class ServiceWarmupListener implements Runnable {

	private static final Logger logger = LoggerLoader.getLogger(ServiceWarmupListener.class);

	private static ConfigManager configManager = ExtensionLoader.getExtension(ConfigManager.class);

	private static final int CHECK_INTERVAL = configManager.getIntValue(Constants.KEY_WEIGHT_WARMUPPERIOD,
			Constants.DEFAULT_WEIGHT_WAMUPPERIOD);

	private static final int START_DELAY = configManager.getIntValue(Constants.KEY_WEIGHT_STARTDELAY, CHECK_INTERVAL);

	private static volatile boolean isServiceWarmupListenerStarted = false;

	private static ServiceWarmupListener currentWarmupListener = null;

	private volatile boolean isStop = false;
    //如果warmupEnable && autoRegisterEnable && autoPublishEnable，启动一个ServiceWarmupListener后台线程
	public static void start() {
		boolean autoPublishEnable = ConfigManagerLoader.getConfigManager().getBooleanValue(
				Constants.KEY_AUTOPUBLISH_ENABLE, true);
		boolean autoRegisterEnable = ConfigManagerLoader.getConfigManager().getBooleanValue(
				Constants.KEY_AUTOREGISTER_ENABLE, true);
		boolean warmupEnable = ConfigManagerLoader.getConfigManager().getBooleanValue(
				Constants.KEY_SERVICEWARMUP_ENABLE, true);
		if (!isServiceWarmupListenerStarted && warmupEnable && autoRegisterEnable && autoPublishEnable) {
			currentWarmupListener = new ServiceWarmupListener();
			Thread t = new Thread(currentWarmupListener);
			t.setDaemon(true);
			t.setName("Pigeon-Service-Warmup-Listener");
			t.start();
			isServiceWarmupListenerStarted = true;
		}
	}

	//将isStop设置为true
	public static void stop() {
		if (currentWarmupListener != null) {
			currentWarmupListener.setStop(true);
			while (isServiceWarmupListenerStarted) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
			}
			currentWarmupListener = null;
		}
	}

	public boolean isStop() {
		return isStop;
	}

	public void setStop(boolean isStop) {
		this.isStop = isStop;
	}

	//暂停START_DELAY，然后检查发布和未发布的服务个数，如果未发布个数不为0，暂停CHECK_INTERVAL，然后再检查，直到所有服务都发布
	//设置StatusContainer为Phase.PUBLISHED
	public void run() {
		try {
			Thread.sleep(START_DELAY);
			if (!isStop) {
				ServiceProviderFactory.setServerWeight(Constants.WEIGHT_START);
			}
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}
		int publishedCount = 0;
		int unpublishedCount = 0;
		while (!isStop
				&& (StatusContainer.getPhase().equals(Phase.TOPUBLISH)
						|| StatusContainer.getPhase().equals(Phase.PUBLISHING) || StatusContainer.getPhase().equals(
						Phase.PUBLISHED))) {
			Map<String, ProviderConfig<?>> services = ServiceProviderFactory.getAllServiceProviders();
			for (Entry<String, ProviderConfig<?>> entry : services.entrySet()) {
				ProviderConfig<?> providerConfig = entry.getValue();
				if (providerConfig.isPublished()) {
					publishedCount++;
				} else {
					unpublishedCount++;
				}
			}
			if (publishedCount > 0 && unpublishedCount == 0) {
				StatusContainer.setPhase(Phase.PUBLISHED);
				break;
			} else {
				try {
					Thread.sleep(CHECK_INTERVAL);
				} catch (InterruptedException e) {
				}
			}
		}
		int weight = Constants.WEIGHT_START;
		while (!isStop && weight < Constants.WEIGHT_DEFAULT) {
			StatusContainer.setPhase(Phase.WARMINGUP);
			try {
				Thread.sleep(CHECK_INTERVAL);
				if (!StatusContainer.getPhase().equals(Phase.WARMINGUP)
						&& !StatusContainer.getPhase().equals(Phase.PUBLISHED)) {
					logger.warn("Warm-up task will be end, current status:" + StatusContainer.getPhase());
					break;
				}
				ServiceProviderFactory.setServerWeight(++weight);
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		}
		if (weight == Constants.WEIGHT_DEFAULT) {
			StatusContainer.setPhase(Phase.WARMEDUP);
		}
		logger.info("Warm-up task end, current weight:" + ServiceProviderFactory.getServerWeight());
		isServiceWarmupListenerStarted = false;
	}
}
