// Pom1 Apple 1 Emulator
// Copyright (C) 2000 Verhille Arnaud
// Copyright (C) 2012 John D. Corrado
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

package com.enders.pomoneemulator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Vibrator;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.InputStream;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Objects;
import java.util.regex.Pattern;

public class Pom1Activity extends AppCompatActivity {
	public SharedPreferences preferences;
	public Pia6820 pia6820;
	public Memory memory;
	public M6502 m6502;
	public Screen screen;
	public Keyboard keyboard;
	public boolean isOptionView;
	public BufferedReader reader;
	//public String filePath;
	public Uri fileURI;

	private Hardware           currentHardware;
	private Rect               windowRect;
	public static Pom1Activity me;
	private KeyboardManager    keyboardManager;
	private Screen screenRow;
	private AdView mAdView;
	private FrameLayout adContainerView;
	private ViewGroup keyboardContainer;
	public Vibrator vibrator;
	private boolean showKeyboard = true;
	private LinearLayout screenContainer;
	private int openMode;
	private boolean loaded;

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getPreferences(MODE_PRIVATE);

		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		pia6820 = new Pia6820(preferences);
		memory = new Memory(this, pia6820, preferences);
		m6502 = new M6502(memory, preferences);

		screen = new Screen(this.getApplicationContext(), pia6820, preferences);

		keyboard = new Keyboard(pia6820, screen);

		//screenContainer = (LinearLayout) findViewById(R.id.screen_container);
		//screenContainer.removeAllViews();
		//screenContainer.addView(screen);
        initGame();
	}

	public void initGame() {

		setContentView(R.layout.activity_apple_one);
		screenContainer = (LinearLayout) findViewById(R.id.screen_container);
		screenContainer.addView(screen);
		screen.setOnKeyListener(keyboard);
		screen.requestFocus();

		me = this;

		currentHardware = new Hardware();

		adContainerView = findViewById(R.id.adView);

		mAdView = new AdView(this);
		mAdView.setAdUnitId("ca-app-pub-7298593861312209/3404672430");
		adContainerView.addView(mAdView);
		loadBanner();

		DisplayMetrics displayMetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
		int height = displayMetrics.heightPixels;
		int width = displayMetrics.widthPixels;

		windowRect = new Rect();
		windowRect.top = 0;
		windowRect.left = 0;
		windowRect.right = width;
		windowRect.bottom = height;
		keyboardManager = new KeyboardManager();
		keyboardManager.keyboard = keyboard;
		keyboardManager.vibrator = vibrator;
		keyboardContainer = (ViewGroup) findViewById(R.id.keyboard_container);
		keyboardContainer.removeAllViews();
		currentHardware.computeKeyDimensions(windowRect);

		getLayoutInflater().inflate(R.layout.keyboard, keyboardContainer, true);

		keyboardContainer.requestLayout();
		loaded = true;

	}
    
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.options, menu);
    	return true;
    }

	private void loadBanner() {
		AdRequest adRequest = new AdRequest.Builder() .build();
		mAdView.setAdSize(AdSize.BANNER);
		mAdView.loadAd(adRequest);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		
		if (isOptionView) {
			menu.getItem(0).setEnabled(false);
			menu.getItem(1).setEnabled(false);
			menu.getItem(2).setEnabled(false);
			menu.getItem(3).setEnabled(false);
			menu.getItem(4).setEnabled(false);
			menu.getItem(5).setEnabled(false);
		} else {
			menu.getItem(0).setEnabled(true);
			menu.getItem(1).setEnabled(true);
			menu.getItem(2).setEnabled(true);
			menu.getItem(3).setEnabled(true);
			menu.getItem(4).setEnabled(true);
			menu.getItem(5).setEnabled(true);
		}
		
		return true;
	}
	
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		EditText editText;
		RadioGroup radioGroup;
		Button button;
		CheckBox checkBox;

		if(item.getItemId() == R.id.reset){
			pia6820.reset();
			m6502.reset();
			return true;
		}

		if(item.getItemId() == R.id.hard_reset) {

			m6502.pause();
			m6502.reset();
			screen.pause();
			screen.reset();
			pia6820.reset();
			memory.reset();
			m6502.resume();
			screen.resume();
			return true;
		}

//		if(item.getItemId() == R.id.configure) {
//
//			m6502.pause();
//			screen.pause();

//			screenContainer.removeView(screen);
//			setContentView(R.layout.configure);
//			radioGroup = (RadioGroup) findViewById(R.id.pixel_size);
//			radioGroup.check(screen.getPixelSize() == 1 ? R.id.pixel_size_1x : R.id.pixel_size_2x);
//			radioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
//				public void onCheckedChanged(RadioGroup group, int checkedId) {
//					CheckBox checkBox = (CheckBox) findViewById(R.id.scanlines);
//
//					if (checkedId == R.id.pixel_size_1x) {
//						checkBox.setChecked(false);
//						checkBox.setEnabled(false);
//					} else
//						checkBox.setEnabled(true);
//				}
//			});
//			checkBox = (CheckBox) findViewById(R.id.scanlines);
//			checkBox.setChecked(screen.getScanlines());
//			checkBox.setEnabled(screen.getPixelSize() != 1);
//			editText = (EditText) findViewById(R.id.terminal_speed);
//			editText.setText(String.valueOf(screen.getTerminalSpeed()));
//			editText.setWidth(editText.getPaddingLeft() + editText.getPaddingRight() + (int) editText.getPaint().measureText("000"));checkBox = (CheckBox) findViewById(R.id.ram_8k);
//			checkBox.setChecked(memory.getRam8k());
//			checkBox = (CheckBox) findViewById(R.id.write_in_rom);
//			checkBox.setChecked(memory.getWriteInRom());
//			editText = (EditText) findViewById(R.id.irq_brk_vector);
//			editText.setText(String.format("%04X", memory.memRead(0xFFFE) | memory.memRead(0xFFFF) << 8));
//			editText.setWidth(editText.getPaddingLeft() + editText.getPaddingRight() + (int) editText.getPaint().measureText("DDDD"));
//			checkBox = (CheckBox) findViewById(R.id.blink_cursor);
//			checkBox.setChecked(screen.getBlinkCursor());
//			checkBox = (CheckBox) findViewById(R.id.block_cursor);
//			checkBox.setChecked(screen.getBlockCursor());
//			button = (Button) findViewById(R.id.save);
//			button.setOnClickListener(new OnClickListener() {
//				public void onClick(View v) {
//					saveConfiguration();
//				}
//			});
//			isOptionView = true;
//			return true;
//		}

		if(item.getItemId() == R.id.load_memory) {
			m6502.pause();
			screen.pause();

			screenContainer.removeView(screen);
			setContentView(R.layout.load_memory);
			radioGroup = (RadioGroup) findViewById(R.id.file_format);
			radioGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {
				public void onCheckedChanged(RadioGroup group, int checkedId) {
					CheckBox checkBox = (CheckBox) findViewById(R.id.simulate_keybaord_input);
					EditText editText = (EditText) findViewById(R.id.starting_address);

					if (checkedId == R.id.file_format_ascii) {
						checkBox.setEnabled(true);
						editText.setEnabled(false);
						editText.setText("");
					} else {
						checkBox.setChecked(false);
						checkBox.setEnabled(false);
						editText.setEnabled(true);
					}
				}
			});
			editText = (EditText) findViewById(R.id.starting_address);
			editText.setEnabled(false);
			editText.setWidth(editText.getPaddingLeft() + editText.getPaddingRight() + (int) editText.getPaint().measureText("DDDD"));
			button = (Button) findViewById(R.id.browse);
			button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					openFile();
				}
			});
			button = (Button) findViewById(R.id.load);
			button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					loadMemory();
				}
			});
			isOptionView = true;
			return true;
		}

		if(item.getItemId() == R.id.save_memory) {
			m6502.pause();
			screen.pause();

			screenContainer.removeView(screen);
			setContentView(R.layout.save_memory);

			editText = (EditText) findViewById(R.id.starting_address);
			editText.setWidth(editText.getPaddingLeft() + editText.getPaddingRight() + (int) editText.getPaint().measureText("DDDD"));
			editText = (EditText) findViewById(R.id.ending_address);
			editText.setWidth(editText.getPaddingLeft() + editText.getPaddingRight() + (int) editText.getPaint().measureText("DDDD"));
			button = (Button) findViewById(R.id.browse);
			button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					createFile();
				}
			});
			button = (Button) findViewById(R.id.save);
			button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					saveMemory();
				}
			});
			isOptionView = true;
			return true;
		}

		if(item.getItemId() == R.id.about) {

			m6502.pause();
			screen.pause();
			isOptionView = true;
			screenContainer.removeView(screen);
			setContentView(R.layout.about);

			WebView wv;
			wv = (WebView) findViewById(R.id.helpview);
			wv.loadUrl("file:///android_asset/help.html");

			return true;
		}

		if(item.getItemId() == R.id.keyboard) {
			if(showKeyboard == true){
				keyboardContainer.setVisibility(View.GONE);
				item.setTitle("Show Keyboard");
				showKeyboard = false;
			}else{
				keyboardContainer.setVisibility(View.VISIBLE);
				item.setTitle("Hide Keyboard");
				showKeyboard = true;
			}

			return true;
		}
		if(item.getItemId() == R.id.quit) {
			finish();
			return true;
		}

		return super.onOptionsItemSelected(item);
    }
	private long mLastPress = 0;

	private static final int PICK_PDF_FILE = 2;

	private void openFile() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");

		// Optionally, specify a URI for the file that should appear in the
		// system file picker when it loads.
		//intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);

		startActivityForResult(intent, PICK_PDF_FILE);
	}

	private static final int CREATE_FILE = 1;

	private void createFile() {
		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_TITLE, "test.bin");

		// Optionally, specify a URI for the directory that should be opened in
		// the system file picker when your app creates the document.
		//intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri);

		startActivityForResult(intent, CREATE_FILE);
	}

	@Override
    public void onBackPressed() {
    	if (isOptionView) {
    		//setConte ntView(screen);
			isOptionView = false;
			setContentView(R.layout.activity_apple_one);
			initGame();
    		m6502.resume();
    		screen.resume();
    	} else {
    	    Toast onBackPressedToast = Toast.makeText(this, "Press Back again to Exit", Toast.LENGTH_SHORT);
    	    long currentTime = System.currentTimeMillis();
    	    if (currentTime - mLastPress > 2000) {
    	        onBackPressedToast.show();
    	        mLastPress = currentTime;
    	    } else {
    	        onBackPressedToast.cancel();  //Difference with previous answer. Prevent continuing showing toast after application exit 
    	        super.onBackPressed();
    	    }
    	}
    }

	@Override
    public void onResume() {
		//Lbog.v("POM1", "onResume" );

		super.onResume();
		
    	if (!isOptionView && loaded == true) {
			screenContainer.removeView(screen);
			initGame();
			m6502.resume();
			screen.resume();
    	} 
    }
	
	@Override
    public void onPause() {
    	super.onPause();
    	
    	//Lbog.v("POM1", "onPause - Rotate" );
    	
    	if (!isOptionView) {
    		m6502.pause();
    		screen.pause(); 
    	}
    	
//    	if (isFinishing()) {
//    		SharedPreferences.Editor editor = preferences.edit();
//    		pia6820.saveState(editor);
//    		memory.saveState(editor);
//    		m6502.saveState(editor);
//    		screen.saveState(editor);
//    		editor.commit();
//    		screen.closeInputFile();
//    	}
    }
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK && requestCode == PICK_PDF_FILE) {
			EditText editText = (EditText) findViewById(R.id.file_path);
			fileURI = data.getData();
			editText.setText(fileURI.getPath());
		} else if (resultCode == Activity.RESULT_OK && requestCode == CREATE_FILE) {
			EditText editText = (EditText) findViewById(R.id.file_path);
			fileURI = data.getData();
			editText.setText(fileURI.getPath());
		} else
			super.onActivityResult(requestCode, resultCode, data);
    }
	
	public void saveConfiguration() {
		EditText editText = (EditText) findViewById(R.id.terminal_speed);
		
		if (editText.length() == 0) {
			Toast.makeText(this, "Terminal speed not set", Toast.LENGTH_SHORT).show();
			return;
		}
		
		int terminalSpeed = Integer.parseInt(editText.getText().toString());
		
		if (terminalSpeed < 1 || terminalSpeed > 120) {
			Toast.makeText(this, "Terminal speed out of range (Range: 1 - 120)", Toast.LENGTH_SHORT).show();
			return;
		}
		
		screen.setTerminalSpeed(terminalSpeed);
		
		editText = (EditText) findViewById(R.id.irq_brk_vector);
		
		if (editText.length() == 0) {
			Toast.makeText(this, "IRQ/BRK vector not set", Toast.LENGTH_SHORT).show();
			return;
		}
		
		int brkVector = Integer.parseInt(editText.getText().toString(), 16);
		memory.memWrite(0xFFFE, brkVector & 0xFF);
		memory.memWrite(0xFFFF, brkVector >> 8);
		
		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.pixel_size);
		
		if (radioGroup.getCheckedRadioButtonId() == R.id.pixel_size_1x) {
			screen.setPixelSize(1);
			screen.setScanlines(false);
		} else
			screen.setPixelSize(2);
		
		CheckBox checkBox = (CheckBox) findViewById(R.id.scanlines);
		screen.setScanlines(checkBox.isChecked());
		checkBox = (CheckBox) findViewById(R.id.ram_8k);
		memory.setRam8k(checkBox.isChecked());
		checkBox = (CheckBox) findViewById(R.id.write_in_rom);
		memory.setWriteInRom(checkBox.isChecked());
		checkBox = (CheckBox) findViewById(R.id.blink_cursor);
		screen.setBlinkCursor(checkBox.isChecked());
		checkBox = (CheckBox) findViewById(R.id.block_cursor);
		screen.setBlockCursor(checkBox.isChecked());
		Toast.makeText(this, "Successfully saved configuration", Toast.LENGTH_SHORT).show();
	}

	@SuppressLint("MissingInflatedId")
	public void loadMemory() {

		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.file_format);
		
		if (radioGroup.getCheckedRadioButtonId() == R.id.file_format_ascii) {
			try {
				InputStream inputStream =
						getContentResolver().openInputStream(fileURI);
				reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)));
				
				CheckBox checkBox = (CheckBox) findViewById(R.id.simulate_keybaord_input);
				
				if (checkBox.isChecked()) {
					if (screen.isInputFileOpen()) {
						DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								switch (which) {
								case DialogInterface.BUTTON_POSITIVE:
									screen.closeInputFile();
									Toast.makeText(Pom1Activity.this, "Canceled loading \"" + screen.getInputFilePath() + "\"", Toast.LENGTH_SHORT).show();
									screen.setInputFile(reader, fileURI);
									break;
								case DialogInterface.BUTTON_NEGATIVE:
									if (reader != null) {
										try {
											reader.close();
										} catch (IOException e) {
											e.printStackTrace();
										}
									}
									break;
								}

								setContentView(R.layout.activity_apple_one);
								initGame();
								isOptionView = false;
								m6502.resume();
								screen.resume();
							}
						};
						AlertDialog.Builder builder = new AlertDialog.Builder(this);
						builder.setMessage("Do you want to cancel the current read?");
						builder.setPositiveButton("Yes", listener);
						builder.setNegativeButton("No", listener);
						builder.show();
						return;
					}
					
					screen.setInputFile(reader, fileURI);
				} else {
					String str;
					int i, address = 0, length, value;
					Pattern pattern = Pattern.compile("(^[0-9A-Fa-f]{1,4}:?$)|(^([0-9A-Fa-f]{1,4})?:\\s?([0-9A-Fa-f]{1,2}\\s{1})+[0-9A-Fa-f]{1,2}$)");
					
					while ((str = reader.readLine()) != null) {
						if (str.length() == 0 || str.charAt(0) == '/')
							continue;
						
						if (pattern.matcher(str).find()) {
							if (str.charAt(0) != ':') {
								i = str.indexOf(':');
								
								if (i != -1) {
									address = Integer.parseInt(str.substring(0, i++), 16);
									
									if (i == str.length())
										continue;
								} else {
									address = Integer.parseInt(str, 16);
									continue;
								}
							} else
								i = 1;
							
							if (str.charAt(i) == ' ')
								i++;
							
							length = str.length();
							
							for (; i < length; i += 3) {
								value = Integer.parseInt(str.substring(i, i + 2), 16);
								memory.memWrite(address++, value);
							}
						}
					}
					
					Toast.makeText(this, "Successfully loaded \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to  load \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
			} finally {
				if (reader != null && !screen.isInputFileOpen()) {
					try {
						reader.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			EditText editText = (EditText) findViewById(R.id.starting_address);
			
			if (editText.length() == 0) {
				Toast.makeText(this, "Starting address not set", Toast.LENGTH_SHORT).show();
				return;
			}
			
			int start = Integer.parseInt(editText.getText().toString(), 16);
			
			File file = new File(fileURI.getPath());

			Cursor returnCursor =
					getContentResolver().query(fileURI, null, null, null, null);
			int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
			returnCursor.moveToFirst();

			int size = (int) returnCursor.getLong(sizeIndex);

			if (size > 65536 || start + size - 1 > 65535) {
				Toast.makeText(this, "File size too large", Toast.LENGTH_SHORT).show();
				return;
			}else{
				Toast.makeText(this, "File size "+size, Toast.LENGTH_SHORT).show();

			}

			InputStream fis = null;

			try {
			 	fis = getContentResolver().openInputStream(fileURI);
			//fis = new InputStream(new File(fileURI.getPath()));
//
				byte[] fbrut = new byte[size];
//
				if (fbrut != null) {
					fis.read(fbrut);
					memory.setMemory(fbrut, start, size);
					Toast.makeText(this, "Successfully loaded \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to load \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
			}
		}

		isOptionView = false;
		setContentView(R.layout.activity_apple_one);
		initGame();
		m6502.resume();
		screen.resume();

		screen.closeInputFile();

		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(screen.getWindowToken(), 0);
	}
	@SuppressLint("MissingInflatedId")
	public void saveMemory() {
		EditText editText = (EditText) findViewById(R.id.file_path);
		
		if (editText.length() == 0) {
			Toast.makeText(this, "File path not set", Toast.LENGTH_SHORT).show();
			return;
		}

		editText = (EditText) findViewById(R.id.starting_address);
		
		if (editText.length() == 0) {
			Toast.makeText(this, "Starting address not set", Toast.LENGTH_SHORT).show();
			return;
		}
		
		int start = Integer.parseInt(editText.getText().toString(), 16);
		
		editText = (EditText) findViewById(R.id.ending_address);
		
		if (editText.length() == 0) {
			Toast.makeText(this, "Ending address not set", Toast.LENGTH_SHORT).show();
			return;
		}
		
		int end = Integer.parseInt(editText.getText().toString(), 16);
		
		if (start > end)
		{
			int temp = start;
			start = end;
			end = temp;
		}
		
		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.file_format);
		byte[] fbrut;
		
		if (radioGroup.getCheckedRadioButtonId() == R.id.file_format_ascii) {
			BufferedWriter writer = null;

			try {

				OutputStream outputStream =
						getContentResolver().openOutputStream(fileURI);
				//writer = new BufferedWriter(new
				writer = new BufferedWriter(new OutputStreamWriter(Objects.requireNonNull(outputStream)));

				fbrut = memory.dumpMemory(start, end);

				if (fbrut != null) {
					String str = "// Pom1 Save ";

					int length = end - start + 1;

					for (int i = 0, j = start; i < length; i++) {
						if (i % 8 == 0) {
							writer.write(str);
							str = String.format("\n%04X:", j);
							j += 8;
						}

						str += String.format("%02X ", fbrut[i]);
					}

					writer.write(str);

					Toast.makeText(this, "Successfully saved \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to save \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
			} finally {
				if (writer != null) {
					try {
						writer.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			FileOutputStream fos = null;

			try {

				ParcelFileDescriptor pfd = getContentResolver().
						openFileDescriptor(fileURI, "wt");

				FileOutputStream fileOutputStream =
						new FileOutputStream(pfd.getFileDescriptor());

				fbrut = memory.dumpMemory(start, end);

				if (fbrut != null && fileOutputStream != null) {

					fileOutputStream.write(fbrut);
					fileOutputStream.close();
					pfd.close();

					Toast.makeText(this, "Successfully saved \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
				}else{
					Toast.makeText(this, "Failed to save \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to save \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
			}
		}

		setContentView(R.layout.activity_apple_one);
		initGame();
		isOptionView = false;
		m6502.resume();
		screen.resume();

		screen.closeInputFile();

		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(screen.getWindowToken(), 0);
	}

	public int getKeyWidth() {
		return currentHardware.getKeyWidth();
	}

	public int getKeyHeight() {
		return currentHardware.getKeyHeight();
	}

	public int getKeyMargin() {
		return currentHardware.getKeyMargin();
	}

	public KeyboardManager getKeyboardManager() {
		return keyboardManager;
	}
}