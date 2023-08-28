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

public class M6502 implements Runnable {
	protected final int N = 0x80;
	protected final int V = 0x40;
	protected final int B = 0x10;
	protected final int D = 0x08;
	protected final int I = 0x04;
	protected final int Z = 0x02;
	protected final int C = 0x01;
	
	protected int accumulator, xRegister, yRegister, statusRegister, stackPointer;
	protected boolean IRQ, NMI;
	protected int programCounter;
	protected byte btmp;
	protected int op, opH, opL, ptr, ptrH, ptrL, tmp;
	protected long lastTime;
	protected int cycles, cyclesBeforeSynchro, synchroMillis;
	protected Thread thread;
	protected Memory memory;
	public volatile boolean running;
	
	protected boolean valid = false;

	
	public M6502(Memory memory, SharedPreferences preferences) {
		this.memory = memory;
		setSpeed(1000, 50);
		accumulator = preferences.getInt("accumulator", 0);
		xRegister = preferences.getInt("xRegister", 0);
		yRegister = preferences.getInt("yRegister", 0);
		statusRegister = preferences.getInt("statusRegister", 0x24);
		stackPointer = preferences.getInt("stackPointer", 0xFF);
		programCounter = preferences.getInt("programCounter", memReadAbsolute(0xFFFC));
	}
	
	public void saveState(Editor editor) {
		editor.putInt("accumulator", accumulator);
		editor.putInt("xRegister", xRegister);
		editor.putInt("yRegister", yRegister);
		editor.putInt("statusRegister", statusRegister);
		editor.putInt("stackPointer", stackPointer);
		editor.putInt("programCounter", programCounter);
	}
	
	public void resume() {
		if (thread == null) {
			running = true;
			thread = new Thread(this);
			lastTime = System.currentTimeMillis();
			thread.start();
		}
	}
	
	public void pause() {
		if (thread != null) {
			running = false;
		
			while (true) {
				try {
					thread.join();
					break;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		
			thread = null;
		}
	}
	
	public void run() {
		while (running) {
			synchronize();
			
			cycles = 0;
			
			while (running && cycles < cyclesBeforeSynchro) {
				if ((statusRegister & I) == 0 && IRQ)
					handleIRQ();
				if (NMI)
					handleNMI();
				
				executeOpcode();
			}
		}
	}
	
	private int memReadAbsolute(int adr) {
		return (memory.memRead(adr) | memory.memRead(adr + 1) << 8);
	}

	private void synchronize() {
		int realTimeMillis = (int) (System.currentTimeMillis() - lastTime);
		int sleepMillis = synchroMillis - realTimeMillis;

		if (sleepMillis < 0)
			sleepMillis = 5;

		try {
			Thread.sleep(sleepMillis);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		lastTime = System.currentTimeMillis();
	}

	private void pushProgramCounter() {
		memory.memWrite(stackPointer + 0x100, programCounter >> 8);
		stackPointer = stackPointer - 1 & 0xFF;
		memory.memWrite(stackPointer + 0x100, programCounter & 0xFF);
		stackPointer = stackPointer - 1 & 0xFF;
		cycles += 2;
	}

	private void popProgramCounter() {
		stackPointer = stackPointer + 1 & 0xFF;
		programCounter = memory.memRead(stackPointer + 0x100);
		stackPointer = stackPointer + 1 & 0xFF;
		programCounter += memory.memRead(stackPointer + 0x100) << 8;
		cycles += 2;
	}

	private void handleIRQ() {
		pushProgramCounter();
		memory.memWrite(0x100 + stackPointer, statusRegister & ~0x10);
		stackPointer = stackPointer - 1 & 0xFF;
		statusRegister |= I;
		programCounter = memReadAbsolute(0xFFFE);
		cycles += 8;
	}

	private void handleNMI() {
		pushProgramCounter();
		memory.memWrite(0x100 + stackPointer, statusRegister & ~0x10);
		stackPointer = stackPointer - 1 & 0xFF;
		statusRegister |= I;
		NMI = false;
		programCounter = memReadAbsolute(0xFFFA);
		cycles += 8;
	}
	
	private void Imp() {
		cycles++;
	}

	private void Imm() {
		op = programCounter++;
	}

	protected void Zero() {
		op = memory.memRead(programCounter++);
		cycles++;
	}

	private void ZeroX() {
		op = memory.memRead(programCounter++) + xRegister & 0xFF;
		cycles++;
	}

	private void ZeroY() {
		op = memory.memRead(programCounter++) + yRegister & 0xFF;
		cycles++;
	}

	protected void Abs() {
		op = memReadAbsolute(programCounter);
		programCounter += 2;
		cycles += 2;
	}

	private void AbsX() {
		opL = memory.memRead(programCounter++) + xRegister;
		opH = memory.memRead(programCounter++) << 8;
		cycles += 2;

		if ((opL & 0x100) != 0)
			cycles++;

		op = opH + opL;
	}

	private void AbsY() {
		opL = memory.memRead(programCounter++) + yRegister;
		opH = memory.memRead(programCounter++) << 8;
		cycles += 2;

		if ((opL & 0x100) != 0)
			cycles++;

		op = opH + opL;
	}

	private void Ind() {
		ptrL = memory.memRead(programCounter++);
		ptrH = memory.memRead(programCounter++) << 8;
		op = memory.memRead(ptrH + ptrL);
		ptrL = ptrL + 1 & 0xFF;
		op += memory.memRead(ptrH + ptrL) << 8;
		cycles += 4;
	}

	private void IndZeroX() {
		ptr = memory.memRead(programCounter++) + xRegister & 0xFF;
		op = memory.memRead(ptr);
		op += memory.memRead(ptr + 1 & 0xFF) << 8;
		cycles += 3;
	}

	private void IndZeroY() {
		ptr = memory.memRead(programCounter++);
		opL = memory.memRead(ptr) + yRegister;
		opH = memory.memRead(ptr + 1 & 0xFF) << 8;
		cycles += 3;

		if ((opL & 0x100) != 0)
			cycles++;

		op = opH + opL;
	}

	private void Rel() {
		op = memory.memRead(programCounter++);

		if ((op & 0x80) != 0)
			op |= 0xFFFFFF00;

		op = op + programCounter & 0xFFFF;
		cycles++;
	}

	private void WAbsX() {
		opL = memory.memRead(programCounter++) + xRegister;
		opH = memory.memRead(programCounter++) << 8;
		cycles += 3;
		op = opH + opL;
	}

	private void WAbsY() {
		opL = memory.memRead(programCounter++) + yRegister;
		opH = memory.memRead(programCounter++) << 8;
		cycles += 3;
		op = opH + opL;
	}

	private void WIndZeroY() {
		ptr = memory.memRead(programCounter++);
		opL = memory.memRead(ptr) + yRegister;
		opH = memory.memRead(ptr + 1 & 0xFF) << 8;
		cycles += 4;
		op = opH + opL;
	}

	private void setStatusRegisterNZ(byte val) {
		if ((val & 0x80) != 0)
			statusRegister |= N;
		else
			statusRegister &= ~N;

		if (val == 0)
			statusRegister |= Z;
		else
			statusRegister &= ~Z;
	}

	private void LDA() {
		accumulator = memory.memRead(op);
		setStatusRegisterNZ((byte) accumulator);
		cycles++;
	}

	private void LDX() {
		xRegister = memory.memRead(op);
		setStatusRegisterNZ((byte) xRegister);
		cycles++;
	}

	private void LDY() {
		yRegister = memory.memRead(op);
		setStatusRegisterNZ((byte) yRegister);
		cycles++;
	}

	private void STA() {
		memory.memWrite(op, accumulator);
		cycles++;
	}

	private void STX() {
		memory.memWrite(op, xRegister);
		cycles++;
	}

	private void STY() {
		memory.memWrite(op, yRegister);
		cycles++;
	}

	private void setFlagCarry(int val) {
		if ((val & 0x100) != 0)
			statusRegister |= C;
		else
			statusRegister &= ~C;
	}

	private void ADC() {
		int Op1 = accumulator, Op2 = memory.memRead(op);
		cycles++;

		if ((statusRegister & D) != 0) {
			if ((Op1 + Op2 + ((statusRegister & C) != 0 ? 1 : 0) & 0xFF) == 0)
				statusRegister |= Z;
			else
				statusRegister &= ~Z;

			tmp = (Op1 & 0x0F) + (Op2 & 0x0F) + ((statusRegister & C) != 0 ? 1 : 0);
			accumulator = tmp < 0x0A ? tmp : tmp + 6;
			tmp = (Op1 & 0xF0) + (Op2 & 0xF0) + (tmp & 0xF0);
			
			if ((tmp & 0x80) != 0)
				statusRegister |= N;
			else
				statusRegister &= ~N;

			if (((Op1 ^ tmp) & ~(Op1 ^ Op2) & 0x80) != 0)
				statusRegister |= V;
			else
				statusRegister &= ~V;

			tmp = (accumulator & 0x0F) | (tmp < 0xA0 ? tmp : tmp + 0x60);
			
			if (tmp >= 0x100)
				statusRegister |= C;
			else
				statusRegister &= ~C;

			accumulator = tmp & 0xFF;
		} else {
			tmp = Op1 + Op2 + ((statusRegister & C) != 0 ? 1 : 0);
			accumulator = tmp & 0xFF;
			
			if (((Op1 ^ accumulator) & ~(Op1 ^ Op2) & 0x80) != 0)
				statusRegister |= V;
			else
				statusRegister &= ~V;

			setFlagCarry(tmp);
			setStatusRegisterNZ((byte) accumulator);
		}
	}

	private void setFlagBorrow(int val) {
		if ((val & 0x100) == 0)
			statusRegister |= C;
		else
			statusRegister &= ~C;
	}

	private void SBC() {
		int Op1 = accumulator, Op2 = memory.memRead(op);
		cycles++;

		if ((statusRegister & D) != 0) {
			tmp = (Op1 & 0x0F) - (Op2 & 0x0F) - ((statusRegister & C) != 0 ? 0 : 1);
			accumulator = (tmp & 0x10) == 0 ? tmp : tmp - 6;
			tmp = (Op1 & 0xF0) - (Op2 & 0xF0) - (accumulator & 0x10);
			accumulator = (accumulator & 0x0F) | ((tmp & 0x100) == 0 ? tmp : tmp - 0x60);
			tmp = Op1 - Op2 - ((statusRegister & C) != 0 ? 0 : 1);
			setFlagBorrow(tmp);
			setStatusRegisterNZ((byte) tmp);
		} else {
			tmp = Op1 - Op2 - ((statusRegister & C) != 0 ? 0 : 1);
			accumulator = tmp & 0xFF;

			if (((Op1 ^ Op2) & (Op1 ^ accumulator) & 0x80) != 0)
				statusRegister |= V;
			else
				statusRegister &= ~V;
			
			setFlagBorrow(tmp);
			setStatusRegisterNZ((byte) accumulator);
		}
	}

	private void CMP() {
		tmp = accumulator - memory.memRead(op);
		cycles++;
		setFlagBorrow(tmp);
		setStatusRegisterNZ((byte) tmp);
	}

	private void CPX() {
		tmp = xRegister - memory.memRead(op);
		cycles++;
		setFlagBorrow(tmp);
		setStatusRegisterNZ((byte) tmp);
	}

	private void CPY() {
		tmp = yRegister - memory.memRead(op);
		cycles++;
		setFlagBorrow(tmp);
		setStatusRegisterNZ((byte) tmp);
	}

	protected void AND() {
		accumulator &= memory.memRead(op);
		cycles++;
		setStatusRegisterNZ((byte) accumulator);
	}

	private void ORA() {
		accumulator |= memory.memRead(op);
		cycles++;
		setStatusRegisterNZ((byte) accumulator);
	}

	protected void EOR() {
		accumulator ^= memory.memRead(op);
		cycles++;
		setStatusRegisterNZ((byte) accumulator);
	}

	private void ASL() {
		btmp = (byte) memory.memRead(op);
		
		if ((btmp & 0x80) != 0)
			statusRegister |= C;
		else
			statusRegister &= ~C;

		btmp <<= 1;
		setStatusRegisterNZ(btmp);
		memory.memWrite(op, btmp & 0xFF);
		cycles += 3;
	}

	private void ASL_A() {
		tmp = accumulator << 1;
		accumulator = tmp & 0xFF;
		setFlagCarry(tmp);
		setStatusRegisterNZ((byte) accumulator);
	}

	private void LSR() {
		btmp = (byte) memory.memRead(op);
		
		if ((btmp & 1) != 0)
			statusRegister |= C;
		else
			statusRegister &= ~C;

		btmp = (byte) ((btmp & 0xFF) >> 1);
		setStatusRegisterNZ(btmp);
		memory.memWrite(op, btmp & 0xFF);
		cycles += 3;
	}

	private void LSR_A() {
		if ((accumulator & 1) != 0)
			statusRegister |= C;
		else
			statusRegister &= ~C;

		accumulator >>= 1;
		setStatusRegisterNZ((byte) accumulator);
	}

	private void ROL() {
		btmp = (byte) memory.memRead(op);
		boolean newCarry = (btmp & 0x80) != 0;
		btmp = (byte) (((btmp & 0xFF) << 1) | ((statusRegister & C) != 0 ? 1 : 0));

		if (newCarry)
			statusRegister |= C;
		else
			statusRegister &= ~C;
		
		setStatusRegisterNZ(btmp);
		memory.memWrite(op, btmp & 0xFF);
		cycles += 3;
	}

	private void ROL_A() {
		tmp = (accumulator << 1) | ((statusRegister & C) != 0 ? 1 : 0);
		accumulator = tmp & 0xFF;
		setFlagCarry(tmp);
		setStatusRegisterNZ((byte) accumulator);
	}

	private void ROR() {
		btmp = (byte) memory.memRead(op);
		boolean newCarry = (btmp & 1) != 0;
		btmp = (byte) (((btmp & 0xFF) >> 1) | ((statusRegister & C) != 0 ? 0x80 : 0));
		
		if (newCarry)
			statusRegister |= C;
		else
			statusRegister &= ~C;

		setStatusRegisterNZ(btmp);
		memory.memWrite(op, btmp & 0xFF);
		cycles += 3;
	}

	private void ROR_A() {
		tmp = accumulator | ((statusRegister & C) != 0 ? 0x100 : 0);
		
		if ((accumulator & 1) != 0)
			statusRegister |= C;
		else
			statusRegister &= ~C;

		accumulator = tmp >> 1;
		setStatusRegisterNZ((byte) accumulator);
	}

	private void INC() {
		btmp = (byte) memory.memRead(op);
		btmp++;
		setStatusRegisterNZ(btmp);
		memory.memWrite(op, btmp & 0xFF);
		cycles += 2;
	}

	private void DEC() {
		btmp = (byte) memory.memRead(op);
		btmp--;
		setStatusRegisterNZ(btmp);
		memory.memWrite(op, btmp & 0xFF);
		cycles += 2;
	}

	private void INX() {
		xRegister = xRegister + 1 & 0xFF;
		setStatusRegisterNZ((byte) xRegister);
	}

	private void INY() {
		yRegister = yRegister + 1 & 0xFF;
		setStatusRegisterNZ((byte) yRegister);
	}

	private void DEX() {
		xRegister = xRegister - 1 & 0xFF;
		setStatusRegisterNZ((byte) xRegister);
	}

	private void DEY() {
		yRegister = yRegister - 1 & 0xFF;
		setStatusRegisterNZ((byte) yRegister);
	}

	private void BIT() {
		btmp = (byte) memory.memRead(op);

		if ((btmp & 0x40) != 0)
			statusRegister |= V;
		else
			statusRegister &= ~V;
		
		if ((btmp & 0x80) != 0)
			statusRegister |= N;
		else
			statusRegister &= ~N;

		if ((btmp & accumulator) == 0)
			statusRegister |= Z;
		else
			statusRegister &= ~Z;

		cycles++;
	}

	private void PHA() {
		memory.memWrite(0x100 + stackPointer, accumulator);
		stackPointer = stackPointer - 1 & 0xFF;
		cycles++;
	}

	private void PHP() {
		memory.memWrite(0x100 + stackPointer, statusRegister);
		stackPointer = stackPointer - 1 & 0xFF;
		cycles++;
	}

	private void PLA() {
		stackPointer = stackPointer + 1 & 0xFF;
		accumulator = memory.memRead(stackPointer + 0x100);
		setStatusRegisterNZ((byte) accumulator);
		cycles += 2;
	}

	private void PLP() {
		stackPointer = stackPointer + 1 & 0xFF;
		statusRegister = memory.memRead(stackPointer + 0x100);
		cycles += 2;
	}

	private void BRK() {
		pushProgramCounter();
		PHP();
		statusRegister |= B;
		programCounter = memReadAbsolute(0xFFFE);
		cycles += 3;
	}

	private void RTI() {
		PLP();
		popProgramCounter();
		cycles++;
	}

	private void JMP() {
		programCounter = op;
	}

	private void RTS() {
		popProgramCounter();
		programCounter++;
		cycles += 2;
	}

	private void JSR() {
		opL = memory.memRead(programCounter++);
		pushProgramCounter();
		programCounter = opL + (memory.memRead(programCounter) << 8);
		cycles += 3;
	}

	private void branch() {
		cycles++;

		if ((programCounter & 0xFF00) != (op & 0xFF00))
			cycles++;

		programCounter = op;
	}

	private void BNE() {
		if ((statusRegister & Z) == 0)
			branch();
	}

	private void BEQ() {
		if ((statusRegister & Z) != 0)
			branch();
	}

	private void BVC() {
		if ((statusRegister & V) == 0)
			branch();
	}

	private void BVS() {
		if ((statusRegister & V) != 0)
			branch();
	}

	private void BCC() {
		if ((statusRegister & C) == 0)
			branch();
	}

	private void BCS() {
		if ((statusRegister & C) != 0)
			branch();
	}

	private void BPL() {
		if ((statusRegister & N) == 0)
			branch();
	}

	private void BMI() {
		if ((statusRegister & N) != 0)
			branch();
	}

	private void TAX() {
		xRegister = accumulator;
		setStatusRegisterNZ((byte) accumulator);
	}

	private void TXA() {
		accumulator = xRegister;
		setStatusRegisterNZ((byte) accumulator);
	}

	private void TAY() {
		yRegister = accumulator;
		setStatusRegisterNZ((byte) accumulator);
	}

	private void TYA() {
		accumulator = yRegister;
		setStatusRegisterNZ((byte) accumulator);
	}

	private void TXS() {
		stackPointer = xRegister;
	}

	private void TSX() {
		xRegister = stackPointer;
		setStatusRegisterNZ((byte) xRegister);
	}

	private void CLC() {
		statusRegister &= ~C;
	}

	private void SEC() {
		statusRegister |= C;
	}

	private void CLI() {
		statusRegister &= ~I;
	}

	private void SEI() {
		statusRegister |= I;
	}

	private void CLV() {
		statusRegister &= ~V;
	}

	private void CLD() {
		statusRegister &= ~D;
	}

	protected void SED() {
		statusRegister |= D;
	}

	protected void NOP() {
	}

	protected void Unoff() {
	}

	protected void Unoff1() {
	}

	protected void Unoff2() {
		programCounter++;
	}

	protected void Unoff3() {
		programCounter += 2;
	}

	protected void Hang() {
		programCounter--;
	}
	
	protected void executeOpcode() {
		int opcode = memory.memRead(programCounter++);

		switch (opcode) {
		case 0x00:
		    Imm();
		    BRK();
		    break;
		case 0x01:
		    IndZeroX();
		    ORA();
		    break;
		case 0x02:
		    Hang();
		    break;
		case 0x03:
		    Unoff();
		    break;
		case 0x04:
		    Unoff2();
		    break;
		case 0x05:
		    Zero();
		    ORA();
		    break;
		case 0x06:
		    Zero();
		    ASL();
		    break;
		case 0x07:
		    Unoff();
		    break;
		case 0x08:
		    Imp();
		    PHP();
		    break;
		case 0x09:
		    Imm();
		    ORA();
		    break;
		case 0x0A:
		    Imp();
		    ASL_A();
		    break;
		case 0x0B:
		    Imm();
		    AND();
		    break;
		case 0x0C:
		    Unoff3();
		    break;
		case 0x0D:
		    Abs();
		    ORA();
		    break;
		case 0x0E:
		    Abs();
		    ASL();
		    break;
		case 0x0F:
		    Unoff();
		    break;
		case 0x10:
		    Rel();
		    BPL();
		    break;
		case 0x11:
		    IndZeroY();
		    ORA();
		    break;
		case 0x12:
		    Hang();
		    break;
		case 0x13:
		    Unoff();
		    break;
		case 0x14:
		    Unoff2();
		    break;
		case 0x15:
		    ZeroX();
		    ORA();
		    break;
		case 0x16:
		    ZeroX();
		    ASL();
		    break;
		case 0x17:
		    Unoff();
		    break;
		case 0x18:
		    Imp();
		    CLC();
		    break;
		case 0x19:
		    AbsY();
		    ORA();
		    break;
		case 0x1A:
		    Unoff1();
		    break;
		case 0x1B:
		    Unoff();
		    break;
		case 0x1C:
		    Unoff3();
		    break;
		case 0x1D:
		    AbsX();
		    ORA();
		    break;
		case 0x1E:
		    WAbsX();
		    ASL();
		    break;
		case 0x1F:
		    Unoff();
		    break;
		case 0x20:
		    JSR();
		    break;
		case 0x21:
		    IndZeroX();
		    AND();
		    break;
		case 0x22:
		    Hang();
		    break;
		case 0x23:
		    Unoff();
		    break;
		case 0x24:
		    Zero();
		    BIT();
		    break;
		case 0x25:
		    Zero();
		    AND();
		    break;
		case 0x26:
		    Zero();
		    ROL();
		    break;
		case 0x27:
		    Unoff();
		    break;
		case 0x28:
		    Imp();
		    PLP();
		    break;
		case 0x29:
		    Imm();
		    AND();
		    break;
		case 0x2A:
		    Imp();
		    ROL_A();
		    break;
		case 0x2B:
		    Imm();
		    AND();
		    break;
		case 0x2C:
		    Abs();
		    BIT();
		    break;
		case 0x2D:
		    Abs();
		    AND();
		    break;
		case 0x2E:
		    Abs();
		    ROL();
		    break;
		case 0x2F:
		    Unoff();
		    break;
		case 0x30:
		    Rel();
		    BMI();
		    break;
		case 0x31:
		    IndZeroY();
		    AND();
		    break;
		case 0x32:
		    Hang();
		    break;
		case 0x33:
		    Unoff();
		    break;
		case 0x34:
		    Unoff2();
		    break;
		case 0x35:
		    ZeroX();
		    AND();
		    break;
		case 0x36:
		    ZeroX();
		    ROL();
		    break;
		case 0x37:
		    Unoff();
		    break;
		case 0x38:
		    Imp();
		    SEC();
		    break;
		case 0x39:
		    AbsY();
		    AND();
		    break;
		case 0x3A:
		    Unoff1();
		    break;
		case 0x3B:
		    Unoff();
		    break;
		case 0x3C:
		    Unoff3();
		    break;
		case 0x3D:
		    AbsX();
		    AND();
		    break;
		case 0x3E:
		    WAbsX();
		    ROL();
		    break;
		case 0x3F:
		    Unoff();
		    break;
		case 0x40:
		    Imp();
		    RTI();
		    break;
		case 0x41:
		    IndZeroX();
		    EOR();
		    break;
		case 0x42:
		    Hang();
		    break;
		case 0x43:
		    Unoff();
		    break;
		case 0x44:
		    Unoff2();
		    break;
		case 0x45:
		    Zero();
		    EOR();
		    break;
		case 0x46:
		    Zero();
		    LSR();
		    break;
		case 0x47:
		    Unoff();
		    break;
		case 0x48:
		    Imp();
		    PHA();
		    break;
		case 0x49:
		    Imm();
		    EOR();
		    break;
		case 0x4A:
		    Imp();
		    LSR_A();
		    break;
		case 0x4B:
		    Unoff();
		    break;
		case 0x4C:
		    Abs();
		    JMP();
		    break;
		case 0x4D:
		    Abs();
		    EOR();
		    break;
		case 0x4E:
		    Abs();
		    LSR();
		    break;
		case 0x4F:
		    Unoff();
		    break;
		case 0x50:
		    Rel();
		    BVC();
		    break;
		case 0x51:
		    IndZeroY();
		    EOR();
		    break;
		case 0x52:
		    Hang();
		    break;
		case 0x53:
		    Unoff();
		    break;
		case 0x54:
		    Unoff2();
		    break;
		case 0x55:
		    ZeroX();
		    EOR();
		    break;
		case 0x56:
		    ZeroX();
		    LSR();
		    break;
		case 0x57:
		    Unoff();
		    break;
		case 0x58:
		    Imp();
		    CLI();
		    break;
		case 0x59:
		    AbsY();
		    EOR();
		    break;
		case 0x5A:
		    Unoff1();
		    break;
		case 0x5B:
		    Unoff();
		    break;
		case 0x5C:
		    Unoff3();
		    break;
		case 0x5D:
		    AbsX();
		    EOR();
		    break;
		case 0x5E:
		    WAbsX();
		    LSR();
		    break;
		case 0x5F:
		    Unoff();
		    break;
		case 0x60:
		    Imp();
		    RTS();
		    break;
		case 0x61:
		    IndZeroX();
		    ADC();
		    break;
		case 0x62:
		    Hang();
		    break;
		case 0x63:
		    Unoff();
		    break;
		case 0x64:
		    Unoff2();
		    break;
		case 0x65:
		    Zero();
		    ADC();
		    break;
		case 0x66:
		    Zero();
		    ROR();
		    break;
		case 0x67:
		    Unoff();
		    break;
		case 0x68:
		    Imp();
		    PLA();
		    break;
		case 0x69:
		    Imm();
		    ADC();
		    break;
		case 0x6A:
		    Imp();
		    ROR_A();
		    break;
		case 0x6B:
		    Unoff();
		    break;
		case 0x6C:
		    Ind();
		    JMP();
		    break;
		case 0x6D:
		    Abs();
		    ADC();
		    break;
		case 0x6E:
		    Abs();
		    ROR();
		    break;
		case 0x6F:
		    Unoff();
		    break;
		case 0x70:
		    Rel();
		    BVS();
		    break;
		case 0x71:
		    IndZeroY();
		    ADC();
		    break;
		case 0x72:
		    Hang();
		    break;
		case 0x73:
		    Unoff();
		    break;
		case 0x74:
		    Unoff2();
		    break;
		case 0x75:
		    ZeroX();
		    ADC();
		    break;
		case 0x76:
		    ZeroX();
		    ROR();
		    break;
		case 0x77:
		    Unoff();
		    break;
		case 0x78:
		    Imp();
		    SEI();
		    break;
		case 0x79:
		    AbsY();
		    ADC();
		    break;
		case 0x7A:
		    Unoff1();
		    break;
		case 0x7B:
		    Unoff();
		    break;
		case 0x7C:
		    Unoff3();
		    break;
		case 0x7D:
		    AbsX();
		    ADC();
		    break;
		case 0x7E:
		    WAbsX();
		    ROR();
		    break;
		case 0x7F:
		    Unoff();
		    break;
		case 0x80:
		    Unoff2();
		    break;
		case 0x81:
		    IndZeroX();
		    STA();
		    break;
		case 0x82:
		    Unoff2();
		    break;
		case 0x83:
		    Unoff();
		    break;
		case 0x84:
		    Zero();
		    STY();
		    break;
		case 0x85:
		    Zero();
		    STA();
		    break;
		case 0x86:
		    Zero();
		    STX();
		    break;
		case 0x87:
		    Unoff();
		    break;
		case 0x88:
		    Imp();
		    DEY();
		    break;
		case 0x89:
		    Unoff2();
		    break;
		case 0x8A:
		    Imp();
		    TXA();
		    break;
		case 0x8B:
		    Unoff();
		    break;
		case 0x8C:
		    Abs();
		    STY();
		    break;
		case 0x8D:
		    Abs();
		    STA();
		    break;
		case 0x8E:
		    Abs();
		    STX();
		    break;
		case 0x8F:
		    Unoff();
		    break;
		case 0x90:
		    Rel();
		    BCC();
		    break;
		case 0x91:
		    WIndZeroY();
		    STA();
		    break;
		case 0x92:
		    Hang();
		    break;
		case 0x93:
		    Unoff();
		    break;
		case 0x94:
		    ZeroX();
		    STY();
		    break;
		case 0x95:
		    ZeroX();
		    STA();
		    break;
		case 0x96:
		    ZeroY();
		    STX();
		    break;
		case 0x97:
		    Unoff();
		    break;
		case 0x98:
		    Imp();
		    TYA();
		    break;
		case 0x99:
		    WAbsY();
		    STA();
		    break;
		case 0x9A:
		    Imp();
		    TXS();
		    break;
		case 0x9B:
		    Unoff();
		    break;
		case 0x9C:
		    Unoff();
		    break;
		case 0x9D:
		    WAbsX();
		    STA();
		    break;
		case 0x9E:
		    Unoff();
		    break;
		case 0x9F:
		    Unoff();
		    break;
		case 0xA0:
		    Imm();
		    LDY();
		    break;
		case 0xA1:
		    IndZeroX();
		    LDA();
		    break;
		case 0xA2:
		    Imm();
		    LDX();
		    break;
		case 0xA3:
		    Unoff();
		    break;
		case 0xA4:
		    Zero();
		    LDY();
		    break;
		case 0xA5:
		    Zero();
		    LDA();
		    break;
		case 0xA6:
		    Zero();
		    LDX();
		    break;
		case 0xA7:
		    Unoff();
		    break;
		case 0xA8:
		    Imp();
		    TAY();
		    break;
		case 0xA9:
		    Imm();
		    LDA();
		    break;
		case 0xAA:
		    Imp();
		    TAX();
		    break;
		case 0xAB:
		    Unoff();
		    break;
		case 0xAC:
		    Abs();
		    LDY();
		    break;
		case 0xAD:
		    Abs();
		    LDA();
		    break;
		case 0xAE:
		    Abs();
		    LDX();
		    break;
		case 0xAF:
		    Unoff();
		    break;
		case 0xB0:
		    Rel();
		    BCS();
		    break;
		case 0xB1:
		    IndZeroY();
		    LDA();
		    break;
		case 0xB2:
		    Hang();
		    break;
		case 0xB3:
		    Unoff();
		    break;
		case 0xB4:
		    ZeroX();
		    LDY();
		    break;
		case 0xB5:
		    ZeroX();
		    LDA();
		    break;
		case 0xB6:
		    ZeroY();
		    LDX();
		    break;
		case 0xB7:
		    Unoff();
		    break;
		case 0xB8:
		    Imp();
		    CLV();
		    break;
		case 0xB9:
		    AbsY();
		    LDA();
		    break;
		case 0xBA:
		    Imp();
		    TSX();
		    break;
		case 0xBB:
		    Unoff();
		    break;
		case 0xBC:
		    AbsX();
		    LDY();
		    break;
		case 0xBD:
		    AbsX();
		    LDA();
		    break;
		case 0xBE:
		    AbsY();
		    LDX();
		    break;
		case 0xBF:
		    Unoff();
		    break;
		case 0xC0:
		    Imm();
		    CPY();
		    break;
		case 0xC1:
		    IndZeroX();
		    CMP();
		    break;
		case 0xC2:
		    Unoff2();
		    break;
		case 0xC3:
		    Unoff();
		    break;
		case 0xC4:
		    Zero();
		    CPY();
		    break;
		case 0xC5:
		    Zero();
		    CMP();
		    break;
		case 0xC6:
		    Zero();
		    DEC();
		    break;
		case 0xC7:
		    Unoff();
		    break;
		case 0xC8:
		    Imp();
		    INY();
		    break;
		case 0xC9:
		    Imm();
		    CMP();
		    break;
		case 0xCA:
		    Imp();
		    DEX();
		    break;
		case 0xCB:
		    Unoff();
		    break;
		case 0xCC:
		    Abs();
		    CPY();
		    break;
		case 0xCD:
		    Abs();
		    CMP();
		    break;
		case 0xCE:
		    Abs();
		    DEC();
		    break;
		case 0xCF:
		    Unoff();
		    break;
		case 0xD0:
		    Rel();
		    BNE();
		    break;
		case 0xD1:
		    IndZeroY();
		    CMP();
		    break;
		case 0xD2:
		    Hang();
		    break;
		case 0xD3:
		    Unoff();
		    break;
		case 0xD4:
		    Unoff2();
		    break;
		case 0xD5:
		    ZeroX();
		    CMP();
		    break;
		case 0xD6:
		    ZeroX();
		    DEC();
		    break;
		case 0xD7:
		    Unoff();
		    break;
		case 0xD8:
		    Imp();
		    CLD();
		    break;
		case 0xD9:
		    AbsY();
		    CMP();
		    break;
		case 0xDA:
		    Unoff1();
		    break;
		case 0xDB:
		    Unoff();
		    break;
		case 0xDC:
		    Unoff3();
		    break;
		case 0xDD:
		    AbsX();
		    CMP();
		    break;
		case 0xDE:
		    WAbsX();
		    DEC();
		    break;
		case 0xDF:
		    Unoff();
		    break;
		case 0xE0:
		    Imm();
		    CPX();
		    break;
		case 0xE1:
		    IndZeroX();
		    SBC();
		    break;
		case 0xE2:
		    Unoff2();
		    break;
		case 0xE3:
		    Unoff();
		    break;
		case 0xE4:
		    Zero();
		    CPX();
		    break;
		case 0xE5:
		    Zero();
		    SBC();
		    break;
		case 0xE6:
		    Zero();
		    INC();
		    break;
		case 0xE7:
		    Unoff();
		    break;
		case 0xE8:
		    Imp();
		    INX();
		    break;
		case 0xE9:
		    Imm();
		    SBC();
		    break;
		case 0xEA:
		    Imp();
		    NOP();
		    break;
		case 0xEB:
		    Imm();
		    SBC();
		    break;
		case 0xEC:
		    Abs();
		    CPX();
		    break;
		case 0xED:
		    Abs();
		    SBC();
		    break;
		case 0xEE:
		    Abs();
		    INC();
		    break;
		case 0xEF:
		    Unoff();
		    break;
		case 0xF0:
		    Rel();
		    BEQ();
		    break;
		case 0xF1:
		    IndZeroY();
		    SBC();
		    break;
		case 0xF2:
		    Hang();
		    break;
		case 0xF3:
		    Unoff();
		    break;
		case 0xF4:
		    Unoff2();
		    break;
		case 0xF5:
		    ZeroX();
		    SBC();
		    break;
		case 0xF6:
		    ZeroX();
		    INC();
		    break;
		case 0xF7:
		    Unoff();
		    break;
		case 0xF8:
		    Imp();
		    SED();
		    break;
		case 0xF9:
		    AbsY();
		    SBC();
		    break;
		case 0xFA:
		    Unoff1();
		    break;
		case 0xFB:
		    Unoff();
		    break;
		case 0xFC:
		    Unoff3();
		    break;
		case 0xFD:
		    AbsX();
		    SBC();
		    break;
		case 0xFE:
		    WAbsX();
		    INC();
		    break;
		case 0xFF:
		    Unoff();
		    break;
		}
	}

	public void reset() {
		statusRegister |= I;
		stackPointer = 0xFF;
		programCounter = memReadAbsolute(0xFFFC);
	}

	public void setSpeed(int freq, int synchroMillis) {
		cyclesBeforeSynchro = synchroMillis * freq;
		this.synchroMillis = synchroMillis;
	}

	public void setIRQ(boolean IRQ) {
		this.IRQ = IRQ;
	}

	public void setNMI() {
		NMI = true;
	}

	public int[] dumpState() {
		int[] state = new int[6];

		state[0] = programCounter;
		state[1] = statusRegister;
		state[2] = accumulator;
		state[3] = xRegister;
		state[4] = yRegister;
		state[5] = stackPointer;

		return state;
	}

	public void loadState(int[] state) {
		programCounter = state[0];
		statusRegister = state[1];
		accumulator = state[2];
		xRegister = state[3];
		yRegister = state[4];
		stackPointer = state[5];
	}
}