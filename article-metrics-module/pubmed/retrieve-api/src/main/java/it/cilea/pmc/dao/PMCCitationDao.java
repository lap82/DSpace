package it.cilea.pmc.dao;

import it.cilea.osd.common.dao.GenericDao;
import it.cilea.pmc.model.PMCCitation;

public interface PMCCitationDao extends GenericDao<PMCCitation, Integer>
{

    PMCCitation uniqueCitationByItemID(Integer itemID);

}
