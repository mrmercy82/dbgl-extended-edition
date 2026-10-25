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
package exodos;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.interfaces.PreProgressNotifyable;


class DotProgress implements PreProgressNotifyable {

	private static final int A_DOT_ONCE_PER_X_TICKS = 100;
	
	int counter_;

	public DotProgress(String title) {
		System.out.print(StringUtils.LF + title + " ");
	}

	@Override
	public void setTotal(long total) {
		// not used
	}

	@Override
	public void incrProgress(long progress) {
		if (counter_++ % A_DOT_ONCE_PER_X_TICKS == 0)
			System.out.print('.');
	}

	@Override
	public void setProgress(long progress) {
		// not used
	}

	@Override
	public void setPreProgress(long preProgress) {
		// not used
	}
}
