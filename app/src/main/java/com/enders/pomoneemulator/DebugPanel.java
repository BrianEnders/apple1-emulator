package com.enders.pomoneemulator;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.enders.pomoneemulator.asm.Opcodes;

import java.util.Locale;

/**
 * The DEBUG tab: run/pause/step controls, CPU registers, a disassembly
 * around the program counter (tap a line to toggle a breakpoint) and a
 * memory viewer.
 */
class DebugPanel {

	interface Host {
		void pauseCpu();

		void resumeCpu();
	}

	private static final int DISASM_LINES = 8;
	private static final int MEM_ROWS = 6;

	private final Context context;
	private final M6502 cpu;
	private final Memory memory;
	private final Host host;
	private final Handler handler = new Handler(Looper.getMainLooper());

	private final LinearLayout root;
	private final TextView runButton;
	private final TextView registers;
	private final LinearLayout disasm;
	private final TextView[] disasmRows = new TextView[DISASM_LINES];
	private final int[] disasmAddr = new int[DISASM_LINES];
	private final TextView memView;
	private final EditText memAddr;
	private int memBase = 0x0300;
	/** Temporary breakpoint used by STEP OVER; -1 when none. */
	private int stepOverTarget = -1;
	private boolean visible;

	private final Runnable ticker = new Runnable() {
		@Override
		public void run() {
			refresh();
			if (visible)
				handler.postDelayed(this, 200);
		}
	};

	DebugPanel(Context context, M6502 cpu, Memory memory, Host host) {
		this.context = context;
		this.cpu = cpu;
		this.memory = memory;
		this.host = host;

		root = new LinearLayout(context);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setBackgroundColor(UiKit.PANEL);
		int pad = UiKit.dp(context, 4);
		root.setPadding(pad, pad, pad, pad);
		int keyH = UiKit.dp(context, 38);

		LinearLayout controls = UiKit.row(context);
		runButton = UiKit.key(context, "❚❚ PAUSE", UiKit.KEY_OPCODE, UiKit.GREEN, v -> toggleRun());
		UiKit.addWeighted(controls, runButton, 1.4f, keyH);
		UiKit.addWeighted(controls, UiKit.key(context, "STEP", UiKit.KEY, UiKit.TEXT, v -> step()), 1, keyH);
		UiKit.addWeighted(controls, UiKit.key(context, "OVER", UiKit.KEY, UiKit.TEXT, v -> stepOver()), 1, keyH);
		UiKit.addWeighted(controls, UiKit.key(context, "BRK@PC", UiKit.KEY_ACTION, UiKit.AMBER, v -> {
			cpu.toggleBreakpoint(cpu.getProgramCounter());
			refresh();
		}), 1.2f, keyH);
		root.addView(controls);

		registers = UiKit.mono(context, 13, UiKit.TEXT);
		registers.setPadding(pad, pad, pad, pad);
		root.addView(registers);

		disasm = new LinearLayout(context);
		disasm.setOrientation(LinearLayout.VERTICAL);
		disasm.setBackgroundColor(UiKit.BG);
		for (int i = 0; i < DISASM_LINES; i++) {
			final int row = i;
			TextView t = UiKit.mono(context, 13, UiKit.TEXT);
			t.setPadding(pad, UiKit.dp(context, 1), pad, UiKit.dp(context, 1));
			t.setOnClickListener(v -> {
				if (!cpu.isRunning()) {
					cpu.toggleBreakpoint(disasmAddr[row]);
					refresh();
				}
			});
			disasmRows[i] = t;
			disasm.addView(t);
		}
		root.addView(disasm);

		LinearLayout memHeader = UiKit.row(context);
		memHeader.setGravity(Gravity.CENTER_VERTICAL);
		TextView memLabel = UiKit.mono(context, 13, UiKit.DIM);
		memLabel.setText(" MEM $");
		memHeader.addView(memLabel);
		memAddr = new EditText(context);
		memAddr.setTypeface(Typeface.MONOSPACE);
		memAddr.setTextSize(13);
		memAddr.setTextColor(UiKit.AMBER);
		memAddr.setSingleLine(true);
		memAddr.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4), new InputFilter.AllCaps()});
		memAddr.setInputType(EditorInfo.TYPE_CLASS_TEXT | EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS
				| EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		memAddr.setImeOptions(EditorInfo.IME_ACTION_DONE);
		memAddr.setText(UiKit.hex4(memBase));
		memAddr.addTextChangedListener(new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
			@Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
			@Override
			public void afterTextChanged(Editable s) {
				try {
					if (s.length() > 0) {
						memBase = Integer.parseInt(s.toString(), 16) & 0xFFF8;
						refresh();
					}
				} catch (NumberFormatException ignored) {
				}
			}
		});
		memHeader.addView(memAddr, new LinearLayout.LayoutParams(UiKit.dp(context, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
		int small = UiKit.dp(context, 32);
		UiKit.addWeighted(memHeader, UiKit.key(context, "▲", UiKit.KEY, UiKit.TEXT, v -> pageMemory(-MEM_ROWS * 8)), 1, small);
		UiKit.addWeighted(memHeader, UiKit.key(context, "▼", UiKit.KEY, UiKit.TEXT, v -> pageMemory(MEM_ROWS * 8)), 1, small);
		UiKit.addWeighted(memHeader, UiKit.key(context, "PC", UiKit.KEY, UiKit.TEXT, v -> showMemory(cpu.getProgramCounter())), 1, small);
		UiKit.addWeighted(memHeader, UiKit.key(context, "ZP", UiKit.KEY, UiKit.TEXT, v -> showMemory(0)), 1, small);
		UiKit.addWeighted(memHeader, UiKit.key(context, "STK", UiKit.KEY, UiKit.TEXT, v -> showMemory(0x100 + cpu.getStackPointer())), 1, small);
		root.addView(memHeader);

		memView = UiKit.mono(context, 12, UiKit.TEXT);
		memView.setPadding(pad, 0, pad, pad);
		memView.setBackgroundColor(UiKit.BG);
		root.addView(memView);
	}

	View getView() {
		return root;
	}

	void setVisible(boolean visible) {
		this.visible = visible;
		handler.removeCallbacks(ticker);
		if (visible)
			handler.post(ticker);
	}

	/** Called on the UI thread after the CPU stopped at a breakpoint. */
	void onBreakpoint(int pc) {
		if (stepOverTarget >= 0) {
			if (cpu.breakpoints[stepOverTarget])
				cpu.toggleBreakpoint(stepOverTarget);
			stepOverTarget = -1;
		}
		refresh();
	}

	void showMemory(int address) {
		memBase = address & 0xFFF8;
		memAddr.setText(UiKit.hex4(memBase));
		refresh();
	}

	private void pageMemory(int delta) {
		showMemory(memBase + delta);
	}

	private void toggleRun() {
		if (cpu.isRunning())
			host.pauseCpu();
		else
			host.resumeCpu();
		refresh();
	}

	private void step() {
		if (cpu.isRunning())
			host.pauseCpu();
		else
			cpu.step();
		refresh();
	}

	/** Runs a JSR to completion; any other instruction is a plain step. */
	private void stepOver() {
		if (cpu.isRunning()) {
			host.pauseCpu();
			refresh();
			return;
		}
		int pc = cpu.getProgramCounter();
		if (memory.peek(pc) != 0x20) { // not JSR
			step();
			return;
		}
		int target = (pc + 3) & 0xFFFF;
		if (!cpu.breakpoints[target]) {
			cpu.toggleBreakpoint(target);
			stepOverTarget = target;
		}
		host.resumeCpu();
		refresh();
	}

	void refresh() {
		boolean running = cpu.isRunning();
		runButton.setText(running ? "❚❚ PAUSE" : "▶ RUN");

		int pc = cpu.getProgramCounter();
		int p = cpu.getStatusRegister();
		SpannableStringBuilder sb = new SpannableStringBuilder();
		reg(sb, "PC", UiKit.hex4(pc));
		reg(sb, "A", UiKit.hex2(cpu.getAccumulator()));
		reg(sb, "X", UiKit.hex2(cpu.getXRegister()));
		reg(sb, "Y", UiKit.hex2(cpu.getYRegister()));
		reg(sb, "SP", UiKit.hex2(cpu.getStackPointer()));
		sb.append('\n');
		String names = "NV-BDIZC";
		for (int i = 0; i < 8; i++) {
			boolean set = (p & (0x80 >> i)) != 0;
			int start = sb.length();
			sb.append(names.charAt(i));
			sb.setSpan(new ForegroundColorSpan(set ? UiKit.GREEN : 0xFF3A403A), start, sb.length(),
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			sb.append(' ');
		}
		int start = sb.length();
		sb.append(running ? "  running" : "  paused");
		sb.setSpan(new ForegroundColorSpan(running ? UiKit.GREEN : UiKit.AMBER), start, sb.length(),
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		registers.setText(sb);

		// Disassembly from the PC (a snapshot while running).
		Opcodes.ByteSource src = memory::peek;
		int addr = pc;
		for (int i = 0; i < DISASM_LINES; i++) {
			disasmAddr[i] = addr;
			int len = Opcodes.length(memory.peek(addr));
			StringBuilder bytes = new StringBuilder();
			for (int b = 0; b < 3; b++)
				bytes.append(b < len ? UiKit.hex2(memory.peek((addr + b) & 0xFFFF)) + " " : "   ");
			boolean bp = cpu.breakpoints[addr];
			String text = String.format(Locale.US, "%s%s %s %s", bp ? "●" : " ",
					UiKit.hex4(addr), bytes, Opcodes.disassemble(src, addr));
			SpannableStringBuilder line = new SpannableStringBuilder(text);
			line.setSpan(new ForegroundColorSpan(UiKit.AMBER), 1, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			line.setSpan(new ForegroundColorSpan(UiKit.DIM), 6, 15, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			line.setSpan(new ForegroundColorSpan(i == 0 ? UiKit.GREEN : UiKit.TEXT), 15, text.length(),
					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (bp)
				line.setSpan(new ForegroundColorSpan(UiKit.RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (i == 0)
				line.setSpan(new BackgroundColorSpan(UiKit.HIGHLIGHT), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			disasmRows[i].setText(line);
			addr = (addr + len) & 0xFFFF;
		}

		// Memory dump with ASCII.
		SpannableStringBuilder mem = new SpannableStringBuilder();
		for (int r = 0; r < MEM_ROWS; r++) {
			int base = (memBase + r * 8) & 0xFFFF;
			int s = mem.length();
			mem.append(UiKit.hex4(base)).append(':');
			mem.setSpan(new ForegroundColorSpan(UiKit.AMBER), s, mem.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			StringBuilder ascii = new StringBuilder();
			for (int c = 0; c < 8; c++) {
				int v = memory.peek((base + c) & 0xFFFF);
				mem.append(' ').append(UiKit.hex2(v));
				int ch = v & 0x7F;
				ascii.append(ch >= 0x20 && ch < 0x7F ? (char) ch : '.');
			}
			s = mem.length();
			mem.append("  ").append(ascii);
			mem.setSpan(new ForegroundColorSpan(UiKit.DIM), s, mem.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			if (r < MEM_ROWS - 1)
				mem.append('\n');
		}
		memView.setText(mem);
	}

	private static void reg(SpannableStringBuilder sb, String name, String value) {
		int start = sb.length();
		sb.append(name).append(' ');
		sb.setSpan(new ForegroundColorSpan(UiKit.DIM), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		start = sb.length();
		sb.append(value).append("  ");
		sb.setSpan(new ForegroundColorSpan(UiKit.GREEN), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
	}
}
