package com.ilog.domainV2;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.IndexAnalysis;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;



public class TestAnsj {
	
	public static void main(String[] args) throws IOException {
		List<Term> parse = ToAnalysis.parse("中华人民 共和国 成立了 ");
		System.out.println(parse);
		List<Term> parse1 = IndexAnalysis.parse("你吃过饭了没有!!!!!吃过无妨论文");
		
	  
		//System.out.println(parse1);
		String text11="ZW321282050000000325";
		
		Tokenizer tokenizer = new AnsjTokenizer(new StringReader(text11), 0, true);
		CharTermAttribute termAtt = tokenizer.addAttribute(CharTermAttribute.class);
		OffsetAttribute offsetAtt = 
				tokenizer.addAttribute(OffsetAttribute.class);
			PositionIncrementAttribute positionIncrementAtt = 
				tokenizer.addAttribute(PositionIncrementAttribute.class);

	    tokenizer.reset();
		while (tokenizer.incrementToken()){

		      System.out.print(new String(termAtt.toString()+" ") );
			//  System.out.print( offsetAtt.startOffset() + "-" + offsetAtt.endOffset() + "-" );
			//System.out.print( positionIncrementAtt.getPositionIncrement() +"/");

		}
		tokenizer.close();
	}
}
