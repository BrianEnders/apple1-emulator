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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.util.Log;

/**
 * Class Hardware is the base class for the various different TRS-80 models. It
 * encapsulates various hardware characteristics of different TRS-80 models. In
 * particular, it computes bitmaps to be used during rendering. The size of the
 * font is determined by the size of the screen and whether the emulator runs in
 * landscape or portrait mode. The goal is to scale the size nicely for
 * different screen resolutions. Each bitmap of 'font' represents one character
 * of the ASCII code. For alphanumeric characters the bundled font
 * asset/fonts/DejaVuSansMono.ttf is used (see generateASCIIFont()). For the
 * TRS-80 pseudo-graphics we compute the bitmaps for the 2x3-per character
 * pseudo pixel graphics (see generateGraphicsFont()).
 */
public class Hardware {
    static class ScreenConfiguration {
        final int   trsScreenCols;
        final int   trsScreenRows;
        final float aspectRatio;

        ScreenConfiguration(int trsScreenCols, int trsScreenRows, float aspectRatio) {
            this.trsScreenCols = trsScreenCols;
            this.trsScreenRows = trsScreenRows;
            this.aspectRatio = aspectRatio;
        }
    }


    final private float     maxKeyBoxSize = 55; // 55dp

    private int             keyWidth;
    private int             keyHeight;
    private int             keyMargin;

    protected Hardware() {
    }

    int getKeyWidth() {
        return keyWidth;
    }

    int getKeyHeight() {
        return keyHeight;
    }

    int getKeyMargin() {
        return keyMargin;
    }


    void computeKeyDimensions(Rect rect) {
        // The maximum number of key "boxes" per row
        int maxKeyBoxes = 10;

        int boxWidth = rect.right / maxKeyBoxes;
        float threshold = pxFromDp(maxKeyBoxSize);
        if (boxWidth > threshold) {
            boxWidth = (int) threshold;
        }
        keyWidth = keyHeight = (int) (boxWidth * 0.9f);
        keyMargin = (boxWidth - keyWidth) / 2;


        Log.v("POM1", "keyWidth - " +keyWidth);
    }

    private float pxFromDp(float dp) {
        return dp * Pom1Activity.me.getResources().getDisplayMetrics().density;
    }

}