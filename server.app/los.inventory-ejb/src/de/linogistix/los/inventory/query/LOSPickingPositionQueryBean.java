/*
 * Copyright (c) 2009-2012 LinogistiX GmbH
 * 
 *  www.linogistix.com
 *  
 *  Project myWMS-LOS
 */
package de.linogistix.los.inventory.query;

import java.util.ArrayList;
import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.Query;

import org.mywms.model.Client;

import de.linogistix.los.inventory.model.LOSPickingPosition;
import de.linogistix.los.inventory.query.dto.LOSPickingPositionTO;
import de.linogistix.los.inventory.service.LOSPickingPositionService;
import de.linogistix.los.model.State;
import de.linogistix.los.query.BODTOConstructorProperty;
import de.linogistix.los.query.BusinessObjectQueryBean;
import de.linogistix.los.query.TemplateQueryWhereToken;
import de.linogistix.los.util.businessservice.ContextService;

/**
 * @author krane
 *
 */
@Stateless
public class LOSPickingPositionQueryBean extends BusinessObjectQueryBean<LOSPickingPosition> implements LOSPickingPositionQueryRemote{

	@EJB
	private ContextService ctxService;
	@EJB
	private LOSPickingPositionService pickingPositionService;
	
	
	@Override
	public String getUniqueNameProp() {
		return "pickingOrderNumber";
	}
	
	@Override
	public Class<LOSPickingPositionTO> getBODTOClass() {
		return LOSPickingPositionTO.class;
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
		propList.add(new BODTOConstructorProperty("id", false));
		propList.add(new BODTOConstructorProperty("state", false));
		propList.add(new BODTOConstructorProperty("pickingType", false));
		propList.add(new BODTOConstructorProperty("amount", false));
		propList.add(new BODTOConstructorProperty("amountPicked", false));
		propList.add(new BODTOConstructorProperty("pickFromUnitLoadLabel", false));
		propList.add(new BODTOConstructorProperty("pickFromLocationName", false));
		propList.add(new BODTOConstructorProperty("pickingOrderNumber", false));
		propList.add(new BODTOConstructorProperty("itemData", false));
		propList.add(new BODTOConstructorProperty("client.number", false));
		
		return propList;
	}
	
	@SuppressWarnings("unchecked")
	public List<LOSPickingPosition> getByCustomerOrder(String customerOrderNumber) {
		StringBuffer b = new StringBuffer();
		b.append(" SELECT pos FROM ");
		b.append(LOSPickingPosition.class.getName()).append(" pos ");
		b.append(" JOIN  FETCH pos.customerOrderPosition ");
		b.append(" WHERE pos.customerOrderPosition.order.number=:orderNumber");
		b.append(" ORDER BY pos.customerOrderPosition.index, pos.id ");
		
		Query query = manager.createQuery(new String(b));
		query.setParameter("orderNumber", customerOrderNumber);
		
		return query.getResultList();
//		return pickingPositionService.getByCustomerOrderNumber(customerOrderNumber);
	}

	@SuppressWarnings("unchecked")
	public List<LOSPickingPosition> queryAll( Client client ) {
		
		if( !ctxService.getCallersClient().isSystemClient() ) {
			client = ctxService.getCallersClient();
		}
		
		StringBuffer buffer = new StringBuffer();
		buffer.append("SELECT pos FROM ");
		buffer.append(LOSPickingPosition.class.getSimpleName());
		buffer.append(" pos ");
		if( client != null ) {
			buffer.append("WHERE client=:client");
		}
		Query q = manager.createQuery(new String(buffer));
		if( client != null ) {
			q = q.setParameter("client", client);
		}
		
		return q.getResultList();
	}

	
    @Override
	protected List<TemplateQueryWhereToken> getAutoCompletionTokens(String value) {
		List<TemplateQueryWhereToken> ret =  new ArrayList<TemplateQueryWhereToken>();
		
		Long iLong = null;
		Integer iInteger = null;
		try {
			iLong = Long.valueOf(value);
			iInteger = Integer.valueOf(value);
		}
		catch( Throwable t) {}
		
		TemplateQueryWhereToken token;
		
		token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_LIKE, "pickFromUnitLoadLabel", value);
		token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
		ret.add(token);
		
		token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_LIKE, "pickFromLocationName", value);
		token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
		ret.add(token);
		
		token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_LIKE, "pickingOrderNumber", value);
		token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
		ret.add(token);

		token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_LIKE, "client.number", value);
		token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
		ret.add(token);
		
		token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_LIKE, "itemData.number", value);
		token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
		ret.add(token);
		
		if( iInteger != null ) {
			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_EQUAL, "state", iInteger);
			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
			ret.add(token);
		}
		if( iLong != null ) {
			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_EQUAL, "id", iLong);
			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_OR);
			ret.add(token);
		}
		
		
		return ret;
	}
    
    
    @Override
	protected List<TemplateQueryWhereToken> getFilterTokens(String filterString) {

		List<TemplateQueryWhereToken> ret =  new ArrayList<TemplateQueryWhereToken>();
		TemplateQueryWhereToken token;

		if( "OPEN".equals(filterString) ) {
			token = new TemplateQueryWhereToken(TemplateQueryWhereToken.OPERATOR_SMALLER, "state", State.PICKED);
			token.setLogicalOperator(TemplateQueryWhereToken.OPERATOR_AND);
			token.setParameterName("finishedState");
			ret.add(token);
		}
		
		return ret;
	}
}
