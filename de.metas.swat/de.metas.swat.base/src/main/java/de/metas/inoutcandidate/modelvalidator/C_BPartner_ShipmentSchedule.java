package de.metas.inoutcandidate.modelvalidator;

/*
 * #%L
 * de.metas.swat.base
 * %%
 * Copyright (C) 2015 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */


import java.math.BigDecimal;
import java.util.Iterator;

import org.adempiere.ad.modelvalidator.annotations.ModelChange;
import org.adempiere.ad.modelvalidator.annotations.Validator;
import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Services;
import org.apache.commons.collections4.IteratorUtils;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.ModelValidator;

import de.metas.bpartner.IBPartnerBL;
import de.metas.bpartner.IBPartnerDAO;
import de.metas.bpartner.model.BPartner;
import de.metas.inoutcandidate.api.IShipmentScheduleInvalidateBL;
import de.metas.inoutcandidate.api.IShipmentSchedulePA;
import de.metas.inoutcandidate.model.I_M_ShipmentSchedule;

@Validator(I_C_BPartner.class)
public class C_BPartner_ShipmentSchedule
{
	/**
	 * 
	 * 
	 * @param bPartner
	 */
	@ModelChange(
			timings = {
					ModelValidator.TYPE_AFTER_NEW,
					ModelValidator.TYPE_AFTER_CHANGE },
			ifColumnsChanged = {
					I_C_BPartner.COLUMNNAME_AllowConsolidateInOut,
					I_C_BPartner.COLUMNNAME_PostageFreeAmt })
	public void inValidateScheds(final I_C_BPartner bpartnerData)
	{
		final BPartner bpartner = Services.get(IBPartnerDAO.class).toBPartnerAgg(bpartnerData);
		
		//
		// Services
		final IShipmentSchedulePA shipmentSchedulePA = Services.get(IShipmentSchedulePA.class);
		final IBPartnerBL bpartnerBL = Services.get(IBPartnerBL.class);
		

		// using iterator because there might be a lot of scheds to update
		final Iterator<? extends I_M_ShipmentSchedule> scheds = shipmentSchedulePA.retrieveForBPartner(bpartner.getBPartnerData());

		for (final I_M_ShipmentSchedule sched : IteratorUtils.asIterable(scheds))
		{
			// sched.getPostageFreeAmt() would return BigDecimal.ZERO, if the
			// value is not set, but we want to get null in that case.
			final BigDecimal schedPostageFree = sched.getPostageFreeAmt();

			final boolean postageFreeChg = schedPostageFree.compareTo(bpartner.getPostageFreeAmt()) != 0;

			final boolean isSOTrx = true;
			final boolean isBPAllowConsolidateInOut = bpartnerBL.isAllowConsolidateInOutEffective(bpartner, isSOTrx);
			final boolean allowConsChg = isBPAllowConsolidateInOut != sched.isAllowConsolidateInOut();

			if (postageFreeChg || allowConsChg)
			{
				sched.setPostageFreeAmt(bpartner.getPostageFreeAmt());
				sched.setAllowConsolidateInOut(isBPAllowConsolidateInOut);

				InterfaceWrapperHelper.save(sched, ITrx.TRXNAME_ThreadInherited);
			}
			// if postageFree is unchanged and still zero, then this sched entry
			// is still valid, even if allow consolidate has changed.
			final boolean stillValid = !postageFreeChg && (bpartner.getPostageFreeAmt().signum() <= 0 || !allowConsChg);

			if (!stillValid)
			{
				// note that we do not need to invalidate the current sched explicitly..it will be updated as part of the segment, unless it has delivery rule force..and if it has that rule, then the
				// bpartner change makes no difference to it, anyways.
				Services.get(IShipmentScheduleInvalidateBL.class).invalidateSegmentForShipmentSchedule(sched);
			}
		}
	}
}
