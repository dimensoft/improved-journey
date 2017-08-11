package com.ilog.domain;

import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

public class AnsjLuceneTest {

	public static void main(String[] args) {
         test();
	}
    public static  void test(){
    	
		//Lucene Document的域名
			String fieldName = "text";
		String text1 = "刘山峰  男性(1岁汉族  372928196906034134  电话:详细  驾驶人  状态:正常  发证机关:菏泽市公安局车辆管理所  2009/07/20~2015/07/20 ";
			
			//实例化IKAnalyzer分词器
			Analyzer analyzer = new AnsjAnalyzer();
			
			
			Directory directory = null;
			IndexWriter iwriter = null;
			IndexReader ireader = null;
			IndexSearcher isearcher = null;
			try {
				//建立内存索引对象
				directory = new RAMDirectory();	 
				
				//配置IndexWriterConfig
				IndexWriterConfig iwConfig = new IndexWriterConfig(Version.LUCENE_44 , analyzer);
				iwConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
				iwriter = new IndexWriter(directory , iwConfig);
				//写入索引
				Document doc = new Document();
				doc.add(new StringField("ID", "10000", Field.Store.YES));
				doc.add(new TextField(fieldName, text1, Field.Store.YES));
				iwriter.addDocument(doc);
				iwriter.close();
				
				
//				//搜索过程**********************************
//			    //实例化搜索器   
				ireader = DirectoryReader.open(directory);
				isearcher = new IndexSearcher(ireader);			
//				
				String keyword = "山峰";			
				//使用QueryParser查询分析器构造Query对象
				QueryParser qp = new QueryParser(Version.LUCENE_30, fieldName,  new AnsjAnalyzer());
				qp.setDefaultOperator(QueryParser.AND_OPERATOR);
				Query query = qp.parse(keyword);
				System.out.println("Query = " + query);
				//搜索相似度最高的5条记录
				TopDocs topDocs = isearcher.search(query , 5);
				System.out.println("命中：" + topDocs.totalHits);
				//输出结果
				ScoreDoc[] scoreDocs = topDocs.scoreDocs;
				for (int i = 0; i < topDocs.totalHits; i++){
					Document targetDoc = isearcher.doc(scoreDocs[i].doc);
					System.out.println("内容：" + targetDoc.toString());
				}			
				
			} catch (CorruptIndexException e) {
				e.printStackTrace();
			} catch (LockObtainFailedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} 
			catch (ParseException e) {
				e.printStackTrace();
			}
			finally{
				if(ireader != null){
					try {
						ireader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if(directory != null){
					try {
						directory.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
	
//}
		
}
}
