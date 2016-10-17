package com.dianping.pigeon.demo.interceptor;

import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.invoker.process.InvokerProcessInterceptor;


public class MyInvokerProcessInterceptor implements InvokerProcessInterceptor {

	@Override
	public void preInvoke(InvocationRequest invocationRequest) {
		System.out.println("preInvoke:" + invocationRequest);
	}

	@Override
	public void postInvoke(InvocationRequest invocationRequest,InvocationResponse invocationResponse) {
		System.out.println("postInvoke:" + invocationResponse);
	}

}
