package com.dianping.pigeon.remoting.common.util;

import java.util.List;

import org.springframework.util.CollectionUtils;

import com.dianping.pigeon.config.ConfigManagerLoader;

public class ServiceConfigUtils {

	private static String interfacePackagesConfig = ConfigManagerLoader.getConfigManager().getStringValue(
			"pigeon.provider.interface.packages", "com.dianping,com.dp");

	private static String[] interfacePackages = new String[] { "com.dianping" };

	static {
		interfacePackages = (interfacePackagesConfig == null || interfacePackagesConfig.length() == 0) ? null
				: Constants.COMMA_SPLIT_PATTERN.split(interfacePackagesConfig);
	}

	//返回类型是否是在interfacePackages包中的
	private static boolean isValidType(Class type) {
		String beanClassName = type.getName();
		for (String pkg : interfacePackages) {
			if (beanClassName.startsWith(pkg)) {
				return true;
			}
		}
		return false;
	}

	//返回服务接口；
	//如果type实现的接口中有在interfacePackages中的，返回该接口类型；查找顺序为1.type实现的接口2.type的父类实现的接口，依次向上
	//否则，如果type实现的接口中没有在interfacePackages中的，返回type实现接口的第一个
	//否则，返回type
	public static <T> Class<?> getServiceInterface(Class<?> type) {
		Class<?>[] interfaces = type.getInterfaces();
		Class<?> interfaceClass = null;
		if (interfaces != null && interfaces.length > 0) {
			interfaceClass = type.getInterfaces()[0];
		} else {
			List<Class<?>> allInterfaces = org.apache.commons.lang.ClassUtils.getAllInterfaces(type);
			if (!CollectionUtils.isEmpty(allInterfaces)) {
				for (Class<?> i : allInterfaces) {
					if (isValidType(i)) {
						interfaceClass = i;
						break;
					}
				}
				if (interfaceClass == null) {
					interfaceClass = allInterfaces.get(0);
				}
			}
			if (interfaceClass == null) {
				interfaceClass = type;
			}
		}
		return interfaceClass;
	}
}
