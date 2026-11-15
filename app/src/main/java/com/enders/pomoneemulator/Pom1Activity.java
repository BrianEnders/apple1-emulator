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
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.OpenableColumns;
import android.util.Log;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;

import com.enders.pomoneemulator.asm.AsmProgram;
import com.enders.pomoneemulator.asm.Assembler;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.InputStream;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
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
	private ConsentInformation consentInformation;
	private final AtomicBoolean mobileAdsInitStarted = new AtomicBoolean(false);
	private volatile boolean mobileAdsReady;

	static final int MODE_TERMINAL = 0, MODE_DEBUG = 1, MODE_ASM = 2;
	private static final String[] MODE_NAMES = {"TERMINAL", "DEBUG", "ASM"};
	private static final String ASM_FILE = "program.asm";
	/** Woz Monitor GETLINE: where an RTS from an assembled program lands. */
	private static final int WOZ_GETLINE = 0xFF1F;

	private int mode = MODE_TERMINAL;
	/** True while the debugger holds the CPU paused; app pause/resume must not restart it. */
	private boolean cpuHeld;
	private AsmProgram asmProgram;
	private AsmPanel asmPanel;
	private DebugPanel debugPanel;
	private final TextView[] modeTabs = new TextView[3];

	private final ActivityResultLauncher<String[]> openSourceLauncher =
			registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importSourceFrom);
	private final ActivityResultLauncher<String> createSourceLauncher =
			registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), this::exportSourceTo);

	private final ActivityResultLauncher<String[]> openDocumentLauncher =
			registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFileChosen);
	private final ActivityResultLauncher<String> createDocumentLauncher =
			registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"), this::onFileChosen);

	@Override
    public void onCreate(Bundle savedInstanceState) {
		EdgeToEdge.enable(this,
				SystemBarStyle.dark(Color.TRANSPARENT),
				SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        preferences = getPreferences(MODE_PRIVATE);

		this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		applySystemBarInsets();
		getOnBackPressedDispatcher().addCallback(this, backCallback);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
			vibrator = vibratorManager.getDefaultVibrator();
		} else {
			vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		}
		pia6820 = new Pia6820(preferences);
		memory = new Memory(this, pia6820, preferences);
		m6502 = new M6502(memory, preferences);

		screen = new Screen(this.getApplicationContext(), pia6820, preferences);

		keyboard = new Keyboard(pia6820, screen);

		asmProgram = loadAsmProgram();
		m6502.setBreakListener(pc -> runOnUiThread(() -> {
			m6502.pause();
			cpuHeld = true;
			setMode(MODE_DEBUG);
			if (debugPanel != null)
				debugPanel.onBreakpoint(pc);
		}));
		mode = preferences.getInt("mode", MODE_TERMINAL);

		//screenContainer = (LinearLayout) findViewById(R.id.screen_container);
		//screenContainer.removeAllViews();
		//screenContainer.addView(screen);
        initGame();
		requestAdConsent();
	}

	/**
	 * The app draws edge-to-edge (required when targeting API 35+), so pad the
	 * content area to keep the screen and keyboard clear of the system bars,
	 * display cutouts and the on-screen IME. The content view survives every
	 * setContentView() call, so this only needs to be set up once.
	 */
	private void applySystemBarInsets() {
		View content = findViewById(android.R.id.content);
		ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
			Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
					| WindowInsetsCompat.Type.displayCutout()
					| WindowInsetsCompat.Type.ime());
			// AppCompat's action bar overlays the content when drawing edge-to-edge;
			// the top inset it hands us already includes the action bar height.
			v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
			return WindowInsetsCompat.CONSUMED;
		});
	}

	@Override
	public void setContentView(int layoutResID) {
		super.setContentView(layoutResID);
		// The system bars sit over the content's padding, so they show the
		// content background: black on the emulator screen (light icons), the
		// theme's light window background on the option screens (dark icons).
		boolean emulatorScreen = layoutResID == R.layout.activity_apple_one;
		View content = findViewById(android.R.id.content);
		content.setBackgroundColor(emulatorScreen ? Color.BLACK : Color.TRANSPARENT);
		WindowInsetsControllerCompat controller =
				WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
		controller.setAppearanceLightStatusBars(!emulatorScreen);
		controller.setAppearanceLightNavigationBars(!emulatorScreen);
	}

	private void requestAdConsent() {
		consentInformation = UserMessagingPlatform.getConsentInformation(this);
		ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
		consentInformation.requestConsentInfoUpdate(this, params,
				() -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> {
					if (formError != null)
						Log.w("POM1", "Consent form error: " + formError.getMessage());
					if (consentInformation.canRequestAds())
						initializeMobileAds();
					invalidateOptionsMenu();
				}),
				requestError -> Log.w("POM1", "Consent info update error: " + requestError.getMessage()));

		// Consent from a previous session can be used right away.
		if (consentInformation.canRequestAds())
			initializeMobileAds();
	}

	private void initializeMobileAds() {
		if (!mobileAdsInitStarted.compareAndSet(false, true))
			return;
		new Thread(() -> MobileAds.initialize(this, status -> runOnUiThread(() -> {
			mobileAdsReady = true;
			loadBanner();
		}))).start();
	}

	private boolean isPrivacyOptionsRequired() {
		return consentInformation != null && consentInformation.getPrivacyOptionsRequirementStatus()
				== ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
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

		if (mAdView != null)
			mAdView.destroy();
		mAdView = new AdView(this);
		mAdView.setAdUnitId("ca-app-pub-7298593861312209/3404672430");
		mAdView.setAdSize(AdSize.BANNER);
		adContainerView.addView(mAdView);
		loadBanner();

		int height = getResources().getDisplayMetrics().heightPixels;
		int width = getResources().getDisplayMetrics().widthPixels;

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

		buildModeBar();
		debugPanel = new DebugPanel(this, m6502, memory, new DebugPanel.Host() {
			@Override
			public void pauseCpu() {
				m6502.pause();
				cpuHeld = true;
			}

			@Override
			public void resumeCpu() {
				cpuHeld = false;
				m6502.resume();
			}
		});
		ViewGroup debugContainer = findViewById(R.id.debug_container);
		debugContainer.addView(debugPanel.getView());
		asmPanel = new AsmPanel(this, asmProgram, asmHost);
		ViewGroup asmContainer = findViewById(R.id.asm_container);
		asmContainer.addView(asmPanel.getView());
		setMode(mode);

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
		if (!mobileAdsReady || mAdView == null)
			return;
		mAdView.loadAd(new AdRequest.Builder().build());
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

		menu.findItem(R.id.privacy_options).setVisible(isPrivacyOptionsRequired());
		
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
			cpuHeld = false;
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
			showKeyboard = !showKeyboard;
			item.setTitle(showKeyboard ? "Hide Keyboard" : "Show Keyboard");
			setMode(mode);
			return true;
		}
		if(item.getItemId() == R.id.privacy_options) {
			UserMessagingPlatform.showPrivacyOptionsForm(this, formError -> {
				if (formError != null)
					Toast.makeText(this, formError.getMessage(), Toast.LENGTH_SHORT).show();
			});
			return true;
		}
		if(item.getItemId() == R.id.quit) {
			finish();
			return true;
		}

		return super.onOptionsItemSelected(item);
    }
	private long mLastPress = 0;

	private Toast backToast;

	private void openFile() {
		openDocumentLauncher.launch(new String[] {"*/*"});
	}

	private void createFile() {
		createDocumentLauncher.launch("memory.bin");
	}

	private void onFileChosen(Uri uri) {
		if (uri == null)
			return;
		fileURI = uri;
		EditText editText = (EditText) findViewById(R.id.file_path);
		if (editText != null)
			editText.setText(uri.getPath());
	}

	private final OnBackPressedCallback backCallback = new OnBackPressedCallback(true) {
		@Override
		public void handleOnBackPressed() {
			if (isOptionView) {
				isOptionView = false;
				setContentView(R.layout.activity_apple_one);
				initGame();
				resumeCpu();
				screen.resume();
				return;
			}

			long currentTime = System.currentTimeMillis();
			if (currentTime - mLastPress > 2000) {
				backToast = Toast.makeText(Pom1Activity.this, "Press Back again to Exit", Toast.LENGTH_SHORT);
				backToast.show();
				mLastPress = currentTime;
			} else {
				if (backToast != null)
					backToast.cancel(); // don't leave the toast showing after exit
				finish();
			}
		}
	};

	@Override
    public void onResume() {
		//Lbog.v("POM1", "onResume" );

		super.onResume();
		
    	if (!isOptionView && loaded == true) {
			screenContainer.removeView(screen);
			initGame();
			resumeCpu();
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
		if (debugPanel != null)
			debugPanel.setVisible(false);
		preferences.edit().putInt("mode", mode).apply();
    	
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
		if (fileURI == null) {
			Toast.makeText(this, "File path not set", Toast.LENGTH_SHORT).show();
			return;
		}

		RadioGroup radioGroup = (RadioGroup) findViewById(R.id.file_format);
		
		if (radioGroup.getCheckedRadioButtonId() == R.id.file_format_ascii) {
			try {
				InputStream inputStream =
						getContentResolver().openInputStream(fileURI);
				reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(inputStream)));

				CheckBox checkBox = (CheckBox) findViewById(R.id.simulate_keybaord_input);
				Log.v("POM1","isFileOpen: "+screen.isInputFileOpen());
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
								resumeCpu();
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
			
			Cursor returnCursor =
					getContentResolver().query(fileURI, null, null, null, null);
			int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
			returnCursor.moveToFirst();

			int size = (int) returnCursor.getLong(sizeIndex);
			returnCursor.close();

			if (size > 65536 || start + size - 1 > 65535) {
				Toast.makeText(this, "File size too large", Toast.LENGTH_SHORT).show();
				return;
			}else{
				Toast.makeText(this, "File size "+size, Toast.LENGTH_SHORT).show();

			}

			try (InputStream fis = getContentResolver().openInputStream(fileURI)) {
				byte[] fbrut = new byte[size];
				int read = 0;
				while (read < size) {
					int n = fis.read(fbrut, read, size - read);
					if (n < 0)
						break;
					read += n;
				}
				memory.setMemory(fbrut, start, read);
				Toast.makeText(this, "Successfully loaded \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
			} catch (IOException e) {
				e.printStackTrace();
				Toast.makeText(this, "Failed to load \"" + fileURI.getPath() + "\"", Toast.LENGTH_SHORT).show();
			}
		}

		isOptionView = false;
		setContentView(R.layout.activity_apple_one);
		initGame();
		resumeCpu();
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
		resumeCpu();
		screen.resume();

		screen.closeInputFile();

		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(screen.getWindowToken(), 0);
	}

	// ------------------------------------------------------------ TERMINAL / DEBUG / ASM

	private void resumeCpu() {
		if (!cpuHeld)
			m6502.resume();
	}

	private void buildModeBar() {
		LinearLayout bar = findViewById(R.id.mode_bar);
		bar.removeAllViews();
		bar.setBackgroundColor(UiKit.PANEL);
		int h = UiKit.dp(this, 34);
		for (int i = 0; i < MODE_NAMES.length; i++) {
			final int m = i;
			TextView tab = UiKit.key(this, MODE_NAMES[i], UiKit.PANEL, UiKit.DIM, v -> setMode(m));
			tab.setTextSize(13);
			UiKit.addWeighted(bar, tab, 1, h);
			modeTabs[i] = tab;
		}
	}

	private void setMode(int newMode) {
		if (mode == MODE_ASM && newMode != MODE_ASM && asmPanel != null)
			asmPanel.onHidden();
		mode = newMode;
		if (screenContainer == null || isOptionView)
			return;
		for (int i = 0; i < modeTabs.length; i++) {
			if (modeTabs[i] == null)
				continue;
			boolean selected = i == mode;
			modeTabs[i].setTextColor(selected ? UiKit.GREEN : UiKit.DIM);
			android.graphics.drawable.Drawable bg = modeTabs[i].getBackground();
			if (bg instanceof android.graphics.drawable.RippleDrawable) {
				android.graphics.drawable.Drawable shape = ((android.graphics.drawable.RippleDrawable) bg).getDrawable(0);
				if (shape instanceof android.graphics.drawable.GradientDrawable)
					((android.graphics.drawable.GradientDrawable) shape).setColor(selected ? UiKit.HIGHLIGHT : UiKit.PANEL);
			}
		}
		screenContainer.setVisibility(mode == MODE_ASM ? View.GONE : View.VISIBLE);
		keyboardContainer.setVisibility(mode == MODE_TERMINAL && showKeyboard ? View.VISIBLE : View.GONE);
		findViewById(R.id.debug_container).setVisibility(mode == MODE_DEBUG ? View.VISIBLE : View.GONE);
		findViewById(R.id.asm_container).setVisibility(mode == MODE_ASM ? View.VISIBLE : View.GONE);
		if (debugPanel != null)
			debugPanel.setVisible(mode == MODE_DEBUG);
		if (mode != MODE_ASM) {
			getWindow().getDecorView().clearFocus();
			screen.requestFocus();
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(screen.getWindowToken(), 0);
		}
	}

	private final AsmPanel.Host asmHost = new AsmPanel.Host() {
		@Override
		public void loadAssembled(Assembler.Result result, boolean run) {
			for (Assembler.Line line : result.lines) {
				int end = line.address + line.bytes.length - 1;
				if (line.bytes.length == 0)
					continue;
				if (end > 0xFFFF || end >= 0xFF00) {
					asmPanel.setStatus("Line " + line.number + " would overwrite the Woz Monitor ($FF00-$FFFF)", UiKit.RED);
					return;
				}
				if (line.address <= 0xD013 && end >= 0xD010) {
					asmPanel.setStatus("Line " + line.number + " would write into the I/O ports ($D010-$D013)", UiKit.RED);
					return;
				}
			}
			for (Assembler.Line line : result.lines)
				for (int i = 0; i < line.bytes.length; i++)
					memory.memWrite(line.address + i, line.bytes[i] & 0xFF);

			if (!run) {
				asmPanel.setStatus(String.format(java.util.Locale.US,
						"Loaded %d bytes · in the monitor type %04XR to run", result.byteCount(), result.entry), UiKit.GREEN);
				return;
			}
			cpuHeld = false;
			m6502.runAt(result.entry, WOZ_GETLINE);
			m6502.resume();
			setMode(MODE_TERMINAL);
		}

		@Override
		public void importSource() {
			openSourceLauncher.launch(new String[]{"text/*", "application/octet-stream", "*/*"});
		}

		@Override
		public void exportSource() {
			createSourceLauncher.launch("program.asm");
		}

		@Override
		public void sourceChanged() {
			saveAsmProgram();
		}

		@Override
		public boolean isBreakpoint(int address) {
			return m6502.breakpoints[address & 0xFFFF];
		}

		@Override
		public void toggleBreakpoint(int address) {
			m6502.toggleBreakpoint(address);
		}
	};

	private AsmProgram loadAsmProgram() {
		if (!getFileStreamPath(ASM_FILE).exists())
			return AsmProgram.example();
		AsmProgram program = new AsmProgram();
		try (InputStream in = openFileInput(ASM_FILE)) {
			program.read(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			Log.w("POM1", "Could not read " + ASM_FILE, e);
		}
		return program;
	}

	private void saveAsmProgram() {
		try (OutputStreamWriter out = new OutputStreamWriter(openFileOutput(ASM_FILE, MODE_PRIVATE), StandardCharsets.UTF_8)) {
			asmProgram.write(out);
		} catch (IOException e) {
			Log.w("POM1", "Could not save " + ASM_FILE, e);
		}
	}

	private void importSourceFrom(Uri uri) {
		if (uri == null)
			return;
		try (InputStream in = getContentResolver().openInputStream(uri)) {
			AsmProgram imported = new AsmProgram();
			imported.read(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8));
			asmProgram.clear();
			asmProgram.lines().putAll(imported.lines());
			saveAsmProgram();
			if (asmPanel != null)
				asmPanel.reload();
			Toast.makeText(this, "Imported " + asmProgram.lines().size() + " lines", Toast.LENGTH_SHORT).show();
		} catch (IOException | RuntimeException e) {
			Toast.makeText(this, "Could not import: " + e.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}

	private void exportSourceTo(Uri uri) {
		if (uri == null)
			return;
		try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
			OutputStreamWriter out = new OutputStreamWriter(Objects.requireNonNull(os), StandardCharsets.UTF_8);
			asmProgram.write(out);
			out.flush();
			Toast.makeText(this, "Exported " + asmProgram.lines().size() + " lines", Toast.LENGTH_SHORT).show();
		} catch (IOException | RuntimeException e) {
			Toast.makeText(this, "Could not export: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
