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

import org.dbgl.gui.controls.Button_;
import org.dbgl.gui.controls.Composite_;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;


public abstract class WizardDialog<T> extends SizeControlledDialog<T> {

	private static final int TAKE_ONE_STEP = 1;

	private final StackLayout wizardLayout_;

	protected Composite contents_;
	protected Button backButton_;
	protected Button nextButton_;
	protected Button cancelButton_;
	protected int step_;

	protected WizardDialog(Shell parent, String dialogName) {
		super(parent, dialogName);

		wizardLayout_ = new StackLayout();
		step_ = 0;
	}

	@Override
	protected void onShellInit() {
		super.onShellInit();

		shell_.setLayout(new GridLayout());

		contents_ = Composite_.on(shell_).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).layout(wizardLayout_).build();

		Composite buttons = Composite_.on(shell_).layoutData(new GridData(SWT.FILL, SWT.FILL, true, false)).innerLayout(2).build();
		Composite backNextButtons = Composite_.on(buttons).layoutData(new GridData(SWT.BEGINNING, SWT.FILL, false, false)).innerLayout(2).build();

		backButton_ = Button_.on(backNextButtons).text().key("button.back").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				goBack();
			}
		}).ctrl();

		nextButton_ = Button_.on(backNextButtons).text().key("button.next").listen(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				goForward();
			}
		}).ctrl();
		shell_.setDefaultButton(nextButton_);

		GridLayout gridLayoutOtherButtons = new GridLayout(2, true);
		gridLayoutOtherButtons.marginWidth = 80;
		gridLayoutOtherButtons.marginHeight = 0;
		Composite otherButtons = Composite_.on(buttons).layoutData(new GridData(SWT.BEGINNING, SWT.FILL, false, false)).layout(gridLayoutOtherButtons).build();

		cancelButton_ = Button_.on(otherButtons).text().key("button.cancel").listen(closeShellAdapter).ctrl();

		setLayoutDataButtons(backButton_, nextButton_, cancelButton_);
	}

	protected void addStep(Group group) {
		group.setParent(contents_);
	}

	@Override
	protected void onShellOpened() {
		activateCurrentStep();
	}

	protected int stepSize(int step, boolean forward) {
		return TAKE_ONE_STEP;
	}

	protected void goBack() {
		step_ -= stepSize(step_, false);
		activateCurrentStep();
	}

	protected void goForward() {
		if (onNext(step_)) {
			if (step_ >= (totalSteps() - 1)) {
				shell_.close();
			} else {
				step_ += stepSize(step_, true);
				activateCurrentStep();
			}
		}
	}

	protected abstract boolean onNext(int step);

	protected void activateCurrentStep() {
		backButton_.setEnabled(step_ > 0);
		nextButton_.setText(step_ < (totalSteps() - 1) ? text_.get("button.next"): text_.get("button.finish"));

		if (step_ < contents_.getChildren().length) {
			wizardLayout_.topControl = contents_.getChildren()[step_];
			contents_.layout();
			wizardLayout_.topControl.setFocus();
		}
	}

	protected int totalSteps() {
		return contents_.getChildren().length;
	}
}
