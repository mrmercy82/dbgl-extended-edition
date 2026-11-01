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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.eclipse.swt.widgets.Group;
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
	
	private Map<String, BiFunction<TemplateProfileBase, Composite, Composite>> pageGenerators_ = new LinkedHashMap<>(); // in tree order, later pages may use controls of earlier ones

	private static final String PAGE_HEADER = "dbgl.pageheader";
	/** set on a composite whose content is only created when its page is shown, but which will have some */
	protected static final String HAS_CONTENT = "dbgl.hascontent";

	/** the complete page tree, in order, so pages without content can be left out of the tree and brought back later */
	private record PageNode(String key, String parentKey, boolean withImage) {
	}

	private final List<PageNode> pageNodes_ = new ArrayList<>();
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
		
		Composite page = pages_.computeIfAbsent(key, k -> {
			Composite p = pageGenerators_.get(k).apply(configurable, rightComposite_);
			onPageCreated(k, p);
			return p;
		});
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

	/** called once for every page, right after it was created */
	protected void onPageCreated(String key, Composite page) {
	}

	/** called when the search text changed, after all pages were created */
	protected void beforeSearch(TemplateProfileBase configurable) {
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
			onPageCreated(k, page);
			page.setVisible(false);
			((GridData)page.getLayoutData()).exclude = true;
			return page;
		});
	}

	protected Composite createPage(String key, Composite composite, int numColumns) {
		Composite page = Composite_.on(composite).grabAllSpace().pageLayout(numColumns).build();
		Chain.on(page).lbl(l -> l.key(key).large().horSpan(numColumns))
			.lbl(l -> l.style(SWT.SEPARATOR | SWT.HORIZONTAL).grabHorSpace().horSpan(numColumns)).build();
		markPageHeader(page);
		return page;
	}

	private static void markPageHeader(Composite page) {
		Stream.of(page.getChildren()).forEach(x -> x.setData(PAGE_HEADER, Boolean.TRUE));
	}
	
	protected Composite createPage(String parentKey, String childKey, Composite composite, int numColumns) {
		Composite page = Composite_.on(composite).grabAllSpace().pageLayout(numColumns).build();
		Chain.on(page)
			.lbl(l -> l.txt(TextService.getInstance().get(parentKey) + " - " + TextService.getInstance().get(childKey)).large().horSpan(numColumns))
			.lbl(l -> l.style(SWT.SEPARATOR | SWT.HORIZONTAL).grabHorSpace().horSpan(numColumns)).build();
		markPageHeader(page);
		return page;
	}
	
	protected TreeItem addPageGenerator(String key, BiFunction<TemplateProfileBase, Composite, Composite> pageGenerator) {
		pageGenerators_.put(key, pageGenerator);
		pageNodes_.add(new PageNode(key, null, true));
		return createTreeItem(null, key, true);
	}
	
	protected TreeItem addPageGenerator(TreeItem parentItem, String key, BiFunction<TemplateProfileBase, Composite, Composite> pageGenerator) {
		pageGenerators_.put(key, pageGenerator);
		pageNodes_.add(new PageNode(key, (String)parentItem.getData(), false));
		return createTreeItem(parentItem, key, false);
	}

	private TreeItem createTreeItem(TreeItem parentItem, String key, boolean withImage) {
		TreeItem treeItem = parentItem == null ? new TreeItem(tree_, SWT.NONE): new TreeItem(parentItem, SWT.NONE);
		treeItem.setData(key);
		treeItem.setText(TextService.getInstance().get(key));
		if (withImage) {
			String img = "bs/" + key.substring(key.lastIndexOf('.') + 1) + ".png";
			treeItem.setImage(ImageService.getResourceImage(getParent().getDisplay(), img));
		}
		return treeItem;
	}

	private static List<TreeItem> allTreeItems(TreeItem[] items) {
		List<TreeItem> result = new ArrayList<>();
		for (TreeItem item: items) {
			result.add(item);
			result.addAll(allTreeItems(item.getItems()));
		}
		return result;
	}

	/**
	 * Leave pages out of the navigation tree when none of their settings are supported by the selected DOSBox version (the
	 * controls are hidden then), and bring them back when they are. A parent page stays as long as one of its sub pages has
	 * content. All pages are created for this, since only a created page knows which of its controls are visible.
	 */
	protected void updatePageTree(TemplateProfileBase configurable, boolean hideEmptyPages) {
		Set<String> shown = new LinkedHashSet<>();
		if (hideEmptyPages) {
			computeAllPages(configurable);
			Set<String> withContent = new HashSet<>();
			pageNodes_.stream().filter(n -> Stream.of(pages_.get(n.key()).getChildren()).anyMatch(SizeControlledPagedDialog::hasVisibleContent)).forEach(n -> withContent.add(n.key()));
			for (PageNode n: pageNodes_) {
				if (withContent.contains(n.key()) || hasDescendantIn(n.key(), withContent))
					shown.add(n.key());
			}
		} else {
			pageNodes_.forEach(n -> shown.add(n.key()));
		}

		List<TreeItem> current = allTreeItems(tree_.getItems());
		if (current.stream().map(x -> (String)x.getData()).toList().equals(new ArrayList<>(shown)))
			return; // nothing changed

		Set<String> expanded = new HashSet<>();
		Set<String> known = new HashSet<>();
		current.forEach(x -> {
			known.add((String)x.getData());
			if (x.getExpanded())
				expanded.add((String)x.getData());
		});
		TreeItem[] sel = tree_.getSelection();
		String selectedKey = sel.length > 0 ? (String)sel[0].getData(): null;

		tree_.setRedraw(false);
		try {
			tree_.removeAll();
			Map<String, TreeItem> items = new HashMap<>();
			for (PageNode n: pageNodes_) {
				if (shown.contains(n.key()))
					items.put(n.key(), createTreeItem(n.parentKey() == null ? null: items.get(n.parentKey()), n.key(), n.withImage()));
			}
			// expand like before; pages that were not in the tree before are expanded like at the start (top level only)
			for (PageNode n: pageNodes_) {
				TreeItem item = items.get(n.key());
				if ((item != null) && (expanded.contains(n.key()) || (!known.contains(n.key()) && (n.parentKey() == null))))
					item.setExpanded(true);
			}
			if (selectedKey != null) {
				String key = selectedKey;
				while ((key != null) && !items.containsKey(key))
					key = parentKeyOf(key);
				if ((key == null) && (tree_.getItemCount() > 0))
					key = (String)tree_.getItem(0).getData(); // the page and all its parents are gone
				if (key != null) {
					tree_.setSelection(items.get(key));
					if (!key.equals(selectedKey))
						renderPage(configurable, items.get(key));
				}
			}
		} finally {
			tree_.setRedraw(true);
		}
	}

	private String parentKeyOf(String key) {
		for (PageNode n: pageNodes_)
			if (n.key().equals(key))
				return n.parentKey();
		return null;
	}

	private boolean hasDescendantIn(String key, Set<String> keys) {
		return pageNodes_.stream().filter(n -> key.equals(n.parentKey())).anyMatch(n -> keys.contains(n.key()) || hasDescendantIn(n.key(), keys));
	}

	/** true if the control shows something besides the page header, separators and filler labels */
	private static boolean hasVisibleContent(Control c) {
		if ((c == null) || !c.getVisible() || (c.getData(PAGE_HEADER) != null) || (c.getData(Chain.UNSUPPORTED) != null))
			return false; // a setting the DOSBox version does not have is no content, even when shown (disabled)
		if (Boolean.TRUE.equals(c.getData(HAS_CONTENT)))
			return true;
		if (c instanceof Label label)
			return ((label.getStyle() & SWT.SEPARATOR) == 0) && (StringUtils.isNotBlank(label.getText()) || (label.getImage() != null));
		if ((c instanceof Group) || (c.getClass() == Composite.class))
			return Stream.of(((Composite)c).getChildren()).anyMatch(SizeControlledPagedDialog::hasVisibleContent);
		return true;
	}
	
	/** the key of the page the control is on, or null */
	protected String pageKeyOf(Control control) {
		Control c = control;
		while ((c != null) && (c.getParent() != rightComposite_))
			c = c.getParent();
		if (c == null)
			return null;
		for (Map.Entry<String, Composite> e: pages_.entrySet())
			if (e.getValue() == c)
				return e.getKey();
		return null;
	}

	protected TreeItem getreeItemByComposite(Composite composite) {
		String key = pages_.entrySet().stream().filter(x -> x.getValue() == composite).map(x -> x.getKey()).findFirst().orElse(null);
		return allTreeItems(tree_.getItems()).stream().filter(x -> x.getData().equals(key)).findFirst().orElse(null);
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
				beforeSearch(base);
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
