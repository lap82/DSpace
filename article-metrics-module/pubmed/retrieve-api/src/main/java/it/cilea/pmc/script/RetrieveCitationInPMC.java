package it.cilea.pmc.script;

import it.cilea.pmc.model.PMCCitation;
import it.cilea.pmc.model.PMCRecord;
import it.cilea.pmc.services.PMCEntrezException;
import it.cilea.pmc.services.PMCEntrezLocalSOLRServices;
import it.cilea.pmc.services.PMCEntrezServices;
import it.cilea.pmc.services.PMCPersistenceService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.LogManager;
import org.dspace.discovery.SearchService;
import org.dspace.discovery.SearchServiceException;
import org.dspace.kernel.ServiceManager;
import org.dspace.utils.DSpace;
import org.springframework.util.StringUtils;

public class RetrieveCitationInPMC
{
    /** log4j logger */
    private static Logger log = Logger.getLogger(RetrieveCitationInPMC.class);

    private static PMCPersistenceService pservice;

    private static PMCEntrezServices entrez = new PMCEntrezServices();

    private static PMCEntrezLocalSOLRServices entrezLocal = new PMCEntrezLocalSOLRServices();

    private static SearchService searcher;

    private static long timeElapsed = 3600000 * 24 * 7; // 1 week

    private static long maxItemToWork = 100;

    public static void main(String[] args) throws SearchServiceException,
            SQLException, AuthorizeException, ParseException
    {
        CommandLineParser parser = new PosixParser();

        Options options = new Options();
        options.addOption("h", "help", false, "help");

        options.addOption(
                "t",
                "time",
                true,
                "Limit to update only citation more old than <t> seconds. Use 0 to force update of all record");

        options.addOption(
                "x",
                "max",
                true,
                "Process a max of <x> items. Only worked items matter, item not worked because up-to-date (see t option) are not counted. Use 0 to set no limits");

        CommandLine line = parser.parse(options, args);

        if (line.hasOption('h'))
        {
            HelpFormatter myhelp = new HelpFormatter();
            myhelp.printHelp("RetrieveCitationInPMC \n", options);
            System.out
                    .println("\n\nUSAGE:\n RetrieveCitationInPMC [-t 3600] [-x 100] \n");
            System.exit(0);
        }

        DSpace dspace = new DSpace();
        int citationRetrieved = 0;
        int itemWorked = 0;
        Date startDate = new Date();

        if (line.hasOption('t'))
        {
            timeElapsed = Long.valueOf(line.getOptionValue('t').trim()) * 1000; // option
                                                                                // is
                                                                                // in
                                                                                // seconds
        }
        if (line.hasOption('x'))
        {
            maxItemToWork = Long.valueOf(line.getOptionValue('x').trim());
        }
        ServiceManager serviceManager = dspace.getServiceManager();
//        System.out.println(serviceManager.getServicesNames());
        searcher = serviceManager.getServiceByName(
                SearchService.class.getName(), SearchService.class);

        pservice = serviceManager.getServiceByName(
                PMCPersistenceService.class.getName(),
                PMCPersistenceService.class);
        SolrQuery query = new SolrQuery();
        query.setQuery("+dc.identifier.pmid:[* TO *] +inarchive:true");
        query.setRows(Integer.MAX_VALUE);
        query.setFields("search.resourceid", "dc.identifier.doi",
                "dc.identifier.pmid");
        QueryResponse qresp = searcher.search(query);
        SolrDocumentList list = qresp.getResults();
        log.info(LogManager.getHeader(null, "retrieve_citation", "Processing "
                + list.getNumFound() + " items"));
        for (SolrDocument doc : list)
        {
            if (maxItemToWork != 0 && itemWorked == maxItemToWork)
                break;

            Integer itemID = (Integer) doc.getFieldValue("search.resourceid");
            if (isCheckRequired(itemID))
            {
                itemWorked++;
                Collection<Object> pmids = (Collection<Object>) doc
                        .getFieldValues("dc.identifier.pmid");

                for (Object pmid : pmids)
                {
                    log.debug(LogManager.getHeader(null, "retrieve_citation",
                            "lookup pmid:" + pmid));
                    try
                    {
                        Integer ipmid = Integer.valueOf((String) pmid);
                        Set<Integer> citingPMCIDs = entrez
                                .getCitedByPMEDID(ipmid);
                        log.debug(LogManager.getHeader(null,
                                "retrieve_citation",
                                "found " + citingPMCIDs.size()
                                        + " citing PMC records"));
                        // if (citingPMCIDs != null && citingPMCIDs.size() > 0)
                        // {
                        citationRetrieved += citingPMCIDs.size();
                        updatePMCCiting(itemID, ipmid, citingPMCIDs);
                        // }
                    }
                    catch (NumberFormatException nfe)
                    {
                        log.error(LogManager.getHeader(null,
                                "retrieve_citation",
                                "Found an invalid PID value! ItemID: " + itemID
                                        + " - PMID: " + pmid));
                    }
                    catch (PMCEntrezException pe)
                    {
                        log.error(LogManager.getHeader(null,
                                "retrieve_citation", "Error in EntrezService"),
                                pe);
                    }
                }
            }
        }
        Date endDate = new Date();
        long processTime = (endDate.getTime() - startDate.getTime()) / 1000;
        log.info(LogManager.getHeader(null, "retrieve_citation",
                "Processing time " + processTime + " sec. - Retrieved "
                        + citationRetrieved + " PMC citation for " + itemWorked
                        + " items"));
    }

    private static void updatePMCCiting(Integer itemID, Integer pmid,
            Set<Integer> pmcIDs) throws SearchServiceException
    {
        Integer[] arrPMCIDs = new Integer[pmcIDs.size()];
        arrPMCIDs = pmcIDs.toArray(arrPMCIDs);
        List<PMCRecord> pmcRecords = null;
        try
        {
            pmcRecords = entrez.getMultiPMCRecord(arrPMCIDs);
            for (PMCRecord pmcRecord : pmcRecords)
            {
                List<Integer> pubmedIDs = entrezLocal.getPubmedIDs(pmcRecord
                        .getId());
                if (pubmedIDs != null && pubmedIDs.size() > 0)
                {
                    pmcRecord.setPubmedIDs(pubmedIDs);
                    SolrQuery query = new SolrQuery();
                    query.setQuery("dc.identifier.pmid:("
                            + StringUtils.collectionToDelimitedString(pubmedIDs,
                                    " OR ") + ")");
                    query.setFields("handle");
                    QueryResponse qresp = searcher.search(query);
                    List<String> handles = new ArrayList<String>();
                    for (SolrDocument doc : qresp.getResults())
                    {
                        handles.add((String) doc
                                .getFieldValue("handle"));
                    }
                    pmcRecord.setHandles(handles);
                }
                pservice.saveOrUpdate(PMCRecord.class, pmcRecord);
            }
        }
        catch (PMCEntrezException e)
        {
            log.error(LogManager.getHeader(null, "updatePMCRecord",
                    "Error in EntrezService"), e);
        }
        PMCCitation citation = new PMCCitation();
        citation.setId(pmid);
        citation.setPmcRecords(pmcRecords);
        citation.setNumCitations(pmcIDs.size());

        SolrQuery query = new SolrQuery();
        query.setQuery("dc.identifier.pmid:" + pmid);
        query.setFields("search.resourceid");
        QueryResponse qresp = searcher.search(query);
        List<Integer> itemIDs = new ArrayList<Integer>();
        for (SolrDocument doc : qresp.getResults())
        {
            itemIDs.add((Integer) doc.getFieldValue("search.resourceid"));
        }
        citation.setItemIDs(itemIDs);
        pservice.saveOrUpdate(PMCCitation.class, citation);
    }

    private static boolean isCheckRequired(Integer itemID)
    {
        if (timeElapsed != 0)
        {
            PMCCitation cit = pservice.getCitationByItemID(itemID);
            if (cit == null)
            {
                return true;
            }
            long now = new Date().getTime();

            Date lastCheck = cit.getTimeStampInfo().getLastModificationTime();
            long lastCheckTime = 0;

            if (lastCheck != null)
                lastCheckTime = lastCheck.getTime();

            return (now - lastCheckTime >= timeElapsed);
        }
        else
        {
            return true;
        }
    }
}
