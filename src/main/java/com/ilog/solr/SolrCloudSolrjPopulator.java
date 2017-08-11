package com.ilog.solr;

import java.io.IOException;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.common.SolrInputDocument;
/**
 * ≤Â»Î ˝æ›
 * @author jason
 *
 */
public class SolrCloudSolrjPopulator {
	public static void main(String[] args) throws IOException, SolrServerException {
		String zkHost = "localhost:2181";
		String defaultCollection = "collection1";
		CloudSolrServer server = new CloudSolrServer(zkHost);
		server.setDefaultCollection(defaultCollection);

		for (int i = 0; i < 1000; ++i) {
			SolrInputDocument doc = new SolrInputDocument();
			doc.addField("cat", "book");
			doc.addField("id", "book-" + i);
			doc.addField("name", "The Legend of Po part " + i);
			server.add(doc);
			if (i % 100 == 0)
				server.commit(); // periodically flush
		}
		server.commit();
	}
}