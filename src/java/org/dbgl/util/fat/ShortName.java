/*
 * Copyright (C) 2009,2010 Matthias Treydte <mt@waldheinz.de>
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.dbgl.util.fat;

import java.util.Arrays;
import org.dbgl.util.iso.Util;


/**
 * Represents a "short" (8.3) file name as used by DOS.
 *
 * @author Matthias Treydte &lt;waldheinz at gmail.com&gt;
 */
final class ShortName {

	private final char[] name_;

	public ShortName(byte[] data) {
		char[] nameArr = new char[8];

		for (int i = 0; i < nameArr.length; i++) {
			nameArr[i] = (char)Util.getUInt8(data, i);
		}

		if (Util.getUInt8(data, 0) == 0x05) {
			nameArr[0] = (char)0xe5;
		}

		char[] extArr = new char[3];
		for (int i = 0; i < extArr.length; i++) {
			extArr[i] = (char)Util.getUInt8(data, 0x08 + i);
		}
		String name = new String(nameArr).trim();
		String ext = new String(extArr).trim();
		checkString(name, "name", 1, 8);
		checkString(ext, "extension", 0, 3);

		char[] result = new char[11];
		Arrays.fill(result, ' ');
		System.arraycopy(name.toCharArray(), 0, result, 0, name.length());
		System.arraycopy(ext.toCharArray(), 0, result, 8, ext.length());

		name_ = result;
	}

	private static void checkString(String str, String strType, int minLength, int maxLength) {
		if (str == null)
			throw new IllegalArgumentException(strType + " is null");
		if (str.length() < minLength)
			throw new IllegalArgumentException(strType + " must have at least " + minLength + " characters: " + str);
		if (str.length() > maxLength)
			throw new IllegalArgumentException(strType + " has more than " + maxLength + " characters: " + str);
	}

	public String asSimpleString() {
		return new String(name_).trim();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [" + asSimpleString() + "]"; // NOI18N
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ShortName)) {
			return false;
		}

		return Arrays.equals(name_, ((ShortName)obj).name_);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(name_);
	}
}
