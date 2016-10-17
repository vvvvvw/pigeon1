package com.dianping.pigeon.remoting.invoker.process.statistics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.config.ConfigManagerLoader;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.util.Constants;

public final class InvokerStatisticsHolder {

	private static final Logger logger = LoggerLoader.getLogger(InvokerStatisticsHolder.class);

	private static ConcurrentHashMap<String, InvokerCapacityBucket> appCapacityBuckets = new ConcurrentHashMap<String, InvokerCapacityBucket>();

	public static final boolean statEnable = ConfigManagerLoader.getConfigManager().getBooleanValue(
			"pigeon.invokerstat.enable", true);

	public static void init() {
	}

	public static Map<String, InvokerCapacityBucket> getCapacityBuckets() {
		return appCapacityBuckets;
	}

	//获取key为targetApp的InvokerCapacityBucket，如果没有，新建一个存入appCapacityBuckets
	public static InvokerCapacityBucket getCapacityBucket(InvocationRequest request, String targetApp) {
		String toApp = targetApp;
		if (toApp == null) {
			toApp = "";
		}
		InvokerCapacityBucket barrel = appCapacityBuckets.get(toApp);
		if (barrel == null) {
			InvokerCapacityBucket newBarrel = new InvokerCapacityBucket(toApp);
			barrel = appCapacityBuckets.putIfAbsent(toApp, newBarrel);
			if (barrel == null) {
				barrel = newBarrel;
			}
		}
		return barrel;
	}


	public static void flowIn(InvocationRequest request, String targetApp) {
		if (checkRequestNeedStat(request)) {
			InvokerCapacityBucket barrel = getCapacityBucket(request, targetApp);
			if (barrel != null) {
				barrel.flowIn(request);
			}
		}
	}

	public static void flowOut(InvocationRequest request, String targetApp) {
		if (checkRequestNeedStat(request)) {
			InvokerCapacityBucket barrel = getCapacityBucket(request, targetApp);
			if (barrel != null) {
				//只修改总请求数，其他每秒、每份、每天的都不变
				barrel.flowOut(request);
			}
		}
	}

	//如果request为空或者request的消息类型不为MESSAGE_TYPE_SERVICE，返回false
	//否则，返回statEnable
	//即检验是否应该更新InvokerCapacityBucket
	public static boolean checkRequestNeedStat(InvocationRequest request) {
		if (request == null || request.getMessageType() != Constants.MESSAGE_TYPE_SERVICE) {
			return false;
		}
		return statEnable;
	}

	public static void removeCapacityBucket(String fromApp) {
		appCapacityBuckets.remove(fromApp);
	}
}
