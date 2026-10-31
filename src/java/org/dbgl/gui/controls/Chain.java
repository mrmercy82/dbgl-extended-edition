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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.dbgl.gui.interfaces.DaControlConvertor;
import org.dbgl.model.NativeCommand;
import org.dbgl.model.aggregate.Profile;
import org.dbgl.model.aggregate.Template;
import org.dbgl.model.conf.Autoexec;
import org.dbgl.model.conf.Configuration;
import org.dbgl.model.conf.Settings;
import org.dbgl.model.entity.GenerationAwareConfigurable;
import org.dbgl.model.entity.TemplateProfileBase;
import org.dbgl.service.SettingsService;
import org.dbgl.service.TextService;
import org.dbgl.util.SystemUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;


public class Chain {

	@FunctionalInterface
	public interface TriConsumer<T, U, V> {
		public void accept(T t, U u, V v);

		public default TriConsumer<T, U, V> andThen(TriConsumer<? super T, ? super U, ? super V> after) {
			Objects.requireNonNull(after);
			return (a, b, c) -> {
				accept(a, b, c);
				after.accept(a, b, c);
			};
		}
	}

	private final Builder builder_;
	private boolean isMultiEdit_, initialValueSet_, conflictingValues_;
	private Object initialValue_, currentValue_;

	public static final class Builder {

		private static final DaControlConvertor defaultConvertor = SingleValueDaControlConvertor.getInstance();

		private final Composite composite_;
		private final List<Label> labels_;
		private final List<Control> controls_;
		private Text text_;
		private final List<Combo> combos_;
		private final List<Button> buttons_;
		private org.eclipse.swt.widgets.List list_;
		private Spinner spinner_;
		private final List<Scale> scales_;

		private Function<TemplateProfileBase, String> customSectionGetter_;
		private BiConsumer<TemplateProfileBase, String> customSectionUpdater_;
		private Function<TemplateProfileBase, List<NativeCommand>> nativeCommandsGetter_;
		private BiConsumer<TemplateProfileBase, List<NativeCommand>> nativeCommandsUpdater_;
		private Function<Template, String> templateStringGetter_;
		private BiConsumer<Template, String> templateStringUpdater_;
		private Function<Profile, String> profileStringGetter_;
		private BiFunction<Profile, Integer, String> profileIndexedStringGetter_;
		private BiConsumer<Profile, String> profileStringUpdater_;
		private TriConsumer<Profile, Integer, String> profileIndexedStringUpdater_;
		private Function<Autoexec, String> autoexecStringGetter_;
		private BiConsumer<Autoexec, String> autoexecStringUpdater_;
		private BiFunction<Autoexec, Integer, String> autoexecIndexedStringGetter_;
		private TriConsumer<Autoexec, Integer, String> autoexecIndexedStringUpdater_;
		private int index_;

		private String section_, sectionNew_, item_, itemNew_;
		private boolean isOnOff_;
		private DaControlConvertor convertor_ = defaultConvertor;
		private Function<TemplateProfileBase, DaControlConvertor> customInputConvertorGetter_;
		private Function<TemplateProfileBase, DaControlConvertor> customOutputConvertorGetter_;

		public Builder(Composite composite) {
			composite_ = composite;
			controls_ = new ArrayList<>();
			labels_ = new ArrayList<>();
			combos_ = new ArrayList<>();
			buttons_ = new ArrayList<>();
			scales_ = new ArrayList<>();
		}

		public Builder lbl(UnaryOperator<Label_.Builder> lb) {
			labels_.add(lb.apply(Label_.on(composite_)).build());
			return this;
		}

		public Builder txt(UnaryOperator<Text_.Builder> tb) {
			text_ = tb.apply(Text_.on(composite_)).build();
			controls_.add(text_);
			return this;
		}

		public Builder cmb(UnaryOperator<Combo_.Builder> cb) {
			Combo combo = cb.apply(Combo_.on(composite_)).build();
			combos_.add(combo);
			controls_.add(combo);
			return this;
		}

		public Builder but(UnaryOperator<Button_.Builder> bb) {
			TextControl_ textControl = text_ != null ? new TextControl_(text_): !combos_.isEmpty() ? new TextControl_(combos_.get(combos_.size() - 1)): null;
			Button button = bb.apply(Button_.on(composite_)).ctrl(textControl);
			buttons_.add(button);
			if (IntStream.of(SWT.RADIO, SWT.TOGGLE, SWT.CHECK).anyMatch(x -> (button.getStyle() & x) == x))
				controls_.add(button);
			return this;
		}

		public Builder lst(UnaryOperator<List_.Builder> lb) {
			list_ = lb.apply(List_.on(composite_)).build();
			controls_.add(list_);
			return this;
		}

		public Builder spn(UnaryOperator<Spinner_.Builder> sb) {
			spinner_ = sb.apply(Spinner_.on(composite_)).build();
			controls_.add(spinner_);
			return this;
		}

		public Builder scl(UnaryOperator<Scale_.Builder> sb) {
			Scale scale = sb.apply(Scale_.on(composite_)).build();
			scales_.add(scale);
			controls_.add(scale);
			return this;
		}

		public Builder section(String section) {
			return section(section, null);
		}

		public Builder section(String sectionOld, String sectionNew) {
			section_ = sectionOld;
			sectionNew_ = sectionNew;
			return this;
		}

		public Builder item(String item) {
			return item(item, null);
		}

		public Builder item(String itemOld, String itemNew) {
			item_ = itemOld;
			itemNew_ = itemNew;
			return this;
		}

		public Builder onOff() {
			isOnOff_ = true;
			return this;
		}

		public Builder convert(DaControlConvertor convertor) {
			convertor_ = convertor;
			return this;
		}
		
		public Builder customInputConvertor(Function<TemplateProfileBase, DaControlConvertor> getMethod) {
			customInputConvertorGetter_  = getMethod;
			return this;
		}
		
		public Builder customOutputConvertor(Function<TemplateProfileBase, DaControlConvertor> getMethod) {
			customOutputConvertorGetter_  = getMethod;
			return this;
		}

		public Builder customSection(Function<TemplateProfileBase, String> getMethod, BiConsumer<TemplateProfileBase, String> updateMethod) {
			customSectionGetter_ = getMethod;
			customSectionUpdater_ = updateMethod;
			return this;
		}

		public Builder nativeCommands(Function<TemplateProfileBase, List<NativeCommand>> getMethod, BiConsumer<TemplateProfileBase, List<NativeCommand>> updateMethod) {
			nativeCommandsGetter_ = getMethod;
			nativeCommandsUpdater_ = updateMethod;
			return this;
		}

		public Builder template(Function<Template, String> getMethod, BiConsumer<Template, String> updateMethod) {
			templateStringGetter_ = getMethod;
			templateStringUpdater_ = updateMethod;
			return this;
		}

		public Builder profile(Function<Profile, String> getMethod, BiConsumer<Profile, String> updateMethod) {
			profileStringGetter_ = getMethod;
			profileStringUpdater_ = updateMethod;
			return this;
		}

		public Builder profile(int i, BiFunction<Profile, Integer, String> getMethod, TriConsumer<Profile, Integer, String> updateMethod) {
			index_ = i;
			profileIndexedStringGetter_ = getMethod;
			profileIndexedStringUpdater_ = updateMethod;
			return this;
		}

		public Builder autoexec(Function<Autoexec, String> getMethod, BiConsumer<Autoexec, String> updateMethod) {
			autoexecStringGetter_ = getMethod;
			autoexecStringUpdater_ = updateMethod;
			return this;
		}

		public Builder autoexec(int i, BiFunction<Autoexec, Integer, String> getMethod, TriConsumer<Autoexec, Integer, String> updateMethod) {
			index_ = i;
			autoexecIndexedStringGetter_ = getMethod;
			autoexecIndexedStringUpdater_ = updateMethod;
			return this;
		}

		public Chain build(List<Chain> chains) {
			Chain chain = build();
			chains.add(chain);
			return chain;
		}

		public Chain build() {
			return new Chain(this);
		}

		public Label label() {
			return build().getLabel();
		}

		public Text text() {
			return build().getText();
		}

		public Combo combo() {
			return build().getCombo();
		}

		public Button button() {
			return build().getButton();
		}

		public org.eclipse.swt.widgets.List list() {
			return build().getList();
		}

		public Spinner spinner() {
			return build().getSpinner();
		}

		public Scale scale() {
			return build().getScale();
		}
	}

	public Chain(Builder builder) {
		builder_ = builder;
	}

	public static Builder on(Composite composite) {
		return new Builder(composite);
	}

	public void multiEdit() {
		isMultiEdit_ = true;
	}

	public void bindListenersAndSetLabelColor() {
		final ModifyListener modifyListener = event -> updateLabelColor();

		final SelectionAdapter selectionListener = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				if ((event.widget instanceof Button) && conflictingValues_) {
					Button button = (Button)event.widget;
					if ((button.getStyle() & SWT.CHECK) == SWT.CHECK) {
						if (button.getSelection()) {
							if (button.getGrayed()) {
								button.setSelection(false);
							}
						} else {
							if (button.getGrayed()) {
								button.setGrayed(false);
							} else {
								button.setGrayed(true);
								button.setSelection(true);
							}
						}
					}
				}
				updateLabelColor();
			}
		};

		for (Control cntrl: builder_.controls_) {
			if (cntrl instanceof Combo)
				((Combo)cntrl).addModifyListener(modifyListener);
			else if (cntrl instanceof Text)
				((Text)cntrl).addModifyListener(modifyListener);
			else if (cntrl instanceof Button)
				((Button)cntrl).addSelectionListener(selectionListener);
			else if (cntrl instanceof Spinner)
				((Spinner)cntrl).addModifyListener(modifyListener);
			else if (cntrl instanceof Scale)
				((Scale)cntrl).addSelectionListener(selectionListener);
			else if (cntrl instanceof org.eclipse.swt.widgets.List)
				((org.eclipse.swt.widgets.List)cntrl).addSelectionListener(selectionListener);
		}

		updateLabelColor();
	}

	private void updateLabelColor() {

		if (hasChangedValue()) {
			Color color = DarkTheme.forced() ? DarkTheme.changedForeground: Display.getDefault().getSystemColor(SWT.COLOR_RED);
			getLabel().setForeground(color);
			getLabel().setToolTipText(TextService.getInstance().get("dialog.multiprofile.title.alteredvalue"));
		} else if (conflictingValues_) {
			Color color = DarkTheme.forced() ? DarkTheme.conflictingForeground: Display.getDefault().getSystemColor(SWT.COLOR_DARK_RED);
			getLabel().setForeground(color);
			getLabel().setToolTipText(TextService.getInstance().get("dialog.multiprofile.title.conflictingvalues"));
		} else {
			Color color = DarkTheme.forced() ? DarkTheme.defaultForeground: Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
			getLabel().setForeground(color);
			getLabel().setToolTipText(TextService.getInstance().get("dialog.multiprofile.title.unalteredvalue"));
		}
	}

	public List<Label> getLabels() {
		return builder_.labels_;
	}

	public Label getLabel() {
		return builder_.labels_.get(0);
	}

	public Text getText() {
		return builder_.text_;
	}

	public List<Combo> getCombos() {
		return builder_.combos_;
	}

	public Combo getCombo() {
		return builder_.combos_.get(0);
	}

	public List<Button> getButtons() {
		return builder_.buttons_;
	}

	public Button getButton() {
		return builder_.buttons_.get(0);
	}

	public org.eclipse.swt.widgets.List getList() {
		return builder_.list_;
	}

	public Spinner getSpinner() {
		return builder_.spinner_;
	}

	public List<Scale> getScales() {
		return builder_.scales_;
	}

	public Scale getScale() {
		return builder_.scales_.get(0);
	}
	
	public Chain init(TemplateProfileBase configurable) {
		if ((configurable.getId() == -1) && (configurable instanceof Profile) 
				&& (((Profile)configurable).getStats() == null))
			multiEdit();
		enableOrDisableControl(configurable);
		
		if (builder_.customInputConvertorGetter_ != null) {
			DaControlConvertor customConv = builder_.customInputConvertorGetter_.apply(configurable);
			if ((customConv != builder_.convertor_) && (customConv != null)) {
				Configuration conf = isMultiEdit_ ? configurable.getConfiguration(): configurable.getCombinedConfiguration();
				String[] values = customConv.toControlValues(conf.getValue(builder_.section_, builder_.item_));
				String value = builder_.convertor_.toConfValue(null, values);
				configurable.setValue(builder_.section_, builder_.item_, value);

				System.err.println("Invalid [" + builder_.section_ + "] " + builder_.item_ + " value detected (" + value + ")");
			}
		}
		
		setComboValues(configurable);
		setControlByConfigurable(configurable, configurable.getCombinedConfiguration());
		if (isMultiEdit_)
			bindListenersAndSetLabelColor();
		return this;
	}

	public void enableOrDisableControl(TemplateProfileBase configurable) {
		if (StringUtils.isNotBlank(builder_.section_)) {
			Configuration conf = configurable.getDosboxVersion().getConfiguration();
			boolean dosboxHasConfSetting = conf.hasValue(confSection(conf), confItem(conf));
			builder_.controls_.forEach(x -> x.setEnabled(dosboxHasConfSetting));
			builder_.labels_.get(0).setEnabled(dosboxHasConfSetting);
			builder_.labels_.get(0).setToolTipText("[" + confSection(conf) +"]  " + confItem(conf));
		}
	}

	public void setControlByConfigurable(TemplateProfileBase configurable, Configuration combinedConf) {
		if (builder_.customSectionGetter_ != null)
			setControlByStringValue(builder_.customSectionGetter_.apply(configurable));
		else if (builder_.nativeCommandsGetter_ != null)
			setControlByNativeCommands(builder_.nativeCommandsGetter_.apply(configurable));
		else if (builder_.templateStringGetter_ != null)
			setControlByStringValue(builder_.templateStringGetter_.apply((Template)configurable));
		else if (builder_.profileStringGetter_ != null)
			setControlByStringValue(builder_.profileStringGetter_.apply((Profile)configurable));
		else if (builder_.profileIndexedStringGetter_ != null)
			setControlByStringValue(builder_.profileIndexedStringGetter_.apply((Profile)configurable, builder_.index_));
		else if (builder_.autoexecStringGetter_ != null)
			setControlByStringValue(builder_.autoexecStringGetter_.apply(isMultiEdit_ ? configurable.getConfiguration().getAutoexec(): combinedConf.getAutoexec()));
		else if (builder_.autoexecIndexedStringGetter_ != null)
			setControlByStringValue(builder_.autoexecIndexedStringGetter_.apply(isMultiEdit_ ? configurable.getConfiguration().getAutoexec(): combinedConf.getAutoexec(), builder_.index_));
		else if (StringUtils.isNotBlank(builder_.section_)) {
			if (allControlsDisabled())
				return;

			Configuration dosboxConf = configurable.getDosboxVersion().getConfiguration();
			Configuration conf = isMultiEdit_ ? configurable.getConfiguration(): combinedConf;
			DaControlConvertor conv = (builder_.customOutputConvertorGetter_ != null) 
					? builder_.customOutputConvertorGetter_.apply(configurable)
					: builder_.convertor_;
			String[] values = conf.hasValue(builder_.section_, builder_.item_) && conf.hasValue(builder_.sectionNew_, builder_.itemNew_)
					? conv.toControlValues(new String[] {conf.getValue(builder_.section_, builder_.item_), conf.getValue(builder_.sectionNew_, builder_.itemNew_)})
					: conv.toControlValues(conf.getValue(confSection(dosboxConf), confItem(dosboxConf)));

			if ((values == null) || (values.length != builder_.controls_.size())) {
				if (isMultiEdit_) {
					for (int i = 0; i < builder_.controls_.size(); i++)
						setFieldValue(builder_.controls_.get(i), null);
				} else {
					builder_.controls_.forEach(x -> x.setEnabled(false));
					System.err.println(toString() + ": control disabled because of configuration mismatch");
					return;
				}
			} else {
				for (int i = 0; i < builder_.controls_.size(); i++)
					setFieldValue(builder_.controls_.get(i), values[i]);
			}

			if (!initialValueSet_) {
				initialValue_ = getCurrentStringValue();
				initialValueSet_ = true;
				conflictingValues_ = (values == null) || (values.length == 0);
			}
		}
	}

	public void updateConfigurableByControl(TemplateProfileBase configurable, Configuration combinedConf) {
		if (builder_.customSectionUpdater_ != null)
			builder_.customSectionUpdater_.accept(configurable, getCurrentStringValue());
		else if (builder_.nativeCommandsUpdater_ != null)
			builder_.nativeCommandsUpdater_.accept(configurable, getCurrentNativeCommands());
		else if (builder_.templateStringUpdater_ != null)
			builder_.templateStringUpdater_.accept((Template)configurable, getCurrentStringValue());
		else if (builder_.profileStringUpdater_ != null)
			builder_.profileStringUpdater_.accept((Profile)configurable, getCurrentStringValue());
		else if (builder_.profileIndexedStringUpdater_ != null)
			builder_.profileIndexedStringUpdater_.accept((Profile)configurable, builder_.index_, getCurrentStringValue());
		else if (builder_.autoexecStringUpdater_ != null)
			builder_.autoexecStringUpdater_.accept(configurable.getConfiguration().getAutoexec(), getCurrentStringValue());
		else if (builder_.autoexecIndexedStringUpdater_ != null)
			builder_.autoexecIndexedStringUpdater_.accept(configurable.getConfiguration().getAutoexec(), builder_.index_, getCurrentStringValue());
		else if (StringUtils.isNotBlank(builder_.section_)) {
			String[] fieldValues = builder_.controls_.stream().map(this::getFieldValue).filter(Objects::nonNull).toArray(String[]::new);
			if (fieldValues.length == 0)
				return;

			if (combinedConf.hasValue(builder_.section_, builder_.item_) && combinedConf.hasValue(builder_.sectionNew_, builder_.itemNew_)) {
				String[] confValues = builder_.convertor_.toConfValues(fieldValues);
				configurable.setValue(builder_.section_, builder_.item_, confValues[0]);
				configurable.setValue(builder_.sectionNew_, builder_.itemNew_, confValues[1]);
			} else {
				Configuration dosboxConf = configurable.getDosboxVersion().getConfiguration();
				String section = confSection(dosboxConf);
				String item = confItem(dosboxConf);
				
				if (builder_.customOutputConvertorGetter_ != null) {
					DaControlConvertor customConv = builder_.customOutputConvertorGetter_.apply(configurable);
					String value = customConv.toConfValue(null, fieldValues);
					configurable.setValue(section, item, value);
				} else {
					String value = builder_.convertor_.toConfValue(combinedConf.getValue(section, item), fieldValues);
					if (isSingleReadOnlyCombo() && StringUtils.isBlank(value)) {
						// nothing selected: never write an empty value, let the DOSBox default apply (also repairs profiles saved with empty values)
						configurable.getConfiguration().removeValueIfSet(section, item);
						return;
					}
					if (isSingleCheckbox() && !builder_.isOnOff_ && Strings.CI.equalsAny(StringUtils.trim(dosboxConf.getValue(section, item)), "on", "off")) {
						// DOSBox Staging 0.83+ uses on/off notation for booleans, keep it so values equal to the DOSBox default stay recognizable
						value = isTrueValue(value) ? "on": "off";
					}
					configurable.setValue(section, item, value);
				}
			}
		}
	}

	public void setControlByNativeCommands(List<NativeCommand> cmds) {
		if (cmds == null) {
			conflictingValues_ = true;
			cmds = new ArrayList<>();
			NativeCommand.insertDosboxCommand(cmds);
		}

		currentValue_ = cmds;
		org.eclipse.swt.widgets.List nativeCommandsList = ((org.eclipse.swt.widgets.List)builder_.controls_.get(0));
		nativeCommandsList.setItems(cmds.stream().map(NativeCommand::toString).toArray(String[]::new));
		nativeCommandsList.notifyListeners(SWT.Selection, new Event());

		if (!initialValueSet_) {
			initialValue_ = cmds;
			initialValueSet_ = true;
		}
	}

	private void setControlByStringValue(String value) {
		setFieldValue(builder_.controls_.get(0), value);

		if (!initialValueSet_) {
			initialValue_ = getCurrentStringValue();
			initialValueSet_ = true;
			conflictingValues_ = (value == null);
		}
	}

	@SuppressWarnings("unchecked")
	public String getInitialNativeCommandsAsString() {
		return nativeCommandsToString((List<NativeCommand>)initialValue_);
	}

	public String getInitialStringValue() {
		return (String)initialValue_;
	}

	@SuppressWarnings("unchecked")
	public List<NativeCommand> getCurrentNativeCommands() {
		return (List<NativeCommand>)currentValue_;
	}

	public String getCurrentNativeCommandsAsString() {
		return nativeCommandsToString(getCurrentNativeCommands());
	}

	private static String nativeCommandsToString(List<NativeCommand> obj) {
		if (obj != null)
			return obj.stream().map(NativeCommand::toString).collect(Collectors.joining("; "));
		return StringUtils.EMPTY;
	}

	public String getCurrentStringValue() {
		String[] values = builder_.controls_.stream().map(this::getFieldValue).filter(Objects::nonNull).toArray(String[]::new);
		return values.length == builder_.controls_.size() ? builder_.convertor_.toConfValue(null, values): null;
	}

	public String getCurrentStringValueForDisplay() {
		String[] values = builder_.controls_.stream().map(this::getFieldValue).filter(Objects::nonNull).toArray(String[]::new);
		return values.length == builder_.controls_.size() ? builder_.convertor_.toConfValueForDisplay(values): "<invalid>";
	}

	public boolean conflictingValues() {
		return conflictingValues_;
	}

	@SuppressWarnings("unchecked")
	public boolean hasChangedValue() {
		if (allControlsDisabled())
			return false;
		else if (builder_.nativeCommandsGetter_ != null)
			return (List<NativeCommand>)initialValue_ != getCurrentNativeCommands();
		else
			return !Strings.CS.equals(getInitialStringValue(), getCurrentStringValue());
	}

	private String getFieldValue(Control control) {
		if (control.isEnabled()) {
			if (control instanceof Text) {
				String contents = ((Text)control).getText();
				String del = ((Text)control).getLineDelimiter();
				return Strings.CS.replace(StringUtils.strip(contents, del), del, SystemUtils.EOLN);
			} else if (control instanceof Combo) {
				return ((Combo)control).getText();
			} else if (control instanceof Button && !((Button)control).getGrayed()) {
				boolean v = ((Button)control).getSelection();
				return builder_.isOnOff_ ? (v ? "on": "off"): String.valueOf(v);
			} else if (control instanceof Scale) {
				return String.valueOf(((Scale)control).getSelection());
			} else if (control instanceof Spinner) {
				int digits = ((Spinner)control).getDigits();
				int selection = ((Spinner)control).getSelection();
				return new BigDecimal(selection).movePointLeft(digits).toString();
			}
		}
		return null;
	}

	private void setFieldValue(Control control, String value) {
		if (control.isEnabled()) {
			if (value == null) {
				if ((control instanceof Button) && ((((Button)control).getStyle() & SWT.CHECK) == SWT.CHECK)) {
					((Button)control).setSelection(true);
					((Button)control).setGrayed(true);
				}
			} else {
				if (control instanceof Text) {
					String newValue = Strings.CS.replace(value, SystemUtils.EOLN, ((Text)control).getLineDelimiter());
					if (!((Text)control).getText().equals(newValue))
						((Text)control).setText(newValue);
				} else if (control instanceof Combo combo) {
					if (StringUtils.isNotBlank(value) && ((combo.getStyle() & SWT.READ_ONLY) == SWT.READ_ONLY) && (combo.indexOf(value) == -1))
						combo.add(value); // keep a value the item list doesn't know instead of silently losing it on save
					if (!combo.getText().equals(value))
						combo.setText(value);
				} else if (control instanceof Button) {
					boolean newValue = isTrueValue(value); // accepts both true/false and on/off (DOSBox Staging 0.83+)
					if ((((Button)control).getSelection() != newValue) || ((Button)control).getGrayed()) {
						((Button)control).setSelection(newValue);
						((Button)control).notifyListeners(SWT.Selection, new Event());
					}
				} else if (control instanceof Scale) {
					Integer newValue = Integer.valueOf(value);
					if (((Scale)control).getSelection() != newValue)
						((Scale)control).setSelection(newValue);
				} else if (control instanceof Spinner) {
					int digits = ((Spinner)control).getDigits();
					int newValue = new BigDecimal(value).movePointRight(digits).intValue();
					if (((Spinner)control).getSelection() != newValue)
						((Spinner)control).setSelection(newValue);
				}
			}
		}
	}

	public void setComboValues(TemplateProfileBase configurable) {
		Map<String, String> map = configurable.getDosboxVersion().getDynamicOptions();
		
		getCombos().forEach(x -> {
			String section = (String)x.getData(Combo_.DYN_OPT_SECTION);
			String item = (String)x.getData(Combo_.DYN_OPT_ITEM);

			@SuppressWarnings("unchecked")
			Function<GenerationAwareConfigurable, String> dynSectionItemGetter = (Function<GenerationAwareConfigurable, String>)x.getData(Combo_.DYN_SECTION_ITEM_GETTER);
			if (dynSectionItemGetter != null) {
				item = dynSectionItemGetter.apply(configurable);
				x.setData(Combo_.DYN_OPT_ITEM, item);
			}
			
			if (StringUtils.isNoneBlank(section, item)) {
				@SuppressWarnings("unchecked")
				Function<GenerationAwareConfigurable, String[]> dynItemsGetter = (Function<GenerationAwareConfigurable, String[]>)x.getData(Combo_.DYN_ITEMS_GETTER);
				
				String[] items = dynItemsGetter != null ? dynItemsGetter.apply(configurable): SettingsService.getInstance().getValues(section, item);
				if (map != null) {
					if (map.containsKey(item)) {
						String[] dbItems = Settings.splitValues(map.get(item));
						if (dbItems != null && dbItems.length > 0) {
							items = dbItems;
						} else {
							System.err.println(toString() + ": control not be populated by DOSBox Version dynamic values because it has [" + item + "] set to [" + map.get(item) + "]");
						}
					} else {
						System.err.println(toString() + ": control could not be populated by DOSBox Version dynamic values because it's missing [" + item + "]");
					}
				}
				x.setItems(items);
			}
		});
	}

	private String confSection(Configuration conf) {
		return StringUtils.isNotBlank(builder_.sectionNew_) && StringUtils.isNotBlank(builder_.itemNew_) && conf.hasValue(builder_.sectionNew_, builder_.itemNew_) ? builder_.sectionNew_
				: builder_.section_;
	}

	private String confItem(Configuration conf) {
		return StringUtils.isNotBlank(builder_.itemNew_)
				&& (conf.hasValue(builder_.section_, builder_.itemNew_) || (StringUtils.isNotBlank(builder_.sectionNew_) && conf.hasValue(builder_.sectionNew_, builder_.itemNew_))) ? builder_.itemNew_
						: builder_.item_;
	}

	private static boolean isTrueValue(String value) {
		return Strings.CI.equalsAny(StringUtils.trim(value), "true", "on", "yes", "1", "enabled");
	}

	private boolean isSingleReadOnlyCombo() {
		return (builder_.controls_.size() == 1) && (builder_.controls_.get(0) instanceof Combo)
				&& ((builder_.controls_.get(0).getStyle() & SWT.READ_ONLY) == SWT.READ_ONLY);
	}

	private boolean isSingleCheckbox() {
		return (builder_.controls_.size() == 1) && (builder_.controls_.get(0) instanceof Button)
				&& ((((Button)builder_.controls_.get(0)).getStyle() & SWT.CHECK) == SWT.CHECK);
	}

	private boolean allControlsDisabled() {
		return builder_.controls_.stream().noneMatch(Control::isEnabled);
	}

	@Override
	public String toString() {
		StringBuilder result = new StringBuilder(getLabel().getText());
		if (StringUtils.isNotBlank(builder_.section_)) {
			result.append(" ([");
			result.append(StringUtils.isNotBlank(builder_.sectionNew_) ? builder_.sectionNew_: builder_.section_);
			result.append("] ").append(StringUtils.isNotBlank(builder_.itemNew_) ? builder_.itemNew_: builder_.item_);
			result.append(")");
		}
		return result.toString();
	}
}
