package com.enders.pomoneemulator;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for the code-built debugger and assembler panels. */
final class UiKit {
	static final int BG = Color.rgb(0x0B, 0x0D, 0x0B);
	static final int PANEL = Color.rgb(0x15, 0x18, 0x15);
	static final int KEY = Color.rgb(0x26, 0x2A, 0x26);
	static final int KEY_OPCODE = Color.rgb(0x1C, 0x33, 0x22);
	static final int KEY_ACTION = Color.rgb(0x3A, 0x2E, 0x14);
	static final int TEXT = Color.rgb(0xE0, 0xE4, 0xDE);
	static final int DIM = Color.rgb(0x7C, 0x86, 0x7C);
	static final int GREEN = Color.rgb(0x4A, 0xF6, 0x26);
	static final int AMBER = Color.rgb(0xFF, 0xB0, 0x2E);
	static final int RED = Color.rgb(0xFF, 0x5A, 0x4F);
	static final int HIGHLIGHT = Color.rgb(0x22, 0x3D, 0x22);

	private UiKit() {
	}

	static int dp(Context c, float dp) {
		return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
				c.getResources().getDisplayMetrics()));
	}

	static TextView mono(Context c, float sp, int color) {
		TextView t = new TextView(c);
		t.setTypeface(Typeface.MONOSPACE);
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
		t.setTextColor(color);
		return t;
	}

	/** A flat key/button with a ripple. */
	static TextView key(Context c, String label, int bg, int fg, View.OnClickListener click) {
		TextView t = mono(c, 14, fg);
		t.setText(label);
		t.setGravity(Gravity.CENTER);
		t.setSingleLine(true);
		t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
		GradientDrawable shape = new GradientDrawable();
		shape.setColor(bg);
		shape.setCornerRadius(dp(c, 5));
		GradientDrawable mask = new GradientDrawable();
		mask.setColor(Color.WHITE);
		mask.setCornerRadius(dp(c, 5));
		t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), shape, mask));
		t.setClickable(true);
		t.setOnClickListener(v -> {
			v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
			click.onClick(v);
		});
		return t;
	}

	/** Adds {@code view} to a horizontal row with equal weight and a small margin. */
	static void addWeighted(LinearLayout row, View view, float weight, int heightPx) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightPx, weight);
		int m = dp(row.getContext(), 2);
		lp.setMargins(m, m, m, m);
		row.addView(view, lp);
	}

	static LinearLayout row(Context c) {
		LinearLayout row = new LinearLayout(c);
		row.setOrientation(LinearLayout.HORIZONTAL);
		return row;
	}

	static String hex2(int v) {
		return String.format(java.util.Locale.US, "%02X", v & 0xFF);
	}

	static String hex4(int v) {
		return String.format(java.util.Locale.US, "%04X", v & 0xFFFF);
	}
}
