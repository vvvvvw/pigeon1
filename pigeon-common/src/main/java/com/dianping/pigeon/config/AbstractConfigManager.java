/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.util.NetUtils;

/**
 * @author xiangwu
 * @Sep 22, 2013
 * 
 */
//继承了ConfigManager，实现了获取信息的一些逻辑
public abstract class AbstractConfigManager implements ConfigManager {

	private static Logger logger = LoggerLoader.getLogger(AbstractConfigManager.class);

	public static final String KEY_GROUP = "swimlane";

	public static final String KEY_LOCAL_IP = "host.ip";

	public static final String KEY_APP_NAME = "app.name";

	public static final String KEY_ENV = "environment";

	public static final String DEFAULT_GROUP = "";

	public static final int DEFAULT_WEIGHT = 1;

	private static List<ConfigChangeListener> configChangeListeners = new ArrayList<ConfigChangeListener>();

	protected Map<String, Object> localCache = new HashMap<String, Object>();

	public abstract String doGetProperty(String key) throws Exception;

	public abstract String doGetLocalProperty(String key) throws Exception;

	public abstract String doGetEnv() throws Exception;

	public abstract String doGetLocalIp() throws Exception;

	public abstract String doGetGroup() throws Exception;

	public abstract void doSetStringValue(String key, String value) throws Exception;

	public abstract void doDeleteKey(String key) throws Exception;


	//读取属性文件，全部放入localCache
	public AbstractConfigManager() {
		Map<String, Object> properties = LocalConfigLoader.load(this);
		localCache.putAll(properties);
	}


	public boolean getBooleanValue(String key, boolean defaultValue) {
		Boolean value = getBooleanValue(key);
		return value != null ? value : defaultValue;
	}

	public Boolean getBooleanValue(String key) {
		return getProperty(key, Boolean.class);
	}

	public long getLongValue(String key, long defaultValue) {
		Long value = getLongValue(key);
		return value != null ? value : defaultValue;
	}

	public Long getLongValue(String key) {
		return getProperty(key, Long.class);
	}

	public int getIntValue(String key, int defaultValue) {
		Integer value = getIntValue(key);
		return value != null ? value : defaultValue;
	}

	public Integer getIntValue(String key) {
		return getProperty(key, Integer.class);
	}

	public float getFloatValue(String key, float defaultValue) {
		Float value = getFloatValue(key);
		return value != null ? value : defaultValue;
	}

	public Float getFloatValue(String key) {
		return getProperty(key, Float.class);
	}
	
	public double getDoubleValue(String key, double defaultValue) {
		Double value = getDoubleValue(key);
		return value != null ? value : defaultValue;
	}

	public Double getDoubleValue(String key) {
		return getProperty(key, Double.class);
	}

	@Override
	public String getStringValue(String key, String defaultValue) {
		String value = getStringValue(key);
		return value != null ? value : defaultValue;
	}

	public String getLocalStringValue(String key) {
		return getPropertyFromLocal(key, String.class);
	}

	//如果localCache中值的类型和所需类型相同，返回该值
	//否则，将localCache中的值转化为字符串；如果值为null，首先System.getProperty(key)，否则System.getenv(key)
	//再再否则，doGetLocalProperty(key)；
	//将value类型转换后，存入localCache并返回，否则，返回null
	private <T> T getPropertyFromLocal(String key, Class<T> type) {
		String strValue = null;
		if (localCache.containsKey(key)) {
			Object value = localCache.get(key);
			if (value.getClass() == type) {
				return (T) value;
			} else {
				strValue = value + "";
			}
		}
		if (strValue == null) {
			strValue = System.getProperty(key);
		}
		if (strValue == null) {
			strValue = System.getenv(key);
		}
		if (strValue == null) {
			try {
				strValue = doGetLocalProperty(key);
			} catch (Throwable e) {
				logger.error("error while reading local config[" + key + "]:" + e.getMessage());
			}
		}
		if (strValue != null) {
			Object value = null;
			if (String.class == type) {
				value = strValue;
			} else if (!StringUtils.isBlank(strValue)) {
				if (Integer.class == type) {
					value = Integer.valueOf(strValue);
				} else if (Long.class == type) {
					value = Long.valueOf(strValue);
				} else if (Float.class == type) {
					value = Float.valueOf(strValue);
				} else if (Boolean.class == type) {
					value = Boolean.valueOf(strValue);
				}
			}
			if (value != null) {
				localCache.put(key, value);
			}
			return (T) value;
		} else {
		}
		return null;
	}


	@Override
	public String getStringValue(String key) {
		return getProperty(key, String.class);
	}


	//如果localCache中值的类型和所需类型相同，返回该值
	//否则，将localCache中的值转化为字符串；如果值为null，首先System.getProperty(key)，否则System.getenv(key)，
	//再再否则，doGetLocalProperty(key)；
	//再再再否则，如果appname不为空白或者是null并且key不以appname开头，doGetProperty(getAppName() + "." + key)
	//再再再再否则，strValue = doGetProperty(key)
	//将value类型转换后，存入localCache并返回，否则，返回null
	private <T> T getProperty(String key, Class<T> type) {
		String strValue = null;
		if (localCache.containsKey(key)) {
			Object value = localCache.get(key);
			if (value.getClass() == type) {
				return (T) value;
			} else {
				strValue = value + "";
			}
		}
		if (strValue == null) {
			strValue = System.getProperty(key);
		}
		if (strValue == null) {
			strValue = System.getenv(key);
		}
		if (strValue == null) {
			try {
				strValue = doGetLocalProperty(key);
			} catch (Throwable e) {
				logger.error("error while reading local config[" + key + "]:" + e.getMessage());
			}
		}
		if (strValue == null && StringUtils.isNotBlank(getAppName())) {
			if (!key.startsWith(getAppName())) {
				try {
					strValue = doGetProperty(getAppName() + "." + key);
					if (strValue != null && logger.isInfoEnabled()) {
						logger.info("read from config server with key[" + getAppName() + "." + key + "]:" + strValue);
					}
				} catch (Throwable e) {
					logger.error("error while reading property[" + getAppName() + "." + key + "]:" + e.getMessage());
				}
			}
		}
		if (strValue == null) {
			try {
				strValue = doGetProperty(key);
				if (strValue != null && logger.isInfoEnabled()) {
					logger.info("read from config server with key[" + key + "]:" + strValue);
				}
			} catch (Throwable e) {
				logger.error("error while reading property[" + key + "]:" + e.getMessage());
			}
		}
		if (strValue != null) {
			Object value = null;
			if (String.class == type) {
				value = strValue;
			} else if (!StringUtils.isBlank(strValue)) {
				if (Integer.class == type) {
					value = Integer.valueOf(strValue);
				} else if (Long.class == type) {
					value = Long.valueOf(strValue);
				} else if (Float.class == type) {
					value = Float.valueOf(strValue);
				} else if (Boolean.class == type) {
					value = Boolean.valueOf(strValue);
				} else if (Double.class == type) {
					value = Double.valueOf(strValue);
				} 
			}
			if (value != null) {
				localCache.put(key, value);
			}
			return (T) value;
		} else {
		}
		return null;
	}

	public int getLocalIntValue(String key, int defaultValue) {
		String strValue = getLocalProperty(key);
		if (!StringUtils.isBlank(strValue)) {
			return Integer.valueOf(strValue);
		}
		return defaultValue;
	}

	public long getLocalLongValue(String key, long defaultValue) {
		String strValue = getLocalProperty(key);
		if (!StringUtils.isBlank(strValue)) {
			return Long.valueOf(strValue);
		}
		return defaultValue;
	}

	public boolean getLocalBooleanValue(String key, boolean defaultValue) {
		String strValue = getLocalProperty(key);
		if (!StringUtils.isBlank(strValue)) {
			return Boolean.valueOf(strValue);
		}
		return defaultValue;
	}

	public String getLocalStringValue(String key, String defaultValue) {
		String value = getLocalProperty(key);
		return value != null ? value : defaultValue;
	}

	//如果localCache中包含key，返回value
	//否则，doGetLocalProperty(String key)，如果不为空，获取到值后放入localCache，并返回
	public String getLocalProperty(String key) {
		if (localCache.containsKey(key)) {
			String value = "" + localCache.get(key);
			return value;
		}
		try {
			String value = doGetLocalProperty(key);
			if (value != null) {
				localCache.put(key, value);
				if (logger.isInfoEnabled()) {
					logger.info("read from config server with key[" + key + "]:" + value);
				}
				return value;
			} else {
			}
		} catch (Throwable e) {
			logger.error("error while reading property[" + key + "]:" + e.getMessage());
		}
		return null;
	}

	//将properties中的属性存入localCache
	@Override
	public void init(Properties properties) {
		for (Iterator ir = properties.keySet().iterator(); ir.hasNext();) {
			String key = ir.next().toString();
			String value = properties.getProperty(key);
			localCache.put(key, value);
		}
	}

	//getLocalProperty(environment)，否则doGetEnv()
	public String getEnv() {
		String value = getLocalProperty(KEY_ENV);
		if (value == null) {
			try {
				value = doGetEnv();
			} catch (Throwable e) {
				logger.error("error while reading env:" + e.getMessage());
			}
			if (value != null) {
				localCache.put(KEY_ENV, value);
			}
		}
		return value;
	}

	//getLocalProperty(app.name)，如果没有，查找app.properties文件
	public String getAppName() {
		String value = getLocalProperty(KEY_APP_NAME);
		if (value == null) {
			try {
				value = LocalConfigLoader.getAppName();
			} catch (Throwable e) {
				logger.error("error while reading app name:" + e.getMessage());
			}
			if (value != null) {
				localCache.put(KEY_APP_NAME, value);
			}
			if (StringUtils.isNotBlank(value)) {
				System.out.println("app name:" + value);
			}
		}
		return value;
	}
	//getLocalProperty(host.ip)，如果没有，doGetLocalIp()
	public String getLocalIp() {
		String value = getLocalProperty(KEY_LOCAL_IP);
		if (value == null) {
			try {
				value = doGetLocalIp();
			} catch (Throwable e) {
				logger.error("error while reading local ip:" + e.getMessage());
			}
			if (StringUtils.isBlank(value)) {
				value = NetUtils.getFirstLocalIp();
			}
			if (value != null) {
				localCache.put(KEY_LOCAL_IP, value);
			}
		}
		return value;
	}

	//getLocalProperty(swimlane)，如果没有，doGetGroup()，如果再没有，返回""
	public String getGroup() {
		String value = getLocalProperty(KEY_GROUP);
		if (value == null) {
			try {
				value = doGetGroup();
			} catch (Throwable e) {
				logger.error("error while reading group:" + e.getMessage());
			}
			if (value != null) {
				localCache.put(KEY_GROUP, value);
			}
		}
		if (value == null) {
			return DEFAULT_GROUP;
		}
		return value;
	}

	//注册configChangeListener
	public void registerConfigChangeListener(ConfigChangeListener configChangeListener) {
		configChangeListeners.add(configChangeListener);
		try {
			doRegisterConfigChangeListener(configChangeListener);
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	public abstract void doRegisterConfigChangeListener(ConfigChangeListener configChangeListener) throws Exception;

	//首先doSetStringValue(key, value)，然后将key和value存入localCache
	@Override
	public void setStringValue(String key, String value) {
		try {
			doSetStringValue(key, value);
			setLocalStringValue(key, value);
		} catch (Throwable e) {
			throw new ConfigException("error while setting key:" + key, e);
		}
	}

	//首先doDeleteKey(key)，然后从localCache删除key和value
	@Override
	public void deleteKey(String key) {
		try {
			doDeleteKey(key);
			localCache.remove(key);
		} catch (Throwable e) {
			throw new ConfigException("error while deleting key:" + key, e);
		}
	}

	@Override
	public void setLocalStringValue(String key, String value) {
		localCache.put(key, value);
	}

	//返回localCache
	public Map<String, Object> getLocalConfig() {
		return localCache;
	}


	public List<ConfigChangeListener> getConfigChangeListeners() {
		return configChangeListeners;
	}

	//当配置改变时，遍历所有configChangeListener，触发onKeyUpdated方法
	public void onConfigUpdated(String key, String value) {
		List<ConfigChangeListener> listeners = getConfigChangeListeners();
		for (ConfigChangeListener listener : listeners) {
			listener.onKeyUpdated(key, value);
		}
	}

	//当有配置添加时，遍历所有configChangeListener，触发onKeyAdded方法
	public void onConfigAdded(String key, String value) {
		List<ConfigChangeListener> listeners = getConfigChangeListeners();
		for (ConfigChangeListener listener : listeners) {
			listener.onKeyAdded(key, value);
		}
	}

	//当有配置去除时，遍历所有configChangeListener，触发onKeyRemoved方法
	public void onConfigRemoved(String key, String value) {
		List<ConfigChangeListener> listeners = getConfigChangeListeners();
		for (ConfigChangeListener listener : listeners) {
			listener.onKeyRemoved(key);
		}
	}

}
