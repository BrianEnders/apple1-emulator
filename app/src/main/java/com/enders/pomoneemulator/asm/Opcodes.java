package com.enders.pomoneemulator.asm;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The official NMOS 6502 instruction set, shared by the assembler and the
 * debugger's disassembler.
 */
public final class Opcodes {

	public enum Mode {
		IMP(1), ACC(1), IMM(2), ZP(2), ZPX(2), ZPY(2), ABS(3), ABSX(3), ABSY(3),
		IND(3), INDX(2), INDY(2), REL(2);

		public final int length;

		Mode(int length) {
			this.length = length;
		}
	}

	/** Mnemonic for each opcode byte, or null for undocumented opcodes. */
	public static final String[] MNEMONIC = new String[256];
	public static final Mode[] MODE = new Mode[256];

	/** mnemonic -> (mode -> opcode) */
	private static final Map<String, Map<Mode, Integer>> LOOKUP = new HashMap<>();

	private static void op(int code, String mnemonic, Mode mode) {
		MNEMONIC[code] = mnemonic;
		MODE[code] = mode;
		Map<Mode, Integer> modes = LOOKUP.get(mnemonic);
		if (modes == null) {
			modes = new HashMap<>();
			LOOKUP.put(mnemonic, modes);
		}
		modes.put(mode, code);
	}

	static {
		Mode IMP = Mode.IMP, ACC = Mode.ACC, IMM = Mode.IMM, ZP = Mode.ZP, ZPX = Mode.ZPX,
				ZPY = Mode.ZPY, ABS = Mode.ABS, ABSX = Mode.ABSX, ABSY = Mode.ABSY,
				IND = Mode.IND, INDX = Mode.INDX, INDY = Mode.INDY, REL = Mode.REL;

		op(0x69, "ADC", IMM); op(0x65, "ADC", ZP); op(0x75, "ADC", ZPX); op(0x6D, "ADC", ABS);
		op(0x7D, "ADC", ABSX); op(0x79, "ADC", ABSY); op(0x61, "ADC", INDX); op(0x71, "ADC", INDY);

		op(0x29, "AND", IMM); op(0x25, "AND", ZP); op(0x35, "AND", ZPX); op(0x2D, "AND", ABS);
		op(0x3D, "AND", ABSX); op(0x39, "AND", ABSY); op(0x21, "AND", INDX); op(0x31, "AND", INDY);

		op(0x0A, "ASL", ACC); op(0x06, "ASL", ZP); op(0x16, "ASL", ZPX); op(0x0E, "ASL", ABS); op(0x1E, "ASL", ABSX);

		op(0x90, "BCC", REL); op(0xB0, "BCS", REL); op(0xF0, "BEQ", REL); op(0x30, "BMI", REL);
		op(0xD0, "BNE", REL); op(0x10, "BPL", REL); op(0x50, "BVC", REL); op(0x70, "BVS", REL);

		op(0x24, "BIT", ZP); op(0x2C, "BIT", ABS);
		op(0x00, "BRK", IMP);
		op(0x18, "CLC", IMP); op(0xD8, "CLD", IMP); op(0x58, "CLI", IMP); op(0xB8, "CLV", IMP);

		op(0xC9, "CMP", IMM); op(0xC5, "CMP", ZP); op(0xD5, "CMP", ZPX); op(0xCD, "CMP", ABS);
		op(0xDD, "CMP", ABSX); op(0xD9, "CMP", ABSY); op(0xC1, "CMP", INDX); op(0xD1, "CMP", INDY);
		op(0xE0, "CPX", IMM); op(0xE4, "CPX", ZP); op(0xEC, "CPX", ABS);
		op(0xC0, "CPY", IMM); op(0xC4, "CPY", ZP); op(0xCC, "CPY", ABS);

		op(0xC6, "DEC", ZP); op(0xD6, "DEC", ZPX); op(0xCE, "DEC", ABS); op(0xDE, "DEC", ABSX);
		op(0xCA, "DEX", IMP); op(0x88, "DEY", IMP);

		op(0x49, "EOR", IMM); op(0x45, "EOR", ZP); op(0x55, "EOR", ZPX); op(0x4D, "EOR", ABS);
		op(0x5D, "EOR", ABSX); op(0x59, "EOR", ABSY); op(0x41, "EOR", INDX); op(0x51, "EOR", INDY);

		op(0xE6, "INC", ZP); op(0xF6, "INC", ZPX); op(0xEE, "INC", ABS); op(0xFE, "INC", ABSX);
		op(0xE8, "INX", IMP); op(0xC8, "INY", IMP);

		op(0x4C, "JMP", ABS); op(0x6C, "JMP", IND);
		op(0x20, "JSR", ABS);

		op(0xA9, "LDA", IMM); op(0xA5, "LDA", ZP); op(0xB5, "LDA", ZPX); op(0xAD, "LDA", ABS);
		op(0xBD, "LDA", ABSX); op(0xB9, "LDA", ABSY); op(0xA1, "LDA", INDX); op(0xB1, "LDA", INDY);
		op(0xA2, "LDX", IMM); op(0xA6, "LDX", ZP); op(0xB6, "LDX", ZPY); op(0xAE, "LDX", ABS); op(0xBE, "LDX", ABSY);
		op(0xA0, "LDY", IMM); op(0xA4, "LDY", ZP); op(0xB4, "LDY", ZPX); op(0xAC, "LDY", ABS); op(0xBC, "LDY", ABSX);

		op(0x4A, "LSR", ACC); op(0x46, "LSR", ZP); op(0x56, "LSR", ZPX); op(0x4E, "LSR", ABS); op(0x5E, "LSR", ABSX);
		op(0xEA, "NOP", IMP);

		op(0x09, "ORA", IMM); op(0x05, "ORA", ZP); op(0x15, "ORA", ZPX); op(0x0D, "ORA", ABS);
		op(0x1D, "ORA", ABSX); op(0x19, "ORA", ABSY); op(0x01, "ORA", INDX); op(0x11, "ORA", INDY);

		op(0x48, "PHA", IMP); op(0x08, "PHP", IMP); op(0x68, "PLA", IMP); op(0x28, "PLP", IMP);

		op(0x2A, "ROL", ACC); op(0x26, "ROL", ZP); op(0x36, "ROL", ZPX); op(0x2E, "ROL", ABS); op(0x3E, "ROL", ABSX);
		op(0x6A, "ROR", ACC); op(0x66, "ROR", ZP); op(0x76, "ROR", ZPX); op(0x6E, "ROR", ABS); op(0x7E, "ROR", ABSX);

		op(0x40, "RTI", IMP); op(0x60, "RTS", IMP);

		op(0xE9, "SBC", IMM); op(0xE5, "SBC", ZP); op(0xF5, "SBC", ZPX); op(0xED, "SBC", ABS);
		op(0xFD, "SBC", ABSX); op(0xF9, "SBC", ABSY); op(0xE1, "SBC", INDX); op(0xF1, "SBC", INDY);

		op(0x38, "SEC", IMP); op(0xF8, "SED", IMP); op(0x78, "SEI", IMP);

		op(0x85, "STA", ZP); op(0x95, "STA", ZPX); op(0x8D, "STA", ABS); op(0x9D, "STA", ABSX);
		op(0x99, "STA", ABSY); op(0x81, "STA", INDX); op(0x91, "STA", INDY);
		op(0x86, "STX", ZP); op(0x96, "STX", ZPY); op(0x8E, "STX", ABS);
		op(0x84, "STY", ZP); op(0x94, "STY", ZPX); op(0x8C, "STY", ABS);

		op(0xAA, "TAX", IMP); op(0xA8, "TAY", IMP); op(0xBA, "TSX", IMP);
		op(0x8A, "TXA", IMP); op(0x9A, "TXS", IMP); op(0x98, "TYA", IMP);
	}

	private Opcodes() {
	}

	public static boolean isMnemonic(String s) {
		return LOOKUP.containsKey(s.toUpperCase(Locale.US));
	}

	/** Returns the opcode for mnemonic + mode, or -1 if that combination does not exist. */
	public static int find(String mnemonic, Mode mode) {
		Map<Mode, Integer> modes = LOOKUP.get(mnemonic.toUpperCase(Locale.US));
		if (modes == null)
			return -1;
		Integer code = modes.get(mode);
		return code == null ? -1 : code;
	}

	public static boolean hasMode(String mnemonic, Mode mode) {
		return find(mnemonic, mode) >= 0;
	}

	/** Bytes taken by the instruction at {@code code} (1 for undocumented opcodes). */
	public static int length(int code) {
		Mode m = MODE[code & 0xFF];
		return m == null ? 1 : m.length;
	}

	public interface ByteSource {
		int read(int address);
	}

	/** Disassembles one instruction, e.g. {@code "LDA ($24),Y"}. */
	public static String disassemble(ByteSource mem, int pc) {
		int code = mem.read(pc & 0xFFFF);
		String mn = MNEMONIC[code];
		if (mn == null)
			return String.format(Locale.US, ".BYTE $%02X", code);
		int lo = mem.read((pc + 1) & 0xFFFF);
		int hi = mem.read((pc + 2) & 0xFFFF);
		int word = lo | hi << 8;
		switch (MODE[code]) {
			case IMP: return mn;
			case ACC: return mn + " A";
			case IMM: return String.format(Locale.US, "%s #$%02X", mn, lo);
			case ZP: return String.format(Locale.US, "%s $%02X", mn, lo);
			case ZPX: return String.format(Locale.US, "%s $%02X,X", mn, lo);
			case ZPY: return String.format(Locale.US, "%s $%02X,Y", mn, lo);
			case ABS: return String.format(Locale.US, "%s $%04X", mn, word);
			case ABSX: return String.format(Locale.US, "%s $%04X,X", mn, word);
			case ABSY: return String.format(Locale.US, "%s $%04X,Y", mn, word);
			case IND: return String.format(Locale.US, "%s ($%04X)", mn, word);
			case INDX: return String.format(Locale.US, "%s ($%02X,X)", mn, lo);
			case INDY: return String.format(Locale.US, "%s ($%02X),Y", mn, lo);
			case REL: return String.format(Locale.US, "%s $%04X", mn, (pc + 2 + (byte) lo) & 0xFFFF);
		}
		return mn;
	}
}
