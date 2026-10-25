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
package org.dbgl.model.aggregate;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.model.FileLocation;
import org.dbgl.model.GenericStats;
import org.dbgl.model.ICanBeDefault;
import org.dbgl.model.entity.Configurable;
import org.dbgl.service.FileLocationService;
import org.dbgl.service.ITextService;
import org.dbgl.service.TextService;


public class DosboxVersion extends Configurable implements ICanBeDefault, Comparable<DosboxVersion> {

	private static final Pattern VERSION_PATRN = Pattern.compile("^(((\\d)\\.(\\d\\d))((\\-|\\.)(\\d))?)|(r(\\d\\d\\d\\d))|((\\d\\d\\d\\d)\\.(\\d\\d)\\.(\\d\\d))$");
	
	private String family_, version_, executableParameters_;
	private boolean default_, multiConfig_, usingCurses_;
	private Map<String, String> dynamicOptions_;
	private FileLocation path_, exe_;
	private GenericStats stats_;

	public DosboxVersion() {
		super();
	}

	public String getFamily() {
		return family_;
	}
	
	public void setFamily(String family) {
		family_ = family;
	}

	public String getVersion() {
		return version_;
	}

	public void setVersion(String version) {
		version_ = version;
	}

	public int getVersionAsInt() {
		Matcher versionMatcher = VERSION_PATRN.matcher(version_);

		int result = 0;
		if (versionMatcher.matches()) {
			if (versionMatcher.group(3) != null)
				result += Integer.valueOf(versionMatcher.group(3)) * 1000;
			if (versionMatcher.group(4) != null)
				result += Integer.valueOf(versionMatcher.group(4)) * 10;
			if (versionMatcher.group(7) != null)
				result += Integer.valueOf(versionMatcher.group(7));
			if (versionMatcher.group(9) != null)
				result += Integer.valueOf(versionMatcher.group(9));
			if (versionMatcher.group(10) != null)
				result += Integer.valueOf(versionMatcher.group(11) + versionMatcher.group(12) + versionMatcher.group(13));
		}
		
		return result;
	}

	public int distance(DosboxVersion other) {
		return Math.abs(other.getVersionAsInt() - getVersionAsInt());
	}

	@Override
	public boolean isDefault() {
		return default_;
	}

	public void setDefault(boolean isDefault) {
		default_ = isDefault;
	}

	public boolean isMultiConfig() {
		return multiConfig_;
	}

	public void setMultiConfig(boolean multiConfig) {
		multiConfig_ = multiConfig;
	}

	public boolean isUsingCurses() {
		return usingCurses_;
	}

	public void setUsingCurses(boolean usingCurses) {
		usingCurses_ = usingCurses;
	}

	public Map<String, String> getDynamicOptions() {
		return dynamicOptions_;
	}

	public void setDynamicOptions(Map<String, String> dynamicOptions) {
		dynamicOptions_ = dynamicOptions;
	}

	public File getPath() {
		return path_.getFile();
	}

	public File getCanonicalPath() {
		return path_.getCanonicalFile();
	}

	public void setPath(String path) {
		path_ = new FileLocation(path, FileLocationService.getInstance().dosboxRelative());
	}

	public File getExe() {
		return exe_.getFile();
	}

	public File getCanonicalExe() {
		return exe_.getCanonicalFile();
	}

	public void setExe(String exe) {
		exe_ = new FileLocation(exe, FileLocationService.getInstance().dosboxRelative());
	}

	public File getExecutable() {
		return StringUtils.isBlank(getExe().getPath()) ? new File(getPath(), FileLocationService.DOSBOX_EXE_STRING): getExe();
	}

	public File getCanonicalExecutable() {
		return StringUtils.isBlank(getExe().getPath()) ? new File(getCanonicalPath(), FileLocationService.DOSBOX_EXE_STRING): getCanonicalExe();
	}

	public String getExecutableParameters() {
		return executableParameters_;
	}

	public void setExecutableParameters(String executableParameters) {
		executableParameters_ = executableParameters;
	}

	public void setConfigurationFileLocation(String file) {
		configuration_.setFileLocation(new FileLocation(file, FileLocationService.getInstance().dosboxRelative()));
	}

	public String getConfigurationString() {
		return configuration_.toString(null);
	}

	public String resetAndLoadConfiguration() throws IOException {
		return configuration_.reloadWithAutoexec(TextService.getInstance(), null);
	}

	public String loadConfigurationData(ITextService text, String data, File file) throws IOException {
		return configuration_.loadDataWithAutoexec(text, data, file, null);
	}

	public File getCwd() {
		return FileLocationService.getInstance().getDosroot();
	}

	public GenericStats getStats() {
		return stats_;
	}

	public void setStats(GenericStats stats) {
		stats_ = stats;
	}

	@Override
	public int compareTo(DosboxVersion comp) {
		int ver1 = getVersionAsInt();
		int ver2 = comp.getVersionAsInt();
		if (ver1 != ver2) {
			return (ver1 - ver2);
		}
		return getTitle().compareTo(comp.getTitle());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof DosboxVersion))
			return false;
		DosboxVersion that = (DosboxVersion)obj;
		return Strings.CS.equals(version_, that.version_) && Strings.CS.equals(getTitle(), that.getTitle());
	}

	@Override
	public int hashCode() {
		return Objects.hash(version_, getTitle());
	}
}
