/*
 * Copyright (c) 2006 - 2010 LinogistiX GmbH
 * 
 *  www.linogistix.com
 *  
 *  Project myWMS-LOS
 */
package de.linogistix.los.inventory.businessservice;

import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.apache.log4j.Logger;
import org.mywms.facade.BasicFacadeBean;
import org.mywms.facade.FacadeException;
import org.mywms.model.Client;
import org.mywms.model.StockUnit;
import org.mywms.model.UnitLoadType;
import org.mywms.service.StockUnitService;

import de.linogistix.los.customization.EntityGenerator;
import de.linogistix.los.inventory.customization.ManageStorageService;
import de.linogistix.los.inventory.exception.InventoryException;
import de.linogistix.los.inventory.exception.InventoryExceptionKey;
import de.linogistix.los.inventory.model.LOSStorageRequest;
import de.linogistix.los.inventory.model.LOSStorageRequestState;
import de.linogistix.los.inventory.model.LOSStorageStrategy;
import de.linogistix.los.inventory.service.InventoryGeneratorService;
import de.linogistix.los.location.businessservice.LOSStorage;
import de.linogistix.los.location.businessservice.LocationReserver;
import de.linogistix.los.location.exception.LOSLocationAlreadyFullException;
import de.linogistix.los.location.exception.LOSLocationException;
import de.linogistix.los.location.exception.LOSLocationNotSuitableException;
import de.linogistix.los.location.exception.LOSLocationReservedException;
import de.linogistix.los.location.exception.LOSLocationWrongClientException;
import de.linogistix.los.location.model.LOSArea;
import de.linogistix.los.location.model.LOSFixedLocationAssignment;
import de.linogistix.los.location.model.LOSStorageLocation;
import de.linogistix.los.location.model.LOSUnitLoad;
import de.linogistix.los.location.model.LOSUnitLoadPackageType;
import de.linogistix.los.location.service.QueryFixedAssignmentService;
import de.linogistix.los.location.service.QueryUnitLoadTypeService;
import de.linogistix.los.query.ClientQueryRemote;

/**
 * 
 * @author trautm
 */
@Stateless
public class StorageBusinessBean extends BasicFacadeBean implements
		StorageBusiness {

	private static final Logger log = Logger
			.getLogger(StorageBusinessBean.class);
	@EJB
	private LOSStorage storageLocService;
	@EJB
	private InventoryGeneratorService genService;
	@EJB
	private ClientQueryRemote clQuery;
	@EJB
	private StockUnitService suService;
	@EJB
	private QueryFixedAssignmentService fixService;
	@EJB
	private LOSInventoryComponent inventoryComponent;
	@EJB
	private QueryUnitLoadTypeService ultService;
	@EJB
	private LocationReserver locationReserver;
	@EJB
	private EntityGenerator entityGenerator;
	@EJB
	private ManageStorageService manageStorageService;
	
	@PersistenceContext(unitName = "myWMS")
	private EntityManager manager;
	
	@EJB
	private LocationFinder locationFinder;
	
	public LOSStorageRequest getOrCreateStorageRequest(Client c, LOSUnitLoad ul) throws FacadeException {
		return getOrCreateStorageRequest(c, ul, false, null, null);
	}
	public LOSStorageRequest getOrCreateStorageRequest(Client c, LOSUnitLoad ul, boolean startProcessing) throws FacadeException {
		return getOrCreateStorageRequest(c, ul, startProcessing, null, null);
	}
	/**
	 * 
	 * The created request contains no destination information yet
	 * 
	 * @param c
	 * @param ul
	 * @param location, if null a location will be searched
	 * @param strategy, if null the default will be used
	 * @return
	 * @throws de.linogistix.los.inventory.exception.InventoryException
	 * @throws org.mywms.service.EntityNotFoundException
	 */
	@SuppressWarnings("unchecked")
	public LOSStorageRequest getOrCreateStorageRequest(Client c, LOSUnitLoad ul, boolean startProcessing, LOSStorageLocation location, LOSStorageStrategy strategy)
			throws FacadeException {
		LOSStorageRequest ret;

		Query query = manager.createQuery("SELECT req FROM "
				+ LOSStorageRequest.class.getSimpleName() + " req "
				+ " WHERE req.unitLoad.labelId=:label "
				+ " AND req.requestState in (:rstate, :processing) ");

		query.setParameter("label", ul.getLabelId());
		query.setParameter("rstate", LOSStorageRequestState.RAW);
		query.setParameter("processing", LOSStorageRequestState.PROCESSING);

		List<LOSStorageRequest> list = query.getResultList();
		if (list == null || list.size() < 1) {
			ret = entityGenerator.generateEntity(LOSStorageRequest.class);
			ret.setUnitLoad(ul);
			ret.setRequestState(startProcessing?LOSStorageRequestState.PROCESSING:LOSStorageRequestState.RAW);
			ret.setNumber(genService.generateStorageRequestNumber(c));
			
			if( location == null ) {
				location = locationFinder.findLocation(ul, null, strategy);
			}
			if( location == null ) {
				throw new InventoryException(
						InventoryExceptionKey.STORAGE_NO_DESTINATION_FOUND,
						new Object[] { ul.getLabelId() });
			}
			ret.setDestination(location);
			locationReserver.allocateLocation(location, ul);
			
			ret.setClient(c);
			manager.persist(ret);
			
			manageStorageService.onStorageRequestStateChange(ret, null);
			
			log.info("CREATED LOSStorageRequest for " + ul.getLabelId());
			return ret;
		} else {
			log.warn("FOUND existing LOSStorageRequest for " + ul.getLabelId());
			return list.get(0);
		}

	}

	@SuppressWarnings("unchecked")
	public LOSStorageRequest getOpenStorageRequest( String unitLoadLabel ) {

		Query query = manager.createQuery("SELECT req FROM "
				+ LOSStorageRequest.class.getSimpleName() + " req "
				+ " WHERE req.unitLoad.labelId=:label "
				+ " AND req.requestState in (:raw,:processing) ");

		query.setParameter("label", unitLoadLabel);
		query.setParameter("raw", LOSStorageRequestState.RAW);
		query.setParameter("processing", LOSStorageRequestState.PROCESSING);

		List<LOSStorageRequest> list = query.getResultList();
		if( list.size() > 0 ) {
			return list.get(0);
		}
		return null;
	}


	public void finishStorageRequest(LOSStorageRequest req,
			LOSStorageLocation sl, boolean force) throws FacadeException,
			LOSLocationException {
		// transfer UnitLoad
		LOSUnitLoad ul;
		LOSStorageLocation assigned;
		boolean partialProcessed = false;
		
		ul = manager.find(LOSUnitLoad.class, req.getUnitLoad().getId());
		sl = manager.find(LOSStorageLocation.class, sl.getId());
		req = manager.find(LOSStorageRequest.class, req.getId());
		
		LOSStorageRequestState stateOld = req.getRequestState();
		
		assigned = manager.find(LOSStorageLocation.class, req.getDestination().getId());
		if (!(sl.equals(assigned))) {
			try {
				locationReserver.checkAllocateLocation(sl, req.getUnitLoad(), force);
			} catch(LOSLocationNotSuitableException ex) {
				throw ex.createRollbackException();
			} catch(LOSLocationWrongClientException ex) {
				throw ex.createRollbackException();
			} catch(LOSLocationReservedException ex) {
				throw ex.createRollbackException();
			} catch(LOSLocationAlreadyFullException ex) {
				throw ex.createRollbackException();
			}
			
				
			// Do not ask questions in front-end.
			// Do not place front-end logic into back-end
			force = true;
			
			if (force){
				// Just to disable locationReservers repair function on empty locations
				req.setRequestState(LOSStorageRequestState.TERMINATED);

				if( !ul.getStorageLocation().equals(sl) ) {
					transferUnitLoad( req, ul, sl );
				}
				locationReserver.deallocateLocation(assigned, ul);
				locationReserver.allocateLocation(sl, ul);
			} else{
				throw new InventoryException(
						InventoryExceptionKey.STORAGE_WRONG_LOCATION_BUT_ALLOWED,
						sl.getName());
			}

			LOSArea area = sl.getArea();
			if( area != null && area.isUseForTransfer() ) {
				// Do not finish the storage order on a transfer location
				req.setRequestState(LOSStorageRequestState.PROCESSING);
				partialProcessed = true;
			} 
			else {
				req.setRequestState(LOSStorageRequestState.TERMINATED);
				req.setDestination(sl);
			}

		} else {
			req.setRequestState(LOSStorageRequestState.TERMINATED);
			if( !ul.getStorageLocation().equals(assigned) ) {
				transferUnitLoad( req, ul, assigned );
			}
		}

		if( stateOld != req.getRequestState() ) {
			manageStorageService.onStorageRequestStateChange(req, stateOld);
		}
		
		if( partialProcessed ) {
			manageStorageService.onStorageRequestPartialProcessed(req);
		}

	}

	private void transferUnitLoad(LOSStorageRequest req, LOSUnitLoad unitload, LOSStorageLocation targetLocation ) throws FacadeException {
		
		LOSFixedLocationAssignment fix = fixService.getByLocation(targetLocation);
		if( fix != null ) {
			for( StockUnit su : unitload.getStockUnitList() ) {
				if( !su.getItemData().equals(fix.getItemData()) ) {
					throw new InventoryException(InventoryExceptionKey.STORAGE_WRONG_LOCATION_NOT_ALLOWED, targetLocation.getName());
				}
			}
			
			if (targetLocation.getUnitLoads() != null && targetLocation.getUnitLoads().size() > 0) {
				// There is already a unit load on the destination. => Add stock
				LOSUnitLoad targetUl = null;
				for( LOSUnitLoad ul : targetLocation.getUnitLoads() ) {
					if( ul.getLabelId().equals(targetLocation.getName() ) ) {
						targetUl = ul;
						break;
					}
				}
				if( targetUl == null ) {
					targetUl = targetLocation.getUnitLoads().get(0);
				}
				
				inventoryComponent.transferStock(unitload, targetUl, req.getNumber(), false);
				storageLocService.sendToNirwana( getCallersUsername(), unitload);
				log.info("Transferred Stock to virtual UnitLoadType: "+unitload.toShortString());
			} else {
				UnitLoadType virtual = ultService.getPickLocationUnitLoadType();
				unitload.setType(virtual);
				unitload.setLabelId(targetLocation.getName());
				storageLocService.transferUnitLoad(getCallersUsername(), targetLocation, unitload, -1, false, false, "", "");

			}
		} 
		else {
			storageLocService.transferUnitLoad(getCallersUsername(), targetLocation, unitload, -1, false, false, "", "");
		}
	}
	
	
	public void finishStorageRequest(LOSStorageRequest req,
			LOSUnitLoad destination) throws FacadeException {
		
		// zuschuetten
		LOSUnitLoad from = manager.find(LOSUnitLoad.class, req.getUnitLoad().getId());
		destination = manager.find(LOSUnitLoad.class, destination.getId()); 
		req = manager.find(LOSStorageRequest.class, req.getId());
		
		LOSStorageRequestState stateOld = req.getRequestState();

		req.setRequestState(LOSStorageRequestState.TERMINATED);

		
		// TODO make choosable from gui
		switch(destination.getPackageType()){
		case OF_SAME_LOT: 
			destination.setPackageType(LOSUnitLoadPackageType.OF_SAME_LOT_CONSOLIDATE);
			manager.flush();
			break;
		case OF_SAME_LOT_CONSOLIDATE:
		case CONTAINER:
			break;
		default:
			throw new InventoryException(InventoryExceptionKey.UNIT_LOAD_CONSTRAINT_VIOLATED, destination.getLabelId());
		}
		
		inventoryComponent.transferStock(from, destination,  req.getNumber(), false);
		
		LOSStorageLocation orig = manager.find(LOSStorageLocation.class, req.getDestination().getId());
		locationReserver.deallocateLocation(orig, from);
		
		// if UnitLoad empty, send to nirwana
		if (req.getUnitLoad().getStockUnitList().isEmpty()) {
			LOSUnitLoad u = manager.find(LOSUnitLoad.class, req.getUnitLoad().getId());
			storageLocService.sendToNirwana(getCallersUsername(), u);
		}
		
		if( stateOld != req.getRequestState() ) {
			manageStorageService.onStorageRequestStateChange(req, stateOld);
		}
		
	}

	// ------------------------------------------------------------------------


	public Client determineClient(LOSUnitLoad ul) {
		Client systemCl;

		systemCl = clQuery.getSystemClient();

		Client ret = systemCl;

		if (!ul.getClient().equals(systemCl)) {
			ret = ul.getClient();
			return ret;
		} else {
			ret = null;
			for (StockUnit u : suService.getListByUnitLoad(ul)) {
				if (ret == null || ret.equals(u.getClient())) {
					ret = u.getClient();
				} else {
					// StockUnits of different clients - take system client
					ret = systemCl;
					return ret;
				}
			}
		}

		if (ret == null) {
			ret = systemCl;
		}

		return ret;
	}


	public void cancelStorageRequest(LOSStorageRequest req) throws FacadeException {

		LOSStorageRequestState stateOld = req.getRequestState();

		req.setRequestState(LOSStorageRequestState.CANCELED);

		if( req.getDestination() != null && req.getUnitLoad() != null ) {
			locationReserver.deallocateLocation(req.getDestination(), req.getUnitLoad());
		}
		else {
			log.warn("cancelStorageRequest: cannot release reservation of location. destination="+req.getDestination()+", unitLoad="+req.getUnitLoad());
		}
		
		if( stateOld != req.getRequestState() ) {
			manageStorageService.onStorageRequestStateChange(req, stateOld);
		}
		
	}

	public void removeStorageRequest(LOSStorageRequest req) throws FacadeException {

		LOSStorageRequestState stateOld = req.getRequestState();

		req.setRequestState(LOSStorageRequestState.CANCELED);

		if( req.getDestination() != null && (stateOld == LOSStorageRequestState.RAW || stateOld == LOSStorageRequestState.PROCESSING) ) {
			locationReserver.deallocateLocation( req.getDestination(), req.getUnitLoad() );
		}
		else {
			log.warn("removeStorageRequest: cannot release reservation of location. destination="+req.getDestination()+", unitLoad="+req.getUnitLoad()+", state="+stateOld);
		}
		
		if( stateOld != req.getRequestState() ) {
			manageStorageService.onStorageRequestStateChange(req, stateOld);
		}
		
		manager.remove(req);
	}

}
