package com.ilog.solr;

import java.net.MalformedURLException;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;

public class SolrJSearcher {
	public static void main(String[] args) throws MalformedURLException, SolrServerException {
		HttpSolrServer solr = new HttpSolrServer("http://localhost:8983/solr");
//		  ModifiableSolrParams params = new ModifiableSolrParams(); 
//        params.setQuery("name:Samsung ");
//        params.setStart(0);
//        params.setRows(100);
		SolrQuery params = new SolrQuery();
		params.setQuery("*:*");
		params.setSort("score ",ORDER.desc);
		params.setStart(Integer.getInteger("0"));
		params.setRows(Integer.getInteger("100"));

		QueryResponse response = solr.query(params);
		SolrDocumentList results = response.getResults();
		for (int i = 0; i < results.size(); ++i) {
			System.out.println(results.get(i));
		}
	}
}