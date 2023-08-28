// Pom1 Apple 1 Emulator
// Copyright (C) 2000 Verhille Arnaud
// Copyright (C) 2012 John D. Corrado
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

package com.enders.pomoneemulator;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

public class Pia6820 {
	private int dspCr, dsp, kbdCr, kbd;
	
	public Pia6820(SharedPreferences preferences) {
		dspCr = preferences.getInt("dspCr", 0);
		dsp = preferences.getInt("dsp", 0);
		kbdCr = preferences.getInt("kbdCr", 0);
		kbd = preferences.getInt("kbd", 0x80);
	}
	
	public void saveState(Editor editor) {
		editor.putInt("dspCr", dspCr);
		editor.putInt("dsp", dsp);
		editor.putInt("kbdCr", kbdCr);
		editor.putInt("kbd", kbd);
	}
	
	public void reset() {
		kbdCr = dspCr = dsp = 0;
		kbd = 0x80;
	}
	
	public void writeDspCr(int dspCr) {
		this.dspCr = dspCr;
	}
	
	public void writeDsp(int dsp) {
		if ((dspCr & 0x04) == 0)
			return;

		this.dsp = dsp;
	}

	public void writeKbdCr(int kbdCr) {
		if (this.kbdCr == 0)
			kbdCr = 0x27;

		this.kbdCr = kbdCr;
	}

	public void writeKbd(int kbd) {
		this.kbd = kbd;
	}

	public int readDspCr() {
		return dspCr;
	}

	public int readDsp() {
		return dsp;
	}

	public int readKbdCr() {
		return kbdCr;
	}

	public int readKbd() {
		kbdCr = 0x27;
		return kbd;
	}
}