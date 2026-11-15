package com.enders.pomoneemulator;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.enders.pomoneemulator.asm.AsmProgram;
import com.enders.pomoneemulator.asm.Assembler;

import java.util.Locale;

/**
 * The ASM tab: a line-numbered 6502 source editor with a live assembler
 * listing and an on-screen keyboard built for assembly.
 */
class AsmPanel {

	interface Host {
		/** Writes the assembled bytes into memory; when {@code run} is true also jumps to the entry point. */
		void loadAssembled(Assembler.Result result, boolean run);

		void importSource();

		void exportSource();

		/** Called after every change so the source can be saved. */
		void sourceChanged();

		boolean isBreakpoint(int address);

		void toggleBreakpoint(int address);
	}

	private static final String[][] GROUPS = {
			{"LD/ST", "LDA", "LDX", "LDY", "STA", "STX", "STY", "TAX", "TXA", "TAY", "TYA", "TSX", "TXS"},
			{"MATH", "ADC", "SBC", "INC", "DEC", "INX", "INY", "DEX", "DEY", "CMP", "CPX", "CPY", "CLC", "SEC"},
			{"LOGIC", "AND", "ORA", "EOR", "BIT", "ASL", "LSR", "ROL", "ROR"},
			{"BRANCH", "BEQ", "BNE", "BCC", "BCS", "BMI", "BPL", "BVC", "BVS", "JMP", "JSR", "RTS"},
			{"STACK", "PHA", "PLA", "PHP", "PLP", "RTI", "BRK", "NOP", "SEI", "CLI", "SED", "CLD", "CLV"},
			{"DIR", ".ORG", ".BYTE", ".WORD", ".ASC", ".RES", "=", "ECHO", "$FFEF", "$FF1F", "$D010", "$D011", "$D012"},
	};

	private final Context context;
	private final AsmProgram program;
	private final Host host;
	private final Assembler assembler = new Assembler();
	private Assembler.Result result;

	private final LinearLayout root;
	private final TextView status;
	private final ListView listing;
	private final ListingAdapter adapter = new ListingAdapter();
	private final EditText input;
	private final LinearLayout keyboard;
	private final LinearLayout opcodeRows;
	private final LinearLayout groupRow;
	private int group;
	private int selectedLine = -1;
	private boolean systemKeyboard;

	AsmPanel(Context context, AsmProgram program, Host host) {
		this.context = context;
		this.program = program;
		this.host = host;

		root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(UiKit.BG);

		int keyH = UiKit.dp(context, 36);

		// Toolbar: RUN, LOAD, RENUM, FILE
		LinearLayout toolbar = UiKit.row(context);
		toolbar.setPadding(UiKit.dp(context, 2), UiKit.dp(context, 2), UiKit.dp(context, 2), 0);
		UiKit.addWeighted(toolbar, UiKit.key(context, "▶ RUN", UiKit.KEY_OPCODE, UiKit.GREEN, v -> load(true)), 1.3f, keyH);
		UiKit.addWeighted(toolbar, UiKit.key(context, "LOAD", UiKit.KEY, UiKit.TEXT, v -> load(false)), 1, keyH);
		UiKit.addWeighted(toolbar, UiKit.key(context, "RENUM", UiKit.KEY, UiKit.TEXT, v -> renumber()), 1, keyH);
		UiKit.addWeighted(toolbar, UiKit.key(context, "FILE ▾", UiKit.KEY, UiKit.TEXT, this::showFileMenu), 1, keyH);
		root.addView(toolbar);

		status = UiKit.mono(context, 12, UiKit.DIM);
		status.setPadding(UiKit.dp(context, 8), UiKit.dp(context, 2), UiKit.dp(context, 8), UiKit.dp(context, 2));
		status.setSingleLine(true);
		status.setOnClickListener(v -> jumpToFirstError());
		root.addView(status);

		listing = new ListView(context);
		listing.setAdapter(adapter);
		listing.setDivider(null);
		listing.setBackgroundColor(UiKit.BG);
		listing.setOnItemClickListener((parent, view, position, id) -> editLine(position));
		listing.setOnItemLongClickListener((parent, view, position, id) -> {
			lineMenu(view, position);
			return true;
		});
		root.addView(listing, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

		// Input line
		LinearLayout inputRow = UiKit.row(context);
		inputRow.setBackgroundColor(UiKit.PANEL);
		inputRow.setGravity(Gravity.CENTER_VERTICAL);
		TextView prompt = UiKit.mono(context, 16, UiKit.GREEN);
		prompt.setText("]");
		prompt.setPadding(UiKit.dp(context, 8), 0, UiKit.dp(context, 2), 0);
		inputRow.addView(prompt);
		input = new EditText(context);
		input.setTypeface(Typeface.MONOSPACE);
		input.setTextSize(16);
		input.setTextColor(UiKit.TEXT);
		input.setHintTextColor(UiKit.DIM);
		input.setHint("10 LDA #$41");
		input.setSingleLine(true);
		input.setBackground(null);
		input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		input.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS
				| EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		input.setShowSoftInputOnFocus(false);
		input.setOnEditorActionListener((v, actionId, event) -> {
			if (actionId == EditorInfo.IME_ACTION_DONE
					|| (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
				enter();
				return true;
			}
			return false;
		});
		inputRow.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
		TextView kbToggle = UiKit.key(context, "ABC", UiKit.KEY, UiKit.TEXT, v -> toggleSystemKeyboard());
		LinearLayout.LayoutParams kbLp = new LinearLayout.LayoutParams(UiKit.dp(context, 52), keyH);
		kbLp.setMargins(UiKit.dp(context, 4), UiKit.dp(context, 2), UiKit.dp(context, 4), UiKit.dp(context, 2));
		inputRow.addView(kbToggle, kbLp);
		root.addView(inputRow);

		// 6502 keyboard
		keyboard = new LinearLayout(context);
		keyboard.setOrientation(LinearLayout.VERTICAL);
		keyboard.setBackgroundColor(UiKit.PANEL);
		keyboard.setPadding(UiKit.dp(context, 2), UiKit.dp(context, 2), UiKit.dp(context, 2), UiKit.dp(context, 4));

		groupRow = UiKit.row(context);
		keyboard.addView(groupRow);
		opcodeRows = new LinearLayout(context);
		opcodeRows.setOrientation(LinearLayout.VERTICAL);
		keyboard.addView(opcodeRows);

		addTextRow(keyH, "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
		addTextRow(keyH, "A", "B", "C", "D", "E", "F", "X", "Y", "#", "$");
		addTextRow(keyH, ",", "(", ")", ":", ";", "+", "-", "<", ">", "\"");

		LinearLayout bottom = UiKit.row(context);
		UiKit.addWeighted(bottom, UiKit.key(context, "CLR", UiKit.KEY_ACTION, UiKit.AMBER, v -> {
			input.setText("");
			selectedLine = -1;
			adapter.notifyDataSetChanged();
		}), 1.2f, keyH);
		UiKit.addWeighted(bottom, UiKit.key(context, "◀", UiKit.KEY, UiKit.TEXT, v -> moveCursor(-1)), 1, keyH);
		UiKit.addWeighted(bottom, UiKit.key(context, "▶", UiKit.KEY, UiKit.TEXT, v -> moveCursor(1)), 1, keyH);
		UiKit.addWeighted(bottom, UiKit.key(context, "SPACE", UiKit.KEY, UiKit.TEXT, v -> insert(" ")), 2.4f, keyH);
		UiKit.addWeighted(bottom, UiKit.key(context, "⌫", UiKit.KEY_ACTION, UiKit.AMBER, v -> backspace()), 1.2f, keyH);
		UiKit.addWeighted(bottom, UiKit.key(context, "⏎", UiKit.KEY_ACTION, UiKit.AMBER, v -> enter()), 1.6f, keyH);
		keyboard.addView(bottom);
		root.addView(keyboard);

		buildGroups(keyH);
		reassemble();
		suggestNext(program.isEmpty() ? 0 : program.lines().lastKey());
	}

	View getView() {
		return root;
	}

	/** Re-reads the program after it was replaced from outside (import). */
	void reload() {
		selectedLine = -1;
		reassemble();
		suggestNext(program.isEmpty() ? 0 : program.lines().lastKey());
	}

	/** Hides the system keyboard when leaving the tab. */
	void onHidden() {
		if (systemKeyboard)
			toggleSystemKeyboard();
	}

	// ------------------------------------------------------------ keyboard

	private void buildGroups(int keyH) {
		groupRow.removeAllViews();
		for (int g = 0; g < GROUPS.length; g++) {
			final int index = g;
			boolean selected = g == group;
			TextView tab = UiKit.key(context, GROUPS[g][0], selected ? UiKit.HIGHLIGHT : UiKit.BG,
					selected ? UiKit.GREEN : UiKit.DIM, v -> {
						group = index;
						buildGroups(keyH);
					});
			tab.setTextSize(11);
			UiKit.addWeighted(groupRow, tab, 1, UiKit.dp(context, 30));
		}

		opcodeRows.removeAllViews();
		String[] ops = GROUPS[group];
		int perRow = 7;
		LinearLayout row = null;
		for (int i = 1; i < ops.length; i++) {
			if ((i - 1) % perRow == 0) {
				row = UiKit.row(context);
				opcodeRows.addView(row);
			}
			final String op = ops[i];
			TextView k = UiKit.key(context, op, UiKit.KEY_OPCODE, UiKit.GREEN, v -> insertWord(op));
			if (op.length() > 4)
				k.setTextSize(11);
			UiKit.addWeighted(row, k, 1, keyH);
		}
		// Pad the last row (and a missing second row) so key sizes stay steady between groups.
		int count = ops.length - 1;
		int rows = 2;
		int slots = rows * perRow;
		for (int i = count; i < slots; i++) {
			if (i % perRow == 0) {
				row = UiKit.row(context);
				opcodeRows.addView(row);
			}
			View spacer = new View(context);
			UiKit.addWeighted(row, spacer, 1, keyH);
		}
	}

	private void addTextRow(int keyH, String... keys) {
		LinearLayout row = UiKit.row(context);
		for (String k : keys)
			UiKit.addWeighted(row, UiKit.key(context, k, UiKit.KEY, UiKit.TEXT, v -> insert(k)), 1, keyH);
		keyboard.addView(row);
	}

	private void insert(String s) {
		Editable text = input.getText();
		int start = Math.max(0, input.getSelectionStart());
		int end = Math.max(0, input.getSelectionEnd());
		text.replace(Math.min(start, end), Math.max(start, end), s);
	}

	/** Inserts a mnemonic or directive with spacing around it. */
	private void insertWord(String word) {
		Editable text = input.getText();
		int pos = Math.max(0, input.getSelectionStart());
		StringBuilder sb = new StringBuilder();
		if (pos > 0 && text.charAt(pos - 1) != ' ')
			sb.append(' ');
		sb.append(word);
		boolean operandFollows = !word.startsWith("$") && !word.equals("ECHO");
		if (operandFollows && (pos >= text.length() || text.charAt(pos) != ' '))
			sb.append(' ');
		insert(sb.toString());
	}

	private void backspace() {
		int start = input.getSelectionStart();
		int end = input.getSelectionEnd();
		if (start != end) {
			input.getText().delete(Math.min(start, end), Math.max(start, end));
		} else if (start > 0) {
			input.getText().delete(start - 1, start);
		}
	}

	private void moveCursor(int delta) {
		int pos = input.getSelectionStart() + delta;
		input.setSelection(Math.max(0, Math.min(input.length(), pos)));
	}

	private void toggleSystemKeyboard() {
		InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		systemKeyboard = !systemKeyboard;
		input.setShowSoftInputOnFocus(systemKeyboard);
		if (systemKeyboard) {
			keyboard.setVisibility(View.GONE);
			input.requestFocus();
			imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
		} else {
			imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
			keyboard.setVisibility(View.VISIBLE);
		}
	}

	// ------------------------------------------------------------ editing

	private void enter() {
		String text = input.getText().toString();
		AsmProgram.Entry e = program.enter(text);
		if (e.error != null) {
			setStatus(e.error, UiKit.RED);
			return;
		}
		if (e.number < 0) // blank input
			return;
		host.sourceChanged();
		reassemble();
		selectedLine = -1;
		if (e.deleted) {
			input.setText("");
			setStatus("Deleted line " + e.number, UiKit.DIM);
		} else {
			suggestNext(e.number);
			scrollTo(e.number);
		}
	}

	/** Pre-fills the input with the next free line number, like AUTO on old machines. */
	private void suggestNext(int after) {
		int next = program.nextNumber(after);
		input.setText(next < 0 ? "" : next + " ");
		input.setSelection(input.length());
	}

	private void editLine(int position) {
		Assembler.Line line = result.lines.get(position);
		selectedLine = line.number;
		input.setText(line.number + " " + line.source);
		input.setSelection(input.length());
		adapter.notifyDataSetChanged();
		if (line.error != null)
			setStatus(line.number + ": " + line.error, UiKit.RED);
	}

	private void lineMenu(View anchor, int position) {
		Assembler.Line line = result.lines.get(position);
		PopupMenu menu = new PopupMenu(context, anchor);
		menu.getMenu().add(0, 1, 0, "Edit line " + line.number);
		menu.getMenu().add(0, 2, 1, "Insert line after");
		menu.getMenu().add(0, 3, 2, "Delete line " + line.number);
		if (line.bytes.length > 0 && line.error == null)
			menu.getMenu().add(0, 4, 3, (host.isBreakpoint(line.address) ? "Clear" : "Set")
					+ " breakpoint at $" + UiKit.hex4(line.address));
		menu.setOnMenuItemClickListener(item -> {
			switch (item.getItemId()) {
				case 1:
					editLine(position);
					return true;
				case 2:
					int next = program.nextNumber(line.number);
					if (next < 0) {
						setStatus("No free line number after " + line.number + ": use RENUM", UiKit.RED);
					} else {
						selectedLine = -1;
						input.setText(next + " ");
						input.setSelection(input.length());
					}
					return true;
				case 3:
					program.remove(line.number);
					host.sourceChanged();
					reassemble();
					return true;
				case 4:
					host.toggleBreakpoint(line.address);
					adapter.notifyDataSetChanged();
					setStatus((host.isBreakpoint(line.address) ? "Breakpoint set at $" : "Breakpoint cleared at $")
							+ UiKit.hex4(line.address) + " · RUN to stop there", UiKit.AMBER);
					return true;
			}
			return false;
		});
		menu.show();
	}

	private void renumber() {
		if (program.renumber(10, 10)) {
			host.sourceChanged();
			selectedLine = -1;
			reassemble();
			suggestNext(program.isEmpty() ? 0 : program.lines().lastKey());
			setStatus("Renumbered 10, 20, 30…", UiKit.DIM);
		} else {
			setStatus("Too many lines to renumber by 10", UiKit.RED);
		}
	}

	private void showFileMenu(View anchor) {
		PopupMenu menu = new PopupMenu(context, anchor);
		menu.getMenu().add(0, 1, 0, "Import source…");
		menu.getMenu().add(0, 2, 1, "Export source…");
		menu.getMenu().add(0, 3, 2, "Load example");
		menu.getMenu().add(0, 4, 3, "New (clear all)");
		menu.setOnMenuItemClickListener(item -> {
			switch (item.getItemId()) {
				case 1:
					host.importSource();
					return true;
				case 2:
					host.exportSource();
					return true;
				case 3:
					confirm("Replace the current source with the example?", () -> {
						program.clear();
						program.lines().putAll(AsmProgram.example().lines());
						host.sourceChanged();
						reload();
					});
					return true;
				case 4:
					confirm("Erase every line?", () -> {
						program.clear();
						host.sourceChanged();
						reload();
					});
					return true;
			}
			return false;
		});
		menu.show();
	}

	private void confirm(String message, Runnable action) {
		if (program.isEmpty()) {
			action.run();
			return;
		}
		new AlertDialog.Builder(context)
				.setMessage(message)
				.setPositiveButton("OK", (d, w) -> action.run())
				.setNegativeButton("Cancel", null)
				.show();
	}

	// ------------------------------------------------------------ assembling

	private void reassemble() {
		result = assembler.assemble(program.lines());
		adapter.notifyDataSetChanged();
		if (program.isEmpty()) {
			setStatus("Type a line like  10 LDA #$41  and press ⏎", UiKit.DIM);
		} else if (!result.ok()) {
			Assembler.Line first = result.firstError();
			setStatus(result.errorCount + (result.errorCount == 1 ? " error" : " errors")
					+ " · line " + first.number + ": " + first.error, UiKit.RED);
		} else {
			int lo = 0xFFFF, hi = 0;
			for (Assembler.Line l : result.lines) {
				if (l.bytes.length > 0) {
					lo = Math.min(lo, l.address);
					hi = Math.max(hi, l.address + l.bytes.length - 1);
				}
			}
			if (result.byteCount() == 0)
				setStatus("OK · no code yet", UiKit.GREEN);
			else
				setStatus(String.format(Locale.US, "OK · %d bytes $%04X-$%04X · entry $%04X",
						result.byteCount(), lo, hi, result.entry), UiKit.GREEN);
		}
	}

	private void load(boolean run) {
		reassemble();
		if (!result.ok()) {
			jumpToFirstError();
			return;
		}
		if (result.byteCount() == 0) {
			setStatus("Nothing to load", UiKit.RED);
			return;
		}
		host.loadAssembled(result, run);
	}

	private void jumpToFirstError() {
		Assembler.Line first = result == null ? null : result.firstError();
		if (first == null)
			return;
		scrollTo(first.number);
		editLine(result.lines.indexOf(first));
	}

	private void scrollTo(int number) {
		for (int i = 0; i < result.lines.size(); i++) {
			if (result.lines.get(i).number == number) {
				listing.smoothScrollToPosition(i);
				return;
			}
		}
	}

	void setStatus(String text, int color) {
		status.setText(text);
		status.setTextColor(color);
	}

	// ------------------------------------------------------------ listing

	private class ListingAdapter extends BaseAdapter {
		@Override
		public int getCount() {
			return result == null ? 0 : result.lines.size();
		}

		@Override
		public Object getItem(int position) {
			return result.lines.get(position);
		}

		@Override
		public long getItemId(int position) {
			return result.lines.get(position).number;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TextView t = (TextView) convertView;
			if (t == null) {
				t = UiKit.mono(context, 12, UiKit.TEXT);
				int p = UiKit.dp(context, 6);
				t.setPadding(p, UiKit.dp(context, 3), p, UiKit.dp(context, 3));
			}
			Assembler.Line line = result.lines.get(position);
			SpannableStringBuilder sb = new SpannableStringBuilder();

			boolean bp = line.bytes.length > 0 && line.error == null && host.isBreakpoint(line.address);
			append(sb, String.format(Locale.US, "%5d", line.number), UiKit.DIM);
			append(sb, bp ? "●" : " ", UiKit.RED);
			if (line.bytes.length > 0) {
				StringBuilder hex = new StringBuilder();
				for (int i = 0; i < Math.min(3, line.bytes.length); i++)
					hex.append(UiKit.hex2(line.bytes[i])).append(i < 2 ? " " : "");
				if (line.bytes.length > 3)
					hex.setCharAt(hex.length() - 1, '…');
				append(sb, UiKit.hex4(line.address) + " ", UiKit.AMBER);
				append(sb, String.format(Locale.US, "%-9s", hex), UiKit.DIM);
			} else {
				append(sb, "              ", UiKit.DIM);
			}
			String src = Assembler.format(line.source);
			int semi = src.indexOf(';');
			boolean wholeComment = src.trim().startsWith(";");
			if (wholeComment) {
				append(sb, src, UiKit.DIM);
			} else if (semi >= 0 && src.indexOf('"') < 0) {
				append(sb, src.substring(0, semi), line.error != null ? UiKit.RED : UiKit.GREEN);
				append(sb, src.substring(semi), UiKit.DIM);
			} else {
				append(sb, src, line.error != null ? UiKit.RED : UiKit.GREEN);
			}
			if (line.error != null) {
				int start = sb.length();
				sb.append("\n      ✗ ").append(line.error);
				sb.setSpan(new ForegroundColorSpan(UiKit.RED), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
				sb.setSpan(new StyleSpan(Typeface.ITALIC), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			}
			t.setText(sb);
			t.setBackgroundColor(line.number == selectedLine ? UiKit.HIGHLIGHT : UiKit.BG);
			return t;
		}

		private void append(SpannableStringBuilder sb, String s, int color) {
			int start = sb.length();
			sb.append(s);
			sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
	}
}
