/*
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
 *  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
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
package org.dbgl.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Alpha 48: a mapper file of DOSBox (all DOSBox kinds write the same: one line per event, its name, then each bind in quotes,
 * e.g. {@code key_space "key 44" "stick_0 button 0" }). DOSBox drops all its own binds when it reads such a file, so it is only
 * ever changed, never written from nothing: lines DBGL does not change stay exactly as they were.
 */
public final class MapperFile {

	private static final Pattern LINE = Pattern.compile("^\\s*(\\S+)((?:\\s+\"[^\"]*\")*)\\s*$");
	private static final Pattern BIND = Pattern.compile("\"([^\"]*)\"");

	/** the lines as read; a changed event line is written anew */
	private final List<String> lines_ = new ArrayList<>();
	/** the binds of each event, in the order of the file, by line index */
	private final Map<Integer, List<String>> binds_ = new LinkedHashMap<>();
	private final Map<Integer, String> events_ = new LinkedHashMap<>();
	/** the binds as read, to write anew only the lines whose binds changed */
	private final Map<Integer, List<String>> read_ = new LinkedHashMap<>();
	private final String eol_;

	public MapperFile(String text) {
		eol_ = text.contains("\r\n") ? "\r\n": "\n";
		String[] all = text.split("\r?\n", -1);
		for (int i = 0; i < all.length; i++) {
			lines_.add(all[i]);
			Matcher m = LINE.matcher(all[i]);
			if (!all[i].isBlank() && m.matches()) {
				events_.put(i, m.group(1));
				List<String> b = new ArrayList<>();
				Matcher bm = BIND.matcher(m.group(2));
				while (bm.find())
					b.add(bm.group(1));
				binds_.put(i, b);
				read_.put(i, List.copyOf(b));
			}
		}
	}

	/** the names of the events for keys of the keyboard ("key_space"), in the order of the file */
	public List<String> keyEvents() {
		return events_.values().stream().filter(e -> e.startsWith("key_")).toList();
	}

	public int eventCount() {
		return events_.size();
	}

	/** the event of the keyboard a bind (e.g. "stick_0 button 0") presses, or null */
	public String keyOf(String bind) {
		for (Map.Entry<Integer, String> e: events_.entrySet())
			if (e.getValue().startsWith("key_") && binds_.get(e.getKey()).contains(bind))
				return e.getValue();
		return null;
	}

	/**
	 * the bind presses this key now (null: no key), and no other key. All other lines keep it: the emulated joystick
	 * ("jbutton_0_0", as in the presets of DOSBox Staging; whether the game sees a joystick is up to joysticktype=hidden), the
	 * hotkeys and modifiers ("hand_shutdown", "mod_3": Back + Start quits in those presets).
	 */
	public void assign(String bind, String keyEvent) {
		if ((keyEvent != null) && !events_.containsValue(keyEvent))
			throw new IllegalArgumentException("no event " + keyEvent);
		for (Map.Entry<Integer, String> e: events_.entrySet()) {
			String ev = e.getValue();
			if (ev.equals(keyEvent)) {
				if (!binds_.get(e.getKey()).contains(bind))
					binds_.get(e.getKey()).add(bind);
			} else if (ev.startsWith("key_")) {
				binds_.get(e.getKey()).remove(bind);
			}
		}
	}

	public String text() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < lines_.size(); i++) {
			if (i > 0)
				sb.append(eol_);
			if (binds_.containsKey(i) && !binds_.get(i).equals(read_.get(i))) {
				sb.append(events_.get(i)).append(' '); // as DOSBox writes it
				for (String b: binds_.get(i))
					sb.append('"').append(b).append("\" ");
			} else {
				sb.append(lines_.get(i));
			}
		}
		return sb.toString();
	}

	/** the controller inputs the assistant offers: the hat (D-pad), the left stick and twelve buttons of the first controller */
	public static final List<String> INPUTS = inputs();

	private static List<String> inputs() {
		List<String> r = new ArrayList<>(List.of("stick_0 hat 0 1", "stick_0 hat 0 4", "stick_0 hat 0 8", "stick_0 hat 0 2", "stick_0 axis 1 0", "stick_0 axis 1 1",
			"stick_0 axis 0 0", "stick_0 axis 0 1"));
		for (int i = 0; i < 12; i++)
			r.add("stick_0 button " + i);
		return List.copyOf(r);
	}
}
