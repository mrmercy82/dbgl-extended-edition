/*
 *  Copyright (C) 2026  mrmercy82 (DBGL Extended Edition, written with the help of AI)
 *  Part of a modified version of DBGL, Copyright (C) 2006-2025  Ronald Blankendaal
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.model.aggregate.DosboxVersion;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.entity.GenerationAwareConfigurable.Generation;
import org.dbgl.model.repository.ProfileRepository;
import org.dbgl.service.SettingsService;
import org.dbgl.util.FilesUtils;
import org.dbgl.util.MapperFile;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Alpha 48: the buttons of a game controller as keys of the keyboard, for one profile, in its own mapper file. A key is dragged
 * from the list on the right onto a button (or chosen with a double click). The mapper file is always one DOSBox wrote itself
 * (DOSBox drops all its own binds when it reads one, so DBGL never writes one from nothing): the profile's, a copy of the one of
 * its DOSBox version, or saved by DOSBox in its mapper. Only the binds of the controller inputs chosen here are changed.
 */
public class ControllerDialog extends SizeControlledButtonDialog<Boolean> {

	/** keys with a name of their own in the list */
	private static final Set<String> NAMED_KEYS = Set.of("space", "enter", "esc", "tab", "bspace", "lctrl", "rctrl", "lalt", "ralt", "lshift", "rshift", "up", "down",
		"left", "right", "kpenter", "insert", "delete", "home", "end", "pageup", "pagedown");

	private final Profile profile_;
	private File mapper_;
	private MapperFile file_;
	/** the key of each input as chosen here (missing: as in the file) */
	private final Map<String, String> chosen_ = new HashMap<>();
	private Table inputs_, keys_;
	private Label status_;
	private Composite baseButtons_;
	private Button hidden_, check_;
	private boolean running_;

	public ControllerDialog(Shell parent, Profile profile) {
		super(parent, "controllerdialog");
		profile_ = profile;
		result_ = false;
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.controller.title");
	}

	@Override
	protected boolean prepare() {
		try {
			profile_.resetAndLoadConfiguration();
		} catch (IOException e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
		mapper_ = mapperFileOf(profile_);
		load();
		return true;
	}

	/** the mapper file of the profile: as its configuration says, or the one DBGL gives it ("12.map" next to it) */
	static File mapperFileOf(Profile prof) {
		String value = prof.getConfiguration().hasValue("sdl", "mapperfile") ? prof.getConfiguration().getValue("sdl", "mapperfile"): null;
		if (StringUtils.isBlank(value)) {
			String pattern = StringUtils.defaultIfBlank(SettingsService.getInstance().getValue("profile", "uniquemapperfile"), "%1$d.map");
			value = String.format(pattern, prof.getId(), FilesUtils.toSafeFilename(prof.getTitle()));
		}
		File f = new File(value);
		return f.isAbsolute() ? f: new File(prof.getConfigurationCanonicalFile().getParentFile(), value);
	}

	/** a mapper file of the DOSBox version, to start from: as its configuration says, else the newest mapper*.map next to it */
	static File mapperFileOf(DosboxVersion v) {
		File dir = v.getConfigurationCanonicalFile().getParentFile();
		String value = v.getConfiguration().hasValue("sdl", "mapperfile") ? v.getConfiguration().getValue("sdl", "mapperfile"): null;
		if (StringUtils.isNotBlank(value)) {
			File f = new File(value).isAbsolute() ? new File(value): new File(dir, value);
			if (f.isFile())
				return f;
		}
		File best = null;
		for (File d: new File[] {dir, v.getCanonicalExecutable().getParentFile()}) {
			File[] found = (d == null) ? null: d.listFiles((x, n) -> n.toLowerCase().startsWith("mapper") && n.toLowerCase().endsWith(".map"));
			if (found != null)
				for (File f: found)
					if (f.isFile() && ((best == null) || (f.lastModified() > best.lastModified())))
						best = f;
		}
		return best;
	}

	private void load() {
		file_ = null;
		try {
			if (mapper_.isFile())
				file_ = new MapperFile(Files.readString(mapper_.toPath(), StandardCharsets.ISO_8859_1));
		} catch (IOException e) {
			file_ = null;
		}
		if ((file_ != null) && file_.keyEvents().isEmpty())
			file_ = null; // not a mapper file DOSBox wrote
	}

	private boolean isStaging083() {
		return profile_.getGeneration() == Generation.staging_0830;
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(2, true));
		Label heading = new Label(contents_, SWT.WRAP);
		heading.setText(profile_.getTitle() + " – " + profile_.getDosboxVersion().getTitle());
		heading.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		status_ = new Label(contents_, SWT.WRAP);
		GridData sd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		sd.widthHint = 600;
		status_.setLayoutData(sd);
		baseButtons_ = new Composite(contents_, SWT.NONE);
		baseButtons_.setLayout(new GridLayout(2, false));
		baseButtons_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		inputs_ = new Table(contents_, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL);
		inputs_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		inputs_.setHeaderVisible(true);
		inputs_.setLinesVisible(true);
		new TableColumn(inputs_, SWT.NONE).setText(text_.get("dialog.controller.column.input"));
		new TableColumn(inputs_, SWT.NONE).setText(text_.get("dialog.controller.column.key"));
		keys_ = new Table(contents_, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER | SWT.V_SCROLL);
		keys_.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		keys_.setHeaderVisible(true);
		new TableColumn(keys_, SWT.NONE).setText(text_.get("dialog.controller.column.keys"));

		Composite row = new Composite(contents_, SWT.NONE);
		row.setLayout(new GridLayout(2, false));
		row.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		Button preset = new Button(row, SWT.PUSH);
		preset.setText(text_.get("dialog.controller.preset"));
		preset.addListener(SWT.Selection, e -> applyPreset());
		Button clear = new Button(row, SWT.PUSH);
		clear.setText(text_.get("dialog.controller.clear"));
		clear.addListener(SWT.Selection, e -> {
			if (inputs_.getSelectionIndex() >= 0)
				choose(MapperFile.INPUTS.get(inputs_.getSelectionIndex()), null);
		});

		Label hint = new Label(contents_, SWT.WRAP);
		hint.setText(text_.get("dialog.controller.hint"));
		GridData hd = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
		hd.widthHint = 600;
		hint.setLayoutData(hd);
		if (isStaging083()) {
			hidden_ = new Button(contents_, SWT.CHECK);
			hidden_.setText(text_.get("dialog.controller.hidden"));
			hidden_.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
			String now = StringUtils.trim(profile_.getCombinedConfiguration().getValue("joystick", "joysticktype"));
			hidden_.setSelection("hidden".equalsIgnoreCase(now) || "none".equalsIgnoreCase(now));
		}

		// a key onto a button: dragged, or a double click with the button chosen on the left
		DragSource drag = new DragSource(keys_, DND.DROP_COPY | DND.DROP_MOVE);
		drag.setTransfer(new Transfer[] {TextTransfer.getInstance()});
		drag.addDragListener(new DragSourceAdapter() {
			@Override
			public void dragSetData(DragSourceEvent event) {
				event.data = keys_.getSelectionCount() > 0 ? (String)keys_.getSelection()[0].getData(): "";
			}
		});
		DropTarget drop = new DropTarget(inputs_, DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_DEFAULT);
		drop.setTransfer(new Transfer[] {TextTransfer.getInstance()});
		drop.addDropListener(new DropTargetAdapter() {
			@Override
			public void dragEnter(DropTargetEvent event) {
				event.detail = DND.DROP_COPY;
			}

			@Override
			public void dragOver(DropTargetEvent event) {
				event.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL;
			}

			@Override
			public void drop(DropTargetEvent event) {
				if ((event.item instanceof TableItem item) && (event.data instanceof String key) && !key.isEmpty())
					choose((String)item.getData(), key);
			}
		});
		keys_.addListener(SWT.DefaultSelection, e -> {
			if ((inputs_.getSelectionIndex() >= 0) && (keys_.getSelectionCount() > 0)) {
				choose(MapperFile.INPUTS.get(inputs_.getSelectionIndex()), (String)keys_.getSelection()[0].getData());
				if (inputs_.getSelectionIndex() < inputs_.getItemCount() - 1)
					inputs_.setSelection(inputs_.getSelectionIndex() + 1); // on to the next button
			}
		});

		check_ = new Button(otherButtons_, SWT.PUSH);
		check_.setText(text_.get("dialog.controller.check"));
		check_.setToolTipText(text_.get("dialog.controller.check.tooltip"));
		check_.addListener(SWT.Selection, e -> {
			if (save())
				runMapper();
		});
		createOkCancelButtons(new org.eclipse.swt.events.SelectionAdapter() {
			@Override
			public void widgetSelected(org.eclipse.swt.events.SelectionEvent event) {
				if (running_)
					return;
				if ((file_ == null) || save())
					shell_.close();
			}
		});
		fill();
	}

	private String keyLabel(String event) {
		String name = event.substring(4);
		return NAMED_KEYS.contains(name) ? text_.get("dialog.controller.key." + name): name.toUpperCase(java.util.Locale.ROOT);
	}

	private String inputLabel(String input) {
		String[] p = input.split(" ");
		return switch (p[1]) {
			case "hat" -> text_.get("dialog.controller.input.hat" + p[3]);
			case "axis" -> text_.get("dialog.controller.input.axis" + p[2] + p[3]);
			default -> text_.get("dialog.controller.input.button", new Object[] {p[2]});
		};
	}

	private String keyNow(String input) {
		return chosen_.containsKey(input) ? chosen_.get(input): file_.keyOf(input);
	}

	private void choose(String input, String keyEvent) {
		if (file_ == null)
			return;
		chosen_.put(input, keyEvent);
		fillInputs();
	}

	/** the arrows for the D-pad and the left stick, some keys games often use for the buttons (as far as the file has them) */
	private void applyPreset() {
		if (file_ == null)
			return;
		List<String> keys = file_.keyEvents();
		String[][] preset = {{"stick_0 hat 0 1", "key_up"}, {"stick_0 hat 0 4", "key_down"}, {"stick_0 hat 0 8", "key_left"}, {"stick_0 hat 0 2", "key_right"},
			{"stick_0 axis 1 0", "key_up"}, {"stick_0 axis 1 1", "key_down"}, {"stick_0 axis 0 0", "key_left"}, {"stick_0 axis 0 1", "key_right"},
			{"stick_0 button 0", "key_space"}, {"stick_0 button 1", "key_lctrl"}, {"stick_0 button 2", "key_lalt"}, {"stick_0 button 3", "key_lshift"},
			{"stick_0 button 6", "key_esc"}, {"stick_0 button 7", "key_enter"}};
		for (String[] p: preset)
			if (keys.contains(p[1]))
				chosen_.put(p[0], p[1]);
		fillInputs();
	}

	private void fill() {
		baseButtons_.setVisible(file_ == null);
		((GridData)baseButtons_.getLayoutData()).exclude = file_ != null;
		for (org.eclipse.swt.widgets.Control c: baseButtons_.getChildren())
			c.dispose();
		if (file_ == null) {
			status_.setText(text_.get("dialog.controller.nobase", new Object[] {mapper_.getPath()}));
			File fromVersion = mapperFileOf(profile_.getDosboxVersion());
			if (fromVersion != null) {
				Button copy = new Button(baseButtons_, SWT.PUSH);
				copy.setText(text_.get("dialog.controller.fromversion", new Object[] {fromVersion.getName()}));
				copy.setToolTipText(fromVersion.getPath());
				copy.addListener(SWT.Selection, e -> {
					try {
						Files.createDirectories(mapper_.getParentFile().toPath());
						Files.copy(fromVersion.toPath(), mapper_.toPath());
						useMapperFileInProfile();
						load();
						fill();
					} catch (IOException ex) {
						Mess_.on(shell_).exception(ex).warning();
					}
				});
			}
			Button fromDosbox = new Button(baseButtons_, SWT.PUSH);
			fromDosbox.setText(text_.get("dialog.controller.fromdosbox"));
			fromDosbox.addListener(SWT.Selection, e -> {
				if (Mess_.on(shell_).key("dialog.controller.fromdosbox.how").confirm() && useMapperFileInProfile())
					runMapper();
			});
		} else {
			status_.setText(text_.get("dialog.controller.base", new Object[] {mapper_.getPath(), file_.eventCount()}));
		}
		inputs_.setEnabled(file_ != null);
		keys_.setEnabled(file_ != null);
		check_.setEnabled(file_ != null);
		keys_.removeAll();
		if (file_ != null)
			for (String k: file_.keyEvents()) {
				TableItem item = new TableItem(keys_, SWT.NONE);
				item.setText(keyLabel(k));
				item.setData(k);
			}
		keys_.getColumn(0).pack();
		fillInputs();
		baseButtons_.layout(true);
		contents_.layout(true, true);
	}

	private void fillInputs() {
		int sel = inputs_.getSelectionIndex();
		inputs_.removeAll();
		for (String input: MapperFile.INPUTS) {
			TableItem item = new TableItem(inputs_, SWT.NONE);
			String key = file_ == null ? null: keyNow(input);
			item.setText(new String[] {inputLabel(input), key == null ? "": keyLabel(key)});
			item.setData(input);
		}
		for (TableColumn c: inputs_.getColumns())
			c.pack();
		if (sel >= 0)
			inputs_.setSelection(sel);
	}

	/** the profile reads its own mapper file (and a controller is not "none", else DOSBox leaves it out); saved */
	private boolean useMapperFileInProfile() {
		try {
			profile_.resetAndLoadConfiguration();
			boolean change = false;
			if (!profile_.getConfiguration().hasValue("sdl", "mapperfile") || StringUtils.isBlank(profile_.getConfiguration().getValue("sdl", "mapperfile"))) {
				File dir = profile_.getConfigurationCanonicalFile().getParentFile();
				profile_.getConfiguration().setValue("sdl", "mapperfile", FilesUtils.relativize(dir, mapper_));
				change = true;
			}
			String type = StringUtils.trim(profile_.getCombinedConfiguration().getValue("joystick", "joysticktype"));
			String wanted = (hidden_ != null) && !hidden_.isDisposed() && hidden_.getSelection() ? "hidden": ("none".equalsIgnoreCase(type) ? "auto": null);
			if ((wanted != null) && !wanted.equalsIgnoreCase(type)) {
				profile_.getConfiguration().setValue("joystick", "joysticktype", wanted);
				change = true;
			}
			if (change) {
				org.dbgl.util.BulkUndo.remember(text_.get("dialog.controller.title"), List.of(profile_));
				new ProfileRepository().update(profile_);
				result_ = true;
			}
			return true;
		} catch (IOException | java.sql.SQLException e) {
			Mess_.on(shell_).exception(e).warning();
			return false;
		}
	}

	/** the chosen keys into the mapper file (the one before kept as .bak), the profile set to use it */
	private boolean save() {
		if (file_ == null)
			return false;
		if (!useMapperFileInProfile())
			return false;
		if (chosen_.isEmpty())
			return true;
		try {
			chosen_.forEach(file_::assign);
			Files.copy(mapper_.toPath(), new File(mapper_.getPath() + ".bak").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.writeString(mapper_.toPath(), file_.text(), StandardCharsets.ISO_8859_1);
			chosen_.clear();
			result_ = true;
			return true;
		} catch (IOException e) {
			Mess_.on(shell_).exception(e).warning();
			return false;
		}
	}

	/** DOSBox with the profile, straight into its mapper; afterwards the file is read again (it may have been changed there) */
	private void runMapper() {
		running_ = true;
		shell_.setCursor(shell_.getDisplay().getSystemCursor(SWT.CURSOR_APPSTARTING));
		org.eclipse.swt.widgets.Display display = shell_.getDisplay();
		Thread t = new Thread(() -> {
			Exception problem = null;
			try {
				org.dbgl.util.ExecuteUtils.runMapper(profile_);
			} catch (IOException e) {
				problem = e;
			}
			Exception p = problem;
			display.asyncExec(() -> {
				if (shell_.isDisposed())
					return;
				running_ = false;
				shell_.setCursor(null);
				if (p != null)
					Mess_.on(shell_).exception(p).warning();
				load();
				fill();
				if (file_ == null)
					Mess_.on(shell_).key("dialog.controller.notsaved", new Object[] {mapper_.getPath()}).warning();
			});
		}, "DOSBox mapper");
		t.setDaemon(true);
		t.start();
	}
}
