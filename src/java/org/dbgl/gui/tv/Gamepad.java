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
package org.dbgl.gui.tv;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.dbgl.util.SystemUtils;


/**
 * Alpha 48: game controllers for the TV mode, without native libraries of our own:
 * <ul>
 * <li>Windows: XInput (Xbox controllers and all that act like one, also through Steam Input or DS4Windows), read by a small
 * PowerShell script that prints the state when it changes; it ends by itself when DBGL is gone</li>
 * <li>Linux: the joystick devices /dev/input/js0 to js3 (8 byte events)</li>
 * </ul>
 * The state of all controllers together becomes presses of {@link Button}; held directions repeat. Elsewhere, or when nothing can
 * be read, there are just no presses (the keyboard works always).
 */
public final class Gamepad {

	/** what the TV mode reacts to */
	public enum Button {
		UP, DOWN, LEFT, RIGHT, A, B, X, Y, LB, RB, START, BACK
	}

	// XInput's bits
	static final int DPAD_UP = 0x1, DPAD_DOWN = 0x2, DPAD_LEFT = 0x4, DPAD_RIGHT = 0x8, START = 0x10, BACK = 0x20, LB = 0x100, RB = 0x200, A = 0x1000, B = 0x2000, X = 0x4000, Y = 0x8000;
	static final int DEAD_ZONE = 16000;
	static final long REPEAT_DELAY = 400, REPEAT_RATE = 130;

	private final Consumer<Button> listener_;
	private volatile boolean running_ = true;
	private final List<Thread> threads_ = new ArrayList<>();
	private Process process_;

	// the combined state; directions as bits too (stick or pad)
	private int buttons_;
	private final int[] stick_ = new int[2];
	private long nextRepeat_;
	private int repeating_;

	private Gamepad(Consumer<Button> listener) {
		listener_ = listener;
	}

	/** starts reading the controllers; the listener is called on a thread of its own */
	public static Gamepad start(Consumer<Button> listener) {
		Gamepad pad = new Gamepad(listener);
		try {
			if (SystemUtils.IS_WINDOWS)
				pad.startXInput();
			else if (SystemUtils.IS_LINUX)
				pad.startLinux();
		} catch (IOException | RuntimeException e) {
			System.out.println("TV mode: no game controllers (" + e.getMessage() + ")");
		}
		pad.startRepeater();
		return pad;
	}

	public void stop() {
		running_ = false;
		if (process_ != null)
			process_.destroy();
		threads_.forEach(Thread::interrupt);
	}

	static final String XINPUT_SCRIPT = String.join("\n",
		"$ErrorActionPreference = 'Stop'",
		"$src = 'using System; using System.Runtime.InteropServices; public static class DbglPad {"
			+ " [StructLayout(LayoutKind.Sequential)] public struct S { public uint p; public ushort b; public byte lt; public byte rt; public short lx; public short ly; public short rx; public short ry; }"
			+ " [DllImport(\"xinput1_4.dll\", EntryPoint=\"XInputGetState\")] public static extern int Get14(int i, out S s);"
			+ " [DllImport(\"xinput9_1_0.dll\", EntryPoint=\"XInputGetState\")] public static extern int Get91(int i, out S s); }'",
		"Add-Type -TypeDefinition $src",
		"$parent = [int]$args[0]; $use14 = $true; $last = ''; $n = 0",
		"while ($true) {",
		"  $b = 0; $lx = 0; $ly = 0",
		"  for ($i = 0; $i -lt 4; $i++) {",
		"    $s = New-Object DbglPad+S",
		"    try { if ($use14) { $r = [DbglPad]::Get14($i, [ref]$s) } else { $r = [DbglPad]::Get91($i, [ref]$s) } } catch { $use14 = $false; continue }",
		"    if ($r -eq 0) { $b = $b -bor $s.b; if ([Math]::Abs([int]$s.lx) -gt [Math]::Abs([int]$lx)) { $lx = [int]$s.lx }; if ([Math]::Abs([int]$s.ly) -gt [Math]::Abs([int]$ly)) { $ly = [int]$s.ly } }",
		"  }",
		"  $line = \"$b $lx $ly\"",
		"  if ($line -ne $last) { [Console]::Out.WriteLine($line); [Console]::Out.Flush(); $last = $line }",
		"  $n++; if (($n % 60) -eq 0) { if (-not (Get-Process -Id $parent -ErrorAction SilentlyContinue)) { exit } }",
		"  Start-Sleep -Milliseconds 30",
		"}");

	private void startXInput() throws IOException {
		String encoded = java.util.Base64.getEncoder().encodeToString(("& { " + XINPUT_SCRIPT + " } " + ProcessHandle.current().pid()).getBytes(StandardCharsets.UTF_16LE));
		process_ = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded).redirectErrorStream(true).start();
		Process p = process_;
		Runtime.getRuntime().addShutdownHook(new Thread(p::destroy));
		Thread t = new Thread(() -> {
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
				for (String line; running_ && (line = r.readLine()) != null;) {
					String[] parts = line.trim().split("\\s+");
					if (parts.length != 3)
						continue; // a message of PowerShell (e.g. no XInput): no controllers then
					try {
						update(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
					} catch (NumberFormatException e) {
						// not a state
					}
				}
			} catch (IOException e) {
				// ended
			}
		}, "TV mode XInput");
		t.setDaemon(true);
		t.start();
		threads_.add(t);
	}

	private void startLinux() {
		for (int i = 0; i < 4; i++) {
			File dev = new File("/dev/input/js" + i);
			if (!dev.canRead())
				continue;
			int[] state = new int[3]; // buttons (as XInput bits), lx, ly
			Thread t = new Thread(() -> readJoystick(dev, state), "TV mode " + dev.getName());
			t.setDaemon(true);
			t.start();
			threads_.add(t);
		}
	}

	/** the joystick interface of Linux; the buttons and axes as the xpad driver numbers them */
	private void readJoystick(File dev, int[] state) {
		try (InputStream in = new FileInputStream(dev)) {
			byte[] e = new byte[8];
			while (running_) {
				int n = in.readNBytes(e, 0, 8);
				if (n < 8)
					return;
				int value = (short)((e[4] & 0xFF) | (e[5] << 8));
				int type = e[6] & 0x7F, number = e[7] & 0xFF;
				if (type == 1) {
					int bit = switch (number) {
						case 0 -> A;
						case 1 -> B;
						case 2 -> X;
						case 3 -> Y;
						case 4 -> LB;
						case 5 -> RB;
						case 6 -> BACK;
						case 7 -> START;
						default -> 0;
					};
					state[0] = (value != 0) ? (state[0] | bit): (state[0] & ~bit);
				} else if (type == 2) {
					if (number == 0)
						state[1] = value;
					else if (number == 1)
						state[2] = -value; // down is positive here, up in XInput
					else if (number == 6)
						state[0] = (state[0] & ~(DPAD_LEFT | DPAD_RIGHT)) | ((value < 0) ? DPAD_LEFT: (value > 0) ? DPAD_RIGHT: 0);
					else if (number == 7)
						state[0] = (state[0] & ~(DPAD_UP | DPAD_DOWN)) | ((value < 0) ? DPAD_UP: (value > 0) ? DPAD_DOWN: 0);
				}
				update(state[0], state[1], state[2]);
			}
		} catch (IOException e) {
			// unplugged
		}
	}

	/** the directions of the pad and the stick together, as the bits of the pad */
	static int directions(int buttons, int lx, int ly) {
		int d = buttons & (DPAD_UP | DPAD_DOWN | DPAD_LEFT | DPAD_RIGHT);
		if (ly > DEAD_ZONE)
			d |= DPAD_UP;
		else if (ly < -DEAD_ZONE)
			d |= DPAD_DOWN;
		if (lx > DEAD_ZONE)
			d |= DPAD_RIGHT;
		else if (lx < -DEAD_ZONE)
			d |= DPAD_LEFT;
		return d;
	}

	/** a new state: the buttons pressed since the last one */
	synchronized void update(int buttons, int lx, int ly) {
		int all = (buttons & ~(DPAD_UP | DPAD_DOWN | DPAD_LEFT | DPAD_RIGHT)) | directions(buttons, lx, ly);
		int pressed = all & ~buttons_;
		buttons_ = all;
		stick_[0] = lx;
		stick_[1] = ly;
		for (int[] m: MAP)
			if ((pressed & m[0]) != 0)
				listener_.accept(Button.values()[m[1]]);
		int dir = all & (DPAD_UP | DPAD_DOWN | DPAD_LEFT | DPAD_RIGHT);
		if (dir != repeating_) {
			repeating_ = dir;
			nextRepeat_ = System.currentTimeMillis() + REPEAT_DELAY;
		}
	}

	static final int[][] MAP = {{DPAD_UP, 0}, {DPAD_DOWN, 1}, {DPAD_LEFT, 2}, {DPAD_RIGHT, 3}, {A, 4}, {B, 5}, {X, 6}, {Y, 7}, {LB, 8}, {RB, 9}, {START, 10}, {BACK, 11}};

	private void startRepeater() {
		Thread t = new Thread(() -> {
			while (running_) {
				try {
					Thread.sleep(40);
				} catch (InterruptedException e) {
					return;
				}
				synchronized (this) {
					if (repeating_ != 0 && System.currentTimeMillis() >= nextRepeat_) {
						nextRepeat_ = System.currentTimeMillis() + REPEAT_RATE;
						for (int i = 0; i < 4; i++)
							if ((repeating_ & MAP[i][0]) != 0)
								listener_.accept(Button.values()[MAP[i][1]]);
					}
				}
			}
		}, "TV mode repeat");
		t.setDaemon(true);
		t.start();
		threads_.add(t);
	}
}
