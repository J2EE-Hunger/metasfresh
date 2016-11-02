package de.metas.dlm.migrator.impl;

import java.sql.SQLException;

import org.adempiere.ad.trx.api.ITrx;
import org.adempiere.ad.trx.api.ITrxManager;
import org.adempiere.ad.trx.api.OnTrxMissingPolicy;
import org.adempiere.exceptions.DBException;
import org.adempiere.model.IContextAware;
import org.adempiere.model.PlainContextAware;
import org.adempiere.util.Services;
import org.adempiere.util.lang.ITableRecordReference;
import org.compiere.util.Env;
import org.slf4j.Logger;

import de.metas.dlm.IDLMService;
import de.metas.dlm.Partition;
import de.metas.dlm.migrator.IMigratorService;
import de.metas.dlm.model.IDLMAware;
import de.metas.logging.LogManager;

/*
 * #%L
 * metasfresh-dlm
 * %%
 * Copyright (C) 2016 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class MigratorService implements IMigratorService
{
	private final transient Logger logger = LogManager.getLogger(getClass());

	@Override
	public void testMigratePartition(final Partition partition)
	{
		if (partition.getRecords().isEmpty())
		{
			return;
		}

		final ITrxManager trxManager = Services.get(ITrxManager.class);
		final String localTrxName = trxManager.createTrxName("testMigratePartition", false);

		final ITableRecordReference firstTableRecordReference = partition.getRecords().get(0);
		final PlainContextAware ctxAware = new PlainContextAware(Env.getCtx(), localTrxName);

		final int dlmLevelBkp = firstTableRecordReference.getModel(ctxAware, IDLMAware.class).getDLM_Level();

		ITrx localTrx = null;
		try
		{
			localTrx = trxManager.get(localTrxName, OnTrxMissingPolicy.CreateNew);

			updateDLMLevel0(partition, DLM_Level_TEST, ctxAware);
			localTrx.commit(true);
			logger.info("Update of {} records to DLM_Level={} succeeeded!", partition.getRecords().size(), DLM_Level_TEST);

			updateDLMLevel0(partition, dlmLevelBkp, ctxAware);
			localTrx.commit(true);
		}
		catch (final SQLException e)
		{
			throw DBException.wrapIfNeeded(e);
		}
		finally
		{
			localTrx.close();
		}
		// if we got here without a DLMException, then the partition can be migrated
	}

	@Override
	public Partition migratePartition(final Partition partition)
	{
		final int targetDlmLevel = partition.getTargetDLMLevel();
		return updateDLMLevel0(partition, targetDlmLevel, new PlainContextAware(Env.getCtx(), ITrx.TRXNAME_ThreadInherited));
	}

	private Partition updateDLMLevel0(final Partition partition, final int targetDlmLevel, final IContextAware ctxAware)
	{
		final String columnName = IDLMAware.COLUMNNAME_DLM_Level;

		final IDLMService dlmService = Services.get(IDLMService.class);
		dlmService.directUpdateDLMColumn(ctxAware, partition, columnName, targetDlmLevel);

		return partition.withCurrentDLMLevel(targetDlmLevel);
	}
}
