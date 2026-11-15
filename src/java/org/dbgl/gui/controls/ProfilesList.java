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
package org.dbgl.gui.controls;

import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.gui.interfaces.ReOrderable;
import org.dbgl.gui.listeners.MeasureListener;
import org.dbgl.gui.listeners.PaintListener;
import org.dbgl.gui.listeners.ToolTipListener;
import org.dbgl.model.ThumbInfo;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.eclipse.nebula.widgets.gallery.AbstractGalleryItemRenderer;
import org.eclipse.nebula.widgets.gallery.Gallery;
import org.eclipse.nebula.widgets.gallery.GalleryItem;
import org.eclipse.nebula.widgets.gallery.NoGroupRenderer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


public class ProfilesList {

	public class ProfilesListItem {
		private final GalleryItem gItem_;
		private final TableItem tItem_;

		public ProfilesListItem(ProfilesList list) {
			if (list.type_ == ProfilesListType.TABLE) {
				gItem_ = null;
				tItem_ = new TableItem(list.table_, SWT.BORDER);
				list.filled_.set(list.table_.getItemCount() - 1); // alpha 50: a row added by hand is filled by who adds it
			} else {
				gItem_ = new GalleryItem(list.group_, SWT.NONE);
				tItem_ = null;
			}
		}

		public ProfilesListItem(TableItem item) {
			gItem_ = null;
			tItem_ = item;
		}

		public ProfilesListItem(GalleryItem item) {
			gItem_ = item;
			tItem_ = null;
		}

		public Object getData() {
			return tItem_ != null ? tItem_.getData(): gItem_.getData();
		}

		public void setData(Object obj) {
			if (tItem_ != null)
				tItem_.setData(obj);
			else
				gItem_.setData(obj);
		}

		public void resetCachedInfo() {
			ThumbInfo thumbInfo = (ThumbInfo)getData();
			thumbInfo.resetCachedInfo();
			setData(thumbInfo);

			if (gItem_ != null)
				gItem_.setImage(null);
		}

		/** a warning sign for profiles with invalid values or missing folders or files */
		public void setWarning(boolean warning) {
			setMarker(warning, false);
		}

		/** the warning sign; else, for a profile with hints only (if they are shown), a note */
		public void setMarker(boolean warning, boolean hint) {
			setMarker(warning, false, hint);
		}

		/** as {@link #setMarker(boolean, boolean)}; severe: the red sign (e.g. the configuration file of the profile is gone) */
		public void setMarker(boolean warning, boolean severe, boolean hint) {
			if (tItem_ != null) {
				Image want = warning ? (severe ? error_: warning_): hint ? hint_: null;
				if (tItem_.getImage(0) != want)
					tItem_.setImage(0, want);
			} else {
				Image want = warning ? (severe ? errorLarge_: warningLarge_): hint ? hintLarge_: null;
				if (gItem_.getData(AbstractGalleryItemRenderer.OVERLAY_TOP_RIGHT) != want) {
					gItem_.setData(AbstractGalleryItemRenderer.OVERLAY_TOP_RIGHT, want);
					gItem_.getParent().redraw();
				}
			}
		}

		public void setText(int i, int columnId, String columnName, String value) {
			if (tItem_ != null)
				tItem_.setText(i, value);
			else {
				if (i == 0)
					gItem_.setText(1, StringUtils.EMPTY);
				if (columnId == 0)
					gItem_.setText(value);
				if (columnId == 7)
					gItem_.setData(AbstractGalleryItemRenderer.OVERLAY_BOTTOM_LEFT, value.equals(TextService.getInstance().get("general.yes")) ? topdog_: null);
				if (StringUtils.isEmpty(value))
					return;
				StringBuilder s = new StringBuilder(gItem_.getText(1));
				if (!StringUtils.isEmpty(s))
					s.append(StringUtils.LF);
				s.append(columnName).append(": ").append(value);
				gItem_.setText(1, s.toString());
			}
		}
	}

	private final ProfilesListType type_;
	private final Gallery gallery_;
	private final GalleryItem group_;
	private final Color bgColor_;
	private final Table table_;
	private final Image topdog_, warning_, warningLarge_, error_, errorLarge_, hint_, hintLarge_;

	private int lastSearchEventTime_;
	private java.util.function.BiConsumer<ProfilesListItem, Integer> filler_;
	private java.util.function.IntFunction<String> label_;
	private final java.util.BitSet filled_ = new java.util.BitSet(); // the rows of the table (their places) that have their texts

	private static final Listener paintListener = new PaintListener();
	private static final Listener measureListener = new MeasureListener();
	private static final Listener toolTipOpenListener = new ToolTipListener();

	private static final SettingsService settings = SettingsService.getInstance();

	public enum ProfilesListType {
		TABLE, SMALL_TILES, MEDIUM_TILES, LARGE_TILES, SMALL_BOXES, MEDIUM_BOXES, LARGE_BOXES
	}

	public ProfilesList(Composite composite, ProfilesListType type) {
		this(composite, type, null, null, null);
	}

	public ProfilesList(Composite composite, ProfilesListType type, ReOrderable reOrderable, int[] columnIds, String[] columnNames) {
		type_ = type;
		topdog_ = ImageService.getResourceImage(composite.getShell().getDisplay(), ImageService.IMG_TOPDOG);
		warning_ = ImageService.getResourceImage(composite.getShell().getDisplay(), ImageService.IMG_WARNING);
		warningLarge_ = ImageService.getResourceImage(composite.getShell().getDisplay(), ImageService.IMG_WARNING_LARGE);
		error_ = ImageService.getResourceImage(composite.getShell().getDisplay(), ImageService.IMG_ERROR);
		errorLarge_ = ImageService.getResourceImage(composite.getShell().getDisplay(), ImageService.IMG_ERROR_LARGE);
		hint_ = ImageService.getResourceImage(composite.getShell().getDisplay(), ImageService.IMG_HINT);
		hintLarge_ = ImageService.getResourceImage(composite.getShell().getDisplay(), ImageService.IMG_HINT_LARGE);

		if (type == ProfilesListType.TABLE) {
			// alpha 50: a virtual table. A row gets its texts when it comes into view, so a list of thousands of profiles is
			// there at once; before, every row was filled each time the list was built
			table_ = Table_.on(composite).header().multi().virtual().build();
			table_.addListener(SWT.SetData, e -> {
				TableItem item = (TableItem)e.item;
				int index = table_.indexOf(item);
				// the table asks when the row has no texts, also for a row that had them before the table was emptied
				if ((filler_ != null) && (index >= 0)) {
					filled_.set(index);
					filler_.accept(new ProfilesListItem(item), index);
				}
			});
			// a virtual table does not search by itself when letters are typed in it (Windows), so it is done here, as Windows
			// does it: the typed beginning of the first column, the same letter again goes on to the next row with it
			StringBuilder typed = new StringBuilder(8);
			table_.addListener(SWT.KeyDown, e -> {
				if ((label_ == null) || ((e.stateMask & (SWT.CTRL | SWT.ALT | SWT.COMMAND)) != 0) || !Character.isLetterOrDigit(e.character))
					return;
				e.doit = false;
				if ((e.time - lastSearchEventTime_) > 750)
					typed.setLength(0);
				lastSearchEventTime_ = e.time;
				int found = typeAhead(typed, e.character, table_.getSelectionIndex(), table_.getItemCount(), label_);
				if (found >= 0) {
					table_.setSelection(found);
					table_.showSelection();
					table_.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
				}
			});
			for (int i = 0; i < columnIds.length; i++)
				addProfileColumn(reOrderable, columnIds, columnNames[columnIds[i]], i);

			table_.setSortColumn(table_.getColumn(findColumnById(columnIds, settings.getIntValues("gui", "sortcolumn")[0])));
			table_.setSortDirection(settings.getBooleanValues("gui", "sortascending")[0] ? SWT.UP: SWT.DOWN);
			table_.setColumnOrder(settings.getIntValues("gui", "columnorder"));

			for (int i = 0; i < columnIds.length; i++) {
				if (columnIds[i] == 20) { // screenshot column
					table_.setData(i);
					table_.addListener(SWT.PaintItem, paintListener);
					table_.addListener(SWT.MeasureItem, measureListener);
				}
			}

			gallery_ = null;
			group_ = null;
			bgColor_ = null;

		} else {

			table_ = null;

			gallery_ = new Gallery(composite, SWT.V_SCROLL | SWT.MULTI | SWT.BORDER);
			if (DarkTheme.forced()) {
				gallery_.setForeground(DarkTheme.defaultForeground);
				gallery_.setBackground(DarkTheme.inputBackground);
			}
			gallery_.setAntialias(SWT.OFF);
			gallery_.setLowQualityOnUserAction(true);
			gallery_.setHigherQualityDelay(100);
			int[] rgb = settings.getIntValues("gui", "gallerybackgroundcolor");
			if (rgb.length == 3) {
				bgColor_ = new Color(rgb[0], rgb[1], rgb[2]);
				gallery_.setBackground(bgColor_);
			} else {
				bgColor_ = null;
			}
			NoGroupRenderer gr = new NoGroupRenderer();

			switch (type) {
				case LARGE_TILES:
					gr.setItemSize(settings.getIntValue("gui", "large_tile_width"), settings.getIntValue("gui", "large_tile_height"));
					break;
				case MEDIUM_TILES:
					gr.setItemSize(settings.getIntValue("gui", "medium_tile_width"), settings.getIntValue("gui", "medium_tile_height"));
					break;
				case SMALL_TILES:
					gr.setItemSize(settings.getIntValue("gui", "small_tile_width"), settings.getIntValue("gui", "small_tile_height"));
					break;
				case LARGE_BOXES:
					gr.setItemSize(settings.getIntValue("gui", "large_box_width"), settings.getIntValue("gui", "large_box_height"));
					break;
				case MEDIUM_BOXES:
					gr.setItemSize(settings.getIntValue("gui", "medium_box_width"), settings.getIntValue("gui", "medium_box_height"));
					break;
				case SMALL_BOXES:
					gr.setItemSize(settings.getIntValue("gui", "small_box_width"), settings.getIntValue("gui", "small_box_height"));
					break;
				default:
					break;
			}
			
			boolean truncPositionEnd = false;
			String truncPos = settings.getValue("gui", "tile_title_trunc_pos");
			if (truncPos != null && truncPos.equalsIgnoreCase("end"))
				truncPositionEnd = true;

			gr.setAutoMargin(true);
			gr.setMinMargin(1);
			gallery_.setGroupRenderer(gr);
			gallery_.setItemRenderer(new GalleryItemRenderer(truncPositionEnd));
			group_ = new GalleryItem(gallery_, SWT.NONE);

			gallery_.getShell().addListener(SWT.Deactivate, toolTipOpenListener);
			gallery_.addListener(SWT.Dispose, toolTipOpenListener);
			gallery_.addListener(SWT.KeyDown, toolTipOpenListener);
			gallery_.addListener(SWT.MouseMove, toolTipOpenListener);
			gallery_.addListener(SWT.MouseWheel, toolTipOpenListener);
			gallery_.addListener(SWT.MouseUp, toolTipOpenListener);
			gallery_.addListener(SWT.MouseHover, toolTipOpenListener);
			gallery_.addListener(SWT.PaintItem, paintListener);

			StringBuilder searchTerm = new StringBuilder(8);
			gallery_.addKeyListener(new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					if ((e.keyCode >= 97 && e.keyCode <= 122) || (e.keyCode >= 48 && e.keyCode <= 57)) {
						if ((e.time - lastSearchEventTime_) > 750)
							searchTerm.setLength(0);
						lastSearchEventTime_ = e.time;

						searchTerm.append(e.character);
						GalleryItem item = Stream.of(group_.getItems()).filter(x -> Strings.CI.startsWith(x.getText(), searchTerm)).findFirst().orElse(null);
						if (item == null)
							searchTerm.deleteCharAt(searchTerm.length() - 1);
						else
							gallery_.setSelection(new GalleryItem[] {item});
					}
				}
			});
		}
	}

	/**
	 * The row to go to for a letter typed in the list, or -1. One letter (or the same letter again): the next row after the
	 * selected one that begins with it, going round. More letters: the first row from the selected one on that begins with
	 * them; a letter nothing begins with is dropped again.
	 */
	public static int typeAhead(StringBuilder typed, char ch, int selected, int count, java.util.function.IntFunction<String> label) {
		typed.append(Character.toLowerCase(ch));
		boolean same = typed.chars().allMatch(x -> x == typed.charAt(0));
		String term = same ? typed.substring(0, 1): typed.toString();
		int from = same ? selected + 1: Math.max(selected, 0);
		for (int n = 0; n < count; n++) {
			int i = Math.floorMod(from + n, count);
			if (Strings.CI.startsWith(StringUtils.defaultString(label.apply(i)), term))
				return i;
		}
		if (!same)
			typed.deleteCharAt(typed.length() - 1);
		return -1;
	}

	/**
	 * Alpha 50, the list as a table: how a row gets its texts (called when it comes into view or is asked for), and the text
	 * of the first column of a row for the search by typing.
	 */
	public void setSource(java.util.function.BiConsumer<ProfilesListItem, Integer> filler, java.util.function.IntFunction<String> label) {
		filler_ = filler;
		label_ = label;
	}

	/** the list as a table: this many rows, empty for now; false for tiles and boxes, which are filled row by row */
	public boolean showRows(int count) {
		if ((type_ != ProfilesListType.TABLE) || (filler_ == null))
			return false;
		table_.setItemCount(count);
		table_.deselectAll();
		table_.clearAll();
		filled_.clear(); // no row has its texts any more (after the table was emptied: it may ask for a row while it changes)
		if (count > 0)
			table_.setTopIndex(0); // as a list built anew: from its beginning, until a selection or the caller says otherwise
		return true;
	}

	/** the rows of the table that have their texts, in the order of the list */
	private TableItem[] filledItems() {
		return filled_.stream().filter(i -> i < table_.getItemCount()).mapToObj(table_::getItem).toArray(TableItem[]::new);
	}

	/** the row if it has its texts already, else null: for what only matters for rows that were shown (a warning sign) */
	public ProfilesListItem peekItem(int index) {
		if (type_ != ProfilesListType.TABLE)
			return new ProfilesListItem(group_.getItem(index));
		// asked by its place, without touching the row: a row the table has not made yet stays unmade
		return (filler_ != null) && !filled_.get(index) ? null: new ProfilesListItem(table_.getItem(index));
	}

	public int getTopIndex() {
		return type_ == ProfilesListType.TABLE ? table_.getTopIndex(): -1;
	}

	public void setTopIndex(int index) {
		if ((type_ == ProfilesListType.TABLE) && (index >= 0) && (index < table_.getItemCount()))
			table_.setTopIndex(index);
	}

	private static int findColumnById(int[] columnIds, int id) {
		return IntStream.range(0, columnIds.length).filter(x -> columnIds[x] == id).findFirst().orElse(-1);
	}

	private void addProfileColumn(ReOrderable reOrderable, int[] columnIds, String title, int colIndex) {
		String width = "column" + (columnIds[colIndex] + 1) + "width";
		TableColumn column = new TableColumn(table_, SWT.NONE);
		column.setWidth(settings.getIntValue("gui", width));
		column.setMoveable(true);
		column.setText(title);
		if ((columnIds[colIndex] == 8) || (columnIds[colIndex] == 9) || (columnIds[colIndex] == 18) || (columnIds[colIndex] == 19)) { // numeric values
			column.setAlignment(SWT.RIGHT);
		}
		if ((columnIds[colIndex] == 20) || (columnIds[colIndex] == 1) || (columnIds[colIndex] == 7)) { // screenshot; alpha 48: tick of setup, star of favourite
			column.setAlignment(SWT.CENTER);
		}
		if (columnIds[colIndex] == org.dbgl.model.OrderingVector.DISK_COLUMN) // alpha 49: sizes
			column.setAlignment(SWT.RIGHT);
		// alpha 49: or as chosen in the settings (0: as above)
		int align = settings.getIntValue("gui", "column" + (columnIds[colIndex] + 1) + "align");
		if ((align >= 1) && (align <= 3))
			column.setAlignment(align == 1 ? SWT.LEFT: align == 2 ? SWT.CENTER: SWT.RIGHT);
		column.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				table_.setSortDirection(table_.getSortColumn().equals(event.widget) && table_.getSortDirection() == SWT.UP ? SWT.DOWN: SWT.UP);
				table_.setSortColumn((TableColumn)event.widget);
				reOrderable.doReorder(colIndex, table_.getSortDirection());
			}
		});
		column.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent event) {
				settings.setIntValue("gui", width, column.getWidth());
			}

			@Override
			public void controlMoved(ControlEvent event) {
				if (event.time != 0) // workaround for buggy SWT behavior in GTK
					settings.setIntValues("gui", "columnorder", table_.getColumnOrder());
			}
		});
	}

	/**
	 * In the table view, the warning sign in front of a title shows what is wrong as tooltip (problems), and clicking it calls
	 * onClick, both with the index of the profile.
	 */
	public void setWarningHandlers(java.util.function.IntFunction<String> problems, java.util.function.IntConsumer onClick) {
		if (type_ != ProfilesListType.TABLE) {
			setGalleryWarningHandlers(problems, onClick);
			return;
		}
		// the sign takes the whole image area in front of the title, a little wider for an easier hit
		java.util.function.Function<org.eclipse.swt.graphics.Point, Integer> signAt = p -> {
			TableItem item = table_.getItem(p);
			if ((item == null) || (item.getImage(0) == null))
				return -1;
			org.eclipse.swt.graphics.Rectangle r = item.getImageBounds(0);
			return (p.x >= r.x - 2) && (p.x <= r.x + r.width + 2) ? table_.indexOf(item): -1;
		};
		org.dbgl.gui.LongToolTips.setProvider(table_, p -> {
			int index = signAt.apply(p);
			return index >= 0 ? problems.apply(index): null;
		});
		table_.addListener(SWT.MouseUp, event -> {
			int index = event.button == 1 ? signAt.apply(new org.eclipse.swt.graphics.Point(event.x, event.y)): -1;
			if (index >= 0)
				table_.getDisplay().asyncExec(() -> onClick.accept(index));
		});
	}

	/** tiles and boxes: the title as tooltip, plus the problems for a profile with a warning sign (top right corner) */
	private void setGalleryWarningHandlers(java.util.function.IntFunction<String> problems, java.util.function.IntConsumer onClick) {
		gallery_.removeListener(SWT.MouseHover, toolTipOpenListener); // replaced by the tooltip below
		java.util.function.Function<org.eclipse.swt.graphics.Point, Integer> signAt = p -> {
			GalleryItem item = gallery_.getItem(p);
			if ((item == null) || (item.getData(AbstractGalleryItemRenderer.OVERLAY_TOP_RIGHT) == null))
				return -1;
			org.eclipse.swt.graphics.Rectangle b = item.getBounds();
			int size = warningLarge_.getBounds().width + 8;
			return (p.x >= b.x + b.width - size) && (p.y <= b.y + size) ? group_.indexOf(item): -1;
		};
		org.dbgl.gui.LongToolTips.setProvider(gallery_, p -> {
			GalleryItem item = gallery_.getItem(p);
			if (item == null)
				return null;
			String title = item.getText(1);
			String problemsText = item.getData(AbstractGalleryItemRenderer.OVERLAY_TOP_RIGHT) != null ? problems.apply(group_.indexOf(item)): null;
			return problemsText == null ? title: title + "\n\n" + problemsText;
		});
		gallery_.addListener(SWT.MouseUp, event -> {
			int index = event.button == 1 ? signAt.apply(new org.eclipse.swt.graphics.Point(event.x, event.y)): -1;
			if (index >= 0)
				gallery_.getDisplay().asyncExec(() -> onClick.accept(index));
		});
	}

	public void addMouseListener(MouseAdapter mouseAdapter) {
		if (type_ == ProfilesListType.TABLE)
			table_.addMouseListener(mouseAdapter);
		else
			gallery_.addMouseListener(mouseAdapter);
	}

	public void addKeyListener(KeyAdapter keyAdapter) {
		if (type_ == ProfilesListType.TABLE)
			table_.addKeyListener(keyAdapter);
		else
			gallery_.addKeyListener(keyAdapter);
	}

	public void addTraverseListener(TraverseListener travListener) {
		if (type_ == ProfilesListType.TABLE)
			table_.addTraverseListener(travListener);
		else
			gallery_.addTraverseListener(travListener);
	}

	public void addSelectionListener(SelectionAdapter selectProfAdapter) {
		if (type_ == ProfilesListType.TABLE)
			table_.addSelectionListener(selectProfAdapter);
		else
			gallery_.addSelectionListener(selectProfAdapter);
	}

	public void setFocus() {
		if (type_ == ProfilesListType.TABLE)
			table_.setFocus();
		else
			gallery_.setFocus();
	}

	public void setRedraw(boolean b) {
		if (type_ == ProfilesListType.TABLE)
			table_.setRedraw(b);
		else
			gallery_.setRedraw(b);
	}

	public void redraw() {
		if (type_ == ProfilesListType.TABLE)
			table_.redraw();
		else
			gallery_.redraw();
	}

	public void setMenu(Menu menu) {
		if (type_ == ProfilesListType.TABLE)
			table_.setMenu(menu);
		else
			gallery_.setMenu(menu);
	}

	public int getSelectionCount() {
		return type_ == ProfilesListType.TABLE ? table_.getSelectionCount(): gallery_.getSelectionCount();
	}

	public int getSelectionIndex() {
		if (type_ == ProfilesListType.TABLE)
			return table_.getSelectionIndex();

		GalleryItem[] items = gallery_.getSelection();
		return items.length == 0 ? -1: group_.indexOf(items[0]);
	}

	public int[] getSelectionIndices() {
		return type_ == ProfilesListType.TABLE ? table_.getSelectionIndices(): Stream.of(gallery_.getSelection()).mapToInt(group_::indexOf).filter(x -> x != -1).toArray();
	}

	public void setSelection(int index) {
		if (type_ == ProfilesListType.TABLE)
			table_.setSelection(index);
		else {
			GalleryItem item = group_.getItem(index);
			if (item != null)
				gallery_.setSelection(new GalleryItem[] {item});
		}
	}

	public void setSelection(int[] indices) {
		if (type_ == ProfilesListType.TABLE)
			table_.setSelection(indices);
		else
			gallery_.setSelection(IntStream.of(indices).mapToObj(group_::getItem).filter(Objects::nonNull).toArray(GalleryItem[]::new));
	}

	/** alpha 49: scrolls so that the (first) chosen profile is in view */
	/** alpha 49: the selected profile as the first row shown (as far as the list can be scrolled) */
	public void showSelectionAtTop() {
		if ((type_ == ProfilesListType.TABLE) && (table_.getSelectionIndex() >= 0))
			table_.setTopIndex(table_.getSelectionIndex());
		else
			showSelection();
	}

	public void showSelection() {
		if (type_ == ProfilesListType.TABLE) {
			table_.showSelection();
		} else {
			GalleryItem[] items = gallery_.getSelection();
			if (items.length > 0)
				gallery_.showItem(items[0]);
		}
	}

	public int getItemCount() {
		return type_ == ProfilesListType.TABLE ? table_.getItemCount(): group_.getItemCount();
	}

	public ProfilesListItem getItem(int index) {
		if (type_ != ProfilesListType.TABLE)
			return new ProfilesListItem(group_.getItem(index));
		TableItem item = table_.getItem(index);
		ProfilesListItem result = new ProfilesListItem(item);
		if ((filler_ != null) && !filled_.get(index)) {
			filled_.set(index);
			filler_.accept(result, index); // alpha 50: a row not shown yet is filled when it is asked for
		}
		return result;
	}

	public void removeAll() {
		if (type_ == ProfilesListType.TABLE) {
			table_.removeAll();
			filled_.clear();
		} else
			Stream.of(group_.getItems()).forEach(group_::remove);
	}

	public void remove(int index) {
		if (type_ == ProfilesListType.TABLE) {
			table_.remove(index);
			// the rows below move up by one, and what is known of them with them
			for (int i = index; i <= filled_.length(); i++)
				filled_.set(i, filled_.get(i + 1));
		} else
			group_.remove(index);
	}

	public void selectAll() {
		if (type_ == ProfilesListType.TABLE)
			table_.selectAll();
		else
			group_.selectAll();
	}

	public ProfilesListItem[] getItems() {
		// alpha 50: of a table the rows that have their texts; the others are filled anew when they come into view
		return type_ == ProfilesListType.TABLE ? Stream.of(filler_ == null ? table_.getItems(): filledItems()).map(ProfilesListItem::new).toArray(ProfilesListItem[]::new)
				: Stream.of(group_.getItems()).map(ProfilesListItem::new).toArray(ProfilesListItem[]::new);
	}

	public Control getControl() {
		return type_ == ProfilesListType.TABLE ? table_: gallery_;
	}

	public void dispose() {
		if (type_ == ProfilesListType.TABLE) {
			table_.removeListener(SWT.PaintItem, paintListener);
			table_.removeListener(SWT.MeasureItem, measureListener);
			table_.dispose();
		} else {
			gallery_.getShell().removeListener(SWT.Deactivate, toolTipOpenListener);
			gallery_.removeListener(SWT.KeyDown, toolTipOpenListener);
			gallery_.removeListener(SWT.MouseMove, toolTipOpenListener);
			gallery_.removeListener(SWT.MouseWheel, toolTipOpenListener);
			gallery_.removeListener(SWT.MouseUp, toolTipOpenListener);
			gallery_.removeListener(SWT.MouseHover, toolTipOpenListener);
			gallery_.removeListener(SWT.PaintItem, paintListener);
			gallery_.dispose();
		}
	}
}
