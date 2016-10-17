/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.process.filter;

import java.util.Map;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.dpsf.async.ServiceFutureFactory;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.exception.InvalidParameterException;
import com.dianping.pigeon.remoting.common.process.ServiceInvocationHandler;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.config.InvokerConfig;
import com.dianping.pigeon.remoting.invoker.config.InvokerMethodConfig;
import com.dianping.pigeon.remoting.invoker.domain.CallbackFuture;
import com.dianping.pigeon.remoting.invoker.domain.InvokerContext;
import com.dianping.pigeon.remoting.invoker.domain.ServiceCallbackWrapper;
import com.dianping.pigeon.remoting.invoker.domain.ServiceFutureImpl;
import com.dianping.pigeon.remoting.invoker.util.InvokerHelper;
import com.dianping.pigeon.remoting.invoker.util.InvokerUtils;
import com.dianping.pigeon.util.CollectionUtils;

/**
 * 执行实际的Remote Call，包括Sync, Future，Callback，Oneway
 * 
 * @author danson.liu
 */
public class RemoteCallInvokeFilter extends InvocationInvokeFilter {

	private static final Logger logger = LoggerLoader.getLogger(RemoteCallInvokeFilter.class);

	private static final InvocationResponse NO_RETURN_RESPONSE = InvokerUtils.createNoReturnResponse();

	@Override
	public InvocationResponse invoke(ServiceInvocationHandler handler, InvokerContext invocationContext)
			throws Throwable {
		if (logger.isDebugEnabled()) {
			logger.debug("invoke the RemoteCallInvokeFilter, invocationContext:" + invocationContext);
		}
		Client client = invocationContext.getClient();
		InvocationRequest request = invocationContext.getRequest();
		InvokerConfig<?> invokerConfig = invocationContext.getInvokerConfig();
		String callType = invokerConfig.getCallType();
		beforeInvoke(request, client);
		InvocationResponse response = null;
		int timeout = request.getTimeout();
		Map<String, InvokerMethodConfig> methods = invokerConfig.getMethods(); //？这边使用ProxyBeanFactory注册的话好像也是为null（还没有测试过）
		//timeout取methodConfig
		if (!CollectionUtils.isEmpty(methods)) {
			InvokerMethodConfig methodConfig = methods.get(request.getMethodName());
			if (methodConfig != null && methodConfig.getTimeout() > 0) {
				timeout = methodConfig.getTimeout();
			}
		}
		//timeout取InvokerHelper.getTimeout()
		Integer timeoutThreadLocal = InvokerHelper.getTimeout();
		if (timeoutThreadLocal != null) {
			timeout = timeoutThreadLocal;
		}
		//如果调用方式为sync，则等待直到timeout返回响应
		if (Constants.CALL_SYNC.equalsIgnoreCase(callType)) {
			CallbackFuture future = new CallbackFuture();
			response = InvokerUtils.sendRequest(client, invocationContext.getRequest(), future);
			if (response == null) {
				response = future.get(timeout);
			}
		}
		//如果调用方式为callback，则直接返回并返回一个NO_RETURN_RESPONSE
		else if (Constants.CALL_CALLBACK.equalsIgnoreCase(callType)) {
			InvokerUtils.sendRequest(client, invocationContext.getRequest(),
					new ServiceCallbackWrapper(invokerConfig.getCallback()));
			response = NO_RETURN_RESPONSE;
		}
		//如果调用方式为future，则创建一个ServiceFutureImpl等待响应返回后处理，并返回一个futureResponse
		else if (Constants.CALL_FUTURE.equalsIgnoreCase(callType)) {
			ServiceFutureImpl future = new ServiceFutureImpl(timeout);
			InvokerUtils.sendRequest(client, invocationContext.getRequest(), future);
			ServiceFutureFactory.setFuture(future);
			response = InvokerUtils.createFutureResponse(future);
			//如果调用方式为oneway，则返回一个NO_RETURN_RESPONSE
		} else if (Constants.CALL_ONEWAY.equalsIgnoreCase(callType)) {
			InvokerUtils.sendRequest(client, invocationContext.getRequest(), null);
			response = NO_RETURN_RESPONSE;
		} else {
			throw new InvalidParameterException("Call type[" + callType + "] is not supported!");
		}
		//后处理
		afterInvoke(request, response, client);
		return response;
	}

}
