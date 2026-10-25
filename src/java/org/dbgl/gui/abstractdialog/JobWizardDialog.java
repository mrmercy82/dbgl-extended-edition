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

import java.lang.Thread.State;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Group_;
import org.dbgl.gui.controls.ProgressBar_;
import org.dbgl.gui.thread.UIThread;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;


public abstract class JobWizardDialog<T> extends WizardDialog<T> {

	protected ProgressBar progressBar_;
	protected Label status_;
	protected Text log_;
	protected UIThread<?> job_;

	protected JobWizardDialog(Shell parent, String dialogName) {
		super(parent, dialogName);
	}

	@Override
	protected void shellDispatchCallback() {
		if (hasJobFinished() && !nextButton_.getEnabled())
			goForward();
	}

	@Override
	protected void activateCurrentStep() {
		super.activateCurrentStep();

		if (job_ != null) {
			backButton_.setEnabled(false);
			nextButton_.setEnabled(hasJobFinished());
			cancelButton_.setEnabled(false);

			if (job_.getState() == State.NEW)
				job_.start();
		}
	}

	@Override
	protected int totalSteps() {
		return super.totalSteps() + 2;
	}

	private boolean hasJobFinished() {
		return job_ != null && job_.getState() == State.TERMINATED;
	}

	protected void addFinalStep(String groupTitle, String statusText) {
		Group progressGroup = Group_.on(shell_).layout(new GridLayout()).key(groupTitle).build();
		progressBar_ = ProgressBar_.on(progressGroup).build();
		Chain chain = Chain.on(progressGroup).lbl(l -> l.key(statusText)).txt(t -> t.multi().readOnly()).build();
		status_ = chain.getLabel();
		log_ = chain.getText();
		addStep(progressGroup);
	}
}
