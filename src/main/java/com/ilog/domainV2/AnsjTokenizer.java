package com.ilog.domainV2;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.index.Term;


public class AnsjTokenizer extends Tokenizer{

	private final static String PUNCTION = "。，！？；,!?;";
	public  static final String SPACES = " 　\t\r\n";

	private static Set<String> stopwords = new HashSet<String>();
	final static String stop = "',，！.`-_=?？\'|\"(){}[]<>*#&^$@!！~:;+/《》—－，。、：；·？“”）（【】［］●'";
	private int analysisType ; 
	private boolean removePunc;
	//for sentences split
	private final StringBuilder buffer = new StringBuilder();
	private int tokenStart = 0, tokenEnd = 0;


	private CharTermAttribute termAtt;
	private OffsetAttribute offsetAtt;
	private TypeAttribute typeAtt;
	private PositionIncrementAttribute positionIncrementAtt;
	int lastOffset = 0;
	int endPosition =0; 
	private Iterator<Term> tokenIter;
	private List<Term> tokenBuffer;
	static
	{
		for(String c : stop.split("")){
			stopwords.add(c);
		}
	}
	
	public AnsjTokenizer(Reader input, int analysisType, boolean removePunc) {
		super(input);
		offsetAtt = addAttribute(OffsetAttribute.class);
		termAtt = addAttribute(CharTermAttribute.class);
		typeAtt = addAttribute(TypeAttribute.class);
		positionIncrementAtt = addAttribute(PositionIncrementAttribute.class);
		this.analysisType = analysisType;
		this.removePunc = true;
		
	}

	@Override
	public boolean incrementToken() throws IOException {
		if (tokenIter == null || !tokenIter.hasNext()){
			String currentSentence = checkSentences();
			if (currentSentence!= null){
				tokenBuffer = new ArrayList<Term>();
				if (analysisType == 1){
					Analysis udf = new ToAnalysis(new StringReader(currentSentence));
					Term term = null ;
					while((term=udf.next())!=null){
						if (removePunc && stopwords.contains(term.getName()))
							continue;
						tokenBuffer.add(term);
					}
				}else {
					for(Term term :  IndexAnalysis.parse(currentSentence)){
						if (removePunc && stopwords.contains(term.getName()))
							continue;
						tokenBuffer.add(term);
					}
				}
				tokenIter = tokenBuffer.iterator();
				if (!tokenIter.hasNext()){
					return false;
				}
			} else {
				return false; // no more sentences, end of stream!
			}
		}
		clearAttributes();
		
		Term term = tokenIter.next();
		if (removePunc){
			while(stopwords.contains(term.getName())){
				if (!tokenIter.hasNext()){
				}else{
					term = tokenIter.next();
				}
			}
		}
		termAtt.append(term.getName());
		termAtt.setLength(term.getName().length());
		
		int currentStart = tokenStart + term.getOffe();
		int currentEnd = tokenStart + term.toValue();
		offsetAtt.setOffset(currentStart,currentEnd);
		typeAtt.setType("word");
		positionIncrementAtt.setPositionIncrement(1);

//		int pi = currentStart - lastOffset;
//		if(term.getOffe()  <= 0) {
//			pi = 1;
//		}
//		positionIncrementAtt.setPositionIncrement( pi );
		lastOffset = currentStart;
		endPosition = currentEnd;
		return true;
	}



	private String checkSentences() throws IOException{
		buffer.setLength(0);
		int ci;
		char ch, pch;
		boolean atBegin = true;
		tokenStart = tokenEnd;
		ci = input.read();
		ch = (char) ci;

		while (true) {
			if (ci == -1) {
				break;
			//判断结尾，有待修改 
			/* * } else if (PUNCTION.indexOf(ch) != -1) {
				// End of a sentence
				buffer.append(ch);
				tokenEnd++;
				break;*/
			} else if (atBegin && SPACES.indexOf(ch) != -1) {
				tokenStart++;
				tokenEnd++;
				ci = input.read();
				ch = (char) ci;
			} else {
				buffer.append(ch);
				atBegin = false;
				tokenEnd++;
				pch = ch;
				ci = input.read();
				ch = (char) ci;
				// Two spaces, such as CR, LF
				if (SPACES.indexOf(ch) != -1&& SPACES.indexOf(pch) != -1) {
					// buffer.append(ch);
					tokenEnd++;
					break;
				}
			}
		}
		if (buffer.length() == 0){
			//sentences finished~	
			return null; 
		}else {
			return buffer.toString();
		}

	}

	public void reset() throws IOException {
		super.reset();
		tokenStart = tokenEnd = 0;
	}
	
	public final void end() {
		// set final offset
		int finalOffset = correctOffset(this.endPosition);
		offsetAtt.setOffset(finalOffset, finalOffset);
	}

}
