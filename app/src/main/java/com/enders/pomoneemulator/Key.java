/*
 * Copyright 2012-2013, Arno Puder
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.enders.pomoneemulator;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class Key extends View {

    final public static int TK_NONE             = -1;
    final public static int TK_0                = 0;
    final public static int TK_1                = 1;
    final public static int TK_2                = 2;
    final public static int TK_3                = 3;
    final public static int TK_4                = 4;
    final public static int TK_5                = 5;
    final public static int TK_6                = 6;
    final public static int TK_7                = 7;
    final public static int TK_8                = 8;
    final public static int TK_9                = 9;
    final public static int TK_A                = 10;
    final public static int TK_B                = 11;
    final public static int TK_C                = 12;
    final public static int TK_D                = 13;
    final public static int TK_E                = 14;
    final public static int TK_F                = 15;
    final public static int TK_G                = 16;
    final public static int TK_H                = 17;
    final public static int TK_I                = 18;
    final public static int TK_J                = 19;
    final public static int TK_K                = 20;
    final public static int TK_L                = 21;
    final public static int TK_M                = 22;
    final public static int TK_N                = 23;
    final public static int TK_O                = 24;
    final public static int TK_P                = 25;
    final public static int TK_Q                = 26;
    final public static int TK_R                = 27;
    final public static int TK_S                = 28;
    final public static int TK_T                = 29;
    final public static int TK_U                = 30;
    final public static int TK_V                = 31;
    final public static int TK_W                = 32;
    final public static int TK_X                = 33;
    final public static int TK_Y                = 34;
    final public static int TK_Z                = 35;
    final public static int TK_COMMA            = 36;
    final public static int TK_DOT              = 37;
    final public static int TK_SLASH            = 38;
    final public static int TK_SPACE            = 39;
    final public static int TK_ADD              = 40;
    final public static int TK_HASH             = 41;
    final public static int TK_BR_OPEN          = 42;
    final public static int TK_BR_CLOSE         = 43;
    final public static int TK_ASTERIX          = 44;
    final public static int TK_DOLLAR           = 45;
    final public static int TK_QUESTION         = 46;
    final public static int TK_LT               = 47;
    final public static int TK_GT               = 48;
    final public static int TK_EQUAL            = 49;
    final public static int TK_PERCENT          = 50;
    final public static int TK_APOS             = 51;
    final public static int TK_EXCLAMATION_MARK = 52;
    final public static int TK_AMP              = 53;
    final public static int TK_QUOT             = 54;
    final public static int TK_SEMICOLON        = 55;
    final public static int TK_ENTER            = 56;
    final public static int TK_CLEAR            = 57;
    final public static int TK_CLEAR_SHORT      = 58;
    final public static int TK_SHIFT_LEFT       = 59;
    final public static int TK_SHIFT_RIGHT      = 60;
    final public static int TK_COLON            = 61;
    final public static int TK_MINUS            = 62;
    final public static int TK_BREAK            = 63;
    final public static int TK_BREAK_SHORT      = 64;
    final public static int TK_UP               = 65;

    final public static int TK_LEFT             = 66;
    final public static int TK_RIGHT            = 67;
    final public static int TK_DOWN             = 68;
    final public static int TK_ALT              = 69;
    final public static int TK_CARAT            = 70;
    final public static int TK_AT            = 71;

    private int idNormal;
    private int idShifted;

    private String  labelNormal;
    private String  labelShifted;
    private boolean isShifted;
    private boolean isPressed;
    private boolean isShiftKey;
    private boolean isAltKey;

    private View keyboardView1 = null;
    private View keyboardView2 = null;

    private int   size;
    private Paint paint;
    private RectF rect;

    private KeyboardManager keyboard;

    private int keyWidth;
    private int keyHeight;
    private int keyMargin;

    private int posX = -1;
    private int posY = -1;


    public Key(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.setBackgroundResource(R.drawable.key_background);

        Pom1Activity emulator = (Pom1Activity) context;
        keyWidth = emulator.getKeyWidth();
        keyHeight = emulator.getKeyHeight();
        keyMargin = emulator.getKeyMargin();
        keyboard = emulator.getKeyboardManager();


        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.Keyboard, 0, 0);
        idNormal = ta.getInt(R.styleable.Keyboard_id, -1);
        idShifted = ta.getInt(R.styleable.Keyboard_idShifted, -1);
        if (idNormal == TK_ALT) {
            labelNormal = "Alt";
        } else {
            KeyMap keyMap = keyboard.getKeyMap(idNormal);
            labelNormal = keyMap.label;
        }
        size = ta.getInteger(R.styleable.Keyboard_size, 1);
        ta.recycle();

        if (idShifted != -1) {
            KeyMap keyMap = keyboard.getKeyMap(idShifted);
            labelShifted = keyMap.label;
            keyboard.addShiftableKey(this);
        }
        isShiftKey = idNormal == TK_SHIFT_LEFT || idNormal == TK_SHIFT_RIGHT;
        if (isShiftKey) {
            labelShifted = labelNormal;
            keyboard.addShiftableKey(this);
        }
        isShifted = false;
        isPressed = false;
        isAltKey = idNormal == TK_ALT;
        paint = new Paint();
        paint.setTypeface(TypefaceCache.get().getTypeface("fonts/DejaVuSansMono.ttf", context));
        paint.setAntiAlias(true);
        float textSizeScale = labelNormal.length() > 1 ? 0.4f : 0.6f;
        paint.setTextSize(keyHeight * textSizeScale);

        rect = new RectF();
        this.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                int action = event.getAction() & MotionEvent.ACTION_MASK;
                if (action == MotionEvent.ACTION_DOWN) {
                    isPressed = true;
                    invalidate();
                }
                if (action == MotionEvent.ACTION_UP) {
                    isPressed = false;
                    invalidate();
                }
                if (isAltKey) {
                    if (action == MotionEvent.ACTION_UP) {
                        //switchKeyboard();
                    }
                    return true;
                }
                if (action == MotionEvent.ACTION_DOWN && !isShiftKey) {
                    if (isShifted && idShifted != -1) {
                        keyboard.keyDown(idShifted);
                    } else {
                        keyboard.keyDown(idNormal);
                    }
                }
                if (action == MotionEvent.ACTION_UP) {
                    if (isShiftKey) {
                        if (!isShifted) {
                            keyboard.shiftKeys(idNormal);
                            keyboard.keyDown(idNormal);
                        } else {
                            keyboard.unshiftKeys();
                            keyboard.keyUp(idNormal);
                        }
                    } else {
                        if (isShifted && idShifted != -1) {
                            keyboard.keyUp(idShifted);
                        } else {
                            keyboard.keyUp(idNormal);
                        }
                        switch (keyboard.getPressedShiftKey()) {
                        case TK_SHIFT_LEFT:
                            keyboard.unshiftKeys();
                            keyboard.keyUp(TK_SHIFT_LEFT);
                            break;
                        case TK_SHIFT_RIGHT:
                            keyboard.unshiftKeys();
                            keyboard.keyUp(TK_SHIFT_RIGHT);
                            break;
                        }
                    }
                }
                return true;
            }
        });
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (isShifted) {
            paint.setColor(Color.WHITE);
            paint.setAlpha(70);
            paint.setStyle(Style.FILL);
            canvas.drawRoundRect(rect, 10, 10, paint);
        }

        if (isPressed) {
            paint.setColor(Color.WHITE);
            paint.setAlpha(95);
            paint.setStyle(Style.FILL);
            canvas.drawRoundRect(rect, 10, 10, paint);
        }

        paint.setColor(Color.GRAY);
        paint.setAlpha(130);
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawRoundRect(rect, 10, 10, paint);

        paint.setColor(getResources().getColor(R.color.key_color));
        paint.setAlpha(110);
        paint.setStyle(Style.FILL);
        paint.setStrokeWidth(1);
        paint.setTextAlign(Align.CENTER);
        int xPos = (int) (rect.right / 2);
        int yPos = (int) ((rect.bottom / 2) - ((paint.descent() + paint.ascent()) / 2));
        canvas.drawText(isShifted ? labelShifted : labelNormal, xPos, yPos, paint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = keyWidth * size;
        int desiredHeight = keyHeight;

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int width;
        int height;

        if (widthMode == MeasureSpec.EXACTLY) {
            width = widthSize;
        } else if (widthMode == MeasureSpec.AT_MOST) {
            width = Math.min(desiredWidth, widthSize);
        } else {
            width = desiredWidth;
        }

        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(desiredHeight, heightSize);
        } else {
            height = desiredHeight;
        }

        setMeasuredDimension(width, height);

        rect.set(1, 1, width - 1, height - 1);

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) this.getLayoutParams();
        if (posX != -1 && posY != -1) {
            params.setMargins(posX, posY, 0, 0);
        } else {
            params.setMargins(keyMargin, keyMargin, keyMargin, keyMargin);
        }
        this.setLayoutParams(params);
    }

    public void setPosition(int x, int y) {
        posX = x;
        posY = y;
    }

    public void shift() {
        if (!isShifted) {
            isShifted = true;
            invalidate();
        }
    }

    public void unshift() {
        if (isShifted) {
            isShifted = false;
            invalidate();
        }
    }

}
