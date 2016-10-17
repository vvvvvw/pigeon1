package com.dianping.pigeon.util;

import java.util.Random;

public class RandomUtils {

	//返回长度为lenght的随机字符串
	public static String newRandomString(int length) {
		String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		Random random = new Random();
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < length; i++) {
			int num = random.nextInt(62);
			sb.append(str.charAt(num));
		}

		return sb.toString();
	}
}
