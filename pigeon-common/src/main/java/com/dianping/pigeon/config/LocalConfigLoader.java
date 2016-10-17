package com.dianping.pigeon.config;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import com.dianping.pigeon.util.FileUtils;

//加载配置文件的类
public class LocalConfigLoader {

	private static final String DEV_PROPERTIES_PATH = "config/applicationContext.properties";

	private static final String PROPERTIES_PATH = "config/pigeon.properties";

	private static final String GLOBAL_PROPERTIES_PATH = "/data/webapps/config/pigeon/pigeon.properties";

	private static String appName = null;

	//加载appName，先找/META-INF/app.properties，如果没有再找/data/webapps/config/目录下的app.properties文件，返回app.name
	public static String getAppName() {
		if (appName == null) {
			try {
				URL appProperties = LocalConfigLoader.class.getResource("/META-INF/app.properties");
				if (appProperties == null) {
					appProperties = new URL("file:" + LocalConfigLoader.class.getResource("/").getPath()
							+ "/META-INF/app.properties");
					if (!new File(appProperties.getFile()).exists()) {
						appProperties = new URL("file:/data/webapps/config/app.properties");
					}
				}
				Properties properties = null;
				if (appProperties != null) {
					properties = FileUtils.readFile(appProperties.openStream());
					appName = properties.getProperty("app.name");
				}
			} catch (Throwable e) {
			}
			if (appName == null) {
				return "";
			}
		}
		return appName;
	}

	//去除properties中的注释，并返回
	private static void loadProperties(Map<String, Object> results, Properties properties) {
		for (Iterator ir = properties.keySet().iterator(); ir.hasNext();) {
			String key = ir.next().toString();
			if (key.startsWith("#")) {
				continue;
			}
			String value = properties.getProperty(key);
			value = value.trim();
			results.put(key, value.trim());
		}
	}

	//从/data/webapps/config/pigeon/pigeon.properties中加载全局属性，
	//从当前线程的classcontextloader路径下的config/pigeon.properties中读取属性文件
	//如果环境值为dev或者是alpha，从当前线程的classcontextloader路径下的config/applicationContext.properties中读取属性；否则，读取config/pigeon_" + env + ".properties属性文件
	//下面的覆盖上面的
	public static Map<String, Object> load(ConfigManager configManager) {
		Map<String, Object> results = new HashMap<String, Object>();
		try {
			loadProperties(results, FileUtils.readFile(new FileInputStream(GLOBAL_PROPERTIES_PATH)));
		} catch (Throwable e) {
		}
		try {
			loadProperties(
					results,
					FileUtils.readFile(Thread.currentThread().getContextClassLoader()
							.getResourceAsStream(PROPERTIES_PATH)));
		} catch (Throwable e) {
		}
		if (configManager != null) {
			String env = configManager.getEnv();
			if (ConfigConstants.ENV_DEV.equalsIgnoreCase(env) || ConfigConstants.ENV_ALPHA.equalsIgnoreCase(env)) {
				try {
					loadProperties(
							results,
							FileUtils.readFile(Thread.currentThread().getContextClassLoader()
									.getResourceAsStream(DEV_PROPERTIES_PATH)));
				} catch (Throwable e) {
				}
			}
			try {
				loadProperties(
						results,
						FileUtils.readFile(Thread.currentThread().getContextClassLoader()
								.getResourceAsStream("config/pigeon_" + env + ".properties")));
			} catch (Throwable e) {
			}
		}
		return results;
	}

}
