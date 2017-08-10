package com.ilog.flume.source.tail;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.print.attribute.standard.PDLOverrideSupported;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ilog.util.file.FileUtil;
import com.ilog.util.security.SecurityUtil;


public class FileTailer implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(FileTailer.class);
	private IFileTailerListener listener = null;
	final static Map<String, String> headers = new HashMap<String, String>();//存放flume事件header信息
	private volatile boolean run = true;
	public static final String FLUME_TIMER_KEY="__flume_timer__";
	public static final String FLUME_STOP_TIMER_KEY="__flume_stop_timer__";
	private File tailFile;
//	public File file;// 当前文件
	private RandomAccessFile fReader = null;// 随机读取文件对象
	Pattern pattern;
	// 内存标记信息
	private Map<String, String> readMark = new HashMap<String, String>();
	// 内存数据存放路径
	private String snapShotMarkDirPath = "";
	private String snapShotMarkPath = "";
	//是否为当前角标文件标识flag
	boolean flag = false;
	private ConcurrentHashMap<String, String> prop;
	IBackupDB backupDB = null;
	long clearTimeInterval = 3600000L;

	public FileTailer(IFileTailerListener listener, ConcurrentHashMap<String, String> prop) {
		this.listener = listener;
		this.prop = prop;
		/**
		 * 对n个source实例，配置n个快照文件
		 */
		this.snapShotMarkDirPath = prop.get("backupFileDirPath");
		this.snapShotMarkPath = prop.get("backupFileDirPath")+File.separator+"mark.txt";
		this.backupDB = new MD5File(snapShotMarkDirPath);
		this.clearTimeInterval = Long.parseLong(prop.get("clearTimeInterval"));
		pattern = Pattern.compile(prop.get("regexFileName"));//必须能匹配上实时文件，也能匹配由实时文件产生的新文件
		this.tailFile = new File(prop.get("fileRootDir")+File.separator+prop.get("filePrefix")+prop.get("fileSuffix"));
		logger.info("开始执行：initMarkData()");
		try {
			this.initMarkData();
		} catch (IOException e) {
			logger.error("加载本地快照文件失败!",e);
		}
		logger.info("结束执行：initMarkData()");
	}
	

	public void stop() {
		this.run = false;
	}

	public void run() {
		try {
			if(!readMark.containsKey("lastFile")){//无快照信息，初始化md5File文件
				//初始化比实时文件修改时间小的文件信息到md5File文件,目的：避免以前未读的归档文件会被读
				initArchiveFileToMd5File(tailFile);
			}
		} catch (IOException e1) {
			logger.error(e1.getMessage(), e1);
		}
		long start = System.currentTimeMillis();
		while (this.run) {
			
			//开启清理线程
			long cur = System.currentTimeMillis();
			long timeSpace = cur - start;
			if(timeSpace > clearTimeInterval){
				new MD5FileClearThread(this,(MD5File)backupDB).start();
				start = cur;
			}
			
			//实时采集主题程序
			try{
				Thread.sleep(1000L);// 为了让下一行fReader.readLine()读取不会出现null的情况
			}catch(InterruptedException ex){
				logger.error(ex.getMessage(), ex);
			}
			listener.handle(null, new HashMap<String,String>(){{put(FLUME_TIMER_KEY,null);}});//保证event及时刷新
			try {
				// 判断快照 存不存在，存在走尝试读逻辑，读完后生成下个文件，若不存在，则直接生成下个文件，统一都由快照管理
				if (readMark.containsKey("lastFile")) {// 有快照信息
					logger.info("程序进入读取快照信息---start---");
					String lastFile = readMark.get("lastFile");// 从内存快照中读取文件信息
					logger.info("从内存readMark中读取lastFile是：" + lastFile);
					// logger.info("程序进入尝试读取内存readMark中的lastFile文件逻辑---start---");
					File lastFileInst = new File(lastFile);
					if(lastFileInst.exists() && lastFileInst.canRead()){
						tryReadLastFile(lastFileInst);
					}
					// logger.info("程序进入尝试读取内存readMark中的lastFile文件逻辑---end---");
					String lastFileMaybe = readMark.get("lastFile");
					if (lastFile.equals(lastFileMaybe)) {
						logger.info("程序进入lastFile和lastFileMaybe是同一个文件逻辑，开始生产下一个要读取的文件逻辑---start---");
						generateNextFileinfo(lastFile, prop.get("fileRootDir"));
						logger.info("程序进入lastFile和lastFileMaybe是同一个文件逻辑，开始生产下一个要读取的文件逻辑---end---");
					}/* else {
						// 持久化快照
						logger.info("程序进入lastFile和lastFileMaybe不是同一个文件逻辑，开始持久化内存readMark信息到本地快照文件--start--");
						backupMarkinfoToFile();
						logger.info("程序进入lastFile和lastFileMaybe不是同一个文件逻辑，开始持久化内存readMark信息到本地快照文件--end--");
						continue;
					}*/
					logger.info("程序进入读取快照信息---end---");
				} else {// 无快照信息
					logger.info("程序进入无快照信息start..");
					generateNextFileinfo(null, prop.get("fileRootDir"));
					logger.info("程序进入无快照信息end..");
				}
				// 持久化快照
				logger.info("程序最后将内存readMark信息落地到本地快照文件中--start--");
				backupMarkinfoToFile();
				logger.info("程序最后将内存readMark信息落地到本地快照文件中--end--");
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
		// 持久化快照
		logger.info("采集线程退出，保存快照信息.");
		backupMarkinfoToFile();
		logger.info("保存快照信息成功.");
	}
	
	/**
	 * 初始化比实时文件修改时间小的文件信息到md5File文件
	 * @param file 一定要实时文件
	 * @throws IOException 
	 */
	private synchronized void initArchiveFileToMd5File(File file) throws IOException {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		logger.info("实时文件："+file.getAbsolutePath() + "---修改时间："+sdf.format(file.lastModified()) + "---start---");
//		logger.info("----------------------111---------------------");
		//遍历当前文件目录下的所有文件，满足条件的归档文件信息会被记录到md5file中。
		String[] specifyFileNames = indexFileNamesByLastModifiedAsc();
//		logger.info("----------------------222---------------------");
		logger.info("类:FileTailer,方法:initArchiveFileToMd5File,信息:程序第一次运行，并且无快照信息时，初始化比实时文件修改时间小的归档文件信息到md5File文件");
		for (String specifyFileName : specifyFileNames) {
			File currFile = new File(specifyFileName);
			if(!currFile.getName().equals(file.getName())){//当前文件(currFile)不能是实时文件(file)
				if(currFile.lastModified() < file.lastModified()){
					String curMD5 = SecurityUtil.encoderMD5(FileUtil.fromFile2Str(currFile));
					RandomAccessFile fReader1 = new RandomAccessFile(currFile, "r");
					long curPos = fReader1.length();
					fReader1.close();
					logger.info("归档文件："+currFile.getAbsolutePath() + "---修改时间："+sdf.format(currFile.lastModified()) + "---文件长度：" + curPos);
					updatePosition(currFile.getAbsolutePath(),curMD5, curPos);
				}
			}else{
				logger.info("遍历过程中实时文件："+file.getAbsolutePath() + "---修改时间："+sdf.format(file.lastModified()));
			}
		}
		logger.info("实时文件："+file.getAbsolutePath() + "---修改时间："+sdf.format(file.lastModified()) + "---end---");
	}


	/**
	 * 将内存快照信息落地到本地文件中
	 */
	private synchronized void backupMarkinfoToFile() {
		Object flineMD5 = readMark.get("fLineMD5");
		if (readMark.size() <= 0 || flineMD5 == null || "".equals(flineMD5.toString().trim())){
			logger.info("backupMarkinfoToFile()方法，内存块readMark的size长度<=0,readMark信息是："+readMark.toString());
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (Entry<String, String> en : readMark.entrySet()) {
			sb.append(en.getKey()).append("=").append(en.getValue()).append("||");
		}
		sb.delete(sb.length() - 2, sb.length());
		logger.info("即将要落地到本地文件的内存信息是："+sb.toString());
		boolean writeSuccessFlag = FileUtil.stringToFile(sb.toString(), snapShotMarkPath, "UTF-8");
		logger.info("内存信息成功写入本地快照文件，writeSuccessFlag："+writeSuccessFlag);
	}
	
	
	/**
	 * 尝试读取lastFile信息
	 * @param lastFile
	 * @throws Exception 
	 */
	private void tryReadLastFile(File lastFile) throws Exception {
		//定位该文件，并读取首行、跟快照里面关于lastFile的相关信息比较
		fReader = new RandomAccessFile(lastFile, "r");
		fReader.seek(0L);
		/**
		 * 首行空处理
		 */
		String lineStr = fReader.readLine();
		if(lineStr == null){
			logger.info("程序读到首行为null或者空字符串情况：文件长度为："+fReader.length()+"---文件当前指针位置在："+fReader.getFilePointer());
			fReader.close();//注意关闭流的操作
			return;
		}
		//检查首行是否相同
		String readMark_fLineMD5 = readMark.get("fLineMD5");
		String lineStr_fLineMD5 = SecurityUtil.encoderMD5(new String(lineStr.getBytes("ISO-8859-1"), "UTF-8"));
		//readMark的MD5为空，说明文件采集还未开始，则进行初始化。
		if(readMark_fLineMD5 == null && lineStr_fLineMD5 != null){
			readMark_fLineMD5 = lineStr_fLineMD5;
			readMark.put("fLineMD5", readMark_fLineMD5);
			readMark.put("cPosition", "0");
			updatePosition(lastFile.getAbsolutePath(),readMark_fLineMD5, 0);
		}
		logger.info("当前要定位读取的文件是："+lastFile);
		logger.info("当前要定位读取的文件首行内容是："+new String(lineStr.getBytes("ISO-8859-1"), "UTF-8"));
		logger.info("当前要定位读取的文件首行内容MD5是："+lineStr_fLineMD5);
		boolean checkFirstMD5 = readMark_fLineMD5.equals(lineStr_fLineMD5);
		if(checkFirstMD5){
			logger.info("比上了");
			//比上了
			//开始读这个文件，直至读完，释放文件句柄。
			fReader.seek(Long.parseLong(readMark.get("cPosition")));
			logger.info("定位当前读取的文件位置是："+readMark.get("cPosition"));
			this.readLines();
//			this.readLinesBybuf();
		}else{
			//没比上
			logger.info("没比上");
			logger.info("类:FileTailer,方法:tryReadLastFile,信息:"+lastFile.getAbsolutePath()+"文件的首行和内存块记录的首行MD5值不匹配，进入查找匹配的文件逻辑...");
			//list这个文件夹下的 所有文件，找到内存块里面首行md5和遍历的当前文件首行相同的MD5
			String[] specifyFileNames = indexFileNamesByLastModifiedAsc();
			if(specifyFileNames.length > 0){
				for (String specifyFileName : specifyFileNames) {
					File currFile = new File(specifyFileName);
					RandomAccessFile currFileReader = new RandomAccessFile(currFile, "r");
					/**
					 * 首行空处理
					 */
					String fileLineStr = currFileReader.readLine();
					currFileReader.close();
					if(fileLineStr == null){
						fileLineStr = "";
					}
					//拿remark中的fLineMD5和遍历的当前文件第一行MD5比对
					if(readMark_fLineMD5.equals(SecurityUtil.encoderMD5(new String(fileLineStr.getBytes("ISO-8859-1"), "UTF-8")))){
						logger.info("类:FileTailer,方法:tryReadLastFile,信息:查找到匹配的文件是："+currFile.getAbsolutePath());
						//更新内存块lastFile
						readMark.put("lastFile", currFile.getAbsolutePath());
						readMark.put("cPosition", String.valueOf(getPostion(readMark_fLineMD5)));
						fReader.close();
						return;
					}
				}
			}
			//没有找到MD5对应的文件,则设置实时文件
			backupDB.remove(readMark.get("fLineMD5"));//删除没有找到MD5对应的记录.
			//更新到实时文件
			String newMD5 = SecurityUtil.encoderMD5(FileUtil.fromFile2Str(tailFile));
			readMark.put("fLineMD5",newMD5);
			readMark.put("cPosition", String.valueOf(getPostion(newMD5)));
			readMark.put("lastFile", tailFile.getAbsolutePath());
			
			logger.info("类:FileTailer,方法:tryReadLastFile,信息:not find match file!!!");
			logger.info("类:FileTailer,方法:tryReadLastFile,信息:not find match file, set lastFile is tailFile!!!");
		}
		fReader.close();
	}

	/**
	 * 
	 * @param lastFile  传入文件
	 * @param parentDir	实时文件根路径
	 * @return 
	 * @throws IOException 
	 */
	private boolean generateNextFileinfo(String lastFile, String parentDir) {
		if(lastFile == null || !new File(lastFile).exists()){
			logger.info("程序首次读取发现没有快照文件，lastFile等于null,将实时文件信息更新到内存readMark中");
			if(!tailFile.exists() || !tailFile.canRead()){
				return false;
			}
			//将实时文件信息更新到内存快照中
			String newMD5 = SecurityUtil.encoderMD5(FileUtil.fromFile2Str(tailFile));
			readMark.put("lastFile", tailFile.getAbsolutePath());
			readMark.put("fLineMD5",newMD5);
			readMark.put("cPosition", String.valueOf(getPostion(newMD5)));
			return true;
		}else {
			//遍历文件目录，找到lastFile下一个离现在时间最近的文件
			//1.对文件目录下所有文件按最后修改时间升序排列，返回该目录下所有匹配的文件绝对路径列表（文件已经按照修改时间sort升序）
			String[] specifyFileNames = indexFileNamesByLastModifiedAsc();
			//2.找到比lastFile文件修改时间大的第一个文件，如果没有找到默认为实时文件。如果文件找到，则为找到的文件
			logger.info("找比lastFile文件修改时间大的第一个文件逻辑---start---");
			File file = findNextFile(lastFile, specifyFileNames);
			logger.info("找比lastFile文件修改时间大的第一个文件逻辑---end---");
			/*if (file == null) {
				file = tailFile;
				logger.info("找比lastFile文件修改时间大的第一个文件,发现没有找到，将当前文件置为实时文件，file："+file.getAbsolutePath());
				readMark.put("lastFile", file.getAbsolutePath());
//				readMark.put("fLineMD5", SecurityUtil.encoderMD5(FileUtil.fromFile2Str(file)));//删除这行bug
			}else */
			if(file != null){
				String newMD5 = SecurityUtil.encoderMD5(FileUtil.fromFile2Str(file));
				readMark.put("lastFile", file.getAbsolutePath());
				logger.info("找比lastFile文件修改时间大的第一个文件,发现找到，file："+file.getAbsolutePath());
				readMark.put("fLineMD5",newMD5);
				readMark.put("cPosition", String.valueOf(getPostion(newMD5)));
			}
			return true;
		}
		
	}
	


	/**
	 * 
	 * @param lastFile	对照的文件
	 * @param specifyFileNames
	 * @return 下一个要执行的文件
	 */
	private File findNextFile(String lastFile, String[] specifyFileNames) {
		File currFile = new File(lastFile);
		File nextFile = null;
		if (specifyFileNames == null) {//理论不会走此逻辑，如果走此逻辑，意味着实时文件也不存在，请查看方法：indexFileNamesByLastModifiedAsc
			logger.info("理论不会走此逻辑，如果走此逻辑，意味着实时文件也不存在，下一个要执行的文件nextFile是："+nextFile);
			return nextFile;
		}
		//如果遍历能找到下一个要执行的文件，那么nextFile就是匹配上的下一个文件，如果找不到那么nextFile值为null
		for (String specifyFileName : specifyFileNames) {
			File file = new File(specifyFileName);
			long nextFileLastModified = file.lastModified();//下一个要执行的文件修改时间
			long currFileLastModified = currFile.lastModified();//当前传入的文件修改时间
			if(nextFileLastModified > currFileLastModified){
				logger.info("类:FileTailer,方法:findNextFile,信息:下一个要执行的文件："+file.getAbsolutePath()+"修改时间大于当前传入的文件："+currFile.getAbsolutePath()+"修改时间");
				logger.info("类:FileTailer,方法:findNextFile,信息:下一个要执行的文件修改时间："+nextFileLastModified+"----大于当前传入的文件修改时间："+currFileLastModified);
				return file;
			}
		}
		logger.info("下一个要执行的文件nextFile是："+nextFile);
		return nextFile;
			
	}


	/**
	 * 读取多行逻辑
	 * @throws Exception
	 */
	public void readLines() throws Exception {
		String curMD5 = (String)readMark.get("fLineMD5");
//		if (fReader.length() == 0) {
//			//变化文件为空
////			readMark.put("cLineMD5", SecurityUtil.encoderMD5(new String("".getBytes("ISO-8859-1"), "UTF-8")));
//			readMark.put("cPosition", "0");
//			updatePosition(curMD5,0);
//		}
		long prePos = fReader.getFilePointer();
		String line = null;
		int temp = 1;
		for(line=fReader.readLine();line != null;line=fReader.readLine()){
			if("".equals(line.trim())){
				continue;
			}
			// 即时写入MAP当前行内容
			String value = new String(line.getBytes("ISO-8859-1"), prop.get("charset"));
			/**logger.info("************************************************************************");
			logger.info("当前一条日志的长度："+value.length());
			logger.info("当前一条日志内容："+value);*/
//			readMark.put("cLineMD5", SecurityUtil.encoderMD5(value));
			//logger.info("当前行MD5："+readMark.get("cLineMD5"));
			//logger.info("当前读取的日志文件："+readMark.get("lastFile")+"---读取位置："+readMark.get("cPosition"));
			//将数据推送出去
//				headers = new HashMap<String, String>();
			listener.handle(value.getBytes("UTF-8"), headers);
			if (temp == 1000) {
				Thread.sleep(1000);
				temp = 0;
			}
			temp++;
		}
		long curPos = fReader.getFilePointer();
		if(curPos != prePos){
			readMark.put("cPosition",String.valueOf(curPos));
			updatePosition(readMark.get("lastFile"),curMD5, curPos);
		}
	}
	
	
//	/**
//	 * 读取多字节逻辑
//	 * @throws Exception
//	 */
//	public void readLinesBybuf() throws Exception {
//		StringBuilder sb = new StringBuilder();
//		if (fReader.length() == 0) {
//			//变化文件为空
////			readMark.put("cLineMD5", SecurityUtil.encoderMD5(new String("".getBytes("ISO-8859-1"), "UTF-8")));
//			readMark.put("cPosition", "0");
//		}
//		long pos = Long.parseLong(readMark.get("cPosition"));
//		long rePos = pos;
//		boolean seenCR = false;
//		int num;
//		while (fReader.getFilePointer() < fReader.length()) {
//			if((num = fReader.read(this.inbuf)) != -1){
//				for (int i = 0; i < num; i++) {
//	    	        byte ch = this.inbuf[i];
//	    	        switch (ch) {
//	    	        case 10:
//	    	          seenCR = false;
//	    	          if(!"".equals(sb.toString())){
//	    	        	  // 即时写入MAP当前行内容
//	    	        	  String value = this.handle(sb.toString());
//	    	        	  logger.info("************************************************************************");
//	    				  logger.info("当前一条日志的长度："+value.length());
//	    				  logger.info("当前一条日志内容："+value);
//	    	        	  readMark.put("cLineMD5", SecurityUtil.encoderMD5(value));
//	    	        	  logger.info("当前行MD5："+readMark.get("cLineMD5"));
//	    	        	  sb.setLength(0);
//	    	        	  rePos = pos + i;
//	    	        	  readMark.put("cPosition", String.valueOf(rePos - 1L));
//	    	        	  logger.info("当前读取的日志文件："+readMark.get("lastFile")+"---读取位置："+readMark.get("cPosition"));
//	    	        	  //将数据推送出去
////	    	        	  headers = new HashMap<String, String>();
//	    	        	  event = EventBuilder.withBody(value.getBytes("UTF-8"), headers);
//	    	        	  eventList.add(event);
//	    	        	  fReader.seek(rePos + 1L);
//	    	          }
//	    	          if (eventList.size() == batchUpperLimit) {//批量提交Event
////	    					channelProcessor.processEventBatch(eventList);
//	    					eventList.clear();
//	    			  }
//	    	          break;
//	    	        case 13:
//	    	          seenCR = true;
//	    	          break;
//	    	        default:
//	    	          sb.append((char)ch);
//	    	        }
//	    	      }
//			}
//			pos = Long.parseLong(readMark.get("cPosition")) + 2L;
//		}
//		System.out.println("跳出多个字节while--true循环---读完当前文件："+readMark.get("lastFile")+"-------当前文件长度："+readMark.get("cPosition"));
//	}


	
	/**
	 * 对readLinesBybuf()返回的每一行数据进行处理，解决乱码问题
	 * @param line 接收的一行数据，数据可能有乱码
	 * @return 返回：处理后的一行数据，解决了乱码
	 */
	private String handle(String line) {
		char[] cs = line.toCharArray();
		byte[] cb = new byte[cs.length];
		for (int i = 0; i < cs.length; i++){
			 cb[i] = (byte)cs[i];
		 }
		String resultLine = "";
		try {
			resultLine = new String(cb, prop.get("charset"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return resultLine;
	}


	/**
	 * 对文件目录下所有文件按最后修改时间升序排列，返回改目录下所有角标文件名称列表信息数组（角标文件名称已经按照修改时间sort升序）
	 * @param prop
	 * @return 返回文件目录下的角标文件绝对路径列表
	 */
	public String[] indexFileNamesByLastModifiedAsc() {
//		logger.info("----------------------333---------------------");
//		logger.info("----------------------fileRootDir---------------------"+prop.get("fileRootDir"));
		List<File> listfiles = FileUtil.getFileSort(prop.get("fileRootDir"));//对文件目录下所有文件按最后修改时间排序
//		logger.info("----------------------444---------------------");
		StringBuffer sb = new StringBuffer();
//		logger.info("----------------------555---------------------");
		for (File file : listfiles) {
			Matcher matcher = pattern.matcher(file.getName());
			if(matcher.find()){
				sb.append(file.getAbsolutePath()).append(",");
			}
		}
		logger.info("----------------------666---------------------");
		String value = sb.toString();
		logger.info("----------------------777---------------------");
		String[] specifyFileNames = null;
		logger.info("----------------------888---------------------");
		if ("".equals(value)) {//理论永远不会走此逻辑，因为value至少也是实时文件路径字符串，因此specifyFileNames = null理论不会出现，如果出现，那么意味着实时文件本身就不存在，导致矛盾！！！
			specifyFileNames = null;
			logger.info("----------------------999---------------------");
		}else {
			specifyFileNames = value.substring(0, value.length()-1).split(",");
			logger.info("----------------------101010---------------------");
		}
		return specifyFileNames;
	}


	/**
	 * 程序启动将本地的内存数据读入
	 * @throws IOException 
	 */
	private void initMarkData() throws IOException {
		File backupDir = new File(snapShotMarkDirPath);
		if(!backupDir.exists()){
			backupDir.mkdirs();
		}
		File markFile = new File(snapShotMarkPath);
		if(markFile.exists()){
			String mVal = FileUtil.fileToString(snapShotMarkPath, "UTF-8");
			logger.info("程序启动读取的本地快照文件内容是："+mVal);
			if (StringUtils.isNotBlank(mVal)) {
				String[] mArray = mVal.split("\\|\\|");
				if (mArray == null && mArray.length <= 0)
					return;
				
				for (String str : mArray) {
					int idx = str.indexOf("=");
					readMark.put(str.substring(0, idx), str.substring(idx + 1));
				}
				logger.info("程序初始化时，读取的本地快照文件有内容，数据载入到内存块readMark中的值是："+readMark.toString());
			}
		}else{
			logger.info("创建快照文件");
			markFile.createNewFile();
		}

	}
	
	//注意：main仅供单元测试，flume应用程序不会走此main方法
	public static void main(String[] args) {
		//PropertyConfigurator.configure("E:/flume1.6/ilog/flume-tailer/src/main/resources/log4j.properties");
		IFileTailerListener listener = new IFileTailerListener() {

			@Override
			public void handle(byte[] body, Map<String, String> headers) {
				if(headers == null) return;
				try {
					System.err.println(new String(body, "UTF-8"));
				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		ConcurrentHashMap<String, String> prop = new ConcurrentHashMap<String, String>();
		prop.put("fileRootDir", "D:/tmp");
		// 批量提交Event个数
		prop.put("batchUpperLimit", "1");
		// 获取快照文件存放根目录
		prop.put("backupFileDirPath", "D:/tmp/checkpoint");
		// // 目录文件组合方式 0:文件log4j滚动、1：文件非log4j滚动
		// // 2：日期目录+文件log4j滚动 3：日期目录+文件非log4j滚动
		// prop.put("rollType", context.getString("rollType",""));
		// 日期目录
		// prop.put("dateDir", context.getString("dateDir",""));
		// 文件前缀（实时文件名前缀）
		prop.put("filePrefix", "OAlog");
		// 文件后缀
		prop.put("fileSuffix", ".log");
		// 文件字符集
		prop.put("charset", "UTF-8");
		// 文件字符集
		prop.put("bufferSize", "4096");
		// 设置正则表达式匹配的文件名
		prop.put("regexFileName", "OAlog.log.*");
		
		prop.put("clearTimeInterval","5000");
		new Thread(new FileTailer(listener, prop)).start();
	}
	
	private void updatePosition(String fileName, String md5, long pos){
		backupDB.save(fileName, md5, pos);
	}
	
	private long getPostion(String md5){
		if(md5 == null) return 0;
		
		return Math.max(0, backupDB.getPos(md5));
	}
}
