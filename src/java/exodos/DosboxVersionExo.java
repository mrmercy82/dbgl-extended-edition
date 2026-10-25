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

public class DosboxVersionExo {

	String title_;
	String family_;
	String version_;
	String executable_;
	String conf_;
	boolean multiconf_;
	
	public DosboxVersionExo(String title, String family, String version, String executable, String conf, boolean multiconf) {
		title_ = title;
		family_ = family;
		version_ = version;
		executable_ = executable;
		conf_ = conf;
		multiconf_ = multiconf;
	}
}
