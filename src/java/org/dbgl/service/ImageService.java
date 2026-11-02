/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-01. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
package org.dbgl.service;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.dbgl.gui.controls.Mess_;
import org.dbgl.util.SystemUtils;
import org.dbgl.util.gif.GifDecoder;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;


public class ImageService {

	private static final Map<String, Image> imageCacheMap = new HashMap<>();

	public static final String IMG_TB_NEW = "tb_new.png";
	public static final String IMG_TB_EDIT = "tb_edit.png";
	public static final String IMG_TB_DELETE = "tb_delete.png";
	public static final String IMG_TB_RUN = "tb_run.png";
	public static final String IMG_TB_SETUP = "tb_setup.png";
	public static final String IMG_TB_ADDGAMEWIZARD = "tb_wizard.png";
	public static final String IMG_RUN = "run.png";
	public static final String IMG_SETUP = "setup.png";
	public static final String IMG_FOLDER = "folder.png";
	public static final String IMG_ZOOM = "zoom.png";
	public static final String IMG_NEW = "new.png";
	public static final String IMG_EDIT = "edit.png";
	public static final String IMG_DUPLICATE = "duplicate.png";
	public static final String IMG_DELETE = "delete.png";
	public static final String IMG_FAVORITE = "favorite.png";
	public static final String IMG_SHORTCUT = "shortcut.png";
	public static final String IMG_REFRESH = "refresh.png";
	public static final String IMG_HOME = "home.png";
	public static final String IMG_DFEND = "dfend.png";
	public static final String IMG_MIGRATE = "case.png";
	public static final String IMG_TABLEEXPORT = "checkout.png";
	public static final String IMG_IMPORT = "import.png";
	public static final String IMG_CLEAN = "clean.png";
	public static final String IMG_SETTINGS = "settings.png";
	public static final String IMG_LOG = "log.png";
	public static final String IMG_EXIT = "stop.png";
	public static final String IMG_FILTER = "filter.png";
	public static final String IMG_EDITFILTER = "editfilter.png";
	public static final String IMG_ABOUT = "about.png";
	public static final String IMG_GRAB = "grab.png";
	public static final String IMG_MOBYGAMES = "moby.png";
	public static final String IMG_METROPOLIS = "metropolis.png";
	public static final String IMG_POUET = "pouet.png";
	public static final String IMG_HOTUD = "hotud.png";
	public static final String IMG_THEGAMESDB = "thegamesdb.png";
	public static final String IMG_SHARE = "share.png";
	public static final String IMG_UNDO = "undo.png";
	public static final String IMG_TOPDOG = "topdog.png";
	public static final String IMG_WARNING = "warning.png";
	public static final String IMG_WARNING_LARGE = "warning_large.png";
	public static final String IMG_TILES_LARGE = "tiles_large.png";
	public static final String IMG_TILES_MEDIUM = "tiles_medium.png";
	public static final String IMG_TILES_SMALL = "tiles_small.png";
	public static final String IMG_BOXES_LARGE = "boxes_large.png";
	public static final String IMG_BOXES_MEDIUM = "boxes_medium.png";
	public static final String IMG_BOXES_SMALL = "boxes_small.png";
	public static final String IMG_TABLE = "table.png";
	public static final String IMG_SCREENSHOTS = "screenshots.png";
	public static final String IMG_NOTES = "notes.png";
	public static final String IMG_WHATSNEW = "whatsnew.png";

	private static final Color widgetBackgroundColor = Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
	private static final Color whiteColor = Display.getDefault().getSystemColor(SWT.COLOR_WHITE);
	private static final Color blackColor = Display.getDefault().getSystemColor(SWT.COLOR_BLACK);
	private static final Color widgetSelectedColor = Display.getDefault().getSystemColor(SWT.COLOR_LIST_SELECTION);

	private static final int SELECTED_BUTTON_BORDER_WIDTH = 2;

	private ImageService() {
	}

	public static Image getResourceImage(Display display, String path) {
		try (InputStream is = ImageService.class.getResourceAsStream("/img/" + path)) {
			return new Image(display, is);
		} catch (IOException e) {
			// nothing we can do
			return null;
		}
	}

	public static Image[] getResourceImages(Display display, String[] path) {
		return Stream.of(path).map(x -> getResourceImage(display, x)).toArray(Image[]::new);
	}

	public static Image getImage(Display display, String path) {
		try {
			if (path.toLowerCase().endsWith(".ico")) {
				ImageLoader iLoader = new ImageLoader();
				iLoader.load(path);
				int bestWidth = 0;
				int bestDepth = 0;
				int bestIndex = 0;
				for (int i = 0; i < iLoader.data.length; i++) {
					if (iLoader.data[i].width >= bestWidth && iLoader.data[i].depth >= bestDepth) {
						bestWidth = iLoader.data[i].width;
						bestDepth = iLoader.data[i].depth;
						bestIndex = i;
					}
				}
				return new Image(display, iLoader.data[bestIndex]);
			}

			Image result = path.toLowerCase().endsWith(".gif") ? getImageUsingGifDecoder(display, path): new Image(display, path);
			if (result == null) {
				System.err.println("Could not load image " + path + ", retrying with generic SWT ImageLoader...");
				result = new Image(display, path);
			}
			return result;
		} catch (Exception e) {
			Mess_.on(null).exception(e).warning();
			return getEmptyImage(display, 10, 10);
		}
	}

	public static ImageData[] getAnimatedImageData(InputStream imageInputStream) throws IOException {
		List<ImageData> result = new ArrayList<>();
		GifDecoder d = new GifDecoder();
		int status = d.read(imageInputStream);
		if (status == GifDecoder.STATUS_OK || status == GifDecoder.STATUS_FORMAT_ERROR) {
			int amount = (d.getDelay(0) < 500) ? 1: d.getFrameCount();
			for (int i = 0; i < amount; i++) {
				ImageData imageData = getImageDataFrame(d, i);
				if (imageData != null)
					result.add(imageData);
			}
		}
		return result.toArray(new ImageData[result.size()]);
	}

	public static Image getWidthLimitedImage(Display display, int width, ImageData data) {
		Image orgImage = new Image(display, data);
		int orgW = orgImage.getBounds().width;
		int orgH = orgImage.getBounds().height;
		int height = (int)(orgH * ((double)width / (double)orgW));
		Image image = createScaledImage(display, orgImage, orgW, orgH, width, height, false, null);
		orgImage.dispose();
		return image;
	}

	public static Image getHeightLimitedImage(Display display, int height, ImageData data) {
		Image orgImage = new Image(display, data);
		int orgW = orgImage.getBounds().width;
		int orgH = orgImage.getBounds().height;
		int width = (int)(orgW * ((double)height / (double)orgH));
		Image image = createScaledImage(display, orgImage, orgW, orgH, width, height, false, null);
		orgImage.dispose();
		return image;
	}

	public static Image getCachedHeightLimitedImage(Display display, int height, String path, String name) {
		return imageCacheMap.computeIfAbsent(path + height + name, h -> {
			Image orgImage = getImage(display, path);
			int orgW = orgImage.getBounds().width;
			int orgH = orgImage.getBounds().height;
			int width = (int)(orgW * ((double)height / (double)orgH));
			Image newImage = createScaledImage(display, orgImage, orgW, orgH, width, height, false, name);
			orgImage.dispose();
			return newImage;
		});
	}

	public static Image getCachedResizedImage(Display display, int width, int height, boolean keepAspectRatio, String path) {
		return imageCacheMap.computeIfAbsent(path + width + height + keepAspectRatio, h -> {
			Image orgImage = getImage(display, path);
			int orgW = orgImage.getBounds().width;
			int orgH = orgImage.getBounds().height;
			Image newImage = createScaledImage(display, orgImage, orgW, orgH, width, height, keepAspectRatio, null);
			orgImage.dispose();
			return newImage;
		});
	}

	public static Image getEmptyImage(Display display, int width, int height) {
		Image image = new Image(display, width, height);
		GC graphc = new GC(image);
		graphc.setBackground(widgetBackgroundColor);
		graphc.fillRectangle(0, 0, width, height);
		graphc.dispose();
		return image;
	}

	public static Image createDisabledImage(Image img) {
		return new Image(img.getDevice(), img, SWT.IMAGE_DISABLE);
	}

	public static Image createSelectedImage(Image img) {
		final int width = img.getBounds().width;
		final int height = img.getBounds().height;
		Image image = new Image(img.getDevice(), width, height);
		GC graphc = new GC(image);
		graphc.drawImage(img, 0, 0);

		graphc.setLineWidth(SELECTED_BUTTON_BORDER_WIDTH);
		for (int i = 0; i < 3; i++) {
			final int d = i * SELECTED_BUTTON_BORDER_WIDTH + SELECTED_BUTTON_BORDER_WIDTH / 2;
			graphc.setForeground(i % 2 == 0 ? widgetSelectedColor: whiteColor);
			graphc.drawRectangle(d, d, width - (d * 2) - SELECTED_BUTTON_BORDER_WIDTH / 2, height - (d * 2) - SELECTED_BUTTON_BORDER_WIDTH / 2);
		}
		graphc.dispose();
		return image;
	}

	public static void save(Display display, ImageData imageData, String filename) throws SWTException {
		Image tmpImage = new Image(display, imageData);
		ImageLoader loader = new ImageLoader();
		loader.data = new ImageData[] {tmpImage.getImageData()};
		loader.save(filename, filename.toLowerCase().endsWith(".jpg") ? SWT.IMAGE_JPEG: SWT.IMAGE_PNG);
		tmpImage.dispose();
	}

	public static void clearCache() {
		imageCacheMap.values().forEach(Image::dispose);
		imageCacheMap.clear();
	}

	public static void clearCache(String path) {
		imageCacheMap.entrySet().stream().filter(x -> x.getKey().startsWith(path)).forEach(x -> x.getValue().dispose());
		imageCacheMap.entrySet().removeIf(x -> x.getKey().startsWith(path));
	}

	public static Image createScaledImage(Display display, Image orgImage, int orgW, int orgH, int width, int height, boolean keepAspectRatio, String name) {
		int cropX = 0;
		int cropY = 0;
		if (keepAspectRatio) {
			double factor = Math.max((double)width / (double)orgW, (double)height / (double)orgH);
			cropX = (int)(((orgW * factor) - width) / factor);
			cropY = (int)(((orgH * factor) - height) / factor);
		}

		Image image = SystemUtils.IS_LINUX && (display.getPrimaryMonitor().getZoom() != 100) 
			? new Image(display, new ImageData(width, height, orgImage.getImageData().depth, orgImage.getImageData().palette))
			: new Image(display, width, height);
		GC graphc = new GC(image);
		graphc.setAntialias(SWT.ON);
		graphc.setInterpolation(SWT.HIGH);
		graphc.drawImage(orgImage, cropX / 2, cropY / 2, orgW - cropX, orgH - cropY, 0, 0, width, height);
		if (name != null) {
			Point size = graphc.textExtent(name);
			graphc.setBackground(blackColor);
			graphc.setForeground(whiteColor);
			graphc.setAlpha(180);
			graphc.drawString(name, width - size.x - 2, height - size.y - 2, false);
		}
		graphc.dispose();
		return image;
	}

	private static Image getImageUsingGifDecoder(Display display, String path) {
		GifDecoder d = new GifDecoder();
		int status = d.read(path);
		ImageData imageData = getImageDataUsingGifDecoder(d, status);
		return imageData != null ? new Image(display, imageData): null;
	}

	public static ImageData getImageDataUsingGifDecoder(InputStream imageInputStream) throws IOException {
		GifDecoder d = new GifDecoder();
		int status = d.read(imageInputStream);
		return getImageDataUsingGifDecoder(d, status);
	}

	private static ImageData getImageDataUsingGifDecoder(GifDecoder d, int status) {
		return (status == GifDecoder.STATUS_OK || status == GifDecoder.STATUS_FORMAT_ERROR) ? getImageDataFrame(d, 0): null;
	}

	protected static ImageData getImageDataFrame(GifDecoder d, int frameNr) {
		BufferedImage bufferedImage = d.getFrame(frameNr);
		if (bufferedImage != null) {
			int[] data = ((DataBufferInt)bufferedImage.getData().getDataBuffer()).getData();
			ImageData imageData = new ImageData(bufferedImage.getWidth(), bufferedImage.getHeight(), 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
			imageData.setPixels(0, 0, data.length, data, 0);
			return imageData;
		}
		return null;
	}
	
	public static ImageData convertAWTImageToSWT(java.awt.Image image) {
        if (image == null) {
            throw new IllegalArgumentException("Null 'image' argument.");
        }
        int w = image.getWidth(null);
        int h = image.getHeight(null);
        if (w == -1 || h == -1) {
            return null;
        }
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics g = bi.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return convertToSWT(bi);
    }
	
	public static ImageData convertToSWT(BufferedImage bufferedImage) {
        if (bufferedImage.getColorModel() instanceof DirectColorModel) {
            DirectColorModel colorModel
                    = (DirectColorModel) bufferedImage.getColorModel();
            PaletteData palette = new PaletteData(colorModel.getRedMask(),
                    colorModel.getGreenMask(), colorModel.getBlueMask());
            ImageData data = new ImageData(bufferedImage.getWidth(),
                    bufferedImage.getHeight(), colorModel.getPixelSize(),
                    palette);
            WritableRaster raster = bufferedImage.getRaster();
            int[] pixelArray = new int[3];
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    raster.getPixel(x, y, pixelArray);
                    int pixel = palette.getPixel(new RGB(pixelArray[0],
                            pixelArray[1], pixelArray[2]));
                    data.setPixel(x, y, pixel);
                }
            }
            return data;
        }
        else if (bufferedImage.getColorModel() instanceof IndexColorModel) {
            IndexColorModel colorModel = (IndexColorModel)
                    bufferedImage.getColorModel();
            int size = colorModel.getMapSize();
            byte[] reds = new byte[size];
            byte[] greens = new byte[size];
            byte[] blues = new byte[size];
            colorModel.getReds(reds);
            colorModel.getGreens(greens);
            colorModel.getBlues(blues);
            RGB[] rgbs = new RGB[size];
            for (int i = 0; i < rgbs.length; i++) {
                rgbs[i] = new RGB(reds[i] & 0xFF, greens[i] & 0xFF,
                        blues[i] & 0xFF);
            }
            PaletteData palette = new PaletteData(rgbs);
            ImageData data = new ImageData(bufferedImage.getWidth(),
                    bufferedImage.getHeight(), colorModel.getPixelSize(),
                    palette);
            data.transparentPixel = colorModel.getTransparentPixel();
            WritableRaster raster = bufferedImage.getRaster();
            int[] pixelArray = new int[1];
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                    raster.getPixel(x, y, pixelArray);
                    data.setPixel(x, y, pixelArray[0]);
                }
            }
            return data;
        }
        return null;
    }
}