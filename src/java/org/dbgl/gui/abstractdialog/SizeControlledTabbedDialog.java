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

import java.util.stream.Stream;
import org.dbgl.gui.controls.CTabFolder_;
import org.dbgl.gui.controls.Composite_;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Shell;


public abstract class SizeControlledTabbedDialog<T> extends SizeControlledButtonDialog<T> {

	protected CTabFolder tabFolder_;

	protected SizeControlledTabbedDialog(Shell parent, String dialogName) {
		super(parent, dialogName);
	}

	@Override
	protected void onShellInit() {
		super.onShellInit();

		GridLayout layout = new GridLayout(2, false);
		layout.horizontalSpacing = layout.marginHeight = layout.marginWidth = 0;
		contents_.setLayout(layout);

		tabFolder_ = CTabFolder_.on(contents_).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).ctrl();
	}

	protected Composite createTabWithComposite(String title, Layout layout) {
		return Composite_.on(tabFolder_).layout(layout).tab(title).build();
	}

	/**
	 * A tab whose contents scroll vertically when the dialog is too small to show them all (e.g. the general settings with many
	 * groups). The returned composite is the one to put the controls in.
	 */
	protected Composite createScrolledTabWithComposite(String title, Layout layout) {
		ScrolledComposite scrolled = new ScrolledComposite(tabFolder_, SWT.V_SCROLL | SWT.H_SCROLL);
		scrolled.setExpandHorizontal(true);
		scrolled.setExpandVertical(true);
		scrolled.getVerticalBar().setIncrement(16);
		Composite content = Composite_.on(scrolled).layout(layout).build();
		scrolled.setContent(content);
		Runnable updateMinSize = () -> {
			if (!scrolled.isDisposed()) {
				int width = Math.max(scrolled.getClientArea().width, content.computeSize(SWT.DEFAULT, SWT.DEFAULT).x); // never squeeze the controls
				scrolled.setMinSize(content.computeSize(width, SWT.DEFAULT));
			}
		};
		scrolled.addListener(SWT.Resize, e -> updateMinSize.run());
		content.addListener(SWT.Resize, e -> scrolled.getDisplay().asyncExec(updateMinSize));
		CTabItem tabItem = new CTabItem(tabFolder_, SWT.NONE);
		tabItem.setText("   " + org.dbgl.service.TextService.getInstance().get(title) + "   ");
		tabItem.setControl(scrolled);
		tabFolder_.setSelection(0);
		return content;
	}

	protected Composite createTabWithComposite(String title, int numColumns) {
		return Composite_.on(tabFolder_).layout(new GridLayout(numColumns, false)).tab(title).build();
	}

	protected CTabItem getTabItemByComposite(Composite composite) {
		return Stream.of(tabFolder_.getItems()).filter(x -> (x.getControl() == composite) || ((x.getControl() instanceof ScrolledComposite sc) && (sc.getContent() == composite))).findFirst().orElse(null);
	}

	protected CTabItem getTabItemByControl(Control control) {
		Composite parent = control.getParent();
		while ((parent != null) && !(parent.getParent() instanceof CTabFolder))
			parent = parent.getParent();
		return getTabItemByComposite(parent);
	}
}
