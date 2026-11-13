/*
 *  Copyright (C) 2006-2025  Ronald Blankendaal
 *  Modified for the DBGL Extended Edition by mrmercy82 (with the help of AI)
 *  since 2026-10-31. Changes and their dates: CHANGELOG-en.txt, CHANGELOG-de.txt.
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
import java.util.List;
import java.util.stream.Stream;

import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Composite_;
import org.dbgl.gui.controls.Mess_;
import org.dbgl.gui.controls.SearchEngineSelector;
import org.dbgl.gui.controls.TextControl_;
import org.dbgl.model.SearchEngineImageInformation;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.model.repository.BaseRepository;
import org.dbgl.model.repository.TemplateRepository;
import org.dbgl.model.repository.TitledEntityRepository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;


public abstract class EditProfileDialog<T> extends EditConfigurableDialog<T> {

	protected String[] developersList_, publishersList_, genresList_, yearsList_, statusList_;
	protected List<String[]> customList_;
	protected List<Template> templatesList_;

	protected Text title_, notes_;
	protected SearchEngineSelector engineSelector_;
	protected Text[] link_, linkTitle_;
	protected Button[] linkBrowseButton_;
	protected Combo developer_, publisher_, genre_, year_;
	protected Scale custom9_;
	protected Combo templateCombo_;
	protected Button loadfix_;
	protected Combo loadfixValue_;
	protected Text img_[], main_, setup_;
	protected Composite webImagesSpaceHolder_;
	protected SearchEngineImageInformation[] imageInformation_;
	protected Button[] imgButtons_;

	protected int templateIndex_;

	protected EditProfileDialog(Shell parent) {
		super(parent, "profiledialog");
	}

	@Override
	protected boolean prepare() {
		if (!super.prepare())
			return false;

		try {
			TemplateRepository templRepo = new TemplateRepository();
			TitledEntityRepository titledRepo = new TitledEntityRepository();

			templatesList_ = templRepo.listAll(dbversionsList_);
			developersList_ = titledRepo.developers();
			publishersList_ = titledRepo.publishers();
			genresList_ = titledRepo.genres();
			yearsList_ = titledRepo.years();
			statusList_ = titledRepo.statuses();

			customList_ = new ArrayList<>();
			for (int i = 0; i < Profile.NR_OF_CUSTOM_STRING_DROPDOWNS; i++) {
				customList_.add(titledRepo.customValues(i));
			}

			templateIndex_ = BaseRepository.indexOfDefault(templatesList_);

			return true;
		} catch (Exception e) {
			Mess_.on(getParent()).exception(e).warning();
			return false;
		}
	}

	protected Composite createInfoPage(TemplateProfileBase configurable, Composite parent, boolean focusOnTitle) {
		Composite composite = createPage("dialog.template.tab.info", parent, 6);

		title_ = Chain.on(composite).lbl(l -> l.key("dialog.profile.title")).txt(t -> t.horSpan(4)).profile(Profile::getTitle, Profile::setTitle).build(metaControls_).init(configurable).getText();

		ToolBar toolBar = createToolBar(composite);

		engineSelector_ = new SearchEngineSelector(toolBar, false);

		developer_ = Chain.on(composite).lbl(l -> l.key("dialog.profile.developer")).cmb(c -> c.horSpan(2).autoSelect(developersList_)).profile(Profile::getDeveloper, Profile::setDeveloper).build(
			metaControls_).init(configurable).getCombo();
		publisher_ = Chain.on(composite).lbl(l -> l.key("dialog.profile.publisher")).cmb(c -> c.horSpan(2).autoSelect(publishersList_)).profile(Profile::getPublisher, Profile::setPublisher).build(
			metaControls_).init(configurable).getCombo();
		genre_ = Chain.on(composite).lbl(l -> l.key("dialog.profile.genre")).cmb(c -> c.horSpan(2).autoSelect(genresList_)).profile(Profile::getGenre, Profile::setGenre).build(
			metaControls_).init(configurable).getCombo();
		year_ = Chain.on(composite).lbl(l -> l.key("dialog.profile.year")).cmb(c -> c.horSpan(2).autoSelect(yearsList_)).profile(Profile::getYear, Profile::setYear).build(metaControls_).init(configurable).getCombo();

		// alpha 48: as wide as the page allows, not as wide as the longest name in the list (the page had to be scrolled sideways)
		for (org.eclipse.swt.widgets.Combo combo: new org.eclipse.swt.widgets.Combo[] {developer_, publisher_, genre_, year_})
			if (combo.getLayoutData() instanceof GridData data) {
				data.widthHint = 100;
				data.horizontalAlignment = SWT.FILL;
				data.grabExcessHorizontalSpace = true;
			}
		link_ = new Text[Profile.NR_OF_LINK_DESTINATIONS];
		linkBrowseButton_ = new Button[Profile.NR_OF_LINK_TITLES];
		linkTitle_ = new Text[Profile.NR_OF_LINK_TITLES];

		for (int i = 0; i < Profile.NR_OF_LINK_TITLES; i++) {
			int j = i + 1;
			Chain chnLink = Chain.on(composite).lbl(l -> l.key("dialog.profile.link", new Object[] {j})).txt(t -> t).profile(i, Profile::getLinkDestination, Profile::setLinkDestination).but(
				b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.DOC, false)).build(metaControls_).init(configurable);
			link_[i] = chnLink.getText();
			linkBrowseButton_[i] = chnLink.getButton();
			linkTitle_[i] = Chain.on(composite).lbl(l -> l.key("dialog.profile.linktitle")).txt(t -> t).profile(i, Profile::getLinkTitle, Profile::setLinkTitle).build(
				metaControls_).init(configurable).getText();
			GridData titleData = new GridData(SWT.FILL, SWT.CENTER, true, false); // alpha 48: up to "Remove"
			titleData.widthHint = 100;
			linkTitle_[i].setLayoutData(titleData);
			linkTitle_[i].setData(org.dbgl.gui.controls.GridCompactor.KEEP_WIDTH, Boolean.TRUE);
			// empty link and title at once, e.g. to make room for more manuals and extras (8 links per profile)
			final int index = i;
			org.eclipse.swt.widgets.Button remove = new org.eclipse.swt.widgets.Button(composite, SWT.PUSH);
			remove.setText(text_.get("dialog.profile.link.remove"));
			remove.setToolTipText(text_.get("dialog.profile.link.remove.tooltip"));
			remove.setLayoutData(new org.eclipse.swt.layout.GridData(SWT.FILL, SWT.CENTER, false, false));
			remove.addListener(SWT.Selection, e -> {
				link_[index].setText("");
				linkTitle_[index].setText("");
			});
		}
		
		Chain.on(composite).lbl(l -> l.key("dialog.profile.status")).cmb(c -> c.horSpan(2).autoSelect(statusList_)).profile(Profile::getStatus, Profile::setStatus).build(metaControls_).init(configurable);
		// alpha 48: "Favorite" only, the long text made its column and so the page wider than the window (as tooltip)
		Chain favorite = Chain.on(composite).lbl(l -> l.key("dialog.profile.favorite.short")).but(b -> b.layoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))).lbl(l -> l).profile(Profile::getFavorite,
			Profile::setFavorite).build(metaControls_).init(configurable);
		favorite.getLabel().setToolTipText(text_.get("dialog.profile.favorite"));
		
		notes_ = Chain.on(composite).lbl(l -> l.key("dialog.profile.notes")).txt(t -> t.horSpan(5).multi().wrap()).profile(Profile::getNotes, Profile::setNotes).build(metaControls_).init(configurable).getText();
		notes_.setFont(stringToFont(display_, settings_.getValues("gui", "notesfont"), notes_.getFont()));
		notes_.addDisposeListener(e -> notes_.getFont().dispose());
		// below the notes: play time, sessions and the last one (of a profile that exists already)
		if ((configurable instanceof Profile prof) && (prof.getId() >= 0) && (prof.getProfileStats() != null) && settings_.getBooleanValue("gui", "showplaytimeininfo")) {
			new org.eclipse.swt.widgets.Label(composite, SWT.NONE);
			org.eclipse.swt.widgets.Label playInfo = new org.eclipse.swt.widgets.Label(composite, SWT.WRAP);
			GridData playInfoData = new GridData(SWT.FILL, SWT.CENTER, true, false, 5, 1);
			playInfoData.widthHint = 200; // wraps if needed, does not make the page wider
			playInfo.setLayoutData(playInfoData);
			playInfo.setText(org.dbgl.util.ProfileReports.playInfo(prof, text_));
		}
		
		limitTextFields(composite);
		if (focusOnTitle) {
			title_.selectAll();
			title_.setFocus();
		}

		return composite;
	}

	/** at most as long as the database keeps (256, links 2048), so that saving does not fail with a long text (e.g. pasted) */
	protected void limitTextFields(Composite composite) {
		java.util.Set<Text> links = link_ == null ? java.util.Set.of(): new java.util.HashSet<>(java.util.Arrays.asList(link_));
		for (org.eclipse.swt.widgets.Control c: composite.getChildren()) {
			if ((c instanceof Text t) && ((t.getStyle() & SWT.MULTI) == 0))
				t.setTextLimit(links.contains(t) ? 2048: 256);
			else if (c instanceof org.eclipse.swt.widgets.Combo combo)
				combo.setTextLimit(256);
			else if (c instanceof Composite inner)
				limitTextFields(inner);
		}
	}
	
	protected Composite createCustomInfoPage(TemplateProfileBase configurable, Composite parent) {
		Composite composite = createPage("dialog.template.tab.info", "dialog.profile.tab.custominfo", parent, 2);
		
		for (int i = 0; i < Profile.NR_OF_CUSTOM_STRING_DROPDOWNS; i++) {
			int j = i + 1;
			Chain.on(composite).lbl(l -> l.txt(settings_.getValue("gui", "custom" + j))).cmb(c -> c.autoSelect(customList_.get(j - 1))).profile(i, Profile::getCustomString,
				Profile::setCustomString).build(metaControls_).init(configurable);
		}

		for (int i = 0; i < Profile.NR_OF_CUSTOM_STRING_FIELDS_1; i++) {
			int j = i + 1;
			Chain.on(composite).lbl(l -> l.txt(settings_.getValue("gui", "custom" + (j + Profile.NR_OF_CUSTOM_STRING_DROPDOWNS)))).txt(t -> t).profile(
				i + Profile.NR_OF_CUSTOM_STRING_DROPDOWNS, Profile::getCustomString, Profile::setCustomString).build(metaControls_).init(configurable);
		}
		for (int i = 0; i < Profile.NR_OF_CUSTOM_STRING_FIELDS_2; i++) {
			int j = i + 1;
			Chain.on(composite).lbl(
				l -> l.txt(settings_.getValue("gui", "custom" + (j + Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + Profile.NR_OF_CUSTOM_INTS + Profile.NR_OF_CUSTOM_STRING_FIELDS_1)))).txt(
					t -> t).profile(i + Profile.NR_OF_CUSTOM_STRING_DROPDOWNS + Profile.NR_OF_CUSTOM_STRING_FIELDS_1, Profile::getCustomString, Profile::setCustomString).build(
						metaControls_).init(configurable);
		}
		
		// alpha 48: all fields twice as long as before, and all equally long (right edges in a line: a text field gets less as it
		// has more border than a drop-down)
		int comboWidth = -1;
		for (org.eclipse.swt.widgets.Control c: composite.getChildren())
			if (c instanceof org.eclipse.swt.widgets.Combo combo) {
				comboWidth = combo.computeSize(400, SWT.DEFAULT).x;
				break;
			}
		for (org.eclipse.swt.widgets.Control c: composite.getChildren())
			if ((c instanceof org.eclipse.swt.widgets.Combo) || (c instanceof Text)) {
				GridData d = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
				d.widthHint = 400;
				if ((c instanceof Text t) && comboWidth > 0)
					d.widthHint = Math.max(100, 400 - (t.computeSize(400, SWT.DEFAULT).x - comboWidth));
				c.setLayoutData(d);
				c.setData(org.dbgl.gui.controls.GridCompactor.KEEP_WIDTH, Boolean.TRUE);
			}

		custom9_ = Chain.on(composite).lbl(l -> l.txt(settings_.getValue("gui", "custom9"))).scl(s -> s).profile(0, Profile::getCustomInt, Profile::setCustomInt).build(
				metaControls_).init(configurable).getScale();
		Chain.on(composite).lbl(l -> l.txt(settings_.getValue("gui", "custom10"))).spn(s -> s).profile(1, Profile::getCustomInt,
				Profile::setCustomInt).build(metaControls_).init(configurable);
		
		limitTextFields(composite);
		return composite;
	}

	protected Composite createDosboxPage(TemplateProfileBase configurable, Composite parent, String capturesText, String configFileText) {
		Composite composite = super.createDosboxPage(configurable, parent, capturesText, configFileText);
		Group associationGroup = (Group) composite.getChildren()[2];

		templateCombo_ = Chain.on(associationGroup).lbl(l -> l.key("dialog.profile.template")).cmb(
			c -> c.wide().items(templatesList_.stream().map(Template::getTitle).toArray(String[]::new)).select(templateIndex_).visibleItemCount(20)).combo();
		// alpha 48: as long as the drop-down of the DOSBox version above it
		if (dbversionCombo_ != null && dbversionCombo_.getLayoutData() instanceof GridData v) {
			GridData t = new GridData(v.horizontalAlignment, SWT.CENTER, v.grabExcessHorizontalSpace, false);
			t.widthHint = v.widthHint;
			templateCombo_.setLayoutData(t);
		}

		Button_.on(associationGroup).text().key("dialog.profile.loadsettings").tooltip("dialog.profile.loadsettings.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (templateCombo_.getSelectionIndex() != -1) {
					if (setButton_.isEnabled()) {
						Mess_.on(shell_).key("dialog.template.required.dosboxassociation").bind(setButton_, getTreeItemByControl(setButton_)).valid();
						return;
					}
					doPerformDosboxConfAction(DosboxConfAction.LOAD_TEMPLATE, dbversionsList_.get(dbversionCombo_.getSelectionIndex()));
				}
			}
		}).ctrl();
		new org.eclipse.swt.widgets.Label(associationGroup, SWT.NONE); // "Reset" stays below "To default"

		Button_.on(associationGroup).text().key("dialog.profile.reloadsettings").tooltip("dialog.profile.reloadsettings.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if (templateCombo_.getSelectionIndex() != -1) {
					if (setButton_.isEnabled()) {
						Mess_.on(shell_).key("dialog.template.required.dosboxassociation").bind(setButton_, getTreeItemByControl(setButton_)).valid();
						return;
					}
					doPerformDosboxConfAction(DosboxConfAction.RELOAD_TEMPLATE, dbversionsList_.get(dbversionCombo_.getSelectionIndex()));
				}
			}
		}).ctrl().setEnabled(dbversionIndex_ != -1);

		return composite;
	}

	@Override
	protected void createMemoryPageAdditions(TemplateProfileBase configurable, Composite composite) {
		loadfix_ = Chain.on(composite).lbl(l -> l.key("dialog.profile.loadfix")).but(b -> b.tooltip("dialog.profile.loadfix.tooltip")).autoexec(Autoexec::getLoadfix, Autoexec::setLoadfix).build(metaControls_).init(configurable).getButton();
		loadfixValue_ = Chain.on(composite).cmb(c -> c.tooltip("dialog.profile.loadfixvalue.tooltip").editable().items("profile", "loadfix_value")).lbl(l -> l.key("dialog.profile.kb")).autoexec(Autoexec::getLoadfixValueAsString,
			Autoexec::setLoadfixValue).build(metaControls_).init(configurable).getCombo();
		loadfix_.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				loadfixValue_.setEnabled(loadfix_.getSelection());
			}
		});
		loadfixValue_.setEnabled(loadfix_.getSelection());

		Chain.on(composite).lbl(l -> l.key("dialog.profile.loadhigh")).but(b -> b.tooltip("dialog.profile.loadhigh.tooltip").horSpan(3)).autoexec(Autoexec::getLoadhigh, Autoexec::setLoadhigh).build(metaControls_).init(configurable);
	}

	@Override
	protected Composite createMountingPage(TemplateProfileBase configurable, Composite parent, boolean multiEdit) {
		Composite composite = super.createMountingPage(configurable, parent, multiEdit);
		Group executeGroup = (Group) composite.getChildren()[composite.getChildren().length - 1];
		
		Composite dosComposite = Composite_.on(executeGroup).horSpan(2).grabHorSpace().innerLayout(7).build();
		main_ = Chain.on(dosComposite).lbl(l -> l.key("dialog.profile.mainexe")).txt(t -> t.horSpan(4)).autoexec(Autoexec::getMain, Autoexec::setMain).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.EXE, false)).but(b -> b.grab(mountingpointsList_, false)).build(metaControls_).init(configurable).getText();
		Chain.on(dosComposite).lbl(l -> l).lbl(l -> l.key("dialog.profile.mainparameters")).txt(t -> t.horSpan(5)).autoexec(Autoexec::getParameters,
			Autoexec::setParameters).build(metaControls_).init(configurable);
		Chain setupChain = Chain.on(dosComposite).lbl(l -> l.key("dialog.profile.setupexe")).txt(t -> t.horSpan(4)).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.EXE, false)).profile(Profile::getSetupString, Profile::setSetupFileLocation).but(
				b -> b.grab(mountingpointsList_, false)).build(metaControls_).init(configurable);
		setup_ = setupChain.getText();
		Chain.on(dosComposite).lbl(l -> l).lbl(l -> l.key("dialog.profile.setupparameters")).txt(t -> t.horSpan(5)).profile(Profile::getSetupParams,
			Profile::setSetupParams).build(metaControls_).init(configurable);
		Text alt1 = Chain.on(dosComposite).lbl(l -> l.key("dialog.profile.altexe", new Object[] {1})).txt(t -> t.horSpan(2)).profile(0, Profile::getAltExeString, Profile::setAltExeFileLocation).build(
			metaControls_).init(configurable).getText();
		Chain alt1Chain = Chain.on(dosComposite).lbl(l -> l).txt(t -> t).but(b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.EXE, false)).profile(0, Profile::getAltExeParam,
			Profile::setAltExeParam).but(b -> b.grab(mountingpointsList_, false)).build(metaControls_).init(configurable);
		Text alt2 = Chain.on(dosComposite).lbl(l -> l.key("dialog.profile.altexe", new Object[] {2})).txt(t -> t.horSpan(2)).profile(1, Profile::getAltExeString, Profile::setAltExeFileLocation).build(
			metaControls_).init(configurable).getText();
		Chain alt2Chain = Chain.on(dosComposite).lbl(l -> l).txt(t -> t).but(b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.EXE, false)).profile(1, Profile::getAltExeParam,
			Profile::setAltExeParam).but(b -> b.grab(mountingpointsList_, false)).build(metaControls_).init(configurable);

		Composite booterComposite = Composite_.on(executeGroup).horSpan(2).grabHorSpace().innerLayout(4).build();
		Chain img1Chain = Chain.on(booterComposite).lbl(l -> l.key("dialog.profile.booterimage1")).txt(t -> t).autoexec(0, Autoexec::getImg, Autoexec::setImg).but(
			b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.BOOTER, false)).but(b -> b.grab(mountingpointsList_, true)).build(metaControls_).init(configurable);
		img_ = new Text[Autoexec.BOOTER_IMAGES];
		img_[0] = img1Chain.getText();
		img1Chain.getButton().setData(Button_.DATA_ALT_CONTROL, main_);
		
		for (int i = 1; i < Autoexec.BOOTER_IMAGES; i++) {
			final int nr = i + 1;
			Chain imgChain = Chain.on(booterComposite).lbl(l -> l.key("dialog.profile.booterimage" + nr)).txt(t -> t).autoexec(i, Autoexec::getImg, Autoexec::setImg).but(
				b -> b.browse(false, Button_.BrowseType.FILE, Button_.CanonicalType.BOOTER, false)).but(b -> b.grab(mountingpointsList_, true)).build(metaControls_).init(configurable);
			img_[i] = imgChain.getText();
			imgChain.getButton().setData(Button_.DATA_ALT_CONTROL, img_[0]);
		}
		
		Chain.on(booterComposite).lbl(l -> l.key("dialog.profile.booterdriveletter")).cmb(c -> c.horSpan(3).items(new String[] {"", "A", "C", "D"}).visibleItemCount(4)).autoexec(
			Autoexec::getImgDriveletter, Autoexec::setImgDriveletter).build(metaControls_).init(configurable);
		new org.eclipse.swt.widgets.Label(booterComposite, SWT.NONE);
		Button_.on(booterComposite).text().key("dialog.profile.win9xsettings").tooltip("dialog.profile.win9xsettings.tooltip").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				applyWindows9xSettings();
			}
		}).ctrl().setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 3, 1));

		for (int i = 0; i < Profile.NR_OF_LINK_DESTINATIONS; i++) {
			linkBrowseButton_[i].setData(Button_.DATA_ALT_CONTROL, main_);
		}

		setupChain.getButton().setData(Button_.DATA_ALT_CONTROL, main_);
		alt1Chain.getButtons().get(0).setData(Button_.DATA_CONTROL, new TextControl_(alt1));
		alt1Chain.getButtons().get(1).setData(Button_.DATA_CONTROL, new TextControl_(alt1));
		alt1Chain.getButton().setData(Button_.DATA_ALT_CONTROL, main_);
		alt2Chain.getButtons().get(0).setData(Button_.DATA_CONTROL, new TextControl_(alt2));
		alt2Chain.getButtons().get(1).setData(Button_.DATA_CONTROL, new TextControl_(alt2));
		alt2Chain.getButton().setData(Button_.DATA_ALT_CONTROL, main_);

		dosRadio_.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				showAndHide(executeGroup, dosComposite, booterComposite, true);
			}
		});
		booterRadio_.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent arg0) {
				showAndHide(executeGroup, dosComposite, booterComposite, false);
			}
		});
		
		showAndHide(executeGroup, dosComposite, booterComposite, configurable.getCombinedConfiguration().getAutoexec().isDos());
		
		if (multiEdit) {
			dosRadio_.setEnabled(false);
			booterRadio_.setEnabled(false);
			Stream.of(booterComposite.getChildren()).forEach(x -> x.setEnabled(false));
			Stream.of(dosComposite.getChildren()).forEach(x -> x.setEnabled(false));
		}
		
		createMountingPageAdditions(configurable, composite);
		
		return composite;
	}
	
	private void showAndHide(Group executeGroup, Composite dosComposite, Composite booterComposite, boolean dos) {
		dosComposite.setVisible(dos);
		((GridData)dosComposite.getLayoutData()).exclude = !dos;
		booterComposite.setVisible(!dos);
		((GridData)booterComposite.getLayoutData()).exclude = dos;
		executeGroup.layout(true, true);
	};
}
