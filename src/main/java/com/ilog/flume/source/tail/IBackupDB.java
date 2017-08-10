package com.ilog.flume.source.tail;

public interface IBackupDB {
	void save(String fileName, String md5,long pos);
	long getPos(String md5);
	void remove(String md5); 
}
