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

import android.content.Context;
import android.content.SharedPreferences;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class Memory {
	private byte[] mem = new byte[65536];
	private boolean ram8k, writeInRom;
	private Pia6820 pia6820;
	private Context context;
	
	public Memory(Context context, Pia6820 pia6820, SharedPreferences preferences) {
		this.context = context;
		this.pia6820 = pia6820;
		ram8k = preferences.getBoolean("ram8k", false);
		writeInRom = preferences.getBoolean("writeInRom", true);
		
		if (context.getFileStreamPath("mem.bin").exists()) {
			FileInputStream fis = null;
			
			try {
				fis = context.openFileInput("mem.bin");
				fis.read(mem);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			loadMonitor();
			loadBasic();
		}
	}
	
	public void saveState(SharedPreferences.Editor editor) {
		editor.putBoolean("ram8k", ram8k);
		editor.putBoolean("writeInRom", writeInRom);
		
		FileOutputStream fos = null;
		
		try {
			fos = context.openFileOutput("mem.bin", Context.MODE_PRIVATE);
			
			for (int i = 0; i < 65536; i++)
				fos.write(mem[i]);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private void loadMonitor() {
		InputStream is = null;
		
		try {
			is = context.getAssets().open("rom/monitor.rom");
			is.read(mem, 0xFF00, 256);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private void loadBasic() {
		InputStream is = null;
		
		try {
			is = context.getAssets().open("rom/basic.rom");
			is.read(mem, 0xE000, 4096);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public void reset() {
		Arrays.fill(mem, 0, 57344, (byte) 0);
		loadMonitor();
		loadBasic();
	}
	
	public void setRam8k(boolean ram8k) {
		this.ram8k = ram8k;
	}
	
	public boolean getRam8k() {
		return ram8k;
	}
	
	public void setWriteInRom(boolean writeInRom) {
		this.writeInRom = writeInRom;
	}
	
	public boolean getWriteInRom() {
		return writeInRom;
	}
	
	public int memRead(int address) {
		if (address == 0xD013)
			return pia6820.readDspCr();
		if (address == 0xD012)
			return pia6820.readDsp();
		if (address == 0xD011)
			return pia6820.readKbdCr();
		if (address == 0xD010)
			return pia6820.readKbd();
		
		return mem[address] & 0xFF;
	}
	
	public void memWrite(int address, int value) {
		if (address == 0xD013) {
			pia6820.writeDspCr(value);
			return;
		}
		if (address == 0xD012) {
			pia6820.writeDsp(value | 0x80);
			return;
		}
		if (address == 0xD011) {
			pia6820.writeKbdCr(value);
			return;
		}
		if (address == 0xD010) {
			pia6820.writeKbd(value);
			return;
		}

		if (address >= 0xFF00 && !writeInRom)
			return;
		//if (ram8k && address >= 0x2000 && address < 0xFF00)
		//	return;
			
		mem[address] = (byte) value;
	}

	public byte[] dumpMemory(int start, int end) {
		byte[] fbrut = new byte[end - start + 1];
		System.arraycopy(mem, start, fbrut, 0, end - start + 1);
		return fbrut;
	}
	
	public void setMemory(byte[] data, int start, int size) {
		System.arraycopy(data, 0, mem, start, size);
	}
}