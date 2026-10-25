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
package org.dbgl.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.TextService;


public class NativeCommand {

	private final FileLocation command_;
	private final String parameters_;
	private final FileLocation cwd_;
	private final boolean waitFor_;
	private final int orderNr_;

	public NativeCommand(String command, String parameters, String cwd, boolean waitFor, int orderNr) {
		command_ = command == null ? null: new FileLocation(command, FileLocationService.getInstance().dataRelative());
		parameters_ = parameters;
		cwd_ = cwd == null ? null: new FileLocation(cwd, FileLocationService.getInstance().dataRelative());
		waitFor_ = waitFor;
		orderNr_ = orderNr;
	}

	public File getCommand() {
		return command_.getFile();
	}

	public File getCanonicalCommand() {
		return command_.getCanonicalFile();
	}

	public boolean isDosboxCommand() {
		return command_ == null;
	}

	public String getParameters() {
		return parameters_;
	}

	public File getCwd() {
		return cwd_.getFile();
	}

	public File getCanonicalCwd() {
		return cwd_.getCanonicalFile();
	}

	public boolean isWaitFor() {
		return waitFor_;
	}

	public int getOrderNr() {
		return orderNr_;
	}

	public List<String> getCanonicalCommandAndParameters() {
		List<String> execCommands = new ArrayList<>();
		execCommands.add(getCanonicalCommand().getPath());
		if (parameters_.length() > 0) {
			Collections.addAll(execCommands, StringUtils.split(parameters_, ' '));
		}
		return execCommands;
	}

	@Override
	public String toString() {
		if (isDosboxCommand())
			return "-- DOSBox --";
		StringBuilder s = new StringBuilder(command_.getFile().getPath());
		if (StringUtils.isNotEmpty(parameters_))
			s.append(' ').append(parameters_);
		if (!cwd_.getFile().getPath().equals(command_.getFile().getParent()))
			s.append(", ").append(cwd_.getFile().getPath());
		if (waitFor_)
			s.append(", ").append(TextService.getInstance().get("dialog.nativecommand.waitfor"));
		return s.toString();
	}

	public static void insertDosboxCommand(List<NativeCommand> nativeCommandsList) {
		int dosboxNr = 0;
		for (int i = 0; i < nativeCommandsList.size(); i++) {
			if (nativeCommandsList.get(i).getOrderNr() == i)
				dosboxNr++;
		}
		nativeCommandsList.add(dosboxNr, new NativeCommand(null, null, null, true, dosboxNr));
	}
}
