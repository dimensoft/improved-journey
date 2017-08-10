package com.ilog.flume.source.tail;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ilog.util.file.FileUtil;
import com.ilog.util.security.SecurityUtil;

public class MD5FileClearThread extends Thread{
	private FileTailer fileTailer = null;
	private MD5File backupDB = null;
	
	Logger logger = LoggerFactory.getLogger(MD5FileClearThread.class);
	
	public MD5FileClearThread(FileTailer fileTailer,MD5File backupDB){
		this.fileTailer = fileTailer;
		this.backupDB = backupDB;
	}
	public void run(){
		logger.info("开启MD5file清理线程.....");
		int clearSize = 0;
		try{
			//获取日志首行的MD5
			Map<String,String> logFileMD5 = new HashMap<String,String>();
			String[] logFilePaths = fileTailer.indexFileNamesByLastModifiedAsc();
			if(logFilePaths == null) return ;
			
			for(String logFilePath : logFilePaths){
				String newMD5 = SecurityUtil.encoderMD5(FileUtil.fromFile2Str(new File(logFilePath)));
				if(newMD5 != null){
					logFileMD5.put(newMD5,logFilePath);
				}else{
					//为了保证MD5file的安全，只要获取不到MD5值，就不执行清理。 
					return;
				}
			}
			
			//获取MD5文件名
			File rootDir = backupDB.getRootDir();
			String[] md5FileNames = rootDir.list(new FilenameFilter(){
				public boolean accept(File dir, String name) {
					return name.endsWith(backupDB.POS_SUFFIX);
				}
			});
			
			//如果MD5文件名在首行MD5 Map中不存在，说明对应的文件已经被删除，则同步删除MD5文件。
			for(String md5FileName : md5FileNames){
				String md5FileKey = md5FileName.substring(0,md5FileName.lastIndexOf(backupDB.POS_SUFFIX));
				if(!logFileMD5.containsKey(md5FileKey)){
					File md5File = new File(rootDir,md5FileName);
					if(md5File.exists() && md5File.canRead()){
						md5File.delete();
						clearSize++;
						logger.info("清理MD5file:"+md5File.getAbsolutePath());
					}
				}
			}
		}catch(Throwable t){
			logger.error("清理线程失败",t);
		}
		
		logger.info("MD5file清理线程结束.共清理了" + clearSize + "个文件");
		
	}
}
