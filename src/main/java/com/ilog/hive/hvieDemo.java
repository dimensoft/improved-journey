package com.ws.hive.demo.testhive;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class hvieDemo {
	public static final String driverName="org.apache.hive.jdbc.HiveDriver";
	public static void main(String[] args) throws Exception{
		Class.forName(driverName);
		Connection con = DriverManager.getConnection("jdbc:hive2://172.16.17.18:10000/default","qwjs","qwjs_szga");
		Statement stat = con.createStatement();
		String sql ="show tables";
		ResultSet rs  = stat.executeQuery(sql);
		while(rs.next()){
			System.out.println(rs.getString(1));
		}
		
	}

}
