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
package org.dbgl.gui.controls;

import org.apache.commons.lang3.BooleanUtils;
import org.dbgl.service.ITextService;
import org.dbgl.service.ImageService;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.searchengine.WebSearchEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;


public class SearchEngineSelector {

	private final Display display_;
	private final ITextService text_;
	private final SettingsService settings_;

	private final ToolItem toolItem_;
	private final boolean addGameWizard_;

	public SearchEngineSelector(ToolBar toolBar, boolean addGameWizard) {
		display_ = toolBar.getDisplay();
		text_ = TextService.getInstance();
		settings_ = SettingsService.getInstance();
		addGameWizard_ = addGameWizard;

		WebSearchEngine defaultEngine = WebSearchEngine.getBySimpleName(settings_.getValue("gui", "searchengine"));
		if (defaultEngine == null)
			defaultEngine = SettingsService.availableWebSearchEngines().get(0);
		toolItem_ = new ToolItem(toolBar, SWT.DROP_DOWN);
		toolItem_.setImage(ImageService.getResourceImage(display_, defaultEngine.getIcon()));
		toolItem_.setToolTipText(text_.get("dialog.profile.consultsearchengine", new String[] {defaultEngine.getName()}));

		Menu menu = new Menu(toolBar.getShell(), SWT.POP_UP);
		for (WebSearchEngine engine: SettingsService.availableWebSearchEngines()) {
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setImage(ImageService.getResourceImage(display_, engine.getIcon()));
			item.setText(engine.getName());
			item.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent event) {
					toolItem_.setImage(((MenuItem)event.widget).getImage());
					toolItem_.setToolTipText(text_.get("dialog.profile.consultsearchengine", new String[] {engine.getName()}));
					toolItem_.setData("selected", true);
					settings_.setValue("gui", "searchengine", engine.getSimpleName());
					if (addGameWizard_)
						settings_.setBooleanValue("addgamewizard", "consultsearchengine", true);
				}
			});
		}

		toolItem_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if ((event.detail == SWT.ARROW) && (toolItem_.getData("profile") == null)) {
					Rectangle rect = toolItem_.getBounds();
					Point pt = toolBar.toDisplay(new Point(rect.x, rect.y + rect.height));
					menu.setLocation(pt.x, pt.y);
					menu.setVisible(true);
				}
			}
		});
	}

	public void addToggleSelectionListener(boolean initialValue) {
		if (!initialValue)
			toolItem_.setImage(ImageService.createDisabledImage(ImageService.getResourceImage(display_, WebSearchEngine.getBySimpleName(settings_.getValue("gui", "searchengine")).getIcon())));
		toolItem_.setData("selected", initialValue);

		toolItem_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (event.detail != SWT.ARROW) {
					WebSearchEngine engine = WebSearchEngine.getBySimpleName(settings_.getValue("gui", "searchengine"));
					boolean selected = !((Boolean)toolItem_.getData("selected"));
					toolItem_.setImage(
						selected ? ImageService.getResourceImage(display_, engine.getIcon()): ImageService.createDisabledImage(ImageService.getResourceImage(display_, engine.getIcon())));
					toolItem_.setData("selected", selected);
					if (addGameWizard_)
						settings_.setBooleanValue("addgamewizard", "consultsearchengine", selected);
				}
			}
		});
	}

	public boolean isSelected() {
		return BooleanUtils.isTrue((Boolean)toolItem_.getData("selected"));
	}

	public ToolItem getToolItem() {
		return toolItem_;
	}
}
