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
package org.dbgl.gui.abstractdialog;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.gui.controls.GridData_;
import org.dbgl.gui.controls.MenuItem_;
import org.dbgl.service.ITextService;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Dialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ExpandBar;
import org.eclipse.swt.widgets.ExpandItem;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;


public abstract class BaseDialog<T> extends Dialog {

	private static final int MIN_BUTTON_WIDTH = 80;
	private static Integer averageCharacterWidth_;

	protected final SelectionAdapter closeShellAdapter = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			shell_.close();
		}
	};

	protected final SelectionAdapter browseAdapter = new SelectionAdapter() {
		@Override
		public void widgetSelected(SelectionEvent event) {
			SystemUtils.openForBrowsing(event.text, getParent());
		}
	};

	protected final Display display_;
	protected final int shellStyle_;
	protected Shell shell_;

	protected final ITextService text_;

	protected T result_;

	protected BaseDialog(Shell parent, int shellStyle) {
		super(parent, SWT.NONE);
		display_ = parent.getDisplay();
		shellStyle_ = shellStyle;
		text_ = TextService.getInstance();
	}

	protected BaseDialog(Shell parent) {
		this(parent, SWT.TITLE | SWT.CLOSE | SWT.BORDER | SWT.RESIZE | SWT.APPLICATION_MODAL);
	}

	public T open() {
		if (!prepare())
			return null;

		shell_ = shellStyle_ == SWT.PRIMARY_MODAL ? getParent(): new Shell(getParent(), shellStyle_);
		if (DarkTheme.forced()) {
			shell_.setBackground(DarkTheme.dialogBackground);
		}
		shell_.setText(getDialogTitle());

		onShellInit();

		onShellCreated();

		shell_.open();

		onShellOpened();

		while (!shell_.isDisposed()) {
			shellDispatchCallback();
			if (!display_.readAndDispatch()) {
				display_.sleep();
			}
		}

		onClose();

		return result_;
	}

	protected boolean prepare() {
		return true;
	}

	protected void onShellInit() {
	}

	protected abstract String getDialogTitle();

	protected abstract void onShellCreated();

	protected void onShellOpened() {
	}

	protected void shellDispatchCallback() {
	}

	protected void onClose() {
	}

	/* Font */
	protected static Font stringToFont(Device device, String[] font, Font defaultFont) {
		try {
			return new Font(device, font[0], Integer.parseInt(font[1]), Integer.parseInt(font[2]));
		} catch (Exception e) {
			e.printStackTrace();
			return defaultFont;
		}
	}

	protected static String fontToString(Font font) {
		FontData data = font.getFontData()[0];
		return data.getName() + '|' + data.getHeight() + '|' + data.getStyle();
	}

	protected static void setLayoutDataButtons(Button... buttons) {
		Optional<Integer> maxWidth = Stream.of(buttons).map(x -> x.computeSize(SWT.DEFAULT, SWT.DEFAULT).x).max(Comparator.naturalOrder());
		if (maxWidth.isPresent()) {
			if (averageCharacterWidth_ == null) {
				GC gc = new GC(buttons[0]);
				averageCharacterWidth_ = (int)gc.getFontMetrics().getAverageCharacterWidth();
				gc.dispose();
			}
			int width = (4 * averageCharacterWidth_) + Math.max(maxWidth.get(), MIN_BUTTON_WIDTH);

			for (Button button: buttons) {
				button.setLayoutData(new GridData_(SWT.BEGINNING, SWT.FILL, true, false).widthHint(width).build());
			}
		}
	}

	/* ScrolledComposite */
	protected static ScrolledComposite createScrolledComposite(Composite composite, int style) {
		ScrolledComposite sComposite = new ScrolledComposite(composite, style);
		if (DarkTheme.forced()) {
			sComposite.setBackground(composite.getBackground());
		}
		return sComposite;
	}

	/* ExpandItem */
	protected static ExpandItem createExpandItem(ExpandBar expandBar, String text, boolean expanded, Composite composite) {
		ExpandItem expandItem = new ExpandItem(expandBar, SWT.NONE);
		expandItem.setText(TextService.getInstance().get(text));
		expandItem.setExpanded(expanded);
		expandItem.setControl(composite);
		expandItem.setHeight(composite.computeSize(SWT.DEFAULT, SWT.DEFAULT).y);
		return expandItem;
	}

	/* Link */
	protected static Link createLink(Composite composite, String text, SelectionListener listener) {
		Link link = new Link(composite, SWT.NONE);
		if (DarkTheme.forced()) {
			link.setBackground(composite.getBackground());
			link.setLinkForeground(DarkTheme.linkForeground);
		}
		link.setText(text);
		link.addSelectionListener(listener);
		return link;
	}

	/* ToolItem */
	protected static ToolItem createImageToolItem(ToolBar toolBar, int style, Image image, String tooltip, SelectionListener listener) {
		ToolItem toolItem = new ToolItem(toolBar, style);
		toolItem.setImage(image);
		toolItem.setToolTipText(tooltip);
		if (listener != null)
			toolItem.addSelectionListener(listener);
		return toolItem;
	}

	protected static ToolItem createImageToolItem(ToolBar toolBar, String title, String img, SelectionListener listener) {
		ToolItem toolItem = new ToolItem(toolBar, SWT.PUSH | SWT.BORDER);
		int displaySelection = SettingsService.getInstance().getIntValue("gui", "buttondisplay");
		if (displaySelection != 1)
			toolItem.setImage(ImageService.getResourceImage(toolBar.getDisplay(), img));
		if (displaySelection == 2)
			toolItem.setToolTipText(TextService.getInstance().get(title));
		else
			toolItem.setText(TextService.getInstance().get(title));
		toolItem.addSelectionListener(listener);
		return toolItem;
	}

	protected static ToolItem createSeparatorToolItem(ToolBar toolBar, int width) {
		ToolItem toolItem = new ToolItem(toolBar, SWT.SEPARATOR | SWT.BORDER);
		toolItem.setWidth(width);
		return toolItem;
	}

	/* Menu */
	protected static Menu createMenu(Menu parentMenu, String text, String img) {
		MenuItem menuItem = MenuItem_.on(parentMenu).key(text).image(img).build();
		Menu menu = new Menu(menuItem);
		menuItem.setMenu(menu);
		return menu;
	}

	protected static Menu createMenu(Menu parentMenu, String text) {
		MenuItem menuItem = MenuItem_.on(parentMenu).key(text).build();
		Menu menu = new Menu(menuItem);
		menuItem.setMenu(menu);
		return menu;
	}

	/* TableColumn */
	protected static TableColumn createTableColumn(Table table, int width, String text) {
		TableColumn column = new TableColumn(table, SWT.NONE);
		column.setWidth(width);
		column.setText(TextService.getInstance().get(text));
		return column;
	}

	/* SashForm */
	protected static SashForm createSashForm(Composite composite) {
		SashForm sashForm = new SashForm(composite, SWT.HORIZONTAL);
		if (DarkTheme.forced()) {
			sashForm.setBackground(composite.getBackground());
		}
		return sashForm;
	}
	
	protected static SashForm createSashForm(Composite composite, int horSpan) {
		SashForm sashForm = createSashForm(composite);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, horSpan, 1));
		return sashForm;
	}

	/* Tree */
	protected static Tree createTree(Composite composite, int style) {
		Tree tree = new Tree(composite, style);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		if (DarkTheme.forced()) {
			tree.setBackground(DarkTheme.inputBackground);
			tree.setForeground(DarkTheme.defaultForeground);
		}
		return tree;
	}

	/* Row */
	protected static Composite createRow(Composite composite) {
		RowLayout rowLayout = new RowLayout();
		rowLayout.spacing = 20;
		rowLayout.marginBottom = rowLayout.marginLeft = 0;
		rowLayout.marginTop = rowLayout.marginBottom = 4;
		return Composite_.on(composite).layoutData(new GridData(SWT.LEFT, SWT.TOP, false, false)).layout(rowLayout).build();
	}

	/* ToolBar */
	protected static ToolBar createToolBar(Composite composite) {
		ToolBar toolBar = new ToolBar(composite, SWT.FLAT);
		if (DarkTheme.forced()) {
			toolBar.setBackground(DarkTheme.toolbarBackground);
			toolBar.setForeground(DarkTheme.defaultForeground);
		}
		return toolBar;
	}

	/* ExpandBar */
	protected static ExpandBar createExpandBar(Composite composite) {
		ExpandBar expandBar = new ExpandBar(composite, SWT.V_SCROLL);
		if (DarkTheme.forced()) {
			expandBar.setBackground(composite.getBackground());
		}
		return expandBar;
	}
}
