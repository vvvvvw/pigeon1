/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.process.filter;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.monitor.MonitorLogger;
import com.dianping.pigeon.monitor.MonitorTransaction;
import com.dianping.pigeon.registry.RegistryManager;
import com.dianping.pigeon.remoting.common.codec.SerializerFactory;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.process.ServiceInvocationHandler;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.config.InvokerConfig;
import com.dianping.pigeon.remoting.invoker.domain.InvokerContext;
import com.dianping.pigeon.util.ContextUtils;
import com.dianping.pigeon.util.VersionUtils;

public class ContextPrepareInvokeFilter extends InvocationInvokeFilter {

	private static final Logger logger = LoggerLoader.getLogger(ContextPrepareInvokeFilter.class);
	private MonitorLogger monitorLogger = ExtensionLoader.getExtension(MonitorLogger.class);
	private ConcurrentHashMap<String, Boolean> versionSupportedMap = new ConcurrentHashMap<String, Boolean>();

	@Override
	public InvocationResponse invoke(ServiceInvocationHandler handler, InvokerContext invocationContext)
			throws Throwable {
		if (logger.isDebugEnabled()) {
			logger.debug("invoke the ContextPrepareInvokeFilter, invocationContext:" + invocationContext);
		}
		initRequest(invocationContext);
		transferContextValueToRequest(invocationContext, invocationContext.getRequest());
		return handler.handle(invocationContext);
	}

	//初始化Request的createTime、timeout、序列化方式、WRITE_BUFF_LIMIT、调用需不需要返回值
	// 如果pigeon.timeout.reset为true，初始化Request的createTime和timeout，以便统一这两个值
	private void initRequest(InvokerContext invokerContext) {
		InvocationRequest request = invokerContext.getRequest();
		request.setCreateMillisTime(System.currentTimeMillis());
		request.setMessageType(Constants.MESSAGE_TYPE_SERVICE);

		checkSerializeSupported(invokerContext);

		InvokerConfig<?> invokerConfig = invokerContext.getInvokerConfig();
		if (invokerConfig != null) {
			request.setTimeout(invokerConfig.getTimeout());
			if (Constants.RESET_TIMEOUT) {
				//从本地上下文中获取requestTimeout,request中的timeout设置为本地上下文和invokerConfig中小的那个
				Object timeout = ContextUtils.getLocalContext(Constants.REQUEST_TIMEOUT);
				if (timeout != null) {
					int timeout_ = Integer.parseInt(String.valueOf(timeout));
					if (timeout_ > 0 && timeout_ < request.getTimeout()) {
						request.setTimeout(timeout_);
					}
				}
			}
			MonitorTransaction transaction = monitorLogger.getCurrentTransaction();
			if (transaction != null) {
				transaction.addData("CurrentTimeout", request.getTimeout());
			}
			request.setAttachment(Constants.REQ_ATTACH_WRITE_BUFF_LIMIT, invokerConfig.isWriteBufferLimit());
			if (Constants.CALL_ONEWAY.equalsIgnoreCase(invokerConfig.getCallType())) {
				request.setCallType(Constants.CALLTYPE_NOREPLY);
			} else {
				request.setCallType(Constants.CALLTYPE_REPLY);
			}
		}
	}

	//预处理序列化方式
	private void checkSerializeSupported(InvokerContext invokerContext) {
		InvocationRequest request = invokerContext.getRequest();
		//如果请求的序列化方式为protobuf或者是fst,检查versionSupportedMap中是否支持，如果版本大于"2.4.3"，则支持
		//如果不支持，默认使用hessian
		if (request.getSerialize() == SerializerFactory.SERIALIZE_PROTO
				|| request.getSerialize() == SerializerFactory.SERIALIZE_FST) {
			Client client = invokerContext.getClient();
			String version = RegistryManager.getInstance().getServerVersion(client.getAddress());
			boolean supported = true;
			if (StringUtils.isBlank(version)) {
				supported = false;
			} else if (versionSupportedMap.containsKey(version)) {
				supported = versionSupportedMap.get(version);
			} else {
				supported = VersionUtils.compareVersion(version, "2.4.3") >= 0;
				versionSupportedMap.putIfAbsent(version, supported);
			}
			if (!supported) {
				request.setSerialize(SerializerFactory.SERIALIZE_HESSIAN);
				invokerContext.getInvokerConfig().setSerialize(SerializerFactory.HESSIAN);
			}
		}
	}

//	将invocationContext中的contextValue字段设置到ContextUtils，并将生成的上下文对象设置到request中
	private void transferContextValueToRequest(final InvokerContext invocationContext, final InvocationRequest request) {
		InvokerConfig<?> metaData = invocationContext.getInvokerConfig();
		Client client = invocationContext.getClient();
		Object contextHolder = ContextUtils.createContext(metaData.getUrl(), invocationContext.getMethodName(),
				client.getHost(), client.getPort());
		if (contextHolder != null) {
			Map<String, Serializable> contextValues = invocationContext.getContextValues();
			if (contextValues != null) {
				for (Map.Entry<String, Serializable> entry : contextValues.entrySet()) {
					ContextUtils.putContextValue(contextHolder, entry.getKey(), entry.getValue());
				}
			}
		}
		request.setContext(contextHolder);
	}

}
