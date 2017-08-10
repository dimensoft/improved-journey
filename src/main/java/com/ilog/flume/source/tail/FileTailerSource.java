package com.ilog.flume.source.tail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.source.AbstractSource;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ilog.util.security.PropertyUtil;


public class FileTailerSource extends AbstractSource implements Configurable, EventDrivenSource,IFileTailerListener {
	private static final Logger log = LoggerFactory.getLogger(FileTailerSource.class);
	FileTailer tailer = null;
	private List<Event> eventList = new ArrayList<Event>();
	private ConcurrentHashMap<String, String> prop = new ConcurrentHashMap<String, String>();//存放配置信息 
	
	private int batchUpperLimit = 1;
	
	private long startTime = System.currentTimeMillis();
	
	public static final long TIME_INTERVAL = 2000;

	public synchronized void start() {
		super.start();
		this.tailer = new FileTailer(this, prop);
		new Thread(this.tailer).start();
	}
	
	public void handle(byte[] body, Map<String,String> headers){
		long cur = System.currentTimeMillis();
		long timeInterval = cur-startTime;
		if(headers != null){
			eventList.add(EventBuilder.withBody(body, headers));
		}
		if (eventList.size()>0 && (eventList.size() == batchUpperLimit || timeInterval>=TIME_INTERVAL)) {//批量提交Event
			getChannelProcessor().processEventBatch(eventList);
			eventList.clear();
			startTime = cur;
		}
	}

	public synchronized void stop() {
		log.info("File Tailer Source is stopping.");
		//确保读取信息写入磁盘文件不丢失
		tailer.stop();
		//实时采集主题程序
		if(log.isInfoEnabled()){
			log.info("file tailer is stopped,wait for bakup marks while sleeping 5s.");
		}
		try{
			Thread.sleep(5000);// 为了让下一行fReader.readLine()读取不会出现null的情况
		}catch(InterruptedException ex){
			log.error("File Tailer Source is intterrupted while sleeping.", ex);
		}
		this.handle(null, new HashMap<String,String>(){{put(FileTailer.FLUME_STOP_TIMER_KEY,null);}});//保证event及时刷新
		super.stop();
		log.info("File Tailer Source is stopped.");
	}

	public void configure(Context context) {
		batchUpperLimit = context.getInteger("batchUpperLimit",1);
		PropertyConfigurator.configure(PropertyUtil.getCurrentConfPath() + "log4j.properties");
		log.info(PropertyUtil.getCurrentConfPath() + "log4j.properties");
		prop.put("fileRootDir", context.getString("fileRootDir",""));
		//批量提交Event个数
		prop.put("batchUpperLimit", context.getString("batchUpperLimit","1"));
		//获取快照文件存放根目录
		prop.put("backupFileDirPath", context.getString("backupFileDirPath", ""));
//		// 目录文件组合方式 0:文件log4j滚动、1：文件非log4j滚动
//		// 2：日期目录+文件log4j滚动 3：日期目录+文件非log4j滚动
//		prop.put("rollType", context.getString("rollType",""));
		// 日期目录
//		prop.put("dateDir", context.getString("dateDir",""));
		// 文件前缀（实时文件名前缀）
		prop.put("filePrefix", context.getString("filePrefix",""));
		// 文件后缀
		prop.put("fileSuffix", context.getString("fileSuffix",""));
		// 文件字符集
		prop.put("charset", context.getString("charset", "UTF-8"));
		// 文件字符集
		prop.put("bufferSize", context.getString("bufferSize", "4096"));
		//设置正则表达式匹配的文件名
		prop.put("regexFileName", context.getString("regexFileName", ".*"));
		
		prop.put("clearTimeInterval", context.getString("clearTimeInterval", "3600000"));
		
	}

}
