/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.container;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
//使用ClassPathXmlApplicationContext加载spring配置文件信息
public final class SpringContainer {

	private static final Logger logger = LoggerLoader.getLogger(SpringContainer.class);

	//默认spring配置文件位置
	public String DEFAULT_SPRING_CONFIG = "classpath*:META-INF/spring/*.xml";

	private static ClassPathXmlApplicationContext context;

	private static volatile boolean isStartup = false;

	public SpringContainer() {

	}

	public SpringContainer(String path) {
		DEFAULT_SPRING_CONFIG = path;
	}

	public ClassPathXmlApplicationContext getContext() {
		return context;
	}

	public void setParentContext(ApplicationContext parentContext) {
		context.setParent(parentContext);
	}

	public Object getBean(String beanName) {
		return context.getBean(beanName);
	}

	public void start() {
		if (!isStartup) {
			synchronized (this) {
				if (!isStartup) {
					String configPath = DEFAULT_SPRING_CONFIG;
					try {
						context = new ClassPathXmlApplicationContext(configPath.split("[,\\s]+"));
						context.start();
						//?这边是不是要再加一个isStartup=true;
					} catch (Throwable e) {
						throw new RuntimeException("error while start spring context:" + configPath, e);
					}
				}
			}
		}
	}

	public void stop() {
		try {
			if (context != null) {
				context.stop();
				context.close();
				context = null;
			}
		} catch (Throwable e) {
			logger.error(e.getMessage(), e);
		}
	}

}