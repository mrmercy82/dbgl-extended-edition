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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.StringUtils;


public class MixerCommand {

	public enum ChannelType { MASTER, SPKR, SB, GUS, FM, DISNEY, CDAUDIO };
	
	public static final ChannelType[] CHANNELS = new ChannelType[] { ChannelType.MASTER, ChannelType.SPKR, ChannelType.SB, ChannelType.GUS, ChannelType.FM, ChannelType.DISNEY, ChannelType.CDAUDIO };
	public static final int DEFAULT_VOLUME_LEVEL = 100; // percent
	public static final int MAX_VOLUME_LEVEL = 200; // percent

	public static class Channel {

		private String name_;
		private boolean enabled_;
		private int left_, right_;

		public Channel(String name, boolean enabled) {
			name_ = name;
			enabled_ = enabled;
			left_ = DEFAULT_VOLUME_LEVEL;
			right_ = DEFAULT_VOLUME_LEVEL;
		}
		
		public String getName() {
			return name_;
		}
		
		public boolean isEnabled() {
			return enabled_;
		}
		
		public void setEnabled(boolean enabled) {
			enabled_ = enabled;
		}

		public void setVolume(String command) {
			try {
				String[] vols = StringUtils.split(command, ':');
				if (vols.length == 2)
					setVolume(Integer.parseInt(vols[0]), Integer.parseInt(vols[1]));
			} catch (NumberFormatException e) {
				// ignore this command
			}
		}

		public void setVolume(int left, int right) {
			left_ = left;
			right_ = right;
		}

		public int getLeft() {
			return left_;
		}

		public int getRight() {
			return right_;
		}

		@Override
		public String toString() {
			return enabled_ && (left_ != DEFAULT_VOLUME_LEVEL || right_ != DEFAULT_VOLUME_LEVEL) ? name_ + ' ' + left_ + ':' + right_: StringUtils.EMPTY;
		}
	}

	private Map<ChannelType, Channel> channels_ = new LinkedHashMap<>();

	public MixerCommand(String command, String[] name, boolean[] enabled) {
		for (int i = 0; i < CHANNELS.length; i++)
			channels_.put(CHANNELS[i], new Channel(name[i], enabled[i]));
		
		String[] elements = StringUtils.split(command, ' ');
		for (int i = 0; i < elements.length; i += 2) {
			for (Channel chan: channels_.values()) {
				if (elements[i].equalsIgnoreCase(chan.name_) && ((i + 1) < elements.length)) {
					chan.setEnabled(true);
					chan.setVolume(elements[i + 1]);
				}
			}
		}
	}
	
	public Iterator<Entry<ChannelType, Channel>> getChannelIterator() {
		return channels_.entrySet().iterator();
	}

	@Override
	public String toString() {
		return StringUtils.join(channels_.values(), ' ').trim().replaceAll("\\s+", " ");
	}
}
