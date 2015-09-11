//neoe(c)
package neoe.util;

import java.io.*;
import java.util.*;

/**
 *
 * @author neoe
 */
public class Config {

	/** set config file name before use */
	public static String CONF_FN = "neoe.cfg";// default

	private static Map<String, Long> confTime = new HashMap<String, Long>();
	private static Map<String, Map> confMap = new HashMap<String, Map>();

	public static Object get(String name) throws Exception {
		return get(CONF_FN, name);
	}

	/**
	 * xxx.[2].yyy.[0]
	 */
	public static Object get(String cfgName, String name) throws Exception {
		if (cfgName == null)
			throw new RuntimeException("config file name not set.");
		Map config = getConfig(cfgName);
		if (config == null) {
			config = initInFileSystem(cfgName);
		}
		Long conf1time = getConfTime(cfgName);
		if (conf1time > 0) {
			File f = new File(cfgName);
			if (f.exists()) {
				long time = f.lastModified();
				if (time > conf1time + 200) {
					Log.log("Log file changed:" + f.getAbsolutePath());
					config =initInFileSystem(cfgName);
				}
			}
		}
		Log.log("config="+config);
		return get(config, name);
	}

	private static long getConfTime(String cfgName) {
		Long t = confTime.get(cfgName);
		return t == null ? 0 : t;
	}

	private static Map getConfig(String cfgName) {
		return confMap.get(cfgName);
	}

	public static Object get(Map config, String name) {
		String[] ss = name.split("\\.");
		Object node = config;
		Object o = null;
		for (int i = 0; i < ss.length; i++) {
			String s = ss[i];
			if (s.startsWith("[") && s.endsWith("]")) {
				int p = Integer.parseInt(s.substring(1, s.length() - 1));
				o = ((List) node).get(p);
			} else {
				o = ((Map) node).get(s);
			}
			node = o;
		}
		if (o == null)
			o = "";
		// Log.log("config["+name+"]="+o);
		return o;
	}



	/**
	 * default
	 * 
	 * @param cfgName
	 */
	static synchronized Map initInFileSystem(String cfgName) throws Exception {
		if (cfgName == null)
			throw new RuntimeException("config file name not set.");
		File f1 = new File(cfgName);
		Log.log("load config file:" + f1.getAbsolutePath());
		FileInputStream in;
		Map config = (Map) PyData.parseAll(FileUtil.readString(
				in = new FileInputStream(f1), "UTF8"));
		in.close();
		long conf1time = f1.lastModified();
		confMap.put(cfgName, config);
		confTime.put(cfgName, conf1time);
		Log.log("config:" + config);
		return config;
	}
}
