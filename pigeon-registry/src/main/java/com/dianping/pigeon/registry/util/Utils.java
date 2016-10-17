/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.registry.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

import com.dianping.pigeon.domain.HostInfo;

public final class Utils {

	private static final Logger logger = LoggerLoader.getLogger(Utils.class);

	//传入8个字节，返回一个long型数字
	public static long getLong(final byte[] b) {

		assert b.length == 8 : "Invalid number of bytes for long conversion";
		int high = getInt(new byte[] { b[0], b[1], b[2], b[3] });
		int low = getInt(new byte[] { b[4], b[5], b[6], b[7] });
		return ((long) (high) << 32) + (low & 0xFFFFFFFFL);
	}

	//传入4个字节，返回一个int型数字
	public static int getInt(final byte[] b) {

		assert b.length == 4 : "Invalid number of bytes for integer conversion";
		return ((b[0] << 24) & 0xFF000000) + ((b[1] << 16) & 0x00FF0000) + ((b[2] << 8) & 0x0000FF00)
				+ (b[3] & 0x000000FF);
	}

	//验证weight是否有效(在(0,100)之间)
	public static void validateWeight(String host, int port, int weight) {
		if (weight < Constants.MIN_WEIGHT || weight > Constants.MAX_WEIGHT)
			throw new IllegalArgumentException("weight should be in range [" + Constants.MIN_WEIGHT + "-"
					+ Constants.MAX_WEIGHT + "]:" + host + ":" + port + "-" + weight);
	}

	//将字符串中的^全部转为/
	public static String unescapeServiceName(String serviceName) {
		return serviceName.replace(Constants.PLACEHOLDER, Constants.PATH_SEPARATOR);
	}
	//将字符串中的/全部转为^
	public static String escapeServiceName(String serviceName) {
		return serviceName.replace(Constants.PATH_SEPARATOR, Constants.PLACEHOLDER);
	}

	//将host字符串和weight解析为HostInfo返回
	public static HostInfo parseHost(String host, int weight) {
		int idx = host.lastIndexOf(":");
		if (idx != -1) {
			String ip = null;
			int port = -1;
			try {
				ip = host.substring(0, idx);
				port = Integer.parseInt(host.substring(idx + 1));
			} catch (RuntimeException e) {
				logger.warn("invalid host: " + host + ", ignored!");
			}
			if (ip != null && port > 0) {
				return new HostInfo(ip, port, weight);
			}
		} else {
			logger.warn("invalid host: " + host + ", ignored!");
		}
		return null;
	}

	//将以，分隔的serviceAddress中有效的地址存入List返回
	public static List<String> getAddressList(String serviceName, String serviceAddress) {
		if (StringUtils.isBlank(serviceAddress)) {
			return Collections.EMPTY_LIST;
		}
		String[] addressArray = serviceAddress.split(",");
		List<String> addressList = new ArrayList<String>();
		for (int i = 0; i < addressArray.length; i++) {
			if (StringUtils.isNotBlank(addressArray[i])) {
				// addressList.add(addressArray[i]);
				String address = addressArray[i];
				int idx = address.lastIndexOf(":");
				if (idx != -1) {
					String host = null;
					int port = -1;
					try {
						host = address.substring(0, idx);
						port = Integer.parseInt(address.substring(idx + 1));
					} catch (RuntimeException e) {
						logger.warn("invalid address:" + address + " for service:" + serviceName);
					}
					if (host != null && port > 0) {
						addressList.add(address);
					}
				}
			}
		}
		return addressList;
	}
}
