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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;


/**
 * alpha 52: an approximate preview of a shader, drawn by DBGL itself on a built-in test picture (320x200 in the 16 EGA colours,
 * drawn here, no image file). Only DOSBox can draw its shaders; this shows the kind of picture (sharp, soft, scanlines, CRT,
 * curved, smoothed edges), as named by {@link ShaderInfo}.
 */
public final class ShaderPreview {

	public static final int SRC_W = 320, SRC_H = 200;

	private static final int[] EGA = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xAA5500, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555,
			0xFF55FF, 0xFFFF55, 0xFFFFFF};

	/** 5x7 letters for the writing on the test picture */
	private static final String[] FONT = {"T:11111,00100,00100,00100,00100,00100,00100", "E:11111,10000,10000,11110,10000,10000,11111", "S:01111,10000,10000,01110,00001,00001,11110",
			"D:11110,10001,10001,10001,10001,10001,11110", "O:01110,10001,10001,10001,10001,10001,01110", "B:11110,10001,10001,11110,10001,10001,11110",
			"X:10001,10001,01010,00100,01010,10001,10001", "3:11110,00001,00001,01110,00001,00001,11110", "2:01110,10001,00001,00010,00100,01000,11111",
			"0:01110,10001,10011,10101,11001,10001,01110", "x:00000,00000,10001,01010,00100,01010,10001"};

	private static volatile BufferedImage testImage_;

	private ShaderPreview() {
	}

	/** @return true if there is a preview for this kind of shader (the end of a key of {@link ShaderInfo}) */
	public static boolean supports(String kind) {
		return kind != null && java.util.Set.of("nearest", "bilinear", "sharp", "crtauto", "crtautomachine", "crtautoarcade", "crtautoarcadesharp", "scanlines", "rgb", "advmame", "xbr",
			"interpolation", "crtcurved", "crt").contains(kind);
	}

	/** @return the kind of shader by its name (as in the shader field), or null if there is no description or preview for it */
	public static String kindOf(String shader) {
		String key = ShaderInfo.keyOf(shader);
		String kind = key == null ? null: key.substring(key.lastIndexOf('.') + 1);
		return supports(kind) ? kind: null;
	}

	/** @return the test picture, 320x200 */
	public static BufferedImage testImage() {
		BufferedImage img = testImage_;
		if (img == null) {
			img = drawTestImage();
			testImage_ = img;
		}
		return img;
	}

	private static BufferedImage drawTestImage() {
		int[] p = new int[SRC_W * SRC_H];
		for (int y = 0; y < SRC_H; y++) {
			for (int x = 0; x < SRC_W; x++) {
				int c;
				if (y < 110) { // the sky: dark blue to light blue, dithered as in old games
					int band = y * 4 / 110;
					boolean dot = ((x + y) & 1) == 0;
					c = band == 0 ? 1: band == 1 ? (dot ? 1: 9): band == 2 ? 9: (dot ? 9: 11);
				} else if (y < 160) { // the ground: green, lighter towards the front
					c = ((y > 135) && (((x + y) & 1) == 0)) ? 10: 2;
				} else {
					c = 0;
				}
				p[y * SRC_W + x] = c;
			}
		}
		circle(p, 262, 34, 17, 14);
		for (int x = 0; x < SRC_W; x++) { // the mountains, with snow at the top
			int top = 80 + (int)(12 * Math.sin(x / 21.0) + 7 * Math.sin(x / 8.0 + 1) + 4 * Math.sin(x / 3.3));
			for (int y = Math.max(0, top); y < 110; y++)
				p[y * SRC_W + x] = (y < top + 4) && (top < 82) ? 15: (y < top + 2) ? 7: 8;
		}
		for (int y = 118; y < 160; y++) // a path
			for (int x = 150 - (y - 118) / 2; x < 170 + (y - 118) / 2; x++)
				p[y * SRC_W + x] = ((x + y) % 3 == 0) ? 7: 6;
		rect(p, 36, 96, 70, 44, 6); // a house: wall, roof, window, door
		for (int i = 0; i < 24; i++)
			for (int x = 30 + i; x < 112 - i; x++)
				p[(96 - i) * SRC_W + x] = 4;
		rect(p, 46, 106, 18, 14, 11);
		rect(p, 54, 106, 2, 14, 0);
		rect(p, 46, 112, 18, 2, 0);
		rect(p, 80, 114, 16, 26, 0);
		rect(p, 92, 126, 2, 2, 14);
		int[][] figure = { // a little walking figure of its own, 10x16
				{0, 0, 0, 14, 14, 14, 14, 0, 0, 0}, {0, 0, 14, 14, 14, 14, 14, 14, 0, 0}, {0, 0, 14, 0, 14, 14, 0, 14, 0, 0}, {0, 0, 14, 14, 14, 14, 14, 14, 0, 0},
				{0, 0, 0, 14, 4, 4, 14, 0, 0, 0}, {0, 12, 12, 12, 12, 12, 12, 12, 12, 0}, {12, 12, 12, 12, 12, 12, 12, 12, 12, 12}, {14, 0, 12, 12, 12, 12, 12, 12, 0, 14},
				{14, 0, 12, 12, 12, 12, 12, 12, 0, 14}, {0, 0, 9, 9, 9, 9, 9, 9, 0, 0}, {0, 0, 9, 9, 0, 0, 9, 9, 0, 0}, {0, 0, 9, 9, 0, 0, 9, 9, 0, 0},
				{0, 0, 9, 9, 0, 0, 9, 9, 0, 0}, {0, 0, 9, 9, 0, 0, 9, 9, 0, 0}, {0, 6, 6, 6, 0, 0, 6, 6, 6, 0}, {0, 6, 6, 6, 0, 0, 6, 6, 6, 0}};
		for (int y = 0; y < figure.length; y++)
			for (int x = 0; x < figure[y].length; x++)
				if (figure[y][x] != 0)
					p[(124 + y) * SRC_W + 196 + x] = figure[y][x];
		text(p, "TEST", 124, 18, 3, 15, 0);
		for (int i = 0; i < 16; i++) // the 16 colours
			rect(p, 4 + i * 19, 164, 17, 12, i == 0 ? 8: i);
		rect(p, 4, 164, 17, 12, 8);
		rect(p, 5, 165, 15, 10, 0);
		for (int y = 182; y < 196; y++) { // fine patterns: one pixel checks, one pixel lines, a slanted line
			for (int x = 4; x < 60; x++)
				p[y * SRC_W + x] = ((x + y) & 1) == 0 ? 15: 0;
			for (int x = 66; x < 122; x++)
				p[y * SRC_W + x] = (x & 1) == 0 ? 15: 0;
			for (int x = 128; x < 184; x++)
				p[y * SRC_W + x] = (y & 1) == 0 ? 15: 0;
		}
		for (int i = 0; i < 56; i++)
			p[(182 + i / 4) * SRC_W + 190 + i] = 15;
		text(p, "320x200", 252, 186, 1, 7, -1);
		BufferedImage img = new BufferedImage(SRC_W, SRC_H, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < p.length; i++)
			img.setRGB(i % SRC_W, i / SRC_W, EGA[p[i]]);
		return img;
	}

	private static void rect(int[] p, int x0, int y0, int w, int h, int c) {
		for (int y = y0; y < y0 + h; y++)
			for (int x = x0; x < x0 + w; x++)
				p[y * SRC_W + x] = c;
	}

	private static void circle(int[] p, int cx, int cy, int r, int c) {
		for (int y = cy - r; y <= cy + r; y++)
			for (int x = cx - r; x <= cx + r; x++)
				if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r)
					p[y * SRC_W + x] = c;
	}

	private static void text(int[] p, String s, int x0, int y0, int scale, int c, int shadow) {
		for (int i = 0; i < s.length(); i++) {
			String glyph = null;
			for (String f: FONT)
				if (f.charAt(0) == s.charAt(i))
					glyph = f.substring(2);
			if (glyph == null)
				continue;
			String[] rows = glyph.split(",");
			for (int gy = 0; gy < rows.length; gy++)
				for (int gx = 0; gx < rows[gy].length(); gx++)
					if (rows[gy].charAt(gx) == '1') {
						if (shadow >= 0)
							rect(p, x0 + (i * 6 + gx) * scale + scale, y0 + gy * scale + scale, scale, scale, shadow);
						rect(p, x0 + (i * 6 + gx) * scale, y0 + gy * scale, scale, scale, c);
					}
		}
	}

	/** @return the test picture as it looks with this kind of shader, w x h (4:3 for the picture of a DOS monitor) */
	public static BufferedImage render(String kind, int w, int h) {
		BufferedImage src = testImage();
		return switch (kind == null ? "nearest": kind) {
			case "bilinear" -> scale(src, w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			case "interpolation" -> scale(src, w, h, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			case "sharp" -> sharp(src, w, h);
			case "scanlines" -> crt(src, w, h, 0.55, 0, false, false);
			case "rgb" -> crt(src, w, h, 0.25, 0.45, false, false);
			case "crtauto", "crtautomachine", "crt" -> crt(src, w, h, 0.45, 0.2, true, false);
			case "crtautoarcade" -> crt(src, w, h, 0.7, 0.2, true, false);
			case "crtautoarcadesharp" -> crt(src, w, h, 0.7, 0.15, false, false);
			case "crtcurved" -> crt(src, w, h, 0.5, 0.25, true, true);
			case "advmame" -> scale(scale2x(scale2x(src)), w, h, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			case "xbr" -> scale(scale2x(scale2x(src)), w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			default -> scale(src, w, h, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		};
	}

	private static BufferedImage scale(BufferedImage src, int w, int h, Object interpolation) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		g.drawImage(src, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	/** sharp: as large as a whole multiple allows with hard pixels, only the rest smoothed (sharp bilinear) */
	private static BufferedImage sharp(BufferedImage src, int w, int h) {
		int k = Math.max(1, Math.max(w / src.getWidth(), h / src.getHeight()) + 1);
		return scale(scale(src, src.getWidth() * k, src.getHeight() * k, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR), w, h, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	}

	/** Scale2x (EPX): edges of pixel graphics smoothed, only colours of the picture */
	static BufferedImage scale2x(BufferedImage src) {
		int sw = src.getWidth(), sh = src.getHeight();
		BufferedImage out = new BufferedImage(sw * 2, sh * 2, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < sh; y++) {
			for (int x = 0; x < sw; x++) {
				int p = src.getRGB(x, y);
				int a = src.getRGB(x, Math.max(0, y - 1)), b = src.getRGB(Math.min(sw - 1, x + 1), y), c = src.getRGB(Math.max(0, x - 1), y), d = src.getRGB(x, Math.min(sh - 1, y + 1));
				int e0 = p, e1 = p, e2 = p, e3 = p;
				if ((a != d) && (c != b)) {
					e0 = c == a ? a: p;
					e1 = a == b ? b: p;
					e2 = c == d ? c: p;
					e3 = d == b ? b: p;
				}
				out.setRGB(2 * x, 2 * y, e0);
				out.setRGB(2 * x + 1, 2 * y, e1);
				out.setRGB(2 * x, 2 * y + 1, e2);
				out.setRGB(2 * x + 1, 2 * y + 1, e3);
			}
		}
		return out;
	}

	/**
	 * a picture tube: soft along the lines, darker between the lines (scan), the coloured dots of the mask (mask), a little glow,
	 * on request the screen curved with darker corners
	 */
	private static BufferedImage crt(BufferedImage src, int w, int h, double scan, double mask, boolean soft, boolean curved) {
		BufferedImage base = scale(src, w, src.getHeight(), soft ? RenderingHints.VALUE_INTERPOLATION_BILINEAR: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		int bw = base.getWidth(), bh = base.getHeight();
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				double u = (x + 0.5) / w, v = (y + 0.5) / h;
				double shade = 1;
				if (curved) { // barrel: the further from the middle, the more it bends
					double cx = u * 2 - 1, cy = v * 2 - 1;
					cx *= 1 + 0.09 * cy * cy;
					cy *= 1 + 0.12 * cx * cx;
					u = (cx + 1) / 2;
					v = (cy + 1) / 2;
					if ((u < 0) || (u >= 1) || (v < 0) || (v >= 1)) {
						out.setRGB(x, y, 0);
						continue;
					}
					shade = Math.min(1, 16 * u * v * (1 - u) * (1 - v) * 1.6);
					shade = Math.pow(shade, 0.35);
				}
				double sy = v * bh - 0.5;
				int row = (int)Math.floor(sy);
				double f = sy - row;
				int rgb0 = base.getRGB(Math.min(bw - 1, (int)(u * bw)), Math.max(0, Math.min(bh - 1, row)));
				int rgb1 = base.getRGB(Math.min(bw - 1, (int)(u * bw)), Math.max(0, Math.min(bh - 1, row + 1)));
				double w0 = beam(f, scan), w1 = beam(1 - f, scan);
				int m = x % 3;
				double[] maskRgb = {m == 0 ? 1: 1 - mask, m == 1 ? 1: 1 - mask, m == 2 ? 1: 1 - mask};
				int outRgb = 0;
				for (int ch = 0; ch < 3; ch++) {
					int shift = 16 - ch * 8;
					double val = (((rgb0 >> shift) & 0xFF) * w0 + ((rgb1 >> shift) & 0xFF) * w1) * maskRgb[ch] * (1 + scan * 0.5 + mask * 0.5) * shade;
					outRgb |= ((int)Math.max(0, Math.min(255, val))) << shift;
				}
				out.setRGB(x, y, outRgb);
			}
		}
		return out;
	}

	/** how much a line of the picture lights a point at this distance (0 its middle, 1 the middle of the next line) */
	private static double beam(double distance, double scan) {
		double d = Math.min(1, Math.abs(distance));
		double gauss = Math.exp(-(d * d) / (2 * 0.32 * 0.32));
		return (1 - scan) * (1 - d) + scan * gauss;
	}
}
