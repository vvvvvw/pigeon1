package com.dianping.pigeon.console.listener;
//发现服务的事件类，包括服务url和重试次数
public class NotifyEvent {

	private String notifyUrl;
	private int retries = 0;

	public String getNotifyUrl() {
		return notifyUrl;
	}

	public void setNotifyUrl(String notifyUrl) {
		this.notifyUrl = notifyUrl;
	}

	public int getRetries() {
		return retries;
	}

	public void setRetries(int retries) {
		this.retries = retries;
	}

}
