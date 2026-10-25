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

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.interfaces.PreProgressNotifyable;


class AsciiProgressBar implements PreProgressNotifyable {

	private static final int TITLE_MAX_WIDTH = 68;
	private static final int TITLE_AREA_WIDTH = 70;
	private static final int PROGR_AREA_WIDTH = 34;
	
	
	final String title_;
	final long total_;
	long preProgress_, progress_;

	public AsciiProgressBar(String title, long total) {
		title_ = title;
		total_ = total;
		out(calc());
	}

	@Override
	public void setTotal(long total) {
		// not used
	}
	
	@Override
	public void setProgress(long progress) {
		// not used
	}

	@Override
	public void incrProgress(long progress) {
		int[] before = calc();
		progress_ += progress;
		preProgress_ = 0L;
		int[] after = calc();
		if (!Arrays.equals(before, after))
			out(after);
	}

	@Override
	public void setPreProgress(long preProgress) {
		int[] before = calc();
		preProgress_ = preProgress;
		int[] after = calc();
		if (!Arrays.equals(before, after))
			out(after);
	}
	
	private int[] calc() {
		int progBlocks = (int)(((float)progress_ / (float)total_) * (float)PROGR_AREA_WIDTH);
		int preBlocks = (int)(((float)preProgress_ / (float)total_) * (float)PROGR_AREA_WIDTH);
		return new int[] { progBlocks, preBlocks };
	}

	private void out(int[] values) {
		System.out.print("\r");
		System.out.print(StringUtils.rightPad(StringUtils.abbreviate(title_, TITLE_MAX_WIDTH), TITLE_AREA_WIDTH, '.'));
		System.out.print(" ");
		System.out.print(StringUtils.repeat('\u2593', values[0]));
		System.out.print(StringUtils.repeat('\u2592', values[1]));
		System.out.print(StringUtils.repeat('\u2591', PROGR_AREA_WIDTH - values[0] - values[1]));
	}
}
