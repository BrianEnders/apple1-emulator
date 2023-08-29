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

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;

public class Keyboard implements OnKeyListener {
	public Pia6820 pia6820;
	private Screen screen;
	
	public Keyboard(Pia6820 pia6820, Screen screen) {
		this.pia6820 = pia6820;
		this.screen = screen;
	}

	public boolean onKey(View view, int keyCode, KeyEvent event) {

        //Leog.v("KEYEVENT", "PIA: " + pia6820.readKbdCr());
		if (pia6820.readKbdCr() == 0x27 && !screen.isInputFileOpen() && event.getAction() == KeyEvent.ACTION_DOWN && (event.getUnicodeChar() & 0xFF80) == 0 && event.getUnicodeChar() != 0) {
            //Loeg.v("KEYEVENT", "KEY: " + keyCode);
			int tmp = event.getUnicodeChar() & 0x7F;
			//Leog.v("KEYEVENT", "KEY tmp: " + tmp);

			if (tmp >= 0x61 && tmp <= 0x7A)
				tmp &= 0x5F;
			
			if (tmp == 0x0A)
				tmp = 0x0D; 

			if (tmp < 0x60) {
				pia6820.writeKbd(tmp | 0x80);
				pia6820.writeKbdCr(0xA7);
			}
		}
		
		if (pia6820.readKbdCr() == 0x27 && !screen.isInputFileOpen() && keyCode == 67 && event.getAction() == KeyEvent.ACTION_DOWN) {
			pia6820.writeKbd(0xDF | 0x80);
			pia6820.writeKbdCr(0xA7);
		}

		return false;
	}

}