package com.enders.pomoneemulator.asm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Line-numbered assembly source, edited the classic way: typing
 * {@code "120 LDA #1"} stores line 120, typing {@code "120"} deletes it.
 */
public class AsmProgram {

	public static final int MAX_LINE = 65535;

	private final TreeMap<Integer, String> lines = new TreeMap<>();

	public SortedMap<Integer, String> lines() {
		return lines;
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	public String get(int number) {
		return lines.get(number);
	}

	public void put(int number, String text) {
		lines.put(number, text);
	}

	public void remove(int number) {
		lines.remove(number);
	}

	public void clear() {
		lines.clear();
	}

	/** Outcome of {@link #enter(String)}. */
	public static class Entry {
		/** Line number that was stored or deleted, or -1 if the input was rejected. */
		public final int number;
		public final boolean deleted;
		public final String error;

		Entry(int number, boolean deleted, String error) {
			this.number = number;
			this.deleted = deleted;
			this.error = error;
		}
	}

	/**
	 * Applies one typed line. A leading number selects the line; without one the
	 * text is appended after the last line.
	 */
	public Entry enter(String input) {
		String s = input.replace('\t', ' ');
		int i = 0;
		while (i < s.length() && s.charAt(i) == ' ')
			i++;
		int digitsStart = i;
		while (i < s.length() && Character.isDigit(s.charAt(i)))
			i++;

		int number;
		String text;
		if (i > digitsStart) {
			if (i - digitsStart > 5)
				return new Entry(-1, false, "Line number too large");
			number = Integer.parseInt(s.substring(digitsStart, i));
			if (number > MAX_LINE)
				return new Entry(-1, false, "Line number too large (max " + MAX_LINE + ")");
			// A single separating space is dropped; extra spaces are kept as indentation.
			text = i < s.length() && s.charAt(i) == ' ' ? s.substring(i + 1) : s.substring(i);
			text = rtrim(text);
			if (text.trim().isEmpty()) {
				boolean existed = lines.remove(number) != null;
				return new Entry(existed ? number : -1, true, existed ? null : "No line " + number);
			}
		} else {
			text = rtrim(s);
			if (text.trim().isEmpty())
				return new Entry(-1, false, null);
			number = lines.isEmpty() ? 10 : lines.lastKey() + 10;
			if (number > MAX_LINE)
				return new Entry(-1, false, "No room after the last line: renumber first");
		}
		lines.put(number, text);
		return new Entry(number, false, null);
	}

	/** A free line number to suggest after {@code number}, or -1 if there is no gap. */
	public int nextNumber(int number) {
		Integer after = lines.higherKey(number);
		if (after == null)
			return number + 10 <= MAX_LINE ? number + 10 : -1;
		int gap = after - number;
		if (gap >= 20)
			return number + 10;
		if (gap >= 2)
			return number + gap / 2;
		return -1;
	}

	/** Renumbers every line as start, start+step, ... */
	public boolean renumber(int start, int step) {
		if (start + (long) step * Math.max(0, lines.size() - 1) > MAX_LINE)
			return false;
		TreeMap<Integer, String> copy = new TreeMap<>();
		int n = start;
		for (String text : lines.values()) {
			copy.put(n, text);
			n += step;
		}
		lines.clear();
		lines.putAll(copy);
		return true;
	}

	/** Writes the listing as {@code "<number> <text>"} lines. */
	public void write(Writer out) throws IOException {
		for (Map.Entry<Integer, String> e : lines.entrySet()) {
			out.write(String.valueOf(e.getKey()));
			out.write(' ');
			out.write(e.getValue());
			out.write('\n');
		}
	}

	/**
	 * Reads a listing. Lines with numbers keep them; plain source (no numbers)
	 * is numbered 10, 20, 30... so ordinary .asm files can be imported.
	 */
	public void read(Reader in) throws IOException {
		lines.clear();
		BufferedReader reader = new BufferedReader(in);
		String line;
		while ((line = reader.readLine()) != null) {
			if (line.trim().isEmpty())
				continue;
			int i = 0;
			while (i < line.length() && Character.isDigit(line.charAt(i)))
				i++;
			if (i > 0 && i <= 5 && (i == line.length() || line.charAt(i) == ' ')) {
				int number = Integer.parseInt(line.substring(0, i));
				if (number <= MAX_LINE) {
					lines.put(number, i < line.length() ? rtrim(line.substring(i + 1)) : "");
					continue;
				}
			}
			int number = lines.isEmpty() ? 10 : lines.lastKey() + 10;
			if (number > MAX_LINE)
				break;
			lines.put(number, rtrim(line));
		}
	}

	private static String rtrim(String s) {
		int end = s.length();
		while (end > 0 && Character.isWhitespace(s.charAt(end - 1)))
			end--;
		return s.substring(0, end);
	}

	/** A small starter program, shown the first time the editor opens. */
	public static AsmProgram example() {
		AsmProgram p = new AsmProgram();
		p.put(10, "; HELLO WORLD FOR THE APPLE-1");
		p.put(20, "ECHO   = $FFEF      ; WOZ MONITOR: PRINT A");
		p.put(30, "       .ORG $0300");
		p.put(40, "START  LDX #0");
		p.put(50, "LOOP   LDA MSG,X");
		p.put(60, "       BEQ DONE");
		p.put(70, "       JSR ECHO");
		p.put(80, "       INX");
		p.put(90, "       BNE LOOP");
		p.put(100, "DONE   RTS          ; BACK TO THE MONITOR");
		p.put(110, "MSG    .BYTE $8D");
		p.put(120, "       .ASC \"HELLO, WORLD!\"");
		p.put(130, "       .BYTE $8D,0");
		return p;
	}
}
