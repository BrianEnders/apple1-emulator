package com.enders.pomoneemulator.asm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;

/**
 * A small two-pass 6502 assembler for line-numbered source.
 *
 * <pre>
 *   10 ; comments start with a semicolon
 *   20 ECHO   = $FFEF          (or: ECHO EQU $FFEF)
 *   30        .ORG $0300       (or: *= $0300 / ORG $0300)
 *   40 START  LDX #0
 *   50 LOOP:  LDA MSG,X
 *   60        BEQ DONE
 *   70        JSR ECHO
 *   80        INX
 *   90        BNE LOOP
 *  100 DONE   RTS
 *  110 MSG    .ASC "HELLO"
 *  120        .BYTE $8D,0
 * </pre>
 *
 * Numbers: $hex, %binary, decimal, 'c'. Expressions: + and - between terms,
 * * for the current address, and a leading &lt; or &gt; for the low/high byte.
 * Directives: .ORG ORG *=, .BYTE .DB DFB, .WORD .DW, .ASC .ASCII .TEXT, .RES .DS, = EQU.
 */
public class Assembler {

	public static final int DEFAULT_ORIGIN = 0x0300;

	/** One source line after assembly. */
	public static class Line {
		public final int number;
		public final String source;
		public int address = -1;
		public byte[] bytes = new byte[0];
		public String error;

		Line(int number, String source) {
			this.number = number;
			this.source = source;
		}
	}

	public static class Result {
		public final List<Line> lines = new ArrayList<>();
		public final Map<String, Integer> symbols = new HashMap<>();
		public int errorCount;
		/** Where Run should start: label START/MAIN/ENTRY if defined, else the first code address. */
		public int entry = -1;

		public boolean ok() {
			return errorCount == 0;
		}

		public int byteCount() {
			int n = 0;
			for (Line l : lines)
				n += l.bytes.length;
			return n;
		}

		public Line firstError() {
			for (Line l : lines)
				if (l.error != null)
					return l;
			return null;
		}
	}

	private static class AsmError extends Exception {
		AsmError(String message) {
			super(message);
		}
	}

	/** Thrown during pass 1 when a symbol is not defined yet. */
	private static class Unresolved extends AsmError {
		Unresolved(String name) {
			super("Undefined symbol " + name);
		}
	}

	private Map<String, Integer> symbols;
	private int pc;
	private int pass;
	/** Instruction sizes chosen in pass 1, keyed by line number, so pass 2 addresses match. */
	private final Map<Integer, Opcodes.Mode> chosenModes = new HashMap<>();
	/** Pass 1 size of each line, so a line that fails in pass 2 keeps later addresses stable. */
	private final Map<Integer, Integer> pass1Sizes = new HashMap<>();
	/** Which line number defined each symbol, to report duplicates. */
	private final Map<String, Integer> definedBy = new HashMap<>();
	private int currentLine;

	public Result assemble(SortedMap<Integer, String> source) {
		Result result = new Result();
		for (Map.Entry<Integer, String> e : source.entrySet())
			result.lines.add(new Line(e.getKey(), e.getValue()));

		symbols = result.symbols;
		chosenModes.clear();
		pass1Sizes.clear();
		definedBy.clear();

		for (pass = 1; pass <= 2; pass++) {
			pc = DEFAULT_ORIGIN;
			for (Line line : result.lines) {
				line.error = null;
				line.bytes = new byte[0];
				line.address = pc;
				currentLine = line.number;
				int start = pc;
				try {
					assembleLine(line);
				} catch (AsmError e) {
					if (pass == 2) {
						line.error = e.getMessage();
						line.bytes = new byte[0];
						Integer size = pass1Sizes.get(line.number);
						if (size != null)
							pc = (start + size) & 0xFFFF;
					}
				}
				if (pass == 1)
					pass1Sizes.put(line.number, (pc - start) & 0xFFFF);
			}
		}

		for (Line line : result.lines) {
			if (line.error != null)
				result.errorCount++;
			else if (result.entry < 0 && line.bytes.length > 0)
				result.entry = line.address;
		}
		for (String name : new String[]{"START", "MAIN", "ENTRY"}) {
			Integer v = symbols.get(name);
			if (v != null) {
				result.entry = v;
				break;
			}
		}
		return result;
	}

	// ---------------------------------------------------------------- line parsing

	private void assembleLine(Line line) throws AsmError {
		String text = stripComment(line.source).trim();
		if (text.isEmpty())
			return;

		// "*= $300"
		if (text.startsWith("*")) {
			String rest = text.substring(1).trim();
			if (rest.startsWith("=")) {
				setOrigin(line, rest.substring(1).trim());
				return;
			}
		}

		String[] head = splitFirst(text);
		String first = head[0];
		String rest = head[1];
		String label = null;

		if (first.endsWith(":")) {
			label = first.substring(0, first.length() - 1);
		} else if (!isKeyword(first)) {
			label = first;
		}

		if (label != null) {
			String[] next = splitFirst(rest);
			String kw = next[0].toUpperCase(Locale.US);
			// "NAME = value" / "NAME EQU value"
			if (kw.equals("=") || kw.equals("EQU") || kw.equals(".EQU")) {
				checkLabel(label);
				define(label, eval(next[1]));
				return;
			}
			if (next[0].startsWith("=")) { // "NAME =$10"
				checkLabel(label);
				define(label, eval(next[0].substring(1) + " " + next[1]));
				return;
			}
			checkLabel(label);
			define(label, pc);
			text = rest;
			if (text.isEmpty())
				return;
			head = splitFirst(text);
			first = head[0];
			rest = head[1];
		}

		String keyword = first.toUpperCase(Locale.US);
		switch (keyword) {
			case ".ORG": case "ORG":
				setOrigin(line, rest);
				return;
			case ".BYTE": case ".DB": case "DFB": case "DB": case ".BYT":
				emitList(line, rest, 1);
				return;
			case ".WORD": case ".DW": case "DW": case "DA": case ".DA":
				emitList(line, rest, 2);
				return;
			case ".ASC": case ".ASCII": case ".TEXT": case "ASC": case ".STR":
				emitList(line, rest, 1);
				return;
			case ".RES": case ".DS": case "DS":
				int count = evalRequired(rest);
				if (count < 0 || count > 0x10000)
					throw new AsmError("Bad size " + count);
				line.bytes = new byte[count];
				pc += count;
				return;
		}

		if (!Opcodes.isMnemonic(keyword))
			throw new AsmError("Unknown instruction " + first);

		assembleInstruction(line, keyword, rest.trim());
	}

	private void setOrigin(Line line, String expr) throws AsmError {
		int value = evalRequired(expr);
		if (value < 0 || value > 0xFFFF)
			throw new AsmError("Origin out of range");
		pc = value;
		line.address = pc;
	}

	private void emitList(Line line, String operands, int width) throws AsmError {
		List<Integer> out = new ArrayList<>();
		for (String item : splitOperands(operands)) {
			item = item.trim();
			if (item.isEmpty())
				throw new AsmError("Missing value");
			if (item.length() >= 2 && item.charAt(0) == '"') {
				if (item.charAt(item.length() - 1) != '"')
					throw new AsmError("Unterminated string");
				for (char c : item.substring(1, item.length() - 1).toCharArray()) {
					out.add((int) c & 0xFF);
					if (width == 2)
						out.add(0);
				}
				continue;
			}
			int v = evalOrZero(item);
			if (width == 1) {
				if (pass == 2 && (v < -128 || v > 255))
					throw new AsmError("Byte out of range: " + item);
				out.add(v & 0xFF);
			} else {
				out.add(v & 0xFF);
				out.add((v >> 8) & 0xFF);
			}
		}
		if (out.isEmpty())
			throw new AsmError("Missing value");
		line.bytes = new byte[out.size()];
		for (int i = 0; i < out.size(); i++)
			line.bytes[i] = (byte) (int) out.get(i);
		pc += out.size();
	}

	private void assembleInstruction(Line line, String mn, String operand) throws AsmError {
		Opcodes.Mode mode;
		String expr = null;
		String up = operand.toUpperCase(Locale.US).replace(" ", "");

		if (up.isEmpty()) {
			mode = Opcodes.hasMode(mn, Opcodes.Mode.IMP) ? Opcodes.Mode.IMP : Opcodes.Mode.ACC;
		} else if (up.equals("A") && Opcodes.hasMode(mn, Opcodes.Mode.ACC) && !symbols.containsKey("A")) {
			mode = Opcodes.Mode.ACC;
		} else if (up.startsWith("#")) {
			mode = Opcodes.Mode.IMM;
			expr = operand.trim().substring(1);
		} else if (up.startsWith("(") && up.endsWith(",X)")) {
			mode = Opcodes.Mode.INDX;
			expr = operand.trim();
			expr = expr.substring(1, expr.toUpperCase(Locale.US).lastIndexOf(','));
		} else if (up.startsWith("(") && up.endsWith("),Y")) {
			mode = Opcodes.Mode.INDY;
			expr = operand.trim();
			expr = expr.substring(1, expr.lastIndexOf(')'));
		} else if (up.startsWith("(") && up.endsWith(")")) {
			mode = Opcodes.Mode.IND;
			expr = operand.trim();
			expr = expr.substring(1, expr.length() - 1);
		} else if (up.endsWith(",X") || up.endsWith(",Y")) {
			boolean x = up.endsWith(",X");
			expr = operand.trim();
			expr = expr.substring(0, expr.lastIndexOf(','));
			mode = pickSize(line, mn, expr,
					x ? Opcodes.Mode.ZPX : Opcodes.Mode.ZPY,
					x ? Opcodes.Mode.ABSX : Opcodes.Mode.ABSY);
		} else if (Opcodes.hasMode(mn, Opcodes.Mode.REL)) {
			mode = Opcodes.Mode.REL;
			expr = operand;
		} else {
			expr = operand;
			mode = pickSize(line, mn, expr, Opcodes.Mode.ZP, Opcodes.Mode.ABS);
		}

		int code = Opcodes.find(mn, mode);
		if (code < 0)
			throw new AsmError(mn + " does not support " + describe(mode) + " mode");

		int value = expr == null ? 0 : evalOrZero(expr);
		byte[] bytes = new byte[mode.length];
		bytes[0] = (byte) code;

		if (mode == Opcodes.Mode.REL) {
			int offset = value - (pc + 2);
			if (pass == 2 && (offset < -128 || offset > 127))
				throw new AsmError("Branch out of range (" + offset + " bytes)");
			bytes[1] = (byte) offset;
		} else if (mode.length == 2) {
			if (pass == 2 && (value < -128 || value > 255))
				throw new AsmError("Value $" + Integer.toHexString(value).toUpperCase(Locale.US) + " does not fit in a byte");
			bytes[1] = (byte) value;
		} else if (mode.length == 3) {
			if (pass == 2 && (value < 0 || value > 0xFFFF))
				throw new AsmError("Address out of range");
			bytes[1] = (byte) value;
			bytes[2] = (byte) (value >> 8);
		}

		line.bytes = bytes;
		pc += bytes.length;
	}

	/** Chooses zero page vs absolute; decided in pass 1 and reused in pass 2. */
	private Opcodes.Mode pickSize(Line line, String mn, String expr, Opcodes.Mode zp, Opcodes.Mode abs) throws AsmError {
		if (pass == 2 && chosenModes.containsKey(line.number))
			return chosenModes.get(line.number);
		Opcodes.Mode mode = abs;
		if (Opcodes.hasMode(mn, zp)) {
			try {
				int v = eval(expr);
				if (v >= 0 && v <= 0xFF)
					mode = zp;
			} catch (Unresolved e) {
				// forward reference: assume a full address
			}
		}
		if (!Opcodes.hasMode(mn, mode) && Opcodes.hasMode(mn, zp))
			mode = zp;
		chosenModes.put(line.number, mode);
		return mode;
	}

	private static String describe(Opcodes.Mode mode) {
		switch (mode) {
			case IMP: return "implied";
			case ACC: return "accumulator";
			case IMM: return "immediate (#)";
			case ZP: case ABS: return "address";
			case ZPX: case ABSX: return ",X";
			case ZPY: case ABSY: return ",Y";
			case IND: return "indirect ()";
			case INDX: return "(zp,X)";
			case INDY: return "(zp),Y";
			case REL: return "relative";
		}
		return mode.name();
	}

	// ---------------------------------------------------------------- symbols

	public static boolean isKeyword(String token) {
		String t = token.toUpperCase(Locale.US);
		if (Opcodes.isMnemonic(t))
			return true;
		switch (t) {
			case ".ORG": case "ORG": case ".BYTE": case ".DB": case "DFB": case "DB": case ".BYT":
			case ".WORD": case ".DW": case "DW": case "DA": case ".DA":
			case ".ASC": case ".ASCII": case ".TEXT": case "ASC": case ".STR":
			case ".RES": case ".DS": case "DS":
				return true;
		}
		return false;
	}

	private static void checkLabel(String label) throws AsmError {
		if (label.isEmpty() || !Character.isLetter(label.charAt(0)) && label.charAt(0) != '_' && label.charAt(0) != '.')
			throw new AsmError("Bad label " + label);
		for (int i = 1; i < label.length(); i++) {
			char c = label.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '.')
				throw new AsmError("Bad label " + label);
		}
	}

	private void define(String label, int value) throws AsmError {
		String key = label.toUpperCase(Locale.US);
		Integer owner = definedBy.get(key);
		if (owner != null && owner != currentLine)
			throw new AsmError("Duplicate label " + label + " (line " + owner + ")");
		definedBy.put(key, currentLine);
		symbols.put(key, value);
	}

	// ---------------------------------------------------------------- expressions

	private int evalRequired(String expr) throws AsmError {
		return eval(expr); // in pass 1 an Unresolved escapes and the line is retried in pass 2
	}

	/** In pass 1 unknown symbols count as 0 (sizes are fixed elsewhere); in pass 2 they are errors. */
	private int evalOrZero(String expr) throws AsmError {
		try {
			return eval(expr);
		} catch (Unresolved e) {
			if (pass == 1)
				return 0;
			throw e;
		}
	}

	int eval(String expr) throws AsmError {
		String s = expr.trim();
		if (s.isEmpty())
			throw new AsmError("Missing value");

		int selector = 0;
		if (s.charAt(0) == '<' || s.charAt(0) == '>') {
			selector = s.charAt(0);
			s = s.substring(1).trim();
		}

		int total = 0;
		int i = 0;
		int sign = 1;
		boolean expectTerm = true;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == ' ') {
				i++;
				continue;
			}
			if (expectTerm) {
				if (c == '-') {
					sign = -sign;
					i++;
					continue;
				}
				if (c == '+') {
					i++;
					continue;
				}
				int start = i;
				int value;
				if (c == '$') {
					i++;
					while (i < s.length() && Character.digit(s.charAt(i), 16) >= 0)
						i++;
					if (i == start + 1)
						throw new AsmError("Bad hex number");
					value = Integer.parseInt(s.substring(start + 1, i), 16);
				} else if (c == '%') {
					i++;
					while (i < s.length() && (s.charAt(i) == '0' || s.charAt(i) == '1'))
						i++;
					if (i == start + 1)
						throw new AsmError("Bad binary number");
					value = Integer.parseInt(s.substring(start + 1, i), 2);
				} else if (Character.isDigit(c)) {
					while (i < s.length() && Character.isDigit(s.charAt(i)))
						i++;
					value = Integer.parseInt(s.substring(start, i));
				} else if (c == '\'') {
					if (i + 1 >= s.length())
						throw new AsmError("Bad character constant");
					value = s.charAt(i + 1) & 0xFF;
					i += 2;
					if (i < s.length() && s.charAt(i) == '\'')
						i++;
				} else if (c == '*') {
					value = pc;
					i++;
				} else if (Character.isLetter(c) || c == '_' || c == '.') {
					while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '.'))
						i++;
					String name = s.substring(start, i).toUpperCase(Locale.US);
					Integer v = symbols.get(name);
					if (v == null)
						throw new Unresolved(s.substring(start, i));
					value = v;
				} else {
					throw new AsmError("Unexpected '" + c + "'");
				}
				total += sign * value;
				sign = 1;
				expectTerm = false;
			} else {
				if (c == '+') {
					sign = 1;
				} else if (c == '-') {
					sign = -1;
				} else {
					throw new AsmError("Unexpected '" + c + "'");
				}
				i++;
				expectTerm = true;
			}
		}
		if (expectTerm)
			throw new AsmError("Incomplete expression");

		if (selector == '<')
			return total & 0xFF;
		if (selector == '>')
			return (total >> 8) & 0xFF;
		return total;
	}

	// ---------------------------------------------------------------- text helpers

	public static String stripComment(String s) {
		boolean inString = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"')
				inString = !inString;
			else if (c == '\'' && !inString && i + 1 < s.length()) {
				i++; // skip the quoted character, it may be ';'
				if (i + 1 < s.length() && s.charAt(i + 1) == '\'')
					i++;
			}
			else if (c == ';' && !inString)
				return s.substring(0, i);
		}
		return s;
	}

	/**
	 * Lays a source line out in columns (label, instruction + operand, comment)
	 * for display, however it was typed. Only the display changes.
	 */
	public static String format(String source) {
		String code = stripComment(source);
		String comment = source.substring(code.length()).trim();
		code = code.trim();
		if (code.isEmpty())
			return comment;
		String label = "";
		String rest = code;
		if (!code.startsWith("*")) {
			String[] head = splitFirst(code);
			if (!isKeyword(head[0])) {
				label = head[0];
				rest = head[1];
			}
		}
		StringBuilder sb = new StringBuilder(label);
		do {
			sb.append(' ');
		} while (sb.length() < 7);
		String[] op = splitFirst(rest);
		sb.append(op[0]);
		if (!op[1].isEmpty())
			sb.append(' ').append(op[1]);
		if (!comment.isEmpty()) {
			do {
				sb.append(' ');
			} while (sb.length() < 20);
			sb.append(comment);
		}
		return sb.toString();
	}

	private static String[] splitFirst(String s) {
		s = s.trim();
		int i = 0;
		while (i < s.length() && !Character.isWhitespace(s.charAt(i)))
			i++;
		return new String[]{s.substring(0, i), s.substring(i).trim()};
	}

	private static List<String> splitOperands(String s) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean inString = false;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '"')
				inString = !inString;
			if (c == '\'' && !inString && i + 1 < s.length()) {
				cur.append(c).append(s.charAt(++i));
				if (i + 1 < s.length() && s.charAt(i + 1) == '\'')
					cur.append(s.charAt(++i));
				continue;
			}
			if (c == ',' && !inString) {
				out.add(cur.toString());
				cur.setLength(0);
			} else {
				cur.append(c);
			}
		}
		out.add(cur.toString());
		return out;
	}
}
