package de.metas.dlm.partitioner.process;

import org.adempiere.util.Services;
import org.compiere.process.SvrProcess;
import org.compiere.util.TrxRunnableAdapter;

import de.metas.connection.IConnectionCustomizerService;
import de.metas.dlm.model.I_DLM_Partition_Config;
import de.metas.dlm.partitioner.IPartitionerService;
import de.metas.dlm.partitioner.PartitionRequestFactory;
import de.metas.dlm.partitioner.PartitionRequestFactory.CreatePartitionRequest;
import de.metas.dlm.partitioner.PartitionRequestFactory.CreatePartitionRequest.OnNotDLMTable;
import de.metas.dlm.partitioner.config.PartitionerConfig;
import de.metas.process.Param;

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

/**
 * Invokes {@link IPartitionerService#createPartition(CreatePartitionRequestDELME)} for a specified number of times.
 *
 * @author metas-dev <dev@metasfresh.com>
 *
 */
public class DLM_Partition_Create extends SvrProcess
{

	private final IPartitionerService partitionerService = Services.get(IPartitionerService.class);

	@Param(mandatory = true, parameterName = I_DLM_Partition_Config.COLUMNNAME_DLM_Partition_Config_ID)
	private I_DLM_Partition_Config configDB;

	@Param(mandatory = true, parameterName = "Count")
	private int count;

	@Param(mandatory = true, parameterName = "DLMOldestFirst")
	private boolean oldestFirst;

	@Param(mandatory = true, parameterName = "OnNotDLMTable")
	private String onNotDLMTable;

	@Override
	protected String doIt() throws Exception
	{
		final PartitionerConfig config = partitionerService.loadPartitionConfig(configDB);

		final CreatePartitionRequest request = PartitionRequestFactory.builder()
				.setConfig(config)
				.setOldestFirst(oldestFirst)
				.setOnNotDLMTable(OnNotDLMTable.valueOf(onNotDLMTable))
				.build();

		final IConnectionCustomizerService connectionCustomizerService = Services.get(IConnectionCustomizerService.class);

		try (final AutoCloseable temporaryCustomizer = connectionCustomizerService.registerTemporaryCustomizer(partitionerService.createConnectionCustomizer()))
		{
			for (int i = 0; i < count; i++)
			{
				trxManager.run(new TrxRunnableAdapter()
				{
					@Override
					public void run(final String localTrxName) throws Exception
					{
						partitionerService.createPartition(request);
						// addLog("@Created@ " + ...); this kind of logging is done in the service methods
					}
				});
			}
		}

		return MSG_OK;
	}

}
