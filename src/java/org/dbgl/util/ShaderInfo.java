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

import java.util.Locale;


/**
 * alpha 52: a short description of a shader, shown below the shader field: what the picture looks like with it. By its name,
 * the same for DOSBox, DOSBox Staging (also "crt/..." and the like) and DOSBox-X; own shader files have none.
 */
public final class ShaderInfo {

	private static final String PREFIX = "dialog.template.glshader.info.";

	private ShaderInfo() {
	}

	/** @return the key of the text that describes the shader, or null if it is not known */
	public static String keyOf(String shader) {
		if (shader == null)
			return null;
		String s = shader.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
		if (s.isEmpty())
			return null;
		String name = s.substring(s.lastIndexOf('/') + 1).replaceFirst("\\.(glsl|fx|slang)$", "");
		String kind = switch (name) {
			case "none", "nearest", "default", "normal" -> "nearest";
			case "bilinear" -> "bilinear";
			case "sharp" -> "sharp";
			case "crt-auto" -> "crtauto";
			case "crt-auto-machine" -> "crtautomachine";
			case "crt-auto-arcade" -> "crtautoarcade";
			case "crt-auto-arcade-sharp" -> "crtautoarcadesharp";
			case "scan2x", "scan3x", "tv2x", "tv3x" -> "scanlines";
			case "rgb2x", "rgb3x" -> "rgb";
			case "advmame2x", "advmame3x", "advinterp2x", "advinterp3x", "scale2x", "scale3x" -> "advmame";
			default -> null;
		};
		if (kind == null) {
			if (name.startsWith("xbr") || name.startsWith("hq") || name.startsWith("super-xbr") || name.startsWith("scalefx"))
				kind = "xbr";
			else if (name.contains("catmull") || name.contains("jinc") || name.contains("lanczos") || name.contains("bicubic") || name.contains("spline"))
				kind = "interpolation";
			else if (name.contains("geom") || name.contains("lottes") || name.contains("curv"))
				kind = "crtcurved";
			else if (name.startsWith("crt") || s.startsWith("crt/") || name.contains("aperture") || name.contains("easymode") || name.contains("hyllian") || name.contains("caligari")
					|| name.contains("zfast") || name.contains("scanline"))
				kind = "crt";
		}
		return kind == null ? null: PREFIX + kind;
	}
}
