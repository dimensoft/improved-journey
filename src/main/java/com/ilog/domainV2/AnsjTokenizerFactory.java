package com.ilog.domainV2;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.util.TokenizerFactory;
import org.apache.lucene.util.AttributeFactory;


public class  AnsjTokenizerFactory extends TokenizerFactory {

	private int analysisType = 0;
	private boolean rmPunc = true;
	
	
	
	public AnsjTokenizerFactory(Map<String, String> args) {
		super(args);
		assureMatchVersion();
		analysisType = getInt(args, "analysisType", 0);
		rmPunc = getBoolean(args, "rmPunc", true);
	}


		return new AnsjTokenizer(input, analysisType, rmPunc);
	}

	
	
}
