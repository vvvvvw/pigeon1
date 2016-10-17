/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.domain;

import java.util.concurrent.TimeUnit;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.ymm.tracker.TrackerContext;
import com.dianping.dpsf.exception.NetTimeoutException;
import com.dianping.pigeon.extension.ExtensionLoader;
import com.dianping.pigeon.monitor.Monitor;
import com.dianping.pigeon.monitor.MonitorLogger;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.exception.RpcException;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.common.util.InvocationUtils;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.route.statistics.ServiceStatisticsHolder;
import com.dianping.pigeon.remoting.invoker.util.InvokerUtils;
import com.dianping.pigeon.util.ContextUtils;

public class CallbackFuture implements Callback, CallFuture {

	private static final Logger logger = LoggerLoader.getLogger(CallbackFuture.class);

	private static final MonitorLogger monitorLogger = ExtensionLoader.getExtension(Monitor.class).getLogger();

	protected InvocationResponse response;
	private CallFuture future;
	private boolean done = false;
	private boolean concelled = false;
	private boolean success = false;
	protected InvocationRequest request;
	protected Client client;

	public void run() {
		synchronized (this) {
			this.done = true;
			if (this.response.getMessageType() == Constants.MESSAGE_TYPE_SERVICE) {
				this.success = true;
			}
			this.notifyAll();
		}
	}

	public void callback(InvocationResponse response) {
		this.response = response;
	}

	public InvocationResponse get() throws InterruptedException {
		return get(Long.MAX_VALUE);
	}

	//判断现在是否到了超时时间，如果没有到超时时间，等待直到超时时间处理response，如果已经到了超时时间，抛出异常，处理自定义参数信息，处理异常响应，返回响应信息
	public InvocationResponse get(long timeoutMillis) throws InterruptedException {
		synchronized (this) {
			long start = request.getCreateMillisTime();
			while (!this.done) {
				long timeoutMillis_ = timeoutMillis - (System.currentTimeMillis() - start);
				if (timeoutMillis_ <= 0) {
					StringBuilder sb = new StringBuilder();
					sb.append("request timeout, current time:").append(System.currentTimeMillis())
							.append("\r\nrequest:").append(InvocationUtils.toJsonString(request)).append("\r\nhost:")
							.append(client.getHost()).append(":").append(client.getPort());
					ServiceStatisticsHolder.flowOut(request, client.getAddress());
					NetTimeoutException e = new NetTimeoutException(sb.toString());
					throw e;
				} else {
					this.wait(timeoutMillis_);
				}
			}
			processContext();

			if (response.getMessageType() == Constants.MESSAGE_TYPE_EXCEPTION) {
				RpcException cause = InvokerUtils.toRpcException(response);
				StringBuilder sb = new StringBuilder();
				sb.append("remote call exception\r\nrequest:").append(InvocationUtils.toJsonString(request))
						.append("\r\nhost:").append(client.getHost()).append(":").append(client.getPort())
						.append("\r\nresponse:").append(InvocationUtils.toJsonString(response));
				logger.error(sb.toString(), cause);
				monitorLogger.logError(sb.toString(), cause);
			} else if (response.getMessageType() == Constants.MESSAGE_TYPE_SERVICE_EXCEPTION) {
				if (Constants.LOG_INVOKER_APP_EXCEPTION) {
					Throwable cause = InvokerUtils.toApplicationException(response);
					StringBuilder sb = new StringBuilder();
					sb.append("remote service exception\r\nrequest:").append(InvocationUtils.toJsonString(request))
							.append("\r\nhost:").append(client.getHost()).append(":").append(client.getPort())
							.append("\r\nresponse:").append(InvocationUtils.toJsonString(response));
					logger.error(sb.toString(), cause);
					monitorLogger.logError(sb.toString(), cause);
				}
			}
			return this.response;
		}
	}

	//处理传递回来的自定义参数信息
	protected void processContext() {
		Object context = ContextUtils.getContext();
		if (context != null) {
			Integer order = ContextUtils.getOrder(this.response.getContext());
			if (order != null && order > 0) {
				ContextUtils.setOrder(context, order);
			}
			if (this.success) {
				// 传递业务上下文
				ContextUtils.addSuccessContext(this.response.getContext());
			} else {
				// 传递业务上下文
				ContextUtils.addFailedContext(this.response.getContext());
			}
			TrackerContext currentContext = (TrackerContext) context;
			TrackerContext responseContext = (TrackerContext) response.getContext();
			if (responseContext != null && responseContext.getExtension() != null) {
				if (currentContext.getExtension() == null)
					currentContext.setExtension(responseContext.getExtension());
				else
					currentContext.getExtension().putAll(responseContext.getExtension());
			}
		}
	}

	public InvocationResponse get(long timeout, TimeUnit unit) throws InterruptedException {
		return get(unit.toMillis(timeout));
	}

	//返回请求是否被取消
	public boolean cancel() {
		if (this.future != null) {
			synchronized (this) {
				this.concelled = this.future.cancel();
				this.notifyAll();
			}
		}
		return this.concelled;
	}

	public boolean isCancelled() {
		return this.concelled;
	}

	public boolean isDone() {
		return this.done;
	}

	public void setRequest(InvocationRequest request) {
		this.request = request;
	}

	@Override
	public void setClient(Client client) {
		this.client = client;
	}

	@Override
	public Client getClient() {
		return this.client;
	}

}
