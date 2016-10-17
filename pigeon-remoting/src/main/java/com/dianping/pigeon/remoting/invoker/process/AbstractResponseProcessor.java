/**
 * 
 */
package com.dianping.pigeon.remoting.invoker.process;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.monitor.Monitor;
import com.dianping.pigeon.monitor.MonitorLogger;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.process.threadpool.ResponseThreadPoolProcessor;

/**
 * @author xiangwu
 * 
 */
public abstract class AbstractResponseProcessor implements ResponseProcessor {

	protected static final Logger logger = LoggerLoader.getLogger(ResponseThreadPoolProcessor.class);

	protected static final MonitorLogger monitorLogger = ExtensionLoader.getExtension(Monitor.class).getLogger();

	public abstract void doProcessResponse(InvocationResponse response, Client client);

	//调用doProcessResponse，如果发生异常，则使用cat记录日志
	@Override
	public void processResponse(InvocationResponse response, Client client) {
		try {
			doProcessResponse(response, client);
		} catch (Throwable e) {
			String error = String.format("process response failed:%s, processor stats:%s", response,
					getProcessorStatistics());
			logger.error(error, e);
			monitorLogger.logError(error, e);
		}
	}

}
