/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package org.dbgl.gui.controls;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;


public abstract class SeparatedValuesDaControlConvertor extends BaseDaControlConvertor {

	protected final char separatorChar_;
			
	public SeparatedValuesDaControlConvertor(char seperatorChar) {
		separatorChar_ = seperatorChar;
	}

	@Override
	public String toConfValue(String existingValue, String[] values) {
		String joinedValues = StringUtils.join(values, separatorChar_);
		if (StringUtils.isBlank(existingValue))
			return joinedValues;

		String[] exVals = toControlValues(existingValue);
		if (exVals.length != values.length) {
			System.err.println("configuration values mismatch: [" + existingValue + "] vs. [" + joinedValues + "]");
			return existingValue;
		}

		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				builder.append(separatorChar_);
			}
			builder.append(StringUtils.isBlank(values[i]) ? exVals[i]: values[i]);
		}
		return builder.toString();
	}

	@Override
	public String toConfValueForDisplay(String[] values) {
		String[] parts = StringUtils.split(toConfValue(null, values), separatorChar_);
		return Stream.of(parts).map(x -> StringUtils.isBlank(x) ? "...": x).collect(Collectors.joining(", "));
	}

	@Override
	public String[] toControlValues(String value) {
		return value == null ? new String[0]: StringUtils.split(value, separatorChar_);
	}
	
	public static SeparatedValuesDaControlConvertor detect(String value) {
		if (StringUtils.contains(value, ' '))
			return SpaceSeparatedDaControlConvertor.getInstance();
		if (StringUtils.contains(value, ','))
			return CommaSeparatedDaControlConvertor.getInstance();
		
		System.err.println("Unexpected value [" + value + "], separator space or comma not found");
		return null;
	}
}