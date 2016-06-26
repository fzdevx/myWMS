/*
 * Copyright (c) 2009-2012 LinogistiX GmbH
 * 
 *  www.linogistix.com
 *  
 *  Project myWMS-LOS
 */
package de.linogistix.los.inventory.service;

import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.NoResultException;
import javax.persistence.Query;

import org.apache.log4j.Logger;
import org.mywms.facade.FacadeException;
import org.mywms.service.BasicServiceBean;

import de.linogistix.los.customization.EntityGenerator;
import de.linogistix.los.inventory.model.LOSCustomerOrder;
import de.linogistix.los.inventory.model.LOSPickingOrder;
import de.linogistix.los.inventory.model.LOSPickingUnitLoad;
import de.linogistix.los.location.model.LOSUnitLoad;

/**
 * @author krane
 *
 */
@Stateless
public class LOSPickingUnitLoadServiceBean extends BasicServiceBean<LOSPickingUnitLoad> implements LOSPickingUnitLoadService{
	Logger log = Logger.getLogger(LOSPickingUnitLoadServiceBean.class);

	@EJB
	private EntityGenerator entityGenerator;
	@EJB
	private LOSCustomerOrderService customerOrderService;
	
	public LOSPickingUnitLoad create(LOSPickingOrder pickingOrder, LOSUnitLoad unitLoad, int index) throws FacadeException {
		
		LOSPickingUnitLoad pickingUnitLoad = entityGenerator.generateEntity(LOSPickingUnitLoad.class);

		pickingUnitLoad.setClient(pickingOrder.getClient());
		pickingUnitLoad.setPickingOrder(pickingOrder);
		pickingUnitLoad.setPositionIndex(index);
		pickingUnitLoad.setUnitLoad(unitLoad);
		
		manager.persist(pickingUnitLoad);
		
		return pickingUnitLoad;
	}

	public LOSPickingUnitLoad getByLabel(String label) {
		Query query = manager.createNamedQuery("LOSPickingUnitLoad.queryByLabel");
		query.setParameter("label", label);
		
		try {
			return (LOSPickingUnitLoad)query.getSingleResult();
		}
		catch (NoResultException ex) {}
		return null;
	}
	
	public LOSPickingUnitLoad getByUnitLoad(LOSUnitLoad unitLoad) {
		Query query = manager.createNamedQuery("LOSPickingUnitLoad.queryByUnitLoad");
		query.setParameter("unitLoad", unitLoad);
		
		try {
			return (LOSPickingUnitLoad)query.getSingleResult();
		}
		catch (NoResultException ex) {}
		return null;
	}

	@SuppressWarnings("unchecked")
	public List<LOSPickingUnitLoad> getByPickingOrder(LOSPickingOrder pickingOrder) {
		Query query = manager.createNamedQuery("LOSPickingUnitLoad.queryByPickingOrder");
		query.setParameter("pickingOrder", pickingOrder);
		
		return query.getResultList();
	}
	
	@SuppressWarnings("unchecked")
	public List<LOSPickingUnitLoad> getByCustomerOrderNumber(String customerOrderNumber) {
		Query query = manager.createNamedQuery("LOSPickingUnitLoad.queryByCustomerOrderNumber");
		query.setParameter("customerOrderNumber", customerOrderNumber);

		return query.getResultList();
	}
	
	@SuppressWarnings("unchecked")
	public List<LOSPickingUnitLoad> getByCustomerOrder(LOSCustomerOrder customerOrder) {
		Query query = manager.createNamedQuery("LOSPickingUnitLoad.queryByCustomerOrderNumber");
		query.setParameter("customerOrderNumber", customerOrder.getNumber());

		return query.getResultList();
	}
	public LOSCustomerOrder getCustomerOrder(LOSPickingUnitLoad pickingUnitLoad) {
		if( pickingUnitLoad == null || pickingUnitLoad.getCustomerOrderNumber() == null || pickingUnitLoad.getCustomerOrderNumber().equals("-") ) {
			return null;
		}
		
		return customerOrderService.getByNumber(pickingUnitLoad.getCustomerOrderNumber());
	}

}
