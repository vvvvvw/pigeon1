package com.dianping.pigeon.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public class LangUtils {


	public static String toString(float value, int maxFactionDigits) {
		NumberFormat nf = DecimalFormat.getInstance();
		nf.setMaximumFractionDigits(maxFactionDigits);
		return nf.format(new BigDecimal(value).setScale(maxFactionDigits, BigDecimal.ROUND_HALF_UP)); ////向“最接近的”数字舍入，如果与两个相邻数字的距离相等，则为向上舍入的舍入模式
	}

	//获取字符串的hash值，用来在com.dianping.pigeon.remoting.provider.config.ServerConfig中计算端口号，但是这个算法看得不是很懂
	public static int hash(String str, int mid, int range) {
		int hash, i;
		for (hash = str.length(), i = 0; i < str.length(); ++i) {
			hash = (hash << 4) ^ (hash >> 28) ^ str.charAt(i);
		}
		return mid + (hash % range);
	}

	//返回t的字符串
	public static String getFullStackTrace(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw, true);
		t.printStackTrace(pw);
		try {
			String str = sw.getBuffer().toString();
			return str;
		} finally {
			pw.close();
		}
	}
}
