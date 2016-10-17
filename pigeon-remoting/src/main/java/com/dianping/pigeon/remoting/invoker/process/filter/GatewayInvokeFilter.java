/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.process.filter;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.dpsf.async.ServiceFutureFactory;
import com.dianping.pigeon.registry.RegistryManager;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.process.ServiceInvocationHandler;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.config.InvokerConfig;
import com.dianping.pigeon.remoting.invoker.domain.InvokerContext;
import com.dianping.pigeon.remoting.invoker.process.statistics.InvokerStatisticsChecker;
import com.dianping.pigeon.remoting.invoker.process.statistics.InvokerStatisticsHolder;
import com.dianping.pigeon.threadpool.DefaultThreadPool;
import com.dianping.pigeon.threadpool.ThreadPool;

/**
 * 
 * 
 */
public class GatewayInvokeFilter extends InvocationInvokeFilter {

	private static final Logger logger = LoggerLoader.getLogger(GatewayInvokeFilter.class);
	private static ThreadPool statisticsCheckerPool = new DefaultThreadPool("Pigeon-Server-Statistics-Checker");

	//初始化请求计数器线程
	static {
		InvokerStatisticsChecker appStatisticsChecker = new InvokerStatisticsChecker();
		statisticsCheckerPool.execute(appStatisticsChecker);
	}

	//从invocationContext中取出client，根据client获取targetApp。更新请求数
	//直接调用handler.handle(invocationContext)，再更新请求数
	@Override
	public InvocationResponse invoke(ServiceInvocationHandler handler, InvokerContext invocationContext)
			throws Throwable {
		if (logger.isDebugEnabled()) {
			logger.debug("invoke the GatewayInvokeFilter, invocationContext:" + invocationContext);
		}
		InvokerConfig<?> invokerConfig = invocationContext.getInvokerConfig();
		InvocationRequest request = invocationContext.getRequest();
		Client client = invocationContext.getClient();
		String targetApp = RegistryManager.getInstance().getServerApp(client.getAddress());
		try {
			InvokerStatisticsHolder.flowIn(request, targetApp);
			try {
				return handler.handle(invocationContext);
			} catch (Throwable e) {
				if (Constants.CALL_FUTURE.equalsIgnoreCase(invokerConfig.getCallType())) {
					//如果发生异常，将ServiceFutureFactory中本线程的ServiceFuture移除
					ServiceFutureFactory.remove();
				}
				throw e;
			}
		} finally {
			InvokerStatisticsHolder.flowOut(request, targetApp);
		}
	}

}
