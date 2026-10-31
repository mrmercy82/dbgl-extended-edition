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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.DarkTheme;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.service.ImageService;
import org.dbgl.service.TextService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;


public abstract class SizeControlledPagedDialog<T> extends SizeControlledButtonDialog<T> {

	protected Text search_;
	protected Tree tree_;
	protected ScrolledComposite rightComposite_;
	
	private Map<String, BiFunction<TemplateProfileBase, Composite, Composite>> pageGenerators_ = new HashMap<>();
	private Map<String, Composite> pages_ = new HashMap<>();

	private static Font bold_;

	protected SizeControlledPagedDialog(Shell parent, String dialogName) {
		super(parent, dialogName);
	}

	@Override
	protected void onShellInit() {
		super.onShellInit();

		GridLayout layout = new GridLayout(2, false);
		layout.horizontalSpacing = layout.marginHeight = layout.marginWidth = 0;
		contents_.setLayout(layout);
		
		SashForm sash = createSashForm(contents_);
		sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		sash.setLayout(new FillLayout());

		Composite leftComposite = Composite_.on(sash).layout(new GridLayout(1, false)).build();
		search_ = Chain.on(leftComposite).txt(t -> t.style(SWT.SEARCH | SWT.ICON_CANCEL | SWT.ICON_SEARCH)
				.layoutData(new GridData(SWT.FILL, SWT.TOP, true, false))).text();
		tree_ = createTree(leftComposite, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		tree_.setFocus();

		rightComposite_ = createScrolledComposite(sash, SWT.V_SCROLL);
		rightComposite_.setExpandVertical(true);
		rightComposite_.setExpandHorizontal(true);
		rightComposite_.setLayout(new GridLayout(1, true));
		rightComposite_.addControlListener(ControlListener.controlResizedAdapter(e -> {
			Control ctrl = rightComposite_.getContent();
			if (ctrl != null) {
				Rectangle r = rightComposite_.getClientArea();
				rightComposite_.setMinSize(ctrl.computeSize(r.width, SWT.DEFAULT));
			}
		}));
		
		sash.setWeights(new int[] {22, 78});
	}
	
	protected void renderPage(TemplateProfileBase configurable, Widget item) {
		String key = (String)item.getData();

		if (pages_.containsKey(key) && pages_.get(key).isVisible())
			return; // page is already visible
		
		pages_.values().stream().filter(x -> x.isVisible()).findAny().ifPresent(x -> {
			x.setVisible(false);
			((GridData) x.getLayoutData()).exclude = true;
		});
		
		Composite page = pages_.computeIfAbsent(key, k -> pageGenerators_.get(k).apply(configurable, rightComposite_));
		page.setVisible(true);
		((GridData)page.getLayoutData()).exclude = false;
		onPageShown(page);
		
		page.pack();
		rightComposite_.setContent(page);
		rightComposite_.setMinSize(page.computeSize(rightComposite_.getClientArea().width, SWT.DEFAULT));
	}
	
	/** called before a page is laid out and shown */
	protected void onPageShown(Composite page) {
	}

	/** re-layout the page currently shown, e.g. after controls were hidden or shown */
	protected void relayoutVisiblePage() {
		pages_.values().stream().filter(Composite::isVisible).findAny().ifPresent(page -> {
			onPageShown(page);
			page.pack();
			page.layout(true, true);
			rightComposite_.setMinSize(page.computeSize(rightComposite_.getClientArea().width, SWT.DEFAULT));
		});
	}

	protected void computeAllPages(TemplateProfileBase configurable) {
		pageGenerators_.keySet().forEach(key -> computePage(configurable, key));
	}
	
	protected Composite computePage(TemplateProfileBase configurable, String key) {
		return pages_.computeIfAbsent(key, k -> {
			Composite page = pageGenerators_.get(k).apply(configurable, rightComposite_);
			page.setVisible(false);
			((GridData)page.getLayoutData()).exclude = true;
			return page;
		});
	}

	protected Composite createPage(String key, Composite composite, int numColumns) {
		Composite page = Composite_.on(composite).grabAllSpace().pageLayout(numColumns).build();
		Chain.on(page).lbl(l -> l.key(key).large().horSpan(numColumns))
			.lbl(l -> l.style(SWT.SEPARATOR | SWT.HORIZONTAL).grabHorSpace().horSpan(numColumns)).build();
		return page;
	}
	
	protected Composite createPage(String parentKey, String childKey, Composite composite, int numColumns) {
		Composite page = Composite_.on(composite).grabAllSpace().pageLayout(numColumns).build();
		Chain.on(page)
			.lbl(l -> l.txt(TextService.getInstance().get(parentKey) + " - " + TextService.getInstance().get(childKey)).large().horSpan(numColumns))
			.lbl(l -> l.style(SWT.SEPARATOR | SWT.HORIZONTAL).grabHorSpace().horSpan(numColumns)).build();
		return page;
	}
	
	protected TreeItem addPageGenerator(String key, BiFunction<TemplateProfileBase, Composite, Composite> pageGenerator) {
		pageGenerators_.put(key, pageGenerator);
		
		TreeItem treeItem = new TreeItem(tree_, SWT.NONE);
		treeItem.setData(key);
		treeItem.setText(TextService.getInstance().get(key));
		String img = "bs/" + key.substring(key.lastIndexOf('.') + 1) + ".png";
		treeItem.setImage(ImageService.getResourceImage(getParent().getDisplay(), img));
		return treeItem;
	}
	
	protected TreeItem addPageGenerator(TreeItem parentItem, String key, BiFunction<TemplateProfileBase, Composite, Composite> pageGenerator) {
		pageGenerators_.put(key, pageGenerator);
		
		TreeItem treeItem = new TreeItem(parentItem, SWT.NONE);
		treeItem.setData(key);
		treeItem.setText(TextService.getInstance().get(key));
		return treeItem;
	}
	
	protected TreeItem getreeItemByComposite(Composite composite) {
		String key = pages_.entrySet().stream().filter(x -> x.getValue() == composite).map(x -> x.getKey()).findFirst().orElse(null);
		return Stream.of(tree_.getItems()).filter(x -> x.getData().equals(key)).findFirst().orElse(null);
	}

	protected TreeItem getTreeItemByControl(Control control) {
		Composite parent = control.getParent();
		while ((parent != null) && (parent.getParent() != rightComposite_))
			parent = parent.getParent();
		return getreeItemByComposite(parent);
	}

	protected void addSearchListener(TemplateProfileBase base) {
		search_.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent arg0) {
				computeAllPages(base);
				String s = search_.getText().strip().toLowerCase();
				Stream.of(tree_.getItems()).forEach(x -> highlight(s, x));
			}
		});
	}

	private void highlight(String s, TreeItem item) {
		boolean inPage = Stream.of(pages_.get(item.getData()).getChildren()).map(x -> 
			containedInControl(s, x)).reduce(Boolean.FALSE, Boolean::logicalOr);
		if (StringUtils.isNotBlank(s) && (inPage || item.getText().toLowerCase().contains(s))) {
			item.setExpanded(true);
			if (bold_ == null) {
				FontData[] fd = item.getFont().getFontData();
				Stream.of(fd).forEach(x -> x.setStyle(SWT.BOLD));
				bold_ = new Font(display_, fd);
			}
			item.setFont(bold_);
		} else {
			item.setFont(null);
		}
		Stream.of(item.getItems()).forEach(x -> highlight(s, x));
	}

	private boolean containedInControl(String s, Control c) {
		if (c instanceof Label) {
			String txt = ((Label) c).getText().toLowerCase();
			if (StringUtils.isNotBlank(txt)) {
				boolean hl = StringUtils.isNotBlank(s) && txt.contains(s);
				c.setBackground(hl ? display_.getSystemColor(DarkTheme.forced() ? SWT.COLOR_DARK_RED: SWT.COLOR_YELLOW): c.getParent().getBackground());
				return hl;
			}
		} else if (c instanceof Composite) {
			return Stream.of(((Composite)c).getChildren()).map(x -> containedInControl(s, x)).reduce(Boolean.FALSE, Boolean::logicalOr);
		}
		return false;
	}
}
