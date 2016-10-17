package com.dianping.pigeon.util;

public class ClassUtils {

	public static Class loadClass(ClassLoader classLoader, String className) throws ClassNotFoundException {
		if (classLoader == null) {
			classLoader = Thread.currentThread().getContextClassLoader();
		}
		return org.apache.commons.lang.ClassUtils.getClass(classLoader, className);
	}

	//使用当前线程的上下文类加载器来加载类
	public static Class loadClass(String className) throws ClassNotFoundException {
		return loadClass(null, className);
	}

	//1.传入classloader不为null，返回传入classloader
	//2.否则，返回当前线程的类加载器
	//3.再否则，返回加载ClassUtils.class类的类加载器
	public static ClassLoader getCurrentClassLoader(ClassLoader classLoader) {
		if(classLoader != null) {
			return classLoader;
		}
		ClassLoader currentLoader = Thread.currentThread().getContextClassLoader();
		if (currentLoader != null) {
			return currentLoader;
		}
		return ClassUtils.class.getClassLoader();
	}
}
