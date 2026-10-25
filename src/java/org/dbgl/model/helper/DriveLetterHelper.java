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
package org.dbgl.model.helper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.dbgl.exception.DrivelettersExhaustedException;


public class DriveLetterHelper {

	private DriveLetterHelper() {
	}

	public static char getFirstAvailable(boolean floppy, Set<Character> usedDrives) throws DrivelettersExhaustedException {
		List<Character> freeDriveletters = new ArrayList<>();
		char start = floppy ? 'A': 'C';
		for (char i = start; i < 'Z'; i++) {
			freeDriveletters.add(i);
		}
		if (!floppy) {
			freeDriveletters.add('A');
			freeDriveletters.add('B');
		}
		for (Character c: usedDrives) {
			freeDriveletters.remove(c);
		}
		if (freeDriveletters.isEmpty()) {
			throw new DrivelettersExhaustedException();
		}
		return freeDriveletters.get(0);
	}

}
