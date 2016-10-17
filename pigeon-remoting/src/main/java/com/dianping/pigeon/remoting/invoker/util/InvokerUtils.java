package com.dianping.pigeon.remoting.invoker.util;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.dianping.dpsf.async.ServiceFuture;
import com.dianping.dpsf.exception.DPSFException;
import com.dianping.pigeon.remoting.common.codec.SerializerFactory;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.exception.ApplicationException;
import com.dianping.pigeon.remoting.common.exception.NetworkException;
import com.dianping.pigeon.remoting.common.exception.RpcException;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.common.util.TimelineManager;
import com.dianping.pigeon.remoting.common.util.TimelineManager.Phase;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.config.InvokerConfig;
import com.dianping.pigeon.remoting.invoker.domain.Callback;
import com.dianping.pigeon.remoting.invoker.domain.InvokerContext;
import com.dianping.pigeon.remoting.invoker.domain.RemoteInvocationBean;
import com.dianping.pigeon.remoting.invoker.exception.RemoteInvocationException;
import com.dianping.pigeon.remoting.invoker.process.InvokerExceptionTranslator;
import com.dianping.pigeon.remoting.invoker.service.ServiceInvocationRepository;

public class InvokerUtils {

	private static ServiceInvocationRepository invocationRepository = ServiceInvocationRepository.getInstance();

	private static InvokerExceptionTranslator invokerExceptionTranslator = new InvokerExceptionTranslator();

	private static AtomicLong requestSequenceMaker = new AtomicLong();

	public static InvocationResponse sendRequest(Client client, InvocationRequest request, Callback callback) {
		if (request.getCallType() == Constants.CALLTYPE_REPLY) {
			RemoteInvocationBean invocationBean = new RemoteInvocationBean();
			invocationBean.request = request;
			invocationBean.callback = callback;
			callback.setRequest(request);
			callback.setClient(client);
			invocationRepository.put(request.getSequence(), invocationBean); //？这边心跳的序列号可能会和服务的序列号重复
		}
		TimelineManager.time(request, TimelineManager.getLocalIp(), Phase.Start);
		InvocationResponse response = null;
		try {
			response = client.write(request, callback);
		} catch (RuntimeException e) {
			invocationRepository.remove(request.getSequence());
			TimelineManager.removeTimeline(request, TimelineManager.getLocalIp());
			throw new NetworkException("remote call failed:" + request, e);
		} finally {
			if (response != null) {
				invocationRepository.remove(request.getSequence());
				TimelineManager.removeTimeline(request, TimelineManager.getLocalIp());
			}
		}
		return response;
	}

	//根据
	public static InvocationRequest createRemoteCallRequest(InvokerContext invocationContext,
			InvokerConfig<?> invokerConfig) {
		InvocationRequest request = invocationContext.getRequest();
		if (request == null) {
			//这边其实就是根据invocationContext创建一个请求
			request = SerializerFactory.getSerializer(invokerConfig.getSerialize()).newRequest(invocationContext);
			invocationContext.setRequest(request);
		}
		request.setSequence(requestSequenceMaker.incrementAndGet() * -1);
		return request;
	}

	public static InvocationResponse createNoReturnResponse() {
		return new NoReturnResponse();
	}

	public static InvocationResponse createFutureResponse(ServiceFuture serviceFuture) {
		FutureResponse resp = new FutureResponse();
		resp.setServiceFuture(serviceFuture);
		return resp;
	}

	public static boolean isHeartErrorResponse(InvocationResponse response) {
		return response != null && response.getMessageType() == Constants.MESSAGE_TYPE_HEART
				&& response.getCause() != null;
	}

	public static RuntimeException toApplicationRuntimeException(InvocationResponse response) {
		Throwable t = toApplicationException(response);
		if (t instanceof RuntimeException) {
			return (RuntimeException) t;
		} else {
			return new ApplicationException(t);
		}
	}

	public static Throwable toApplicationException(InvocationResponse response) {
		Object responseReturn = response.getReturn();
		if (responseReturn == null) {
			return new ApplicationException(response.getCause());
		} else if (responseReturn instanceof DPSFException) {
			return new ApplicationException(invokerExceptionTranslator.translate((DPSFException) responseReturn));
		} else if (responseReturn instanceof RpcException) {
			return new ApplicationException((RpcException) responseReturn);
		} else if (responseReturn instanceof RuntimeException) {
			return (RuntimeException) responseReturn;
		} else if (responseReturn instanceof Throwable) {
			return new RemoteInvocationException((Throwable) responseReturn);
		} else if (responseReturn instanceof Map) {
			Map errors = (Map) responseReturn;
			String detailMessage = (String) errors.get("detailMessage");
			StackTraceElement[] stackTrace = (StackTraceElement[]) errors.get("stackTrace");
			ApplicationException e = new ApplicationException(detailMessage);
			e.setStackTrace(stackTrace);
			return e;
		} else {
			return new ApplicationException(responseReturn.toString());
		}
	}

	public static RpcException toRpcException(InvocationResponse response) {
		Throwable e = null;
		Object responseReturn = response.getReturn();
		//如果return为空的话，抛出RemoteInvocationException
		if (responseReturn == null) {
			return new RemoteInvocationException(response.getCause());

		} else if (responseReturn instanceof DPSFException) {
			e = invokerExceptionTranslator.translate((DPSFException) responseReturn);
		} else if (responseReturn instanceof Throwable) {
			e = (Throwable) responseReturn;
		} else if (responseReturn instanceof Map) {
			Map errors = (Map) responseReturn;
			String detailMessage = (String) errors.get("detailMessage");
			StackTraceElement[] stackTrace = (StackTraceElement[]) errors.get("stackTrace");
			e = new RemoteInvocationException(detailMessage);
			e.setStackTrace(stackTrace);
		} else {
			e = new RemoteInvocationException(responseReturn.toString());
		}
		if (!(e instanceof RpcException)) {
			return new RemoteInvocationException(e);
		}
		return (RpcException) e;
	}

	//返回为空的响应
	static class NoReturnResponse implements InvocationResponse {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 4348389641787057819L;

		private long invokerRequestTime;

		private long invokerResponseTime;

		private long providerRequestTime;

		private long providerResponseTime;

		public long getInvokerRequestTime() {
			return invokerRequestTime;
		}

		public void setInvokerRequestTime(long invokerRequestTime) {
			this.invokerRequestTime = invokerRequestTime;
		}

		public long getInvokerResponseTime() {
			return invokerResponseTime;
		}

		public void setInvokerResponseTime(long invokerResponseTime) {
			this.invokerResponseTime = invokerResponseTime;
		}

		public long getProviderRequestTime() {
			return providerRequestTime;
		}

		public void setProviderRequestTime(long providerRequestTime) {
			this.providerRequestTime = providerRequestTime;
		}

		public long getProviderResponseTime() {
			return providerResponseTime;
		}

		public void setProviderResponseTime(long providerResponseTime) {
			this.providerResponseTime = providerResponseTime;
		}

		@Override
		public void setMessageType(int messageType) {
		}

		@Override
		public int getMessageType() {
			return 0;
		}

		@Override
		public String getCause() {
			return null;
		}

		@Override
		public Object getReturn() {
			return null;
		}

		@Override
		public void setReturn(Object obj) {
		}

		@Override
		public byte getSerialize() {
			return 0;
		}

		@Override
		public void setSequence(long seq) {
		}

		@Override
		public long getSequence() {
			return 0;
		}

		@Override
		public Object getObject() {
			return null;
		}

		@Override
		public Object getContext() {
			return null;
		}

		@Override
		public void setContext(Object context) {
		}

		@Override
		public void setSerialize(byte serialize) {
		}
	}

	public static class FutureResponse implements InvocationResponse {

		/**
		 * serialVersionUID
		 */
		private static final long serialVersionUID = 4348389641787057819L;

		private long invokerRequestTime;

		private long invokerResponseTime;

		private long providerRequestTime;

		private long providerResponseTime;

		private ServiceFuture serviceFuture;

		public ServiceFuture getServiceFuture() {
			return serviceFuture;
		}

		public void setServiceFuture(ServiceFuture serviceFuture) {
			this.serviceFuture = serviceFuture;
		}

		public long getInvokerRequestTime() {
			return invokerRequestTime;
		}

		public void setInvokerRequestTime(long invokerRequestTime) {
			this.invokerRequestTime = invokerRequestTime;
		}

		public long getInvokerResponseTime() {
			return invokerResponseTime;
		}

		public void setInvokerResponseTime(long invokerResponseTime) {
			this.invokerResponseTime = invokerResponseTime;
		}

		public long getProviderRequestTime() {
			return providerRequestTime;
		}

		public void setProviderRequestTime(long providerRequestTime) {
			this.providerRequestTime = providerRequestTime;
		}

		public long getProviderResponseTime() {
			return providerResponseTime;
		}

		public void setProviderResponseTime(long providerResponseTime) {
			this.providerResponseTime = providerResponseTime;
		}

		@Override
		public void setMessageType(int messageType) {
		}

		@Override
		public int getMessageType() {
			return 0;
		}

		@Override
		public String getCause() {
			return null;
		}

		@Override
		public Object getReturn() {
			return null;
		}

		@Override
		public void setReturn(Object obj) {
		}

		@Override
		public byte getSerialize() {
			return 0;
		}

		@Override
		public void setSequence(long seq) {
		}

		@Override
		public long getSequence() {
			return 0;
		}

		@Override
		public Object getObject() {
			return null;
		}

		@Override
		public Object getContext() {
			return null;
		}

		@Override
		public void setContext(Object context) {
		}

		@Override
		public void setSerialize(byte serialize) {
		}
	}
}
