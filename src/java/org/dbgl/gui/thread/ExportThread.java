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
package org.dbgl.gui.thread;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.apache.commons.io.FileUtils;
import org.dbgl.model.GamePack;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.GamePackEntry;
import org.dbgl.service.ImportExportProfilesService;
import org.dbgl.util.StringRelatedUtils;
import org.dbgl.util.archive.ZipUtils;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Text;


public class ExportThread extends UIThread<GamePackEntry> {

	private GamePack gamePack_;
	private ZipOutputStream zipOutputStream_;

	public ExportThread(Text log, ProgressBar progressBar, Label status, GamePack gamePack, File target) throws IOException, ParserConfigurationException, TransformerException {
		super(log, progressBar, status, false);

		gamePack_ = gamePack;

		long bytes = 0;
		for (GamePackEntry entry: gamePack.getEntries()) {
			if (gamePack_.isCapturesAvailable())
				bytes += FileUtils.sizeOfDirectory(entry.getProfile().getCanonicalCaptures());
			if (gamePack_.isMapperfilesAvailable() && entry.getProfile().getCustomMapperFile() != null)
				bytes += entry.getProfile().getCustomMapperFile().length();
			if (gamePack_.isGamedataAvailable() && gamePack.getEntries().stream().noneMatch(x -> entry != x && entry.getGameDir().equals(x.getGameDir()) && (entry.getId() > x.getId())))
				bytes += FileUtils.sizeOfDirectory(entry.getCanonicalGameDir());
			bytes++;
		}

		setObjects(gamePack.getEntries());
		setTotal(bytes);

		zipOutputStream_ = new ZipOutputStream(new FileOutputStream(target));

		ImportExportProfilesService.export(gamePack, zipOutputStream_);
	}

	@Override
	public String work(GamePackEntry entry) throws IOException {
		displayTitle(text_.get("dialog.export.exporting", new Object[] {getTitle(entry)}));

		Profile prof = entry.getProfile();

		if (gamePack_.isCapturesAvailable()) {
			try {
				ZipUtils.zipDir(zipOutputStream_, prof.getCanonicalCaptures(), entry.getArchiveCapturesDir(), prof.getCanonicalCaptures(), this);
			} catch (IOException e) {
				throw new IOException(text_.get("dialog.export.error.exportcaptures", new Object[] {prof.getTitle(), StringRelatedUtils.toString(e)}), e);
			}
		}

		if (gamePack_.isMapperfilesAvailable() && entry.getProfile().getCustomMapperFile() != null) {
			try {
				ZipUtils.zipEntry(zipOutputStream_, entry.getProfile().getCustomMapperFile(), entry.getArchiveMapper(), this);
			} catch (IOException e) {
				throw new IOException(text_.get("dialog.export.error.exportmapper", new Object[] {prof.getTitle(), StringRelatedUtils.toString(e)}), e);
			}
		}

		if (gamePack_.isGamedataAvailable() && getObjects().stream().noneMatch(x -> entry != x && entry.getGameDir().equals(x.getGameDir()) && (entry.getId() > x.getId()))) {
			try {
				ZipUtils.zipDir(zipOutputStream_, entry.getCanonicalGameDir(), entry.getArchiveGameDir(), entry.getCanonicalGameDir(), this);
			} catch (IOException e) {
				throw new IOException(text_.get("dialog.export.error.exportgamedata", new Object[] {prof.getTitle(), StringRelatedUtils.toString(e)}), e);
			}
		}

		return null;
	}

	@Override
	public String getTitle(GamePackEntry entry) {
		return entry.getProfile().getTitle();
	}

	@Override
	public void preFinish() throws IOException {
		zipOutputStream_.close();
	}
}