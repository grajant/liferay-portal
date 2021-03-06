/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.dynamic.data.mapping.internal.upgrade.v2_0_1;

import com.liferay.dynamic.data.mapping.io.DDMFormJSONDeserializer;
import com.liferay.dynamic.data.mapping.io.DDMFormJSONSerializer;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.kernel.util.Validator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.Map;
import java.util.Objects;

/**
 * @author Lino Alves
 */
public class UpgradeAutocompleteDDMTextFieldSetting extends UpgradeProcess {

	public UpgradeAutocompleteDDMTextFieldSetting(
		DDMFormJSONDeserializer ddmFormJSONDeserializer,
		DDMFormJSONSerializer ddmFormJSONSerializer) {

		_ddmFormJSONDeserializer = ddmFormJSONDeserializer;
		_ddmFormJSONSerializer = ddmFormJSONSerializer;
	}

	@Override
	protected void doUpgrade() throws Exception {
		StringBundler sb = new StringBundler(4);

		sb.append("select DDMStructure.structureId, DDMStructure.definition ");
		sb.append("from DDMFormInstance inner join DDMStructure on ");
		sb.append("DDMFormInstance.structureId = DDMStructure.structureId ");
		sb.append("where DDMStructure.definition like '%\"type\":\"text\"%'");

		try (PreparedStatement ps1 = connection.prepareStatement(sb.toString());
			PreparedStatement ps2 =
				AutoBatchPreparedStatementUtil.concurrentAutoBatch(
					connection,
					"update DDMStructure set definition = ? where " +
						"structureId = ?")) {

			try (ResultSet rs = ps1.executeQuery()) {
				while (rs.next()) {
					long structureId = rs.getLong(1);
					String definition = rs.getString(2);

					String newDefinition = upgradeDDMFormInstanceStructure(
						definition);

					if (Objects.equals(definition, newDefinition)) {
						continue;
					}

					ps2.setString(1, newDefinition);

					ps2.setLong(2, structureId);

					ps2.addBatch();
				}

				ps2.executeBatch();
			}
		}
	}

	protected boolean isAutocompleteEnabled(Map<String, Object> properties) {
		String dataSourceType = (String)properties.get("dataSourceType");

		if (Validator.isNotNull(dataSourceType)) {
			return true;
		}

		return false;
	}

	protected String upgradeDDMFormInstanceStructure(String definition)
		throws Exception {

		DDMForm ddmForm = _ddmFormJSONDeserializer.deserialize(definition);

		for (DDMFormField ddmFormField : ddmForm.getDDMFormFields()) {
			if (Objects.equals(ddmFormField.getType(), "text")) {
				Map<String, Object> properties = ddmFormField.getProperties();

				if (!properties.containsKey("autocomplete")) {
					properties.put(
						"autocomplete", isAutocompleteEnabled(properties));
				}
			}
		}

		return _ddmFormJSONSerializer.serialize(ddmForm);
	}

	private final DDMFormJSONDeserializer _ddmFormJSONDeserializer;
	private final DDMFormJSONSerializer _ddmFormJSONSerializer;

}