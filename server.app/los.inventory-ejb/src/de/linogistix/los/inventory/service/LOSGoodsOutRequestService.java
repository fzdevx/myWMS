/*
 * Copyright (c) 2006 - 2012 LinogistiX GmbH
 * 
 *  www.linogistix.com
 *  
 *  Project myWMS-LOS
 */
package de.linogistix.los.inventory.service;

import java.util.List;

import javax.ejb.Local;

import org.mywms.model.Client;
import org.mywms.model.UnitLoad;
import org.mywms.service.BasicService;

import de.linogistix.los.inventory.model.LOSCustomerOrder;
import de.linogistix.los.inventory.model.LOSGoodsOutRequest;

@Local
public interface LOSGoodsOutRequestService extends
		BasicService<LOSGoodsOutRequest> {

	public List<LOSGoodsOutRequest> getByCustomerOrder(LOSCustomerOrder order);

	public LOSGoodsOutRequest getByNumber(Client client, String number);
	
	public LOSGoodsOutRequest getByNumber(String number);
	
	public List<LOSGoodsOutRequest> getByUnitLoad(UnitLoad ul);

}
