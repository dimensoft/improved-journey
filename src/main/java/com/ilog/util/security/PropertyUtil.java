package com.ilog.util.security;


import org.apache.log4j.Logger;


public class PropertyUtil {
	private static Logger logger = Logger.getLogger(PropertyUtil.class);
	
	/**
	 * 获取当前项目配置文件根路径 
	 * @return java.lang.String
	 */

	public static String getCurrentConfPath() {
		String c_path = PropertyUtil.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		String os_name = System.getProperty("os.name").toLowerCase();
		c_path = os_name.startsWith("win") ? c_path.substring(1, c_path.lastIndexOf("/") + 1) : c_path.substring(0, c_path.lastIndexOf("/") + 1);
		return c_path + "../conf/";
	}
}
