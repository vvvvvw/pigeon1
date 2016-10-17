/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.extension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

/**
 * 
 * @author xiangwu
 * @Sep 11, 2013
 * 
 */
public final class ExtensionLoader {

	private static final Logger logger = LoggerLoader.getLogger(ExtensionLoader.class);

	private static Map<Class<?>, Object> extensionMap = new ConcurrentHashMap<Class<?>, Object>();

	private static Map<Class<?>, List<?>> extensionListMap = new ConcurrentHashMap<Class<?>, List<?>>();


	//如果extensionMap中没有，向extensionMap中存入clazz类型，然后返回该类型的对象
	public static <T> T getExtension(Class<T> clazz) {
		T extension = (T) extensionMap.get(clazz);
		if (extension == null) {
			extension = newExtension(clazz);
			if (extension != null) {
				extensionMap.put(clazz, extension);
			}
		}
		return extension;
	}

	public static <T> List<T> getExtensionList(Class<T> clazz) {
		List<T> extensions = (List<T>) extensionListMap.get(clazz);
		if (extensions == null) {
			extensions = newExtensionList(clazz);
			if (!extensions.isEmpty()) {
				extensionListMap.put(clazz, extensions);
			}
		}
		return extensions;
	}

	//加载在META-INF/services/定义的clazz接口或者抽象类的其中一个实现类  ，但是好像找不到这个services文件夹
	public static <T> T newExtension(Class<T> clazz) {
		ServiceLoader<T> serviceLoader = ServiceLoader.load(clazz);
		for (T service : serviceLoader) {
			return service;
		}
		logger.warn("no extension found for class:" + clazz.getName());
		return null;
	}

	//加载在META-INF/services/定义的接口接口或者抽象类的所有实现类的列表
	public static <T> List<T> newExtensionList(Class<T> clazz) {
		ServiceLoader<T> serviceLoader = ServiceLoader.load(clazz);
		List<T> extensions = new ArrayList<T>();
		for (T service : serviceLoader) {
			extensions.add(service);
		}
		if (extensions.isEmpty()) {
			logger.warn("no extension found for class:" + clazz.getName());
		}
		return extensions;
	}
}
