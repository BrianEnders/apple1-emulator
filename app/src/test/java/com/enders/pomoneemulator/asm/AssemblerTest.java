package com.enders.pomoneemulator.asm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.TreeMap;

public class AssemblerTest {

	private static Assembler.Result asm(String... lines) {
		TreeMap<Integer, String> src = new TreeMap<>();
		for (int i = 0; i < lines.length; i++)
			src.put((i + 1) * 10, lines[i]);
		return new Assembler().assemble(src);
	}

	private static byte[] bytes(Assembler.Result r) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (Assembler.Line l : r.lines)
			out.write(l.bytes, 0, l.bytes.length);
		return out.toByteArray();
	}

	private static byte[] b(int... v) {
		byte[] out = new byte[v.length];
		for (int i = 0; i < v.length; i++)
			out[i] = (byte) v[i];
		return out;
	}

	@Test
	public void helloWorld() {
		Assembler.Result r = asm(
				"; print HELLO",
				"ECHO   = $FFEF",
				"       .ORG $0300",
				"START  LDX #0",
				"LOOP:  LDA MSG,X",
				"       BEQ DONE",
				"       JSR ECHO",
				"       INX",
				"       BNE LOOP",
				"DONE   RTS",
				"MSG    .ASC \"HI\"",
				"       .BYTE $8D,0");
		assertTrue(String.valueOf(r.firstError() == null ? "" : r.firstError().error), r.ok());
		assertEquals(0x0300, r.entry);
		assertArrayEquals(b(
				0xA2, 0x00,             // LDX #0
				0xBD, 0x0E, 0x03,       // LDA MSG,X  (MSG = $030E, forward ref -> absolute)
				0xF0, 0x06,             // BEQ DONE
				0x20, 0xEF, 0xFF,       // JSR ECHO
				0xE8,                   // INX
				0xD0, 0xF5,             // BNE LOOP
				0x60,                   // RTS
				'H', 'I', 0x8D, 0x00), bytes(r));
	}

	@Test
	public void addressingModes() {
		Assembler.Result r = asm(
				"*= $1000",
				"PTR = $24",
				" LDA #<MSG",
				" LDA #>MSG",
				" LDA PTR",
				" LDA PTR,X",
				" LDX PTR,Y",
				" LDA (PTR),Y",
				" LDA (PTR,X)",
				" JMP ($FFFC)",
				" ASL",
				" ROL A",
				" STA $D012",
				"MSG NOP");
		assertTrue(r.ok());
		assertArrayEquals(b(
				0xA9, 0x16, 0xA9, 0x10,
				0xA5, 0x24, 0xB5, 0x24, 0xB6, 0x24,
				0xB1, 0x24, 0xA1, 0x24,
				0x6C, 0xFC, 0xFF,
				0x0A, 0x2A,
				0x8D, 0x12, 0xD0,
				0xEA), bytes(r));
	}

	@Test
	public void expressionsAndWords() {
		Assembler.Result r = asm(
				"*=$200",
				"A1 .WORD A1+2, $1234",
				" .BYTE 'A', %101, 10-3",
				" .RES 2",
				" LDA #';'",
				" LDA #' '");
		assertTrue(r.ok());
		assertArrayEquals(b(0x02, 0x02, 0x34, 0x12, 'A', 5, 7, 0, 0, 0xA9, ';', 0xA9, ' '), bytes(r));
	}

	@Test
	public void reportsErrorsPerLine() {
		Assembler.Result r = asm(
				" LDA #$100",
				" FOO #1",
				" JMP NOWHERE",
				" STX $10,X",
				"X1 NOP",
				"X1 NOP");
		assertEquals(5, r.errorCount);
		assertNotNull(r.lines.get(0).error);
		assertNotNull(r.lines.get(1).error);
		assertTrue(r.lines.get(2).error.contains("NOWHERE"));
		assertNotNull(r.lines.get(3).error);
		assertNull(r.lines.get(4).error);
		assertTrue(r.lines.get(5).error.contains("Duplicate"));
	}

	@Test
	public void branchOutOfRange() {
		String[] lines = new String[3];
		lines[0] = "L BNE L2";
		lines[1] = " .RES 200";
		lines[2] = "L2 RTS";
		Assembler.Result r = asm(lines);
		assertEquals(1, r.errorCount);
		assertTrue(r.lines.get(0).error.contains("range"));
	}

	@Test
	public void disassembler() {
		byte[] mem = b(0xB1, 0x24, 0xD0, 0xFE, 0x6C, 0xFC, 0xFF, 0x02);
		Opcodes.ByteSource src = a -> a < mem.length ? mem[a] & 0xFF : 0;
		assertEquals("LDA ($24),Y", Opcodes.disassemble(src, 0));
		assertEquals("BNE $0002", Opcodes.disassemble(src, 2));
		assertEquals("JMP ($FFFC)", Opcodes.disassemble(src, 4));
		assertEquals(".BYTE $02", Opcodes.disassemble(src, 7));
	}

	@Test
	public void formatsColumns() {
		assertEquals("       NOP", Assembler.format("NOP"));
		assertEquals("LOOP   LDA MSG,X", Assembler.format("LOOP:  LDA MSG,X".replace(":", "")));
		assertEquals("ECHO   = $FFEF      ; PRINT", Assembler.format("ECHO = $FFEF ; PRINT"));
		assertEquals("       *= $300", Assembler.format("*= $300"));
		assertEquals("; NOTE", Assembler.format("  ; NOTE"));
	}

	@Test
	public void programLineEntry() {
		AsmProgram p = new AsmProgram();
		assertEquals(10, p.enter("LDA #1").number);
		assertEquals(20, p.enter("NOP").number);
		assertEquals(15, p.enter("15 INX").number);
		assertEquals("INX", p.get(15));
		assertEquals(12, p.nextNumber(10));
		assertTrue(p.enter("15").deleted);
		assertNull(p.get(15));
		assertEquals(30, p.nextNumber(20));
		p.renumber(100, 5);
		assertEquals("LDA #1", p.get(100));
		assertEquals("NOP", p.get(105));
	}
}
