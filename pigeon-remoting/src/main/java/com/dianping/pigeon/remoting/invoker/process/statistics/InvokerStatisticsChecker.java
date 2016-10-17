package com.dianping.pigeon.remoting.invoker.process.statistics;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

public class InvokerStatisticsChecker implements Runnable {

	private static final Logger logger = LoggerLoader.getLogger(InvokerStatisticsChecker.class);

	@Override
	public void run() {
		InvokerStatisticsHolder.init();
		InvokerCapacityBucket.init();
		int i = 0, j = 0;
		while (!Thread.currentThread().isInterrupted()) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
			}

			if (InvokerStatisticsHolder.getCapacityBuckets() != null) {
				try {
					//每5秒更新一次秒计数器
					for (InvokerCapacityBucket bucket : InvokerStatisticsHolder.getCapacityBuckets().values()) {
						bucket.resetRequestsInSecondCounter();
					}
					//每1分钟更新一次分钟计数器
					if (++i % 12 == 0) {
						i = 0;
						for (InvokerCapacityBucket bucket : InvokerStatisticsHolder.getCapacityBuckets().values()) {
							bucket.resetRequestsInMinuteCounter();
						}
					}
					//每1天更新一次天计数器
					if (++j % 17280 == 0) {
						j = 0;
						for (InvokerCapacityBucket bucket : InvokerStatisticsHolder.getCapacityBuckets().values()) {
							bucket.resetRequestsInDayCounter();
						}
					}
				} catch (Throwable e) {
					logger.error("Check expired request in app statistics failed, detail[" + e.getMessage() + "].", e);
				}
			}
		}
	}

}
