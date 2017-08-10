package com.ilog.flume.source.tail;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

public class MD5File implements IBackupDB{
	public static final String SEPERATOR = " ";
	public static final String POS_SUFFIX = ".pos"; 
	public static final String TMP_SUFFIX = POS_SUFFIX + ".tmp";
	private File root;
	public MD5File(String backupPath){
		root = new File(new File(backupPath),"md5file");
		if(!root.exists()){
			root.mkdirs();
		}
	}
	public void save(String fileName, String md5, long pos){
		String tmpFileName = getMD5TempFileName(md5);
		File tmp = new File(root, tmpFileName);
		File md5File = new File(root,getMD5FileName(md5));
		FileWriter fw = null;
		try {
			fw = new FileWriter(tmp);
			fw.write(pos + SEPERATOR + fileName);
			fw.close();
			fw = null;
			md5File.delete();
			tmp.renameTo(md5File);
			tmp.delete();
		} catch (IOException e) {
			throw new BackupDBException(e);
		}finally{
			if(fw != null){
				try {
					fw.close();
				} catch (IOException e) {
					throw new BackupDBException(e);
				}
			}
		}

	}

	public long getPos(String md5){
		File md5file = new File(root,getMD5FileName(md5));
		if(!md5file.exists()){
			return -1;
		}
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(md5file.getPath())));
			String posStr = br.readLine();
			if(posStr != null){
				return Long.parseLong(posStr.substring(0,posStr.indexOf(SEPERATOR)).trim());
			}else{
				return -1;
			}
		}catch (IOException e) {
			throw new BackupDBException(e);
		}finally{
			if(br != null){
				try {
					br.close();
				} catch (IOException e) {
					throw new BackupDBException(e);
				}
			}
		}
	}

	public void remove(String md5)  throws BackupDBException{
		File md5file = new File(root,getMD5FileName(md5));
		md5file.deleteOnExit();
	}
	
	private String getMD5FileName(String md5){
		return md5 + POS_SUFFIX;
	}
	
	private String getMD5TempFileName(String md5){
		return "._" + md5 + TMP_SUFFIX;
	}
	
	public File getRootDir(){
		return this.root;
	}
}
