package com.dianping.pigeon.console;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;

import com.dianping.pigeon.config.ConfigManager;
import com.dianping.pigeon.extension.ExtensionLoader;

public class Utils {

	private static final List<String> LOCAL_IP_LIST = new ArrayList<String>();
	private static ConfigManager configManager = ExtensionLoader.getExtension(ConfigManager.class);
	private static final String DEFAULT_SIGN = "WQMlgikuPfCFNE8=ZEhN2k8xxJMu";
	private static final String SIGN = configManager.getStringValue("pigeon.console.sign", DEFAULT_SIGN);
   //加入ipv4和ipv6的本地回环地址
	static {
		LOCAL_IP_LIST.add("127.0.0.1");
		LOCAL_IP_LIST.add("0:0:0:0:0:0:0:1");
	}

	//获取发送请求的客户端地址（如果代理服务器不是nginx、squid、apache、weblogic的话，也有可能获取的是代理服务器的地址）
	public static String getIpAddr(HttpServletRequest request) {
		//X-Forwarded-For:简称XFF头，它代表客户端，也就是HTTP的请求端真实的IP，只有在通过了HTTP 代理或者负载均衡服务器时才会添加该项
		String ip = request.getHeader("x-forwarded-for");
		//如果代理服务器使用的不是Nginx、Suid或者是nginx.conf中forwarded_for 设成了 off
		//则查看Proxy-Client-IP或者是WL-Proxy-Client-IP，Proxy-Client-IP一般是经过apache http服务器的请求才会有，用apache http做代理时一般会加上Proxy-Client-IP请求头，而WL- Proxy-Client-IP是他的weblogic插件加上的头
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		//如果都没有，则直接获取地址
		if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		return ip;
	}

	//获取request客户端的ip地址，如果LOCAL_IP_LIST或者是onfigManager.getLocalIp()含有这个地址，
	//并且得到的sign字段和SIGN一致，则返回ture，否则，false
	public static boolean isGranted(HttpServletRequest request) {
		boolean isGranted = false;
		String ip = Utils.getIpAddr(request);
		String sign = request.getParameter("sign");
		isGranted = LOCAL_IP_LIST.contains(ip) || ip.equals(configManager.getLocalIp());
		if (!isGranted && StringUtils.isNotBlank(sign)) {
			isGranted = sign.equals(SIGN);
		}
		return isGranted;
	}
}
