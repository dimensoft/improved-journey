package com.ilog.impala;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author huzhengyuan
 */

/// <summary>
/// Summary description for Tokeniser.
/// Partition string into SUBwords
/// </summary>
public class Tokeniser implements ITokeniser {

	// / <summary>
	// / 以空白字符进行简单分词，并忽略大小写，
	// / 实际情况中可以用其它中文分词算法
	// / </summary>
	// / <param name="input"></param>
	// / <returns></returns>

	public Tokeniser() {
	}

	public List<String> partition(String input) {

		String r = "([ \\t{}():;. \n])";
		input = input.toLowerCase();
		Pattern p = Pattern.compile(r);

		String[] tokens = input.split(r);

		List<String> filter = new ArrayList<String>();

		for (int i = 0; i < tokens.length; i++) {
			//System.out.println(tokens[i]);
			//Matcher m = p.matcher(tokens[i]);
			//if (m.groupCount() == 0 && tokens[i].trim().length() > 0
			String token=tokens[i];
			System.out.println(token);
			if (token.trim().length() > 0&& !StopWordsHandler.IsStopword(token)) {
				System.out.println(token);
				filter.add(token);
			}

			/*
			 * MatchCollection mc=r.matches(tokens[i]); if (mc.Count <= 0 &&
			 * tokens[i].Trim().Length > 0 && !StopWordsHandler.IsStopword
			 * (tokens[i]) ) filter.add(tokens[i]) ; }
			 */

		}
		return filter;
	}
}
