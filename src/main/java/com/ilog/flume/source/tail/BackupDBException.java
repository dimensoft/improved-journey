package com.ilog.flume.source.tail;

public class BackupDBException extends RuntimeException{
	public BackupDBException(Exception e){
		super(e); 
	}
	
	public BackupDBException(String msg, Exception e){
		super(msg, e);
	}
	
	public BackupDBException(){
		super();
	}
	
	public BackupDBException(String msg){
		super(msg);
	}
}
