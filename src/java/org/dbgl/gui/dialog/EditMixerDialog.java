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

import java.util.Iterator;
import java.util.Map.Entry;
import org.dbgl.gui.abstractdialog.SizeControlledButtonDialog;
import org.dbgl.gui.controls.Chain;
import org.dbgl.gui.controls.Group_;
import org.dbgl.model.MixerCommand;
import org.dbgl.model.MixerCommand.Channel;
import org.dbgl.model.MixerCommand.ChannelType;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;


public class EditMixerDialog extends SizeControlledButtonDialog<String> {

	private final MixerCommand mixerCommand_;

	public EditMixerDialog(Shell parent, String command, String[] deviceNames, boolean[] enabledDevices) {
		super(parent, "mixerdialog");

		mixerCommand_ = new MixerCommand(command, deviceNames, enabledDevices);
	}

	@Override
	protected String getDialogTitle() {
		return text_.get("dialog.mixer.title");
	}

	@Override
	protected void onShellCreated() {
		contents_.setLayout(new GridLayout(MixerCommand.CHANNELS.length, true));

		Iterator<Entry<ChannelType, Channel>> it = mixerCommand_.getChannelIterator();
		while (it.hasNext()) {
			Entry<ChannelType, Channel> entry = it.next();
			Channel channel = entry.getValue();

			Group channelGroup = Group_.on(contents_).layout(new GridLayout(2, false)).layoutData(new GridData(SWT.FILL, SWT.FILL, true, true)).key("dialog.mixer." + entry.getKey().name().toLowerCase()).build();
			Chain chnChannel = Chain.on(channelGroup)
				.but(b -> b.horSpan(2).key("dialog.mixer.set").select(channel.isEnabled()))
				.lbl(l -> l).lbl(l -> l)
				.scl(s -> s.vertical().select(MixerCommand.MAX_VOLUME_LEVEL - channel.getLeft()).max(MixerCommand.MAX_VOLUME_LEVEL).pageIncr(10).enable(channel.isEnabled()))
				.scl(s -> s.vertical().select(MixerCommand.MAX_VOLUME_LEVEL - channel.getRight()).max(MixerCommand.MAX_VOLUME_LEVEL).pageIncr(10).enable(channel.isEnabled()))
				.but(b -> b.horSpan(2).key("dialog.mixer.lockbalance").select(channel.getLeft() == channel.getRight()).enable(channel.isEnabled())).build();
			Label left = chnChannel.getLabels().get(0);
			Label right = chnChannel.getLabels().get(1);
			Scale scaleLeft = chnChannel.getScales().get(0);
			Scale scaleRight = chnChannel.getScales().get(1);
			Button set = chnChannel.getButtons().get(0);
			Button lock = chnChannel.getButtons().get(1);

			setVolumeBar(left, right, scaleLeft, scaleRight);

			set.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent arg0) {
					channel.setEnabled(set.getSelection());
					scaleLeft.setEnabled(channel.isEnabled());
					scaleRight.setEnabled(channel.isEnabled());
					lock.setEnabled(channel.isEnabled());
					setVolumeBar(left, right, scaleLeft, scaleRight);
				}
			});
			
			scaleLeft.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent arg0) {
					if (lock.getSelection()) {
						scaleRight.setSelection(scaleLeft.getSelection());
					}
					channel.setVolume(MixerCommand.MAX_VOLUME_LEVEL - scaleLeft.getSelection(), MixerCommand.MAX_VOLUME_LEVEL - scaleRight.getSelection());
					setVolumeBar(left, right, scaleLeft, scaleRight);
				}
			});

			scaleRight.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent arg0) {
					if (lock.getSelection()) {
						scaleLeft.setSelection(scaleRight.getSelection());
					}
					channel.setVolume(MixerCommand.MAX_VOLUME_LEVEL - scaleLeft.getSelection(), MixerCommand.MAX_VOLUME_LEVEL - scaleRight.getSelection());
					setVolumeBar(left, right, scaleLeft, scaleRight);
				}
			});
		}

		createOkCancelButtons(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				result_ = mixerCommand_.toString();
				shell_.close();
			}
		});
	}

	private void setVolumeBar(Label left, Label right, Scale scaleLeft, Scale scaleRight) {
		left.setText(text_.get("dialog.mixer.leftchannelvolume", new Integer[] {MixerCommand.MAX_VOLUME_LEVEL - scaleLeft.getSelection()}));
		left.pack();
		right.setText(text_.get("dialog.mixer.rightchannelvolume", new Integer[] {MixerCommand.MAX_VOLUME_LEVEL - scaleRight.getSelection()}));
		right.pack();
	}
}
