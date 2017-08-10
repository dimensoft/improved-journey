package com.ilog.util.security;

import java.security.MessageDigest;
import org.apache.log4j.Logger;


public class SecurityUtil {

	private final static Logger logger = Logger.getLogger(SecurityUtil.class);

	/**
	 * 加密 MD5 
	 * 
	 * @param strInput
	 *            输入字符串
	 * @return String
	 * @throws Exception
	 */
	public final static String encoderMD5(String strInput) {
		if(strInput == null){
			return null;
		}
		MessageDigest messageDigest = null;
		try {
			messageDigest = MessageDigest.getInstance("MD5");
			messageDigest.reset();
			messageDigest.update(strInput.getBytes("UTF-8"));
		} catch (Exception ex) {
			logger.error("create MD5 error!",ex);
		}

		byte[] byteArray = messageDigest.digest();
		StringBuffer md5StrBuff = new StringBuffer();
		for (byte b : byteArray) {
			if (Integer.toHexString(0xFF & b).length() == 1) {
				md5StrBuff.append("0").append(Integer.toHexString(0xFF & b));
			} else {
				md5StrBuff.append(Integer.toHexString(0xFF & b));
			}
		}
		return md5StrBuff.toString();
	}
	
	/**
	   * 将字符串进行md5运行
	   * @param str
	   * @return
	   * @throws Exception
	   */
	  public static String EncoderByMd5(String str) throws Exception {
	        MessageDigest md5=MessageDigest.getInstance("md5");//返回实现指定摘要算法的 MessageDigest 对象。
	        md5.update(str.getBytes());//先将字符串转换成byte数组，再用byte 数组更新摘要
	        byte[] nStr = md5.digest();//哈希计算，即加密
	        return bytes2Hex(nStr);//加密的结果是byte数组，将byte数组转换成字符串
	   }
	  
	  private static String bytes2Hex(byte[] bts) {
	        String des = "";
	        String tmp = null;

	        for (int i = 0; i < bts.length; i++) {
	            tmp = (Integer.toHexString(bts[i] & 0xFF));
	            if (tmp.length() == 1) {
	                des += "0";
	            }
	            des += tmp;
	        }
	        return des;
	    }
	
	
    
}
