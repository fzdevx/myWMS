/*
 * LOSUnitLoadQueryBean.java
 *
 * Created on 14. September 2006, 06:53
 *
 * Copyright (c) 2006-2012 LinogistiX GmbH. All rights reserved.
 *
 *<a href="http://www.linogistix.com/">browse for licence information</a>
 *
 */

package de.linogistix.los.location.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ejb.Stateless;
import javax.persistence.Query;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.mywms.model.StockUnit;

import de.linogistix.los.location.constants.LOSUnitLoadLockState;
import de.linogistix.los.location.model.LOSStorageLocation;
import de.linogistix.los.location.model.LOSUnitLoad;
import de.linogistix.los.location.query.dto.UnitLoadTO;
import de.linogistix.los.query.BODTOConstructorProperty;
import de.linogistix.los.query.BusinessObjectQueryBean;
import de.linogistix.los.query.TemplateQueryWhereToken;

/**
 *
 * @author <a href="http://community.mywms.de/developer.jsp">Andreas Trautmann</a>
 */
@Stateless
public class LOSUnitLoadQueryBean 
        extends BusinessObjectQueryBean<LOSUnitLoad> 
        implements LOSUnitLoadQueryRemote
{
	private static final Logger log = Logger.getLogger(LOSUnitLoadQueryBean.class);

	@Override
	public String getUniqueNameProp() {
		return "labelId";
	}

	@Override
	protected String[] getBODTOConstructorProps() {
		return new String[]{};
	}

	@Override
	protected List<BODTOConstructorProperty> getBODTOConstructorProperties() {
		List<BODTOConstructorProperty> propList = super.getBODTOConstructorProperties();
		
		propList.add(new BODTOConstructorProperty("id", false));
		propList.add(new BODTOConstructorProperty("version", false));
		propList.add(new BODTOConstructorProperty("labelId", false));
		propList.add(new BODTOConstructorProperty("client.number", false));
		propList.add(new BODTOConstructorProperty("lock", false));
		propList.add(new BODTOConstructorProperty("storageLocation.name", false));
		propList.add(new BODTOConstructorProperty("carrier", false));
		propList.add(new BODTOConstructorProperty("type.name", false));
		
		return propList;
	}

	@Override
	public Class<UnitLoadTO> getBODTOClass() {
		return UnitLoadTO.class;
	}

	
    @Override
	protected List<TemplateQueryWhereToken> getAutoCompletionTokens(String value) {
    	
		Integer iValue = null;
		try {
			iValue = Integer.valueOf(value);
		}
		catch( Throwable t) {}
    	
    	
		List<TemplateQueryWhereToken> ret =  new ArrayList<TemplateQueryWhereToken>();

		TemplateQueryWhereToken token;

		token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_LIKE, "client.number", value);
		token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
		ret.add(token);

		token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_LIKE, "storageLocation.name", value);
		token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
		ret.add(token);
		
		token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_LIKE, "labelId", value);
		token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
		ret.add(token);
		
		if( iValue != null ) {
			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_EQUAL, "lock", iValue);
			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
			ret.add(token);
		}
		
		return ret;
	}
    
    @Override
  	protected List<TemplateQueryWhereToken> getFilterTokens(String filterString) {

  		List<TemplateQueryWhereToken> ret =  new ArrayList<TemplateQueryWhereToken>();
  		TemplateQueryWhereToken token;

  		if( "AVAILABLE".equals(filterString) ) {
  			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_EQUAL, "lock", 0);
  			token.setParameterName("availableLock");
  			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_AND);
  			ret.add(token);
  		}
  		if( "CARRIER".equals(filterString) ) {
  			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_TRUE, "carrier", 0);
  			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_AND);
  			ret.add(token);
  			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_NOT_EQUAL, "lock", 9);
  			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_AND);
  			ret.add(token);
  		}
  		if( "EMPTY".equals(filterString) ) {
  			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_MANUAL, 
  					"NOT EXISTS(FROM " + StockUnit.class.getSimpleName() + " WHERE unitLoad = ul)");
  			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_AND);
  			ret.add(token);
  			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_FALSE, "carrier", 0);
  			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_AND);
  			ret.add(token);
  			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_NOT_EQUAL, "lock", 9);
  			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_AND);
  			ret.add(token);
  		}
		if( "OUT".equals(filterString) ) {
			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_EQUAL, "lock", 100); // StockUnitLockState.PICKED_FOR_GOODSOUT.getLock()
  			token.setParameterName("outLock1");
			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
			ret.add(token);
			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_EQUAL, "lock", LOSUnitLoadLockState.SHIPPED.getLock());
  			token.setParameterName("outLock2");
			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
			ret.add(token);
		}


  		return ret;
  	}
    
  	@Override
  	public long countUnitLoadsByStorageLocation(LOSStorageLocation sl) {
  		Query query = manager.createNamedQuery("LOSUnitLoad.countByLocation");
  		query.setParameter("location", sl);
      Long count = (Long)query.getSingleResult();
      return (count == null) ? 0 : count;
  	}

    @SuppressWarnings("unchecked")
   	@Override
  	public List<LOSUnitLoad> getListByStorageLocation(LOSStorageLocation sl) {
      	
    		if (sl.getType().getId() == 1) {
    			//we log this as an error so we get stack trace.
    			if (sl.getId() == 0 || sl.getId() == 1 || "Goods-Out".equals(sl.getName())) {
    				log.error("Fetching unit loads for system location " + sl.getName() + " is dangerous and cause a OutOfMemoryError\n" + ExceptionUtils.getStackTrace(new RuntimeException()));    			
    				return Collections.emptyList();
    			}
    			else {  				
    				log.warn("Fetching unit loads for system location " + sl.getName());    			
    			}
    		}

  		Query query = manager.createNamedQuery("LOSUnitLoad.queryByLocation");
  		query.setParameter("location", sl);
                  
          return (List<LOSUnitLoad>)query.getResultList();
      }


}
