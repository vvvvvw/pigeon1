/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker;

import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.domain.InvocationResponse;
import com.dianping.pigeon.remoting.common.exception.NetworkException;
import com.dianping.pigeon.remoting.invoker.domain.Callback;
import com.dianping.pigeon.remoting.invoker.domain.ConnectInfo;

/**
 * 
 * 
 * @author jianhuihuang
 * @version $Id: Client.java, v 0.1 2013-7-16 上午10:36:30 jianhuihuang Exp $
 */
//客户端连接和发送请求
public interface Client {

	ConnectInfo getConnectInfo();

	void connect();

	InvocationResponse write(InvocationRequest request, Callback callback) throws NetworkException;

	InvocationResponse write(InvocationRequest request) throws NetworkException;

	void connectionException(Object attachment, Throwable e);

	void processResponse(InvocationResponse response);

	boolean isConnected();

	boolean isActive();

	void setActive(boolean active);

	boolean isWritable();

	String getHost();

	String getAddress();

	int getPort();

	void close();

	boolean isDisposable();

	void dispose();
	
	String getProtocol();

}
