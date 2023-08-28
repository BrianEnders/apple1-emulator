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
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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
import android.widget.RelativeLayout;
import android.widget.TableRow;
import android.widget.Toast;
import android.Manifest;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
	public String filePath;


	private static final int MY_REQUEST_CODE_PERMISSION = 1000;
	private static final int MY_RESULT_CODE_FILECHOOSER = 2000;
	private static final int STORAGE_PERMISSION_CODE = 100;
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
		//screen.setOnKeyListener(keyboard);
//		mAdView = new AdView(this);
//		mAdView.setAdUnitId(getString(R.string.banner_ad_unit_id));
//		adContainerView.addView(mAdView);

//		screenRow.addView(screen);
//

		//screen.setFocusableInTouchMode(true);

		me = this;

		currentHardware = new Hardware();

		//setContentView(screen);

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

		Log.v("POM1", "windowRect.right - " +windowRect.right);
		keyboardManager = new KeyboardManager();
		keyboardManager.keyboard = keyboard;
		keyboardManager.vibrator = vibrator;
		keyboardContainer = (ViewGroup) findViewById(R.id.keyboard_container);
		keyboardContainer.removeAllViews();
		currentHardware.computeKeyDimensions(windowRect);

		getLayoutInflater().inflate(R.layout.keyboard, keyboardContainer, true);

		keyboardContainer.requestLayout();
		loaded = true;

		//m6502.resume();
		//pia6820.reset();
		//memory.reset();
		//memory.resume();

	}
    
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.options, menu);
    	return true;
    }

	private void loadBanner() {
		// Create an ad request. Check your logcat output for the hashed device ID
		// to get test ads on a physical device, e.g.,
		// "Use AdRequest.Builder.addTestDevice("ABCDE0123") to get test ads on this
		// device."
		AdRequest adRequest =
				new AdRequest.Builder() .build();

		//AdSize adSize = getAdSize();
		// Step 4 - Set the adaptive ad size on the ad view.
		mAdView.setAdSize(AdSize.BANNER);

		//mAdView.setAdSize(adSize);


		// Step 5 - Start loading the ad in the background.
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

					if (checkPermission()) {
						Log.d("POM1", "onClick: Permissions already granted...");
						startFileExplorerActivity(SelectionMode.MODE_OPEN);
						//createFolder();
					} else {
						Log.d("POM1", "onClick: Permissions was not granted, request...");
						openMode = SelectionMode.MODE_OPEN;
						requestPermission();
					}

					//askPermissionAndBrowseFile();
					//startFileExplorerActivity(SelectionMode.MODE_OPEN);
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
					if (checkPermission()) {
						Log.d("POM1", "onClick: Permissions already granted...");
						startFileExplorerActivity(SelectionMode.MODE_CREATE);
						//createFolder();
					} else {
						Log.d("POM1", "onClick: Permissions was not granted, request...");
						openMode = SelectionMode.MODE_CREATE;
						requestPermission();
					}
					//startFileExplorerActivity(SelectionMode.MODE_CREATE);
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

	public boolean checkPermission(){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
			//Android is 11(R) or above
			return Environment.isExternalStorageManager();
		}
		else{
			//Android is below 11(R)
			int write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
			int read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

			return write == PackageManager.PERMISSION_GRANTED && read == PackageManager.PERMISSION_GRANTED;
		}
	}

	private void requestPermission(){
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
			//Android is 11(R) or above
			try {
				Log.d("POM1", "requestPermission: try");

				Intent intent = new Intent();
				intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
				Uri uri = Uri.fromParts("package", this.getPackageName(), null);
				intent.setData(uri);
				storageActivityResultLauncher.launch(intent);
			}
			catch (Exception e){
				Log.e("POM1", "requestPermission: catch", e);
				Intent intent = new Intent();
				intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
				storageActivityResultLauncher.launch(intent);
			}
		}
		else {
			//Android is below 11(R)
			ActivityCompat.requestPermissions(
					this,
					new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
					STORAGE_PERMISSION_CODE
			);
		}
	}

	private ActivityResultLauncher<Intent> storageActivityResultLauncher = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(),
			new ActivityResultCallback<ActivityResult>() {
				@Override
				public void onActivityResult(ActivityResult result) {
					Log.d("POM1", "onActivityResult: ");
					//here we will handle the result of our intent
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
						//Android is 11(R) or above
						if (Environment.isExternalStorageManager()){
							//Manage External Storage Permission is granted
							Log.d("POM1", "onActivityResult: Manage External Storage Permission is granted");
							startFileExplorerActivity(openMode);//createFolder();
						}
						else{
							//Manage External Storage Permission is denied
							Log.d("POM1", "onActivityResult: Manage External Storage Permission is denied");
							Toast.makeText(Pom1Activity.this, "Manage External Storage Permission is denied", Toast.LENGTH_SHORT).show();
						}
					}
					else {
						//Android is below 11(R)
					}
				}
			}
	);

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
		Log.v("POM1", "onResume" );

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
    	
    	Log.v("POM1", "onPause - Rotate" );
    	
    	if (!isOptionView) {
    		m6502.pause();
    		screen.pause(); 
    	}
    	
    	if (isFinishing()) {
    		SharedPreferences.Editor editor = preferences.edit();
    		pia6820.saveState(editor);
    		memory.saveState(editor);
    		m6502.saveState(editor);
    		screen.saveState(editor);
    		editor.commit();
    		screen.closeInputFile();
    	}
    }
	
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK && requestCode == 0) {
			EditText editText = (EditText) findViewById(R.id.file_path);
			editText.setText(data.getStringExtra(FileDialog.RESULT_PATH));
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
		EditText editText = (EditText) findViewById(R.id.file_path);
		
		if (editText.length() == 0) {
			Toast.makeText(this, "File path not set", Toast.LENGTH_SHORT).show();
			return;
		}
		
		filePath = editText.getText().toString();
		
		if (!filePath.startsWith("/") || filePath.endsWith("/")) {
			Toast.makeText(this, "File path is invalid", Toast.LENGTH_SHORT).show();
			return;
		}
		
		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.file_format);
		
		if (radioGroup.getCheckedRadioButtonId() == R.id.file_format_ascii) {
			try {
				reader = new BufferedReader(new FileReader(new File(filePath)));
				
				CheckBox checkBox = (CheckBox) findViewById(R.id.simulate_keybaord_input);
				
				if (checkBox.isChecked()) {
					if (screen.isInputFileOpen()) {
						DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int which) {
								switch (which) {
								case DialogInterface.BUTTON_POSITIVE:
									screen.closeInputFile();
									Toast.makeText(Pom1Activity.this, "Canceled loading \"" + screen.getInputFilePath() + "\"", Toast.LENGTH_SHORT).show();
									screen.setInputFile(reader, filePath);
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
					
					screen.setInputFile(reader, filePath);
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
					
					Toast.makeText(this, "Successfully loaded \"" + filePath + "\"", Toast.LENGTH_SHORT).show();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to load \"" + filePath + "\"", Toast.LENGTH_SHORT).show();
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
			editText = (EditText) findViewById(R.id.starting_address);
			
			if (editText.length() == 0) {
				Toast.makeText(this, "Starting address not set", Toast.LENGTH_SHORT).show();
				return;
			}
			
			int start = Integer.parseInt(editText.getText().toString(), 16);
			
			File file = new File(filePath);
			
			int size = (int) file.length();
			
			if (size > 65536 || start + size - 1 > 65535) {
				Toast.makeText(this, "File size too large", Toast.LENGTH_SHORT).show();
				return;
			}
			
			FileInputStream fis = null;

			try {
				fis = new FileInputStream(new File(filePath));
				
				byte[] fbrut = new byte[size];
				
				if (fbrut != null) {
					fis.read(fbrut);
					memory.setMemory(fbrut, start, size);
					Toast.makeText(this, "Successfully loaded \"" + filePath + "\"", Toast.LENGTH_SHORT).show();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to load \"" + filePath + "\"", Toast.LENGTH_SHORT).show();
			}
		}

		isOptionView = false;
		setContentView(R.layout.activity_apple_one);
		initGame();
		m6502.resume();
		screen.resume();

		//screen.closeInputFile();

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
		
		filePath = editText.getText().toString();
		
		if (!filePath.startsWith("/") || filePath.endsWith("/")) {
			Toast.makeText(this, "File path is invalid", Toast.LENGTH_SHORT).show();
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
				writer = new BufferedWriter(new FileWriter(new File(filePath)));
				fbrut = memory.dumpMemory(start, end);
				
				if (fbrut != null) {
					String str = "// Pom1 Save - " + filePath.substring(filePath.lastIndexOf('/') + 1);
					
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
					
					Toast.makeText(this, "Successfully saved \"" + filePath + "\"", Toast.LENGTH_SHORT).show();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to save \"" + filePath + "\"", Toast.LENGTH_SHORT).show();
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
				fos = new FileOutputStream(new File(filePath));
				fbrut = memory.dumpMemory(start, end);
				
				if (fbrut != null) {
					fos.write(fbrut);
					Toast.makeText(this, "Successfully saved \"" + filePath + "\"", Toast.LENGTH_SHORT).show();
				}
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to save \"" + filePath + "\"", Toast.LENGTH_SHORT).show();
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
	
	public void startFileExplorerActivity(int selectionMode) {
		Intent intent = new Intent(this, FileDialog.class);
        intent.putExtra(FileDialog.START_PATH, Environment.getExternalStorageDirectory().getPath());
        intent.putExtra(FileDialog.CAN_SELECT_DIR, false);
        intent.putExtra(FileDialog.SELECTION_MODE, selectionMode);
        startActivityForResult(intent, 0);
	}

	// When you have the request results
	@Override
	public void onRequestPermissionsResult(int requestCode,
										   String permissions[], int[] grantResults) {

		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		//
		switch (requestCode) {
			case MY_REQUEST_CODE_PERMISSION: {

				// Note: If request is cancelled, the result arrays are empty.
				// Permissions granted (CALL_PHONE).
				if (grantResults.length > 0
						&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {

					Log.i( "POM1","Permission granted!");
					Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show();

					startFileExplorerActivity(openMode);
					//this.doBrowseFile();
				}
				// Cancelled or denied.
				else {
					Log.i("POM1","Permission denied!");
					Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show();
				}
				break;
			}
		}
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