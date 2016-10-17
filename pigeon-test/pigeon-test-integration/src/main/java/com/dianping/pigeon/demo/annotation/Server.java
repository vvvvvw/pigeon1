/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.demo.annotation;

import com.dianping.pigeon.container.SpringContainer;


public class Server {

	private static SpringContainer SERVER_CONTAINER = new SpringContainer(
			"classpath*:META-INF/spring/annotation/provider.xml");

	public static void main(String[] args) throws Exception {
		SERVER_CONTAINER.start();

		System.in.read();
	}

}
