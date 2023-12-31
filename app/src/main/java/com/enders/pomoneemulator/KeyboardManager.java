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

import android.content.res.XmlResourceParser;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KeyboardManager {

    /**
     * The delays in milli-seconds when to inject the matching key-up event
     * after a key-down event. If this delay is too short, the emulated TRS
     * might miss the key-up event. This can especially happen when using an
     * external keyboard with key-repeat.
     */
    final static private int KEY_UP_DELAY = 100;

    private List<Key>     shiftableKeys;
    private int           pressedShiftKey;

    public Keyboard keyboard;

    private static List<KeyMap> keyboardMapping;

    private Handler handler = new Handler();

    public Vibrator vibrator;
    
    static {
        keyboardMapping = parseKeyMap(R.xml.keymap_us);
    }

    static private List<KeyMap> parseKeyMap(int keyMapLayout) {
        XmlResourceParser parser = Pom1Activity.me.getResources()
                .getXml(keyMapLayout);
        List<KeyMap> keyMap = new ArrayList<KeyMap>();
        int nextFree = 15;
        try {
            parser.next();
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    if (tagName.equals("KeyMap")) {
                        String label = parser.getAttributeValue(null, "label");
                        String sym = parser.getAttributeValue(null, "sym");
                        String key = parser.getAttributeValue(null, "key");
                        String name = parser.getAttributeValue(null, "name");
                        String value = parser.getAttributeValue(null, "value");
                        KeyMap km = new KeyMap();
                        km.label = label;
                        km.sym = Long.decode(sym).intValue();
                        if (key.equals("NEXT_FREE")) {
                            km.key = nextFree++;
                        } else {
                            km.key = key.charAt(0);
                        }
                        km.name = name;
                        km.value = Long.decode(value).intValue();
                        keyMap.add(km);
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        parser.close();
        return keyMap;
    }

    public KeyboardManager() {
        shiftableKeys = new ArrayList<>();
        pressedShiftKey = Key.TK_NONE;
    }

    public KeyMap getKeyMap(int id) {
        return keyboardMapping.get(id);
    }

    public KeyMap getKeyMap(String name) {
        for (KeyMap key : keyboardMapping) {
            if (key.name.equals(name)) {
                return key;
            }
        }
        return null;
    }

    public void injectKey(char ch) {
        String keyName;
        switch (ch) {
        case ' ':
            keyName = "key_SPACE";
            break;
        case ':':
            keyName = "key_COLON";
            break;
        case '"':
            keyName = "key_QUOT";
            break;
        case '/':
            keyName = "key_SLASH";
            break;
        case '\n':
            keyName = "key_ENTER";
            break;
        default:
            keyName = "key_" + ch;
            break;
        }

        final KeyMap key = getKeyMap(keyName);
        keyDown(key.value);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                keyUp(key.value);
            }
        }, KEY_UP_DELAY);
    }

    public void addShiftableKey(Key key) {
        shiftableKeys.add(key);
    }

    public void shiftKeys(int shiftKey) {
        pressedShiftKey = shiftKey;
        for (Key key : shiftableKeys) {
            key.shift();
        }
    }

    public void unshiftKeys() {
        pressedShiftKey = Key.TK_NONE;
        for (Key key : shiftableKeys) {
            key.unshift();
        }
    }

    public int getPressedShiftKey() {
        return pressedShiftKey;
    }

    public void allCursorKeysUp() {
        keyUp(Key.TK_LEFT);
        keyUp(Key.TK_RIGHT);
        keyUp(Key.TK_UP);
        keyUp(Key.TK_DOWN);
    }

    public void pressKeyDown() {
        keyDown(Key.TK_DOWN);
    }

    public void pressKeyUp() {
        keyDown(Key.TK_UP);
    }

    public void pressKeyLeft() {
        keyDown(Key.TK_LEFT);
    }

    public void pressKeyRight() {
        keyDown(Key.TK_RIGHT);
    }

    public void pressKeySpace() {
        keyDown(Key.TK_SPACE);
    }

    public void unpressKeyDown() {
        keyUp(Key.TK_DOWN);
    }

    public void unpressKeyUp() {
        keyUp(Key.TK_UP);
    }

    public void unpressKeyLeft() {
        keyUp(Key.TK_LEFT);
    }

    public void unpressKeyRight() {
        keyUp(Key.TK_RIGHT);
    }

    public void unpressKeySpace() {
        keyUp(Key.TK_SPACE);
    }

    public void keyDown(int keyId) {

        KeyMap keyMap = keyboardMapping.get(keyId);

        keyDown(keyMap.sym, keyMap.key, keyId);
        // it is safe to cancel other vibrations currently taking place
        vibrator.cancel();
        vibrator.vibrate(15);
    }

    public void keyDown(int sym, int key, int keyId) {
        final int SDL_KEYDOWN = 2;
        //Leog.v("POM1", "keyId 1 - " +sym+" "+ key+" "+ keyId);

        if (keyId == 66) {
            keyboard.pia6820.writeKbd(0xDF | 0x80);
            keyboard.pia6820.writeKbdCr(0xA7);
        }else {

            if (key >= 0x61 && key <= 0x7A)
                key &= 0x5F;
            if (keyId == 56)
                key = 0x0D;
            if (keyId == 66)
                key = 0x27;
            if (keyId == 51)
                key = 0x27;

            Log.v("POM1", "keyId 2 - " + sym + " " + key + " " + keyId);

            if (key < 0x60) {
                keyboard.pia6820.writeKbd(key | 0x80);
                keyboard.pia6820.writeKbdCr(0xA7);
            }
        }
        //XTRS.addKeyEvent(SDL_KEYDOWN, sym, key);
    }

    public void keyUp(int keyId) {
        KeyMap keyMap = keyboardMapping.get(keyId);
        keyUp(keyMap.sym, keyMap.key);
    }

    public void keyUp(int sym, int key) {
        final int SDL_KEYUP = 3;


        //keyboard.addKeyEvent(SDL_KEYUP, sym, key);
    }

    public boolean keyDown(KeyEvent event) {
        int key = mapKeyEventToTRS(event);
        if (key == Key.TK_NONE) {
            return false;
        }
        keyDown(key);
        return true;
    }

    public boolean keyUp(KeyEvent event) {
        final int key = mapKeyEventToTRS(event);
        if (key == Key.TK_NONE) {
            return false;
        }
        handler.postDelayed(new Runnable() {

            @Override
            public void run() {
                keyUp(key);
            }
        }, KEY_UP_DELAY);
        return true;
    }

    private int mapKeyEventToTRS(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int keyController = mapGameControllerButton(keyCode);
        if (keyController != Key.TK_NONE) {
            return keyController;
        }
        switch (keyCode) {
        case KeyEvent.KEYCODE_ENTER:
            return Key.TK_ENTER;
        case KeyEvent.KEYCODE_DEL:
        case KeyEvent.KEYCODE_DPAD_LEFT:
            return Key.TK_LEFT;
        case KeyEvent.KEYCODE_DPAD_RIGHT:
            return Key.TK_RIGHT;
        case KeyEvent.KEYCODE_DPAD_DOWN:
            return Key.TK_DOWN;
        case KeyEvent.KEYCODE_DPAD_UP:
            return Key.TK_UP;
        case KeyEvent.KEYCODE_B:
            if ((event.getMetaState() & KeyEvent.META_CTRL_ON) != 0) {
                return Key.TK_BREAK;
            }
            break;
        case KeyEvent.KEYCODE_C:
            if ((event.getMetaState() & KeyEvent.META_CTRL_ON) != 0) {
                return Key.TK_CLEAR;
            }
            break;
        }

        int key = event.getUnicodeChar();
        if (key >= '0' && key <= '9') {
            return key - '0';
        }
        if (key >= 'a' && key <= 'z') {
            // Convert to upper case
            key -= 0x20;
        }
        if (key >= 'A' && key <= 'Z') {
            return key - 'A' + 10;
        }
        switch (key) {
        case ',':
            return Key.TK_COMMA;
        case '.':
            return Key.TK_DOT;
        case '/':
            return Key.TK_SLASH;
        case ' ':
            return Key.TK_SPACE;
        case '^':
             return Key.TK_CARAT;
        case '+':
            return Key.TK_ADD;
        case '#':
            return Key.TK_HASH;
        case '(':
            return Key.TK_BR_OPEN;
        case ')':
            return Key.TK_BR_CLOSE;
        case '*':
            return Key.TK_ASTERIX;
        case '$':
            return Key.TK_DOLLAR;
        case '?':
            return Key.TK_QUESTION;
        case '<':
            return Key.TK_LT;
        case '>':
            return Key.TK_GT;
        case '=':
            return Key.TK_EQUAL;
        case '%':
            return Key.TK_PERCENT;
        case '&':
            return Key.TK_AMP;
        case '\'':
            return Key.TK_APOS;
        case '!':
            return Key.TK_EXCLAMATION_MARK;
        case '@':
            return Key.TK_AT;
        case '"':
            return Key.TK_QUOT;
        case ';':
            return Key.TK_SEMICOLON;
        case 0xa:
            return Key.TK_ENTER;
        case ':':
            return Key.TK_COLON;
        case '-':
            return Key.TK_MINUS;
        case 0x8:
            return Key.TK_LEFT;
        }
        return Key.TK_NONE;
    }

    private int mapGameControllerButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_L1:
                return Key.TK_SPACE;
            case KeyEvent.KEYCODE_BUTTON_R1:
                return Key.TK_SPACE;
            case KeyEvent.KEYCODE_BUTTON_X:
                return Key.TK_SPACE;
            case KeyEvent.KEYCODE_BUTTON_Y:
                return Key.TK_SPACE;
            case KeyEvent.KEYCODE_BUTTON_B:
                return Key.TK_SPACE;
            case KeyEvent.KEYCODE_BUTTON_A:
                return Key.TK_SPACE;
        }
        return Key.TK_NONE;
    }
}
