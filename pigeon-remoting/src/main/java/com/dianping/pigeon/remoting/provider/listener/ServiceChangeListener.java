/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.provider.listener;

import com.dianping.pigeon.remoting.provider.config.ProviderConfig;
//服务发布和摘掉的监听器
public interface ServiceChangeListener {

	void notifyServicePublished(ProviderConfig<?> providerConfig);

	void notifyServiceUnpublished(ProviderConfig<?> providerConfig);
}
