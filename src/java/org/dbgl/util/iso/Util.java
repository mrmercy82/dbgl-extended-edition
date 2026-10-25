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
package org.dbgl.util.iso;

import java.io.IOException;
import java.io.UnsupportedEncodingException;


public class Util {

	private Util() {
	}

	/**
	 * Gets an 8-bit unsigned integer from the given byte array at the given offset.
	 *
	 * @param src
	 * @param offset
	 * @return
	 */
	public static int getUInt8(byte[] src, int offset) {
		return src[offset] & 0xFF;
	}

	/**
	 * Gets a 16-bit unsigned integer from the given byte array at the given offset.
	 *
	 * @param src
	 * @param offset
	 * @return
	 */
	public static int getUInt16(byte[] src, int offset) {
		int v0 = src[offset + 0] & 0xFF;
		int v1 = src[offset + 1] & 0xFF;
		return ((v1 << 8) | v0);
	}

	/**
	 * Gets a 32-bit unsigned integer from the given byte array at the given offset.
	 *
	 * @param src
	 * @param offset
	 * @return
	 */
	public static long getUInt32(byte[] src, int offset) {
		long v0 = src[offset + 0] & 0xFF;
		long v1 = src[offset + 1] & 0xFF;
		long v2 = src[offset + 2] & 0xFF;
		long v3 = src[offset + 3] & 0xFF;
		return ((v3 << 24) | (v2 << 16) | (v1 << 8) | v0);
	}

	/**
	 * Gets a string of d-characters. See section 7.4.1.
	 *
	 * @param block
	 * @param pos
	 * @param length
	 * @return
	 */
	public static String getDChars(byte[] block, int pos, int length) {
		return new String(block, pos - 1, length).trim();
	}

	/**
	 * Gets a string of d-characters. See section 7.4.1.
	 *
	 * @param block
	 * @param pos
	 * @param length
	 * @param encoding
	 * @return
	 */
	public static String getDChars(byte[] block, int pos, int length, String encoding) throws IOException {
		try {
			return new String(block, pos - 1, length, encoding).trim();
		} catch (UnsupportedEncodingException ex) {
			throw new IOException(ex);
		}
	}
}