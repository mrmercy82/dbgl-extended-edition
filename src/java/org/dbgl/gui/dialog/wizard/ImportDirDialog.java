/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-11-07. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
package org.dbgl.gui.dialog.wizard;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.dbgl.constants.Constants;
import org.dbgl.gui.abstractdialog.JobWizardDialog;
import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.Label_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.ProgressBar_;
import org.dbgl.gui.controls.Table_;
import org.dbgl.gui.controls.Text_;
import org.dbgl.gui.thread.ImportDirThread;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.factory.ProfileFactory;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.DosboxVersionRepository;
import org.dbgl.model.repository.GameFilesRepository;
import org.dbgl.model.repository.GameFilesRepository.FileData;
import org.dbgl.model.repository.GameFilesRepository.FileSet;
import org.dbgl.model.repository.GameFilesRepository.FileType;
import org.dbgl.model.repository.GameFilesRepository.GameData;
import org.dbgl.model.repository.GameFilesRepository.GameDirEntry;
import org.dbgl.model.repository.TemplateRepository;
import org.dbgl.service.ImageService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.searchengine.MetropolisSearchEngine;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;


public class ImportDirDialog extends JobWizardDialog<Object> {

	private static final String TRIANGLE_DOWN = "\u25BE";

	private final File dir_;
	private final List<Profile> existingProfiles_;

	private DosboxVersion defaultDosboxVersion_;
	private List<Template> templatesList_;
	private Profile profile_;

	private ProgressBar progress_, finished_;
	private Text scanInfo_;
	private List<GameDirEntry> games_;
	private Table table_;

	private Combo machine_, core_, cycles_, mapper_;
	private Button consultConf_;

	public ImportDirDialog(Shell parent, File dir, List<Profile> exisitingProfiles) {
		super(parent, "importdirdialog");
		dir_ = dir;
		existingProfiles_ = exisitingProfiles;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.importdir.title");
	}

	@Override
	protected boolean prepare() {
		try {
			List<DosboxVersion> dbversionsList = new DosboxVersionRepository().listAll();
			templatesList_ = new TemplateRepository().listAll(dbversionsList);

			StringBuilder warningsLog = new StringBuilder();

			defaultDosboxVersion_ = BaseRepository.findDefault(dbversionsList);
			warningsLog.append(defaultDosboxVersion_.resetAndLoadConfiguration());

			Template template = BaseRepository.findDefault(templatesList_);
			if (template != null)
				warningsLog.append(template.resetAndLoadConfiguration());

			profile_ = ProfileFactory.create(defaultDosboxVersion_, template);

			if (StringUtils.isNotBlank(warningsLog))
				Mess_.on(getParent()).txt(warningsLog.toString()).warning();

			return true;
		} catch (Exception e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
	}

	@Override
	protected void onShellCreated() {
		Group progressGroup = Group_.on(shell_).layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false)).layout(new GridLayout()).key("dialog.importdir.processing").build();
		Label_.on(progressGroup).key("dialog.importdir.intro", dir_.getPath()).build();
		progress_ = ProgressBar_.on(progressGroup).indeterminate().build();
		finished_ = ProgressBar_.on(progressGroup).sel(100).build();
		((GridData)finished_.getLayoutData()).exclude = true;
		finished_.setVisible(false);
		scanInfo_ = Text_.on(progressGroup).multi().wrap().readOnly().build();
		addStep(progressGroup);

		Group gamesGroup = Group_.on(shell_).layout(new GridLayout(6, true)).key("dialog.importdir.selectgames").build();

		table_ = Table_.on(gamesGroup).horSpan(6).check().scroll().header().build();

		Listener sortListener = e -> {
			int selIndex = table_.getSelectionIndex();
			GameDirEntry selWebProfile = selIndex != -1 ? games_.get(selIndex): null;
			TableColumn column = (TableColumn)e.widget;
			int index = (Integer)column.getData();
			switch (index) {
				case 1:
					Collections.sort(games_, new GameDirEntry.byDir());
					break;
				case 2:
					Collections.sort(games_, new GameDirEntry.byTitle());
					break;
				case 3:
					Collections.sort(games_, new GameDirEntry.byMain());
					break;
				case 4:
					Collections.sort(games_, new GameDirEntry.bySetup());
					break;
				case 5:
					Collections.sort(games_, new GameDirEntry.byPublisher());
					break;
				case 6:
					Collections.sort(games_, new GameDirEntry.byYear());
					break;
				case 7:
					Collections.sort(games_, new GameDirEntry.byScore());
					break;
				default: // do nothing
			}
			table_.removeAll();
			populate(table_);
			table_.setSortColumn(column);
			table_.setSortDirection(SWT.UP);
			for (int i = 0; i < games_.size(); i++) {
				if (selWebProfile == games_.get(i)) {
					table_.setSelection(i);
					break;
				}
			}
		};

		addColumn("dialog.importdir.column.choose", 0, sortListener);
		addColumn("dialog.importdir.column.folder", 1, sortListener);
		addColumn("dialog.importdir.column.title", 2, sortListener);
		addColumn("dialog.importdir.column.main", 3, sortListener);
		addColumn("dialog.importdir.column.setup", 4, sortListener);
		addColumn("dialog.importdir.column.publisher", 5, sortListener);
		addColumn("dialog.importdir.column.year", 6, sortListener);
		addColumn("dialog.importdir.column.score", 7, sortListener);

		Chain.on(gamesGroup).but(b -> b.text().key("button.all").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				for (TableItem item: table_.getItems())
					item.setChecked(true);
			}
		})).but(b -> b.text().key("button.none").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				for (TableItem item: table_.getItems())
					item.setChecked(false);
			}
		})).but(b -> b.text().key("dialog.importdir.button.deselectwithoutmain").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IntStream.range(0, games_.size()).filter(x -> !games_.get(x).getOptMain().isPresent()).forEach(x -> table_.getItem(x).setChecked(false));
			}
		})).but(b -> b.text().key("dialog.importdir.button.deselectexistinggames").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IntStream.range(0, games_.size()).filter(
					x -> existingProfiles_.stream().anyMatch(y -> games_.get(x).dir_.equals(y.getCombinedConfiguration().getAutoexec().getCanonicalGameDir()))).forEach(
						x -> table_.getItem(x).setChecked(false));
			}
		})).build();
		Chain scoreFilter = Chain.on(gamesGroup).but(b -> b.text().key("dialog.importdir.button.deselectbelowscore")).spn(s -> s.min(10).max(100).select(35)).build();
		scoreFilter.getButton().addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				int score = scoreFilter.getSpinner().getSelection();
				IntStream.range(0, games_.size()).filter(x -> games_.get(x).score_ < score).forEach(x -> table_.getItem(x).setChecked(false));
			}
		});
		addStep(gamesGroup);

		Group step6Group = Group_.on(shell_).layout(new GridLayout(3, false)).key("dialog.addgamewizard.step6").build();
		Combo template = Chain.on(step6Group).lbl(l -> l.key("dialog.profile.template")).cmb(
			c -> c.wide().items(templatesList_.stream().map(Template::getTitle).toArray(String[]::new)).select(BaseRepository.indexOfDefault(templatesList_))).combo();
		Button_.on(step6Group).text().key("dialog.profile.reloadsettings").tooltip("dialog.profile.reloadsettings.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (template.getSelectionIndex() != -1) {
					doReloadTemplate(templatesList_.get(template.getSelectionIndex()));
				}
			}
		}).build();
		machine_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.machine")).cmb(
			c -> c.horSpan(2).items("profile", profile_.machineSectionItem()).visibleItemCount(20).tooltip(
				"dialog.template.machine.tooltip")).combo();
		core_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.core")).cmb(c -> c.horSpan(2).items("profile", "core").visibleItemCount(20).tooltip("dialog.template.core.tooltip")).combo();
		cycles_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.cycles")).cmb(
			c -> c.editable().horSpan(2).items("profile", "cycles").visibleItemCount(15).tooltip("dialog.template.cycles.tooltip")).combo();
		mapper_ = Chain.on(step6Group).lbl(l -> l.key("dialog.template.mapperfile")).cmb(
			c -> c.horSpan(2).items(new String[] {text_.get("dialog.addgamewizard.mapper.generic"), text_.get("dialog.addgamewizard.mapper.specific")}).visibleItemCount(5).tooltip(
				"dialog.template.mapperfile.tooltip")).combo();
		mapper_.select(settings_.getBooleanValue("addgamewizard", "useuniquemapperfile") ? 1: 0);
		mapper_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				settings_.setBooleanValue("addgamewizard", "useuniquemapperfile", ((Combo)event.widget).getSelectionIndex() == 1);
			}
		});
		consultConf_ = Chain.on(step6Group).lbl(l -> l).but(b -> b.layoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false)).horSpan(2).imageText(ImageService.IMG_SHARE,
			"button.consultconfsearchengine", Constants.DBCONFWS).toggle()).button();
		addStep(step6Group);

		addFinalStep("dialog.importdir.progress", "dialog.importdir.startimport");

		updateControlsByProfile();
	}

	private void addColumn(String title, int colIndex, Listener sortListener) {
		String width = "importdir_column_" + (colIndex + 1) + "width";
		TableColumn column = createTableColumn(table_, settings_.getIntValue("gui", width), title);
		column.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent event) {
				settings_.setIntValue("gui", width, ((TableColumn)event.widget).getWidth());
			}
		});
		column.addListener(SWT.Selection, sortListener);
		column.setData(colIndex);
	}

	@Override
	protected void onShellOpened() {
		super.onShellOpened();

		nextButton_.setEnabled(false);

		new Thread() {
			@Override
			public void run() {
				try {
					defaultDosboxVersion_.resetAndLoadConfiguration();

					GameFilesRepository repo = new GameFilesRepository();

					List<String> knownFilenames = repo.listAllFilenames();
					display_.asyncExec(() -> scanInfo_.append(text_.get("dialog.importdir.notice.scandirforknownfiles", new Object[] {dir_.getPath(), knownFilenames.size()}).concat("  ")));

					List<ImmutablePair<File, byte[]>> fileMd5s = new ArrayList<>();
					for (File file: FileUtils.listFiles(dir_, new NameFileFilter(knownFilenames), TrueFileFilter.INSTANCE))
						fileMd5s.add(new ImmutablePair<>(file, FilesUtils.md5(file)));

					display_.asyncExec(() -> {
						scanInfo_.append(text_.get("dialog.importdir.notice.foundknownfiles", new Object[] {fileMd5s.size()}).concat(scanInfo_.getLineDelimiter()));
						scanInfo_.append(text_.get("dialog.importdir.notice.scoringfilesets").concat("  "));
					});

					Map<Integer, GameData> games = repo.getGamesWithMd5Ids(fileMd5s);

					games_ = new ArrayList<>();
					for (Map.Entry<File, List<ImmutablePair<File, byte[]>>> dirFile: fileMd5s.stream().collect(Collectors.groupingBy(x -> x.getKey().getParentFile())).entrySet()) {
						GameDirEntry gameDirEntry = constructGameDirEntry(dirFile.getKey(), dirFile.getValue(), games);
						if (gameDirEntry != null)
							games_.add(gameDirEntry);
					}
					Collections.sort(games_);

					display_.syncExec(() -> {
						scanInfo_.append(text_.get("dialog.importdir.notice.foundgames", new Object[] {games_.size()}).concat(scanInfo_.getLineDelimiter()));
						progress_.setVisible(false);
						((GridData)progress_.getLayoutData()).exclude = true;
						finished_.setVisible(true);
						((GridData)finished_.getLayoutData()).exclude = false;
						finished_.getParent().layout();
						if (!games_.isEmpty()) {
							constructGameList();
							nextButton_.setEnabled(true);
							goForward();
						}
					});
				} catch (SQLException | NoSuchAlgorithmException | IOException e) {
					display_.syncExec(() -> Mess_.on(shell_).exception(e).warning());
				}
			}
		}.start();
	}

	private static GameDirEntry constructGameDirEntry(File dir, List<ImmutablePair<File, byte[]>> files, Map<Integer, GameData> games) throws IOException {
		for (Map.Entry<Integer, GameData> gameData: games.entrySet()) {
			for (FileSet set: gameData.getValue().fileSets_) {
				for (ImmutablePair<File, byte[]> file: files) {
					String filename = file.getKey().getName();
					if (set.set_.containsKey(filename)) {
						FileData fileData = set.set_.get(filename);
						if (Arrays.equals(fileData.md5_, file.getValue()))
							fileData.scoreByType();
					}
				}
			}
		}

		FileSet set = games.values().stream().flatMap(x -> x.fileSets_.stream()).max(Comparator.comparing(FileSet::getScore)).orElse(null);

		if (set == null || set.getScore() <= 0)
			return null;

		List<File> exes = FilesUtils.listExecutablesInDirRecursive(dir);
		if (exes.isEmpty())
			return null;

		Map.Entry<Integer, GameData> game = games.entrySet().stream().filter(x -> x.getValue().fileSets_.contains(set)).findFirst().orElse(null);
		if (game == null)
			return null;

		GameDirEntry result = new GameDirEntry(dir,
				set.set_.entrySet().stream().filter(x -> x.getValue().type_ == FileType.MAIN && x.getValue().getScore() > 0 && exes.stream().anyMatch(y -> y.getName().equals(x.getKey()))).map(
					x -> new File(dir, x.getKey())).findFirst(),
				set.set_.entrySet().stream().filter(x -> x.getValue().type_ == FileType.SETUP && x.getValue().getScore() > 0 && exes.stream().anyMatch(y -> y.getName().equals(x.getKey()))).map(
					x -> new File(dir, x.getKey())).findFirst(),
				exes, MetropolisSearchEngine.getInstance().getEntryDetailedInformation(game.getValue().webProfile_), set.getScore(), set.set_.entrySet().stream().sorted((o1, o2) -> {
					int comp = Integer.compare(o2.getValue().getScore(), o1.getValue().getScore());
					return comp == 0 ? o1.getKey().compareTo(o2.getKey()): comp;
				}).map(x -> x.getKey() + " (" + x.getValue().getScore() + ")").collect(Collectors.joining(", "))

		);

		games.values().forEach(x -> x.fileSets_.forEach(y -> y.set_.values().forEach(FileData::resetScore)));
		return result;
	}

	@Override
	protected boolean onNext(int step) {
		if (step == 1) {
			return conditionsForStep2Ok();
		} else if (step == 2) {
			// no conditions
		} else if (step == 3) {
			if (mapper_.getSelectionIndex() == 1)
				profile_.setValue("sdl", "mapperfile", settings_.getValue("profile", "uniquemapperfile"));
			updateProfileByControls();
			List<GameDirEntry> games = IntStream.range(0, games_.size()).filter(x -> table_.getItem(x).getChecked()).mapToObj(x -> games_.get(x)).toList();
			job_ = new ImportDirThread(log_, progressBar_, status_, games, profile_, consultConf_.getSelection());
		} else if (step == 4) {
			if (job_.isEverythingOk()) {
				Mess_.on(shell_).key("dialog.import.notice.importok").display();
			} else {
				Mess_.on(shell_).key("dialog.import.error.problem").warning();
			}
			setReviewStatus("dialog.export.reviewlog");
			status_.pack();

			result_ = Boolean.valueOf(job_ != null);
		}
		return true;
	}

	private void populate(Table table) {
		for (GameDirEntry game: games_) {
			TableItem newItemTableItem = new TableItem(table, SWT.NONE);
			newItemTableItem.setChecked(game.getOptMain().isPresent() && existingProfiles_.stream().noneMatch(x -> game.dir_.equals(x.getCombinedConfiguration().getAutoexec().getCanonicalGameDir())));
			newItemTableItem.setText(1, game.dir_.getPath());
			newItemTableItem.setText(2, game.webProfile_.getTitle());
			newItemTableItem.setText(3, game.getOptMain().isPresent() ? game.getMain().getName() + (game.executables_.size() > 1 ? ' ' + TRIANGLE_DOWN: StringUtils.EMPTY): TRIANGLE_DOWN);
			newItemTableItem.setText(4, game.getOptSetup().isPresent() ? game.getSetup().getName() + (game.executables_.size() > 1 ? ' ' + TRIANGLE_DOWN: StringUtils.EMPTY)
					: game.executables_.size() > 1 ? TRIANGLE_DOWN: StringUtils.EMPTY);
			newItemTableItem.setText(5, StringUtils.defaultString(game.webProfile_.getPublisherName()));
			newItemTableItem.setText(6, game.webProfile_.getYear());
			newItemTableItem.setText(7, Integer.toString(game.score_));
			newItemTableItem.setData(game.explanation_);
		}
	}

	private void constructGameList() {
		populate(table_);

		final TableEditor editor = new TableEditor(table_);
		editor.horizontalAlignment = SWT.LEFT;
		editor.grabHorizontal = true;

		table_.addListener(SWT.MouseDown, event -> {

			Control existingEditorControl = editor.getEditor();
			if (existingEditorControl != null)
				existingEditorControl.dispose();

			Rectangle clientArea = table_.getClientArea();
			Point pt = new Point(event.x, event.y);
			int index = table_.getTopIndex();

			while (index < table_.getItemCount()) {

				boolean visible = false;
				final TableItem item = table_.getItem(index);
				final GameDirEntry game = games_.get(index);

				for (int i = 0; i < table_.getColumnCount(); i++) {

					Rectangle rect = item.getBounds(i);

					if (rect.contains(pt)) {
						final int column = i;

						if (column == 2) {
							final Text title = new Text(table_, SWT.NONE);
							title.setText(item.getText(i));
							title.selectAll();
							title.setFocus();
							editor.setEditor(title, item, i);
							Listener textListener = e -> {
								if (e.type == SWT.FocusOut) {
									game.webProfile_.setTitle(title.getText());
									title.dispose();
								} else if (e.type == SWT.Traverse) {
									if (e.detail == SWT.TRAVERSE_RETURN) {
										game.webProfile_.setTitle(title.getText());
									}
									if (e.detail == SWT.TRAVERSE_RETURN || e.detail == SWT.TRAVERSE_ESCAPE) {
										title.dispose();
										e.doit = false;
									}
								}
								item.setText(column, game.webProfile_.getTitle());
							};
							title.addListener(SWT.FocusOut, textListener);
							title.addListener(SWT.Traverse, textListener);
							return;
						} else if (column == 3 || column == 4) {
							if ((column == 3 && game.getOptMain().isPresent() && (game.executables_.size() <= 1)) || (column == 4 && (game.executables_.size() <= 1)))
								return;

							final Combo combo = new Combo(table_, SWT.READ_ONLY);
							combo.setItems(game.executables_.stream().map(File::getName).toArray(String[]::new));
							combo.add("-", 0);
							if (column == 3 && game.getOptMain().isPresent())
								combo.setText(game.getMain().getName());
							else if (column == 4 && game.getOptSetup().isPresent())
								combo.setText(game.getSetup().getName());
							else
								combo.select(0);
							editor.setEditor(combo, item, i);
							combo.setFocus();

							Listener comboListener = e -> {
								Optional<File> optFile = combo.getSelectionIndex() == 0 ? Optional.empty(): Optional.of(new File(game.dir_, combo.getText()));
								if (e.type == SWT.Selection) {
									if (column == 3)
										game.setOptMain(optFile);
									else
										game.setOptSetup(optFile);
									combo.dispose();
								} else if (e.type == SWT.Traverse) {
									if (e.detail == SWT.TRAVERSE_RETURN) {
										if (column == 3)
											game.setOptMain(optFile);
										else
											game.setOptSetup(optFile);
									}
									if (e.detail == SWT.TRAVERSE_RETURN || e.detail == SWT.TRAVERSE_ESCAPE) {
										combo.dispose();
										e.doit = false;
									}
								}
								if (column == 3)
									item.setText(column,
										game.getOptMain().isPresent() ? game.getMain().getName() + (game.executables_.size() > 1 ? ' ' + TRIANGLE_DOWN: StringUtils.EMPTY): TRIANGLE_DOWN);
								else
									item.setText(column, game.getOptSetup().isPresent() ? game.getSetup().getName() + (game.executables_.size() > 1 ? ' ' + TRIANGLE_DOWN: StringUtils.EMPTY)
											: game.executables_.size() > 1 ? TRIANGLE_DOWN: StringUtils.EMPTY);
							};
							combo.addListener(SWT.Selection, comboListener);
							combo.addListener(SWT.Traverse, comboListener);
							return;
						}
					}
					if (!visible && rect.intersects(clientArea)) {
						visible = true;
					}
				}
				if (!visible)
					return;
				index++;
			}
		});

		// Implement a "fake" tooltip
		final Listener labelListener = event -> {
			Label label = (Label)event.widget;
			Shell labelShell = label.getShell();
			if (event.type == SWT.MouseDown) {
				Event e = new Event();
				e.item = (TableItem)label.getData("_TABLEITEM");
				// Assuming table is single select, set the selection as if the mouse down event went through to the table
				table_.setSelection(new TableItem[] {(TableItem)e.item});
				table_.notifyListeners(SWT.Selection, e);
				labelShell.dispose();
				table_.setFocus();
			} else if (event.type == SWT.MouseExit) {
				labelShell.dispose();
			}
		};

		Listener tableListener = new Listener() {
			Shell tip = null;
			Label label = null;

			@Override
			public void handleEvent(Event event) {
				switch (event.type) {
					case SWT.Dispose:
					case SWT.KeyDown:
					case SWT.MouseMove: {
						if (tip == null)
							break;
						tip.dispose();
						tip = null;
						label = null;
						break;
					}
					case SWT.MouseHover: {
						Point pt = new Point(event.x, event.y);
						TableItem item = table_.getItem(pt);
						if (item != null) {
							if (tip != null && !tip.isDisposed())
								tip.dispose();

							Rectangle rectColumnScore = item.getBounds(7);
							if (rectColumnScore.contains(pt)) {
								tip = new Shell(shell_, SWT.ON_TOP | SWT.NO_FOCUS | SWT.TOOL);
								tip.setBackground(display_.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
								FillLayout layout = new FillLayout();
								layout.marginWidth = 2;
								tip.setLayout(layout);
								label = new Label(tip, SWT.NONE);
								label.setForeground(display_.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
								label.setBackground(display_.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
								label.setData("_TABLEITEM", item);
								label.setText((String)item.getData());
								label.addListener(SWT.MouseExit, labelListener);
								label.addListener(SWT.MouseDown, labelListener);
								Point size = tip.computeSize(SWT.DEFAULT, SWT.DEFAULT);
								Point p = table_.toDisplay(rectColumnScore.x, rectColumnScore.y);
								tip.setBounds(p.x - size.x - 5, p.y - 1, size.x, size.y);
								tip.setVisible(true);
							}
						}
						break;
					}
					default:
				}
			}
		};
		table_.addListener(SWT.Dispose, tableListener);
		table_.addListener(SWT.KeyDown, tableListener);
		table_.addListener(SWT.MouseMove, tableListener);
		table_.addListener(SWT.MouseHover, tableListener);
	}

	private boolean conditionsForStep2Ok() {
		Mess_.Builder mess = Mess_.on(shell_);
		if (Stream.of(table_.getItems()).noneMatch(TableItem::getChecked))
			mess.key("dialog.importdir.required.onegametoimport").bind(table_);

		for (int i = 0; i < games_.size(); i++) {
			if (table_.getItem(i).getChecked()) {
				GameDirEntry game = games_.get(i);
				if (!game.getOptMain().isPresent() || StringUtils.isBlank(game.getMain().getName()))
					mess.key("dialog.importdir.required.main").bind(table_);
				if (StringUtils.isBlank(game.webProfile_.getTitle()))
					mess.key("dialog.importdir.required.title").bind(table_);
			}
		}

		return mess.valid();
	}

	private void doReloadTemplate(Template template) {
		try {
			StringBuilder warningsLog = new StringBuilder();

			warningsLog.append(template.resetAndLoadConfiguration());
			warningsLog.append(profile_.reloadTemplate(profile_.getDosboxVersion(), template));

			updateControlsByProfile();

			if (StringUtils.isNotEmpty(warningsLog))
				Mess_.on(getParent()).txt(warningsLog.toString()).warning();
		} catch (IOException e) {
			Mess_.on(getParent()).exception(e).warning();
		}
	}

	private void updateControlsByProfile() {
		Configuration combinedConf = profile_.getCombinedConfiguration();
		machine_.setText(combinedConf.getValue("dosbox", "machine"));
		core_.setText(combinedConf.getValue("cpu", "core"));
		cycles_.setText(combinedConf.getValue("cpu", "cycles"));
	}

	private void updateProfileByControls() {
		profile_.setValue("dosbox", "machine", machine_.getText());
		profile_.setValue("cpu", "core", core_.getText());
		profile_.setValue("cpu", "cycles", cycles_.getText());
	}
}