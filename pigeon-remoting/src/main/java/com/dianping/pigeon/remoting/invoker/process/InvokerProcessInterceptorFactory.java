/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2014 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.process;

import java.util.ArrayList;
import java.util.List;

public class InvokerProcessInterceptorFactory {

	private static List<InvokerProcessInterceptor> interceptors = new ArrayList<InvokerProcessInterceptor>();

	//注册拦截器
	public static boolean registerInterceptor(InvokerProcessInterceptor providerProcessInterceptor) {
		if (!interceptors.contains(providerProcessInterceptor)) {
			return interceptors.add(providerProcessInterceptor);
		}
		return false;
	}

	//注销拦截器
	public static boolean unregisterInterceptor(InvokerProcessInterceptor providerProcessInterceptor) {
		return interceptors.remove(providerProcessInterceptor);
	}

	//得到拦截器列表
	public static List<InvokerProcessInterceptor> getInterceptors() {
		return interceptors;
	}
}
