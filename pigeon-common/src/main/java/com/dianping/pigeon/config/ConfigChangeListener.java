package com.dianping.pigeon.config;

//接口，当key变化时，会触发其中的方法
public interface ConfigChangeListener {

	void onKeyUpdated(String key, String value);

	void onKeyAdded(String key, String value);

	void onKeyRemoved(String key);

}
