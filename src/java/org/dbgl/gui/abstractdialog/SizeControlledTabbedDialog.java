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

	protected Composite createTabWithComposite(String title, int numColumns) {
		return Composite_.on(tabFolder_).layout(new GridLayout(numColumns, false)).tab(title).build();
	}

	protected CTabItem getTabItemByComposite(Composite composite) {
		return Stream.of(tabFolder_.getItems()).filter(x -> x.getControl() == composite).findFirst().orElse(null);
	}

	protected CTabItem getTabItemByControl(Control control) {
		Composite parent = control.getParent();
		while ((parent != null) && !(parent.getParent() instanceof CTabFolder))
			parent = parent.getParent();
		return getTabItemByComposite(parent);
	}
}
