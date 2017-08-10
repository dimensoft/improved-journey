package com.ilog.flume.source.tail;

import java.util.Map;

public interface IFileTailerListener {
	void handle(byte[] body, Map<String,String> headers); 
}
