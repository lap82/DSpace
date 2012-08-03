/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.discovery;

import org.apache.solr.common.SolrInputDocument;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;


/**
 * Example class that prints out the handle of the DSpace Object currently being indexed
 *
 * @author Kevin Van de Velde (kevin at atmire dot com)
 */
public class SolrServiceIndexOutputPlugin implements SolrServiceIndexPlugin{

    @Override
    public void additionalIndex(DSpaceObject dso, SolrInputDocument document) {
        System.out.println("Currently indexing: " + dso.getHandle());
        if(dso.getType()==Constants.ITEM) {
        	Item item = (Item)dso;
        	document.addField("withdrawn", item.isWithdrawn());
        	document.addField("inarchive", item.isArchived());
        }
    }
}
