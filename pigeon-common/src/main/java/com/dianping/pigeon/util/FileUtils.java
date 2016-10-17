package com.dianping.pigeon.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Map.Entry;
import java.util.Properties;

import com.dianping.pigeon.log.LoggerLoader;
import org.apache.logging.log4j.Logger;

public class FileUtils {

	private static Logger logger = LoggerLoader.getLogger(FileUtils.class);

	//使用utf-8编码从文件中读取属性值，并去除空格
	public static Properties readFile(InputStream is) {
		Properties properties = new Properties();
		BufferedReader br = null;
		if (is != null) {
			try {
				br = new BufferedReader(new InputStreamReader(is, "utf-8"));
				String line = null;
				while ((line = br.readLine()) != null) {
					int idx = line.indexOf("=");
					if (idx != -1) {
						String key = line.substring(0, idx);
						String value = line.substring(idx + 1);
						properties.put(key.trim(), value.trim());
					}
				}
			} catch (Throwable e) {
				logger.error("", e);
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
					}
				}
			}
		}
		return properties;
	}

	//使用utf-8编码将属性和值写入文件
	public static void writeFile(File file, Properties properties) throws IOException {
		if (!file.exists()) {
			File dir = file.getParentFile();
			if (!dir.exists()) {
				dir.mkdirs();
			}
		}
		PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "utf-8"));
		for (Entry<Object, Object> entry : properties.entrySet()) {
			pw.println(entry.getKey() + "=" + entry.getValue());
		}
		pw.close();
	}
}
