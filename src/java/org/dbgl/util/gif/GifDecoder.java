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

 * Class GifDecoder - Decodes a GIF file into one or more frames. <br>
 *
 * <pre>
 * Example:
 *    GifDecoder d = new GifDecoder();
 *    d.read("sample.gif");
 *    int n = d.getFrameCount();
 *    for (int i = 0; i < n; i++) {
 *       BufferedImage frame = d.getFrame(i);  // frame i
 *       int t = d.getDelay(i);  // display duration of frame in milliseconds
 *       // do something with frame
 *    }
 * </pre>
 *
 * No copyright asserted on the source code of this class. May be used for any
 * purpose, however, refer to the Unisys LZW patent for any additional
 * restrictions. Please forward any corrections to questions at fmsware.com.
 *
 * @author Kevin Weiner, FM Software; LZW decoder adapted from John Cristy's
 *         ImageMagick.
 *         Ronald Blankendaal; made small changes to reduce warnings and use Java Collection Generics
 * @version 1.03a November 2003
 */
package org.dbgl.util.gif;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.dbgl.util.searchengine.WebSearchEngine;


public class GifDecoder {

	/**
	 * File read status: No errors.
	 */
	public static final int STATUS_OK = 0;

	/**
	 * File read status: Error decoding file (may be partially decoded)
	 */
	public static final int STATUS_FORMAT_ERROR = 1;

	/**
	 * File read status: Unable to open source.
	 */
	public static final int STATUS_OPEN_ERROR = 2;

	// max decoder pixel stack size
	private static final int MAX_STACK_SIZE = 4096;

	private BufferedInputStream in_;
	private int status_;

	private int width_; // full image width
	private int height_; // full image height
	private boolean gctFlag_; // global color table used
	private int gctSize_; // size of global color table

	private int[] gct_; // global color table
	private int[] lct_; // local color table
	private int[] act_; // active color table

	private int bgIndex_; // background color index
	private int bgColor_; // background color
	private int lastBgColor_; // previous bg color

	private boolean interlace_; // interlace flag

	private int ix_, iy_, iw_, ih_; // current image rectangle
	private Rectangle lastRect_; // last image rect
	private BufferedImage image_; // current frame
	private BufferedImage lastImage_; // previous frame

	private byte[] block_ = new byte[256]; // current data block
	private int blockSize_ = 0; // block size

	// last graphic control extension info
	private int dispose_ = 0;
	// 0=no action; 1=leave in place; 2=restore to bg; 3=restore to prev
	private int lastDispose_ = 0;
	private boolean transparency_ = false; // use transparent color
	private int delay_ = 0; // delay in milliseconds
	private int transIndex_; // transparent color index

	// LZW decoder working arrays
	private short[] prefix_;
	private byte[] suffix_;
	private byte[] pixelStack_;
	private byte[] pixels_;

	private ArrayList<GifFrame> frames_; // frames read from current file
	private int frameCount_;

	private static class GifFrame {
		private final BufferedImage image_;
		private final int delay_;

		public GifFrame(BufferedImage im, int del) {
			image_ = im;
			delay_ = del;
		}
	}

	/**
	 * Reads GIF image from stream
	 *
	 * @param BufferedInputStream containing GIF file.
	 * @return read status code (0 = no errors)
	 * @throws IOException
	 */
	public int read(BufferedInputStream is) throws IOException {
		init();
		if (is != null) {
			in_ = is;
			readHeader();
			if (!err()) {
				readContents();
				if (frameCount_ < 0) {
					status_ = STATUS_FORMAT_ERROR;
				}
			}
		} else {
			status_ = STATUS_OPEN_ERROR;
		}
		if (is != null)
			is.close();
		return status_;
	}

	/**
	 * Reads GIF image from stream
	 *
	 * @param InputStream containing GIF file.
	 * @return read status code (0 = no errors)
	 * @throws IOException
	 */
	public int read(InputStream is) throws IOException {
		init();
		if (is != null) {
			if (!(is instanceof BufferedInputStream))
				is = new BufferedInputStream(is);
			in_ = (BufferedInputStream)is;
			readHeader();
			if (!err()) {
				readContents();
				if (frameCount_ < 0) {
					status_ = STATUS_FORMAT_ERROR;
				}
			}
		} else {
			status_ = STATUS_OPEN_ERROR;
		}
		if (is != null)
			is.close();
		return status_;
	}

	/**
	 * Reads GIF file from specified file/URL source (URL assumed if name contains ":/" or "file:")
	 *
	 * @param name String containing source
	 * @return read status code (0 = no errors)
	 */
	public int read(String name) {
		status_ = STATUS_OK;
		try {
			name = name.trim().toLowerCase();
			if ((name.indexOf("file:") >= 0) || (name.indexOf(":/") > 0)) {
				in_ = new BufferedInputStream(WebSearchEngine.getHttpURLConnection(name).getInputStream());
			} else {
				in_ = new BufferedInputStream(new FileInputStream(name));
			}
			status_ = read(in_);
		} catch (IOException e) {
			status_ = STATUS_OPEN_ERROR;
		}

		return status_;
	}

	/**
	 * Gets the number of frames read from file.
	 *
	 * @return frame count
	 */
	public int getFrameCount() {
		return frameCount_;
	}

	/**
	 * Gets the image contents of frame n.
	 *
	 * @return BufferedImage representation of frame, or null if n is invalid.
	 */
	public BufferedImage getFrame(int n) {
		BufferedImage im = null;
		if ((n >= 0) && (n < frameCount_)) {
			im = frames_.get(n).image_;
		}
		return im;
	}

	/**
	 * Gets display duration for specified frame.
	 *
	 * @param n int index of frame
	 * @return delay in milliseconds
	 */
	public int getDelay(int n) {
		delay_ = -1;
		if ((n >= 0) && (n < frameCount_)) {
			delay_ = frames_.get(n).delay_;
		}
		return delay_;
	}

	/**
	 * Creates new frame image from current data (and previous frames as specified by their disposition codes).
	 */
	private void setPixels() {
		// expose destination image's pixels as int array
		int[] dest = ((DataBufferInt)image_.getRaster().getDataBuffer()).getData();

		// fill in starting image contents based on last image's dispose code
		if (lastDispose_ > 0) {
			if (lastDispose_ == 3) {
				// use image before last
				int n = frameCount_ - 2;
				if (n > 0) {
					lastImage_ = getFrame(n - 1);
				} else {
					lastImage_ = null;
				}
			}

			if (lastImage_ != null) {
				int[] prev = ((DataBufferInt)lastImage_.getRaster().getDataBuffer()).getData();
				System.arraycopy(prev, 0, dest, 0, width_ * height_);
				// copy pixels

				if (lastDispose_ == 2) {
					// fill last image rect area with background color
					Graphics2D g = image_.createGraphics();
					Color c = null;
					if (transparency_) {
						c = new Color(0, 0, 0, 0); // assume background is transparent
					} else {
						c = new Color(lastBgColor_); // use given background color
					}
					g.setColor(c);
					g.setComposite(AlphaComposite.Src); // replace area
					g.fill(lastRect_);
					g.dispose();
				}
			}
		}

		// copy each source line to the appropriate place in the destination
		int pass = 1;
		int inc = 8;
		int iline = 0;
		for (int i = 0; i < ih_; i++) {
			int line = i;
			if (interlace_) {
				if (iline >= ih_) {
					pass++;
					switch (pass) {
						case 2:
							iline = 4;
							break;
						case 3:
							iline = 2;
							inc = 4;
							break;
						case 4:
							iline = 1;
							inc = 2;
							break;
						default:
					}
				}
				line = iline;
				iline += inc;
			}
			line += iy_;
			if (line < height_) {
				int k = line * width_;
				int dx = k + ix_; // start of line in dest
				int dlim = dx + iw_; // end of dest line
				if ((k + width_) < dlim) {
					dlim = k + width_; // past dest edge
				}
				int sx = i * iw_; // start of line in source
				while (dx < dlim) {
					// map color and insert in destination
					int index = (pixels_[sx++]) & 0xff;
					int c = act_[index];
					if (c != 0) {
						dest[dx] = c;
					}
					dx++;
				}
			}
		}
	}

	/**
	 * Decodes LZW image data into pixel array. Adapted from John Cristy's ImageMagick.
	 * 
	 * @throws IOException
	 */
	private void decodeImageData() throws IOException {
		int nullCode = -1;
		int npix = iw_ * ih_;
		int available, clear, codeMask, codeSize, endOfInformation, inCode, oldCode, bits, code, count, i, datum, dataSize, first, top, bi, pi;

		if ((pixels_ == null) || (pixels_.length < npix)) {
			pixels_ = new byte[npix]; // allocate new pixel array
		}
		if (prefix_ == null)
			prefix_ = new short[MAX_STACK_SIZE];
		if (suffix_ == null)
			suffix_ = new byte[MAX_STACK_SIZE];
		if (pixelStack_ == null)
			pixelStack_ = new byte[MAX_STACK_SIZE + 1];

		// Initialize GIF data stream decoder.

		dataSize = read();
		clear = 1 << dataSize;
		endOfInformation = clear + 1;
		available = clear + 2;
		oldCode = nullCode;
		codeSize = dataSize + 1;
		codeMask = (1 << codeSize) - 1;
		for (code = 0; code < clear; code++) {
			prefix_[code] = 0;
			suffix_[code] = (byte)code;
		}

		// Decode GIF pixel stream.

		datum = bits = count = first = top = pi = bi = 0;

		for (i = 0; i < npix;) {
			if (top == 0) {
				if (bits < codeSize) {
					// Load bytes until there are enough bits for a code.
					if (count == 0) {
						// Read a new data block.
						count = readBlock();
						if (count <= 0)
							break;
						bi = 0;
					}
					datum += ((block_[bi]) & 0xff) << bits;
					bits += 8;
					bi++;
					count--;
					continue;
				}

				// Get the next code.

				code = datum & codeMask;
				datum >>= codeSize;
				bits -= codeSize;

				// Interpret the code

				if ((code > available) || (code == endOfInformation))
					break;
				if (code == clear) {
					// Reset decoder.
					codeSize = dataSize + 1;
					codeMask = (1 << codeSize) - 1;
					available = clear + 2;
					oldCode = nullCode;
					continue;
				}
				if (oldCode == nullCode) {
					pixelStack_[top++] = suffix_[code];
					oldCode = code;
					first = code;
					continue;
				}
				inCode = code;
				if (code == available) {
					pixelStack_[top++] = (byte)first;
					code = oldCode;
				}
				while (code > clear) {
					pixelStack_[top++] = suffix_[code];
					code = prefix_[code];
				}
				first = (suffix_[code]) & 0xff;

				// Add a new string to the string table,

				if (available >= MAX_STACK_SIZE)
					break;
				pixelStack_[top++] = (byte)first;
				prefix_[available] = (short)oldCode;
				suffix_[available] = (byte)first;
				available++;
				if (((available & codeMask) == 0) && (available < MAX_STACK_SIZE)) {
					codeSize++;
					codeMask += available;
				}
				oldCode = inCode;
			}

			// Pop a pixel off the pixel stack.

			top--;
			pixels_[pi++] = pixelStack_[top];
			i++;
		}

		for (i = pi; i < npix; i++) {
			pixels_[i] = 0; // clear missing pixels
		}

	}

	/**
	 * Returns true if an error was encountered during reading/decoding
	 */
	private boolean err() {
		return status_ != STATUS_OK;
	}

	/**
	 * Initializes or re-initializes reader
	 */
	private void init() {
		status_ = STATUS_OK;
		frameCount_ = 0;
		frames_ = new ArrayList<>();
		gct_ = null;
		lct_ = null;
	}

	/**
	 * Reads a single byte from the input stream.
	 */
	private int read() {
		int curByte = 0;
		try {
			curByte = in_.read();
		} catch (IOException e) {
			status_ = STATUS_FORMAT_ERROR;
		}
		return curByte;
	}

	/**
	 * Reads next variable length block from input.
	 *
	 * @return number of bytes stored in "buffer"
	 * @throws IOException
	 */
	private int readBlock() throws IOException {
		blockSize_ = read();
		int n = 0;
		if (blockSize_ > 0) {
			int count = 0;
			while (n < blockSize_) {
				count = in_.read(block_, n, blockSize_ - n);
				if (count == -1)
					break;
				n += count;
			}

			if (n < blockSize_) {
				status_ = STATUS_FORMAT_ERROR;
			}
		}
		return n;
	}

	/**
	 * Reads color table as 256 RGB integer values
	 *
	 * @param ncolors int number of colors to read
	 * @return int array containing 256 colors (packed ARGB with full alpha)
	 */
	private int[] readColorTable(int ncolors) {
		byte[] c = new byte[3 * ncolors];
		try {
			IOUtils.readFully(in_, c);
		} catch (IOException e) {
			status_ = STATUS_FORMAT_ERROR;
		}

		int[] tab = new int[256]; // max size to avoid bounds checks
		int i = 0;
		int j = 0;
		while (i < ncolors) {
			int r = (c[j++]) & 0xff;
			int g = (c[j++]) & 0xff;
			int b = (c[j++]) & 0xff;
			tab[i++] = 0xff000000 | (r << 16) | (g << 8) | b;
		}
		return tab;
	}

	/**
	 * Main file parser. Reads GIF content blocks.
	 * 
	 * @throws IOException
	 */
	private void readContents() throws IOException {
		// read GIF file content blocks
		boolean done = false;
		while (!(done || err())) {
			int code = read();
			switch (code) {

				case 0x2C: // image separator
					readImage();
					break;

				case 0x21: // extension
					code = read();
					switch (code) {
						case 0xf9: // graphics control extension
							readGraphicControlExt();
							break;

						case 0xff: // application extension
							readBlock();
							StringBuilder app = new StringBuilder();
							for (int i = 0; i < 11; i++) {
								app.append((char)block_[i]);
							}
							if (app.toString().equals("NETSCAPE2.0")) {
								readNetscapeExt();
							} else
								skip(); // don't care
							break;

						default: // uninteresting extension
							skip();
					}
					break;

				case 0x3b: // terminator
					done = true;
					break;

				case 0x00: // bad byte, but keep going and see what happens
					break;

				default:
					status_ = STATUS_FORMAT_ERROR;
			}
		}
	}

	/**
	 * Reads Graphics Control Extension values
	 */
	private void readGraphicControlExt() {
		read(); // block size
		int packed = read(); // packed fields
		dispose_ = (packed & 0x1c) >> 2; // disposal method
		if (dispose_ == 0) {
			dispose_ = 1; // elect to keep old image if discretionary
		}
		transparency_ = (packed & 1) != 0;
		delay_ = readShort() * 10; // delay in milliseconds
		transIndex_ = read(); // transparent color index
		read(); // block terminator
	}

	/**
	 * Reads GIF file header information.
	 */
	private void readHeader() {
		StringBuilder id = new StringBuilder();
		for (int i = 0; i < 6; i++) {
			id.append((char)read());
		}
		if (!id.toString().startsWith("GIF")) {
			status_ = STATUS_FORMAT_ERROR;
			return;
		}

		readLSD();
		if (gctFlag_ && !err()) {
			gct_ = readColorTable(gctSize_);
			bgColor_ = gct_[bgIndex_];
		}
	}

	/**
	 * Reads next frame image
	 * 
	 * @throws IOException
	 */
	private void readImage() throws IOException {
		ix_ = readShort(); // (sub)image position & size
		iy_ = readShort();
		iw_ = readShort();
		ih_ = readShort();

		int packed = read();
		boolean lctFlag = (packed & 0x80) != 0; // 1 - local color table flag
		interlace_ = (packed & 0x40) != 0; // 2 - interlace flag
		// 3 - sort flag
		// 4-5 - reserved
		int lctSize = 2 << (packed & 7); // 6-8 - local color table size

		if (lctFlag) {
			lct_ = readColorTable(lctSize); // read table
			act_ = lct_; // make local table active
		} else {
			act_ = gct_; // make global table active
			if (bgIndex_ == transIndex_)
				bgColor_ = 0;
		}
		int save = 0;
		if (transparency_) {
			save = act_[transIndex_];
			act_[transIndex_] = 0; // set transparent color if specified
		}

		if (act_ == null) {
			status_ = STATUS_FORMAT_ERROR; // no color table defined
		}

		if (err())
			return;

		decodeImageData(); // decode pixel data
		skip();

		if (err())
			return;

		frameCount_++;

		// create new image to receive frame data
		image_ = new BufferedImage(width_, height_, BufferedImage.TYPE_INT_ARGB_PRE);

		setPixels(); // transfer pixel data to image

		frames_.add(new GifFrame(image_, delay_)); // add image to frame list

		if (transparency_) {
			act_[transIndex_] = save;
		}
		resetFrame();

	}

	/**
	 * Reads Logical Screen Descriptor
	 */
	private void readLSD() {

		// logical screen size
		width_ = readShort();
		height_ = readShort();

		// packed fields
		int packed = read();
		gctFlag_ = (packed & 0x80) != 0; // 1 : global color table flag
		// 2-4 : color resolution
		// 5 : gct sort flag
		gctSize_ = 2 << (packed & 7); // 6-8 : gct size

		bgIndex_ = read(); // background color index
		read(); // pixel aspect ratio
	}

	/**
	 * Reads Netscape extension to obtain iteration count
	 * 
	 * @throws IOException
	 */
	private void readNetscapeExt() throws IOException {
		do {
			readBlock();
		} while ((blockSize_ > 0) && !err());
	}

	/**
	 * Reads next 16-bit value, LSB first
	 */
	private int readShort() {
		// read 16-bit value, LSB first
		return read() | (read() << 8);
	}

	/**
	 * Resets frame state for reading next image.
	 */
	private void resetFrame() {
		lastDispose_ = dispose_;
		lastRect_ = new Rectangle(ix_, iy_, iw_, ih_);
		lastImage_ = image_;
		lastBgColor_ = bgColor_;
		dispose_ = 0;
		transparency_ = false;
		delay_ = 0;
		lct_ = null;
	}

	/**
	 * Skips variable length blocks up to and including next zero length block.
	 * 
	 * @throws IOException
	 */
	private void skip() throws IOException {
		do {
			readBlock();
		} while ((blockSize_ > 0) && !err());
	}
}
