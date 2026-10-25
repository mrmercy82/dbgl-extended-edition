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
package org.dbgl.gui.dialog;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.constants.Constants;
import org.dbgl.gui.abstractdialog.SizeControlledTabbedDialog;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.Filter;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.FilterRepository;
import org.dbgl.model.repository.ProfileRepository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;


public class EditFilterDialog extends SizeControlledTabbedDialog<Filter> {

	private static String EMPTY_FIELD = "[ ]";

	private final Filter filter_;
	private final Set<Integer> selectedProfileIds_;
	private final String[] columnNames_;

	private List<DosboxVersion> dbversionsList_;
	private List<Profile> profilesList_;

	public EditFilterDialog(Shell parent, Filter filter, Set<Integer> selectedProfileIds, String[] columnNames) {
		super(parent, "filterdialog");
		filter_ = filter;
		selectedProfileIds_ = selectedProfileIds;
		columnNames_ = columnNames;
	}

	@Override
	protected String getDialogTitle() {
		return filter_ == null ? text_.get("dialog.filter.title.add"): text_.get("dialog.filter.title.edit", new Object[] {filter_.getTitle(), filter_.getId()});
	}

	@Override
	protected boolean prepare() {
		try {
			dbversionsList_ = new DosboxVersionRepository().listAll();
			profilesList_ = new ProfileRepository().list(StringUtils.EMPTY, StringUtils.EMPTY, dbversionsList_);
		} catch (SQLException e) {
			Mess_.on(shell_).exception(e).warning();
		}
		return true;
	}

	@Override
	protected void onShellCreated() {
		Composite composite = createTabWithComposite("dialog.filter.tab.info", 2);

		SashForm sashForm = createSashForm(composite, 2);

		Composite leftComposite = Composite_.on(sashForm).innerLayout(2).build();
		Text filterTitle = Chain.on(leftComposite).lbl(l -> l.key("dialog.filter.title")).txt(t -> t).text();
		Text filterText = Chain.on(leftComposite).lbl(l -> l.key("dialog.filter.filter")).txt(Text_.Builder::multi).text();

		Tree tree = createTree(sashForm, SWT.BORDER | SWT.CHECK);

		for (int i = 0; i < columnNames_.length; i++) {
			if (i == 20)
				continue; // skip screenshot category

			TreeItem item = new TreeItem(tree, SWT.NONE);
			item.setText(columnNames_[i]);

			class TreeNodeItem implements Comparable<TreeNodeItem> {
				String value;
				String subQuery;
				String likeQuery;

				public TreeNodeItem(String v, String q, String l) {
					value = StringUtils.isEmpty(v) ? EMPTY_FIELD: v;
					subQuery = q;
					likeQuery = l;
				}

				@Override
				public int hashCode() {
					return Objects.hash(value, subQuery, likeQuery);
				}

				@Override
				public boolean equals(Object obj) {
					if (this == obj)
						return true;
					if (!(obj instanceof TreeNodeItem))
						return false;
					TreeNodeItem that = (TreeNodeItem)obj;
					return Strings.CS.equals(value, that.value) && Strings.CS.equals(subQuery, that.subQuery) && Strings.CS.equals(likeQuery, that.likeQuery);
				}

				@Override
				public int compareTo(TreeNodeItem o) {
					int eq1 = value.equals(EMPTY_FIELD) ? 1: 0;
					int eq2 = o.value.equals(EMPTY_FIELD) ? 1: 0;
					if (eq1 + eq2 > 0)
						return eq2 - eq1;
					return value.compareToIgnoreCase(o.value);
				}
			}

			SortedSet<TreeNodeItem> values = new TreeSet<>();
			switch (i) {
				case 0:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(p.getTitle(), "GAM.TITLE='" + p.getTitle() + "'", "GAM.TITLE"));
					break;
				case 1:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(text_.toString(p.hasSetup()), p.hasSetup() ? "GAM.SETUP<>''": "(GAM.SETUP IS NULL OR GAM.SETUP='')", null));
					break;
				case 2:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(p.getDeveloper(), "DEV.NAME='" + p.getDeveloper() + "'", "DEV.NAME"));
					break;
				case 3:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(p.getPublisher(), "PUBL.NAME='" + p.getPublisher() + "'", "PUBL.NAME"));
					break;
				case 4:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(p.getGenre(), "GEN.NAME='" + p.getGenre() + "'", "GEN.NAME"));
					break;
				case 5:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(p.getYear(), "YR.YEAR='" + p.getYear() + "'", null));
					break;
				case 6:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(p.getStatus(), "STAT.STAT='" + p.getStatus() + "'", "STAT.STAT"));
					break;
				case 7:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(text_.toString(p.isFavorite()), "GAM.FAVORITE=" + p.isFavorite(), null));
					break;
				case 8:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(String.valueOf(p.getId()), "GAM.ID=" + p.getId(), null));
					break;
				case 9:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(String.valueOf(p.getDosboxVersion().getId()), "GAM.DBVERSION_ID=" + p.getDosboxVersion().getId(), null));
					break;
				case 10:
				case 11:
				case 12:
				case 13:
					for (Profile p: profilesList_) {
						int idx = i - Constants.RO_COLUMN_NAMES;
						values.add(new TreeNodeItem(String.valueOf(p.getCustomStrings()[idx]), "CUST" + (idx + 1) + ".VALUE='" + p.getCustomStrings()[idx] + "'", "CUST" + (idx + 1) + ".VALUE"));
					}
					break;
				case 14:
				case 15:
				case 16:
				case 17:
					for (Profile p: profilesList_) {
						int idx = i - Constants.RO_COLUMN_NAMES;
						values.add(new TreeNodeItem(String.valueOf(p.getCustomStrings()[idx]), "GAM.CUSTOM" + (idx + 1) + "='" + p.getCustomStrings()[idx] + "'", "GAM.CUSTOM" + (idx + 1)));
					}
					break;
				case 18:
				case 19:
					for (Profile p: profilesList_) {
						int idx = i - Constants.RO_COLUMN_NAMES - 8;
						values.add(new TreeNodeItem(String.valueOf(p.getCustomInts()[idx]), "GAM.CUSTOM" + (idx + 9) + "=" + p.getCustomInts()[idx], null));
					}
					break;
				case 21:
					for (Profile p: profilesList_) {
						String dbversionTitle = BaseRepository.findById(dbversionsList_, p.getDosboxVersion().getId()).getTitle();
						values.add(new TreeNodeItem(dbversionTitle, "GAM.DBVERSION_ID=" + p.getDosboxVersion().getId(), null));
					}
					break;
				case 22:
					for (Profile p: profilesList_) {
						Date date = p.getStats().getCreated();
						values.add(new TreeNodeItem(text_.toString(date), toDatabaseString("GAM.STATS_CREATED", date), null));
					}
					break;
				case 23:
					for (Profile p: profilesList_) {
						Date date = p.getStats().getModified();
						values.add(new TreeNodeItem(text_.toString(date), toDatabaseString("GAM.STATS_LASTMODIFY", date), null));
					}
					break;
				case 24:
					for (Profile p: profilesList_) {
						Date date = p.getStats().getLastRun();
						values.add(new TreeNodeItem(text_.toString(date), toDatabaseString("GAM.STATS_LASTRUN", date), null));
					}
					break;
				case 25:
					for (Profile p: profilesList_) {
						Date date = p.getProfileStats().getLastSetup();
						values.add(new TreeNodeItem(text_.toString(date), toDatabaseString("GAM.STATS_LASTSETUP", date), null));
					}
					break;
				case 26:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(String.valueOf(p.getStats().getRuns()), "GAM.STATS_RUNS=" + p.getStats().getRuns(), null));
					break;
				case 27:
					for (Profile p: profilesList_)
						values.add(new TreeNodeItem(String.valueOf(p.getProfileStats().getSetups()), "GAM.STATS_SETUPS=" + p.getProfileStats().getSetups(), null));
					break;
				case 28:
				case 29:
				case 30:
				case 31:
					for (Profile p: profilesList_) {
						int idx = i - Constants.RO_COLUMN_NAMES - Constants.EDIT_COLUMN_NAMES_1;
						int c = i - Constants.RO_COLUMN_NAMES - Constants.STATS_COLUMN_NAMES + 1;
						values.add(new TreeNodeItem(String.valueOf(p.getCustomStrings()[idx]), "GAM.CUSTOM" + c + "='" + p.getCustomStrings()[idx] + "'", "GAM.CUSTOM" + c));
					}
					break;
				default:
			}
			for (TreeNodeItem v: values) {
				TreeItem valueItem = new TreeItem(item, SWT.NONE);
				valueItem.setText(v.value);
				valueItem.setData(v.subQuery);
				valueItem.setGrayed(true);

				if (v.likeQuery != null) {
					String sentence = v.value.replaceAll("\\p{Punct}", " ");
					String[] words = sentence.split("\\s+");
					if (words.length > 1) {
						for (String w: words) {
							TreeItem likeItem = new TreeItem(valueItem, SWT.NONE);
							likeItem.setText(w);
							likeItem.setData("UPPER(" + v.likeQuery + ") LIKE '%" + w.toUpperCase() + "%'");
						}
					}
				}
			}
		}

		tree.addListener(SWT.Selection, event -> {
			if (event.detail == SWT.CHECK) {
				TreeItem tItem = (TreeItem)event.item;
				int depth = depth(tItem);
				if (depth == 0) {
					if (tItem.getChecked()) {
						if (tItem.getGrayed() || getAllCheckedItems(tItem).isEmpty()) {
							tItem.setChecked(false);
						}
					} else {
						if (tItem.getGrayed()) {
							tItem.setGrayed(false);
						} else {
							tItem.setGrayed(true);
							tItem.setChecked(true);
						}
					}
				} else if (depth == 1) {
					TreeItem parent = tItem.getParentItem();
					parent.setChecked(!getAllCheckedItems(parent).isEmpty());
				} else {
					if (tItem.getChecked()) {
						if (tItem.getGrayed()) {
							tItem.setChecked(false);
						}
					} else {
						if (tItem.getGrayed()) {
							tItem.setGrayed(false);
						} else {
							tItem.setGrayed(true);
							tItem.setChecked(true);
						}
					}
					TreeItem parent = tItem.getParentItem().getParentItem();
					parent.setChecked(!getAllCheckedItems(parent).isEmpty());
				}
			}
		});

		tree.addListener(SWT.Selection, event -> {
			List<String> rootQueriesAnd = new ArrayList<>();
			List<String> rootQueriesOr = new ArrayList<>();
			StringBuilder generatedTitle = null;

			for (TreeItem rootItem: tree.getItems()) {
				if (rootItem.getChecked()) {
					List<String> subQueriesAnd = new ArrayList<>();
					List<String> subQueriesOr = new ArrayList<>();

					for (TreeItem item: getAllCheckedItems(rootItem)) {
						TreeItem parent = item.getParentItem();
						TreeItem parentOfParent = null;
						if (parent != null) {
							if (generatedTitle == null) {
								parentOfParent = parent.getParentItem();
								if (parentOfParent != null)
									generatedTitle = new StringBuilder(parentOfParent.getText() + ": " + item.getText());
								else
									generatedTitle = new StringBuilder(parent.getText() + ": " + item.getText());
							} else if (!generatedTitle.toString().endsWith("...")) {
								generatedTitle.append("...");
							}
							if (item.getGrayed())
								subQueriesOr.add((String)item.getData());
							else
								subQueriesAnd.add((String)item.getData());
						}
					}

					String resultAnd = StringUtils.join(subQueriesAnd, StringUtils.LF + "\tAND ");
					String resultOr = StringUtils.join(subQueriesOr, StringUtils.LF + "\tOR ");
					boolean and = StringUtils.isNotBlank(resultAnd);
					boolean or = StringUtils.isNotBlank(resultOr);
					String result = null;
					if (and && or) {
						result = "(" + resultAnd + ")" + StringUtils.LF + "AND" + StringUtils.LF + "(" + resultOr + ")";
					} else if (and) {
						result = resultAnd;
					} else if (or) {
						result = resultOr;
					}

					if (result != null) {
						if (rootItem.getGrayed())
							rootQueriesOr.add("(" + result + ")");
						else
							rootQueriesAnd.add("(" + result + ")");
					}
				}
			}

			String resultAnd = StringUtils.join(rootQueriesAnd, StringUtils.LF + "AND" + StringUtils.LF);
			String resultOr = StringUtils.join(rootQueriesOr, StringUtils.LF + "OR" + StringUtils.LF);
			boolean and = StringUtils.isNotBlank(resultAnd);
			boolean or = StringUtils.isNotBlank(resultOr);
			String result = null;
			if (and && or) {
				result = "(" + resultAnd + ")" + StringUtils.LF + "AND" + StringUtils.LF + "(" + resultOr + ")";
			} else if (and) {
				result = resultAnd;
			} else if (or) {
				result = resultOr;
			}

			if (StringUtils.isNotBlank(generatedTitle))
				filterTitle.setText(generatedTitle.toString());
			if (StringUtils.isNotBlank(result))
				filterText.setText(result);
		});

		sashForm.setWeights(60, 40);

		Text results = Chain.on(composite).lbl(l -> l.key("dialog.filter.result")).txt(Text_.Builder::readOnly).text();

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (!isValid(filterTitle)) {
					return;
				}
				try {
					if (filter_ == null) {
						Filter f = new Filter(filterTitle.getText(), filterText.getText());
						result_ = new FilterRepository().add(f);
					} else {
						filter_.setTitle(filterTitle.getText());
						filter_.setFilter(filterText.getText());
						new FilterRepository().update(filter_);
						result_ = filter_;
					}
				} catch (SQLException e) {
					Mess_.on(shell_).exception(e).warning();
				}
				shell_.close();
			}
		});

		filterTitle.addListener(SWT.Verify, event -> {
			String oldTitle = ((Text)event.widget).getText();
			String newTitle = oldTitle.substring(0, event.start) + event.text + oldTitle.substring(event.end);
			if (StringUtils.isEmpty(filterText.getText()) || filterText.getText().equals("UPPER(GAM.TITLE) LIKE '%" + oldTitle.toUpperCase() + "%'")) {
				if (StringUtils.isEmpty(newTitle)) {
					filterText.setText(StringUtils.EMPTY);
				} else {
					filterText.setText("UPPER(GAM.TITLE) LIKE '%" + newTitle.toUpperCase() + "%'");
				}
			}
		});
		filterText.addModifyListener(event -> {
			try {
				List<Profile> tmpList = new ProfileRepository().list("", filterText.getText(), dbversionsList_);
				results.setText(text_.get("dialog.filter.notice.results", new Object[] {tmpList.size()}));
				okButton_.setEnabled(true);
			} catch (SQLException e) {
				results.setText(text_.get("dialog.filter.error.invalidcondition"));
				okButton_.setEnabled(false);
			}
		});

		// init values
		if (filter_ != null) {
			filterTitle.setText(filter_.getTitle());
			filterText.setText(filter_.getFilter());
		} else {
			if (selectedProfileIds_ != null) {
				filterText.setText("GAM.ID IN (" + StringUtils.join(selectedProfileIds_, ',') + ")");
			}
		}
		filterTitle.setFocus();
	}

	private static int depth(TreeItem item) {
		int result = 0;
		while ((item = item.getParentItem()) != null)
			result++;
		return result;
	}

	private List<TreeItem> getAllCheckedItems(TreeItem treeItem) {
		List<TreeItem> result = new ArrayList<>();
		for (TreeItem item: treeItem.getItems()) {
			if (item.getChecked())
				result.add(item);
			result.addAll(getAllCheckedItems(item));
		}
		return result;
	}

	private boolean isValid(Text title) {
		Mess_.Builder mess = Mess_.on(shell_);
		if (StringUtils.isBlank(title.getText())) {
			mess.key("dialog.filter.required.title").bind(title);
		}
		return mess.valid();
	}

	private static String toDatabaseString(String dbField, Date date) {
		StringBuilder sb = new StringBuilder();
		if (date == null) {
			sb.append(dbField).append(" IS NULL");
		} else {
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			sb.append("YEAR(").append(dbField).append(")=").append(cal.get(Calendar.YEAR));
			sb.append(" AND MONTH(").append(dbField).append(")=").append(cal.get(Calendar.MONTH) + 1);
			sb.append(" AND DAYOFMONTH(").append(dbField).append(")=").append(cal.get(Calendar.DAY_OF_MONTH));
		}
		return sb.toString();
	}
}
