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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class Screen extends SurfaceView implements Runnable, SurfaceHolder.Callback {
	private Context context;
	private Bitmap bgimage;
    private byte[] charac = new byte[1024], screenTbl = new byte[960];
	private int indexX, indexY, terminalSpeed, columns, width, height;
	private int scale;
	private boolean scanlines;
	private long lastTime; 
	private boolean blinkCursor, blockCursor;
	private Pia6820 pia6820;
	private Thread thread;
	private boolean clearCursor;
	private long lastBlinkTime;
	private volatile boolean running;
	private SurfaceHolder holder;
	private Canvas canvas;
	private Rect rect;
	private Paint paint;
	private BufferedReader reader;
	private String filePath;
	private char[] buffer = new char[1024];
	private int[] bufferI;
	private int i, length;
	private Bitmap bufferImage;
	private Rect src, bgsrc;
	private Rect dest, bgdest;

	private final Matrix mMatrix = new Matrix();

//	public Screen(Context context, AttributeSet attrs) {
//		super(context, attrs);
//
//		this.context = context;
//		holder = getHolder();
//		holder.addCallback(this);
//
//		setFocusable(true);
//		setFocusableInTouchMode(true);
//		requestFocus();
//	}

	public Screen(Context context, Pia6820 pia6820, SharedPreferences preferences){
		super(context);

		this.context = context;
		holder = getHolder();
		holder.addCallback(this);

		loadCharMap();
		this.pia6820 = pia6820;
		indexX = preferences.getInt("indexX", 0);
		indexY = preferences.getInt("indexY", 0);
		terminalSpeed = preferences.getInt("terminalSpeed", 60);
		scanlines = preferences.getBoolean("scanlines", false);
		blinkCursor = preferences.getBoolean("blinkCursor", true);
		blockCursor = preferences.getBoolean("blockCursor", false);

		try {
			InputStream open = context.getAssets().open("bg.png");
			bgimage = BitmapFactory.decodeStream(open);
			bgsrc = new Rect(0, 0, bgimage.getWidth(), bgimage.getHeight());

		} catch (IOException e) {
			e.printStackTrace();
		}

		if (context.getFileStreamPath("screenTbl.bin").exists()) {
			FileInputStream fis = null;

			try {
				fis = context.openFileInput("screenTbl.bin");
				fis.read(screenTbl);
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}

		rect = new Rect();
		paint = new Paint();

		paint.setFilterBitmap(true);
		paint.setAntiAlias(true);
		setFocusable(true);
		setFocusableInTouchMode(true);
		requestFocus();
	}
//
//	public Screen(Context context, Pia6820 pia6820, SharedPreferences preferences) {
//
//		super(context);
//		this.pia6820 = pia6820;
//		loadCharMap();
//		indexX = preferences.getInt("indexX", 0);
//		indexY = preferences.getInt("indexY", 0);
//		terminalSpeed = preferences.getInt("terminalSpeed", 60);
//		scanlines = preferences.getBoolean("scanlines", false);
//		blinkCursor = preferences.getBoolean("blinkCursor", true);
//		blockCursor = preferences.getBoolean("blockCursor", false);
//
//        try {
//            InputStream open = context.getAssets().open("bg_front.png");
//            bgimage = BitmapFactory.decodeStream(open);
//            bgsrc = new Rect(0, 0, bgimage.getWidth(), bgimage.getHeight());
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//
//
//        if (context.getFileStreamPath("screenTbl.bin").exists()) {
//			FileInputStream fis = null;
//
//			try {
//				fis = context.openFileInput("screenTbl.bin");
//				fis.read(screenTbl);
//			} catch (IOException e) {
//				e.printStackTrace();
//			} finally {
//				if (fis != null) {
//					try {
//						fis.close();
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
//				}
//			}
//		}
//
//		holder = getHolder();
//		holder.addCallback(this);
//		rect = new Rect();
//		paint = new Paint();
//
//		paint.setFilterBitmap(true);
//		paint.setAntiAlias(true);
//		//WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
//		//width = wm.getDefaultDisplay().getWidth();
//		//height = wm.getDefaultDisplay().getHeight();
//	}
//
	public void saveState(Editor editor) {
		editor.putInt("indexX", indexX);
		editor.putInt("indexY", indexY);
		editor.putInt("pixelSize", 1);
		editor.putInt("terminalSpeed", terminalSpeed);
		editor.putBoolean("scanlines", scanlines);
		editor.putBoolean("blinkCursor", blinkCursor);
		editor.putBoolean("blockCursor", blockCursor);
		
		FileOutputStream fos = null;
		
		try {
			fos = getContext().openFileOutput("screenTbl.bin", Context.MODE_PRIVATE);
			fos.write(screenTbl);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fos != null) {
				try {
					fos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public void resume() {
		if (thread == null) {
			running = true;
			thread = new Thread(this);
			lastTime = System.currentTimeMillis();
			thread.start();
		}
	}
	
	public void pause() {
		if (thread != null) {
			running = false;
		
			while (true) {
				try {
					thread.join();
					break;
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		
			thread = null;
		}
	}
	
	public void run() {
		while (!holder.getSurface().isValid());
		
		redrawScreen();
		
		int tmp;
		
		while (running) {
			if (pia6820.readKbdCr() == 0x27 && reader != null) {
				if (i < length) {
					tmp = buffer[i++];
					
					if (tmp >= 0x61 && tmp <= 0x7A)
						tmp &= 0x5F;
					else if (tmp == 0x0D)
						i++;
					else if (tmp == 0x0A)
						tmp = 0x0D;
					
					if (tmp < 0x60) {
						pia6820.writeKbd(tmp | 0x80);
						pia6820.writeKbdCr(0xA7);
					}
				} else {
					i = 0;
					
					try {
						length = reader.read(buffer);
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					if (length == -1) {
						closeInputFile();
						post(new Runnable() {
							public void run() {
								Toast.makeText(getContext(), "Successfully loaded \"" + filePath + "\"", Toast.LENGTH_SHORT).show();
							}	
						});
					}
				}
			}
			
			updateScreen();
		}
	}
	
	private void loadCharMap() {
		InputStream is = null;
		
		try {
			is = getContext().getAssets().open("rom/charmap.rom");
			is.read(charac);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	// char res 5x8
	// screen chars 40x24
	// res 200x192
	public void setPixelSize(int pz) {

		int ori = getResources().getConfiguration().orientation;
		
		if(ori == Configuration.ORIENTATION_PORTRAIT)
		{
			if(width > 280)
			{
				columns = 40;
				scale = (width)/(280);
			}else{
				scale = 1;
				columns = width < 280 ? width / (7) : 40;
			}
		}else{
			scale = 1;
			columns = width < 280 ? width / (7) : 40;
		}

		scale = 2;
	}

	public int getPixelSize() {
		return 1;
	}

	public void setScanlines(boolean scanlines) {
		this.scanlines = scanlines;
	}

	public boolean getScanlines() {
		return scanlines;
	}

	public void setTerminalSpeed(int terminalSpeed) {
		this.terminalSpeed = terminalSpeed;
	}

	public int getTerminalSpeed() {
		return terminalSpeed;
	}

	private void synchronizeOutput() {
		int sleepMillis = (int) ((1000 / terminalSpeed) - (System.currentTimeMillis() - lastTime));

		if (sleepMillis > 0) {
			try {
				Thread.sleep(sleepMillis);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		lastTime = System.currentTimeMillis();
	}

	private void newLine() {
		System.arraycopy(screenTbl, 40, screenTbl, 0, 40 * 23);
		Arrays.fill(screenTbl, 40 * 23, 40 * 24, (byte) 0);
	}

	private void outputDsp(int dsp) {
		dsp &= 0x7F;

		int tmp = dsp;

		if (dsp >= 0x60 && dsp <= 0x7F)
			tmp &= 0x5F;

		switch (tmp) {
		case 0x0D:
			indexX = 0;
			indexY++;
			break;
		default:
			if (tmp >= 0x20 && tmp <= 0x5F) {
				screenTbl[indexY * 40 + indexX] = (byte) tmp;
				indexX++;
			}
			break;
		}

		if (indexX == columns) {
			indexX = 0;
			indexY++;
		}
		if (indexY == 24) {
			newLine();
			indexY--;
		}

		pia6820.writeDsp(dsp);
	}

	public void drawCharac(int xPosition, int yPosition, int r, int g, int b, int characNumber)
	{
		for (int k = 0; k < 8; k++)
		{
			for (int l = 1; l < 8; l++)
			{
				if ((charac[characNumber * 8 + k] & (0x01 << l)) != 0)
				{
					int x = (int) (xPosition + l);
                    int y = (int) (yPosition + k);

					bufferI[bufferImage.getWidth() * y*2 + x*2] = Color.rgb(r, g, b);
					bufferI[bufferImage.getWidth() * y*2 + x*2 + 1] = Color.rgb(r, g, b);
//
					//bufferI[(bufferImage.getWidth() * (y*2+1)) + x*2] = Color.rgb(r, g, b);
					//bufferI[(bufferImage.getWidth() * (y*2+1)) + x*2 + 1] = Color.rgb(r, g, b);

				}
			}
		}
	}

	public void setBlinkCursor(boolean blinkCursor) {
		this.blinkCursor = blinkCursor;
	}

	public boolean getBlinkCursor() {
		return blinkCursor;
	}

	public void setBlockCursor(boolean blockCursor) {
		this.blockCursor = blockCursor;
	}

	public boolean getBlockCursor() {
		return blockCursor;
	}

	private void drawBlinkingCursor() {
		if ((System.currentTimeMillis() - lastBlinkTime) > 500) {
			lastBlinkTime = System.currentTimeMillis();

			//canvas = holder.lockCanvas();


			//canvas.drawBitmap(bufferImage, src, dest, paint);
			holder.unlockCanvasAndPost(canvas);

			clearCursor = !clearCursor;
		}
	}

	public void redrawScreen() {
		int xPosition, yPosition;
				
		canvas = holder.lockCanvas();
		canvas.drawARGB(255, 0, 0, 0);

		//canvas.drawARGB(255, 242, 227, 198);

		//bufferImage = Bitmap.createBitmap(40*7*2+2, 24*8*2, Bitmap.Config.ARGB_8888);

		if(bufferImage != null)
		{
			for (int x = 0; x < bufferImage.getWidth() * bufferImage.getHeight(); x++)
			{
				if(bufferI != null)
					bufferI[x] = 0;
			}
		}
		
		for (int i = 0; i < columns; i++)
		{
			for (int j = 0; j < 24; j++)
			{
				xPosition = i*7;
				yPosition = j*8;
				
				drawCharac(xPosition, yPosition, 0, 255, 0, screenTbl[j * 40 + i]);
			}
		}
		
		if(blinkCursor)
		{
			if ((System.currentTimeMillis() - lastBlinkTime) > 500) {
				lastBlinkTime = System.currentTimeMillis();
				
				int indexXPosition = indexX*7;
				int indexYPosition = indexY*8;
							
				clearCursor = !clearCursor;
			}
			
			if (!clearCursor) {
				drawCharac((int)(indexX*7), (int)(indexY * 8), 0, 255, 0, blockCursor ? 0x01 : 0x40);
			}
		}
	
		if(bufferImage != null && src != null && dest != null)
		{




			//Bitmap fish = fisheye(bufferImage, 0.5f);

			//canvas.drawBitmap(bufferImage,0,0,paint);

			bufferImage.setPixels(bufferI, 0, bufferImage.getWidth(), 0, 0,
					bufferImage.getWidth(), bufferImage.getHeight());
            canvas.drawBitmap(bufferImage, src, dest, paint);

			canvas.drawBitmap(bgimage, bgsrc, bgdest, paint);
			//canvas.drawBitmapMesh(bufferImage, 7, 5, v, 0, null, 0, paint);
		}
							
		holder.unlockCanvasAndPost(canvas);
	}
	
	public void reset() {
		indexX = indexY = 0;

		Arrays.fill(screenTbl, (byte) 0);
		
		lastTime = System.currentTimeMillis();
		
		redrawScreen();
	}
	
	private void updateScreen() {
		int dsp = pia6820.readDsp();

		if ((dsp & 0x80) != 0) {
			outputDsp(dsp);
			synchronizeOutput();
		} //else if (blinkCursor)
			//drawBlinkingCursor();
		
		redrawScreen();
	}
	
	@Override
	public boolean onKeyPreIme(int keyCode, KeyEvent event) {
		
		if (keyCode == KeyEvent.KEYCODE_BACK) {
    		InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    		imm.hideSoftInputFromWindow(getWindowToken(), 0);
		}
		
		return super.onKeyPreIme(keyCode, event);
	}
	
	public void setInputFile(BufferedReader reader, String filePath) {
		this.reader = reader;
		this.filePath = filePath;
		i = length = 0;
	}
	
	public void closeInputFile() {
		if (reader != null) {
			try {
				reader.close();
				reader = null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public boolean isInputFileOpen() {
		return (reader != null);
	}
	
	public String getInputFilePath() {
		return filePath;
	}
	
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		
		Log.v("POM1", "GO");
		this.width = width;
		this.height = height; 
		
		bufferImage = Bitmap.createBitmap((40*7)*2, 24*8*2, Bitmap.Config.ARGB_8888);
		bufferI = new int[bufferImage.getWidth()*bufferImage.getHeight()];
		int ori = getResources().getConfiguration().orientation;
	
		if(ori == Configuration.ORIENTATION_PORTRAIT)
		{
			columns = 40;
			scale = (width)/280;
		}else{
			scale = (height)/(24*8);
			columns = 40;
		}
		if(scale==0)scale = 1;
		//scale = 2;
		
		src = new Rect(0, 0, bufferImage.getWidth(), bufferImage.getHeight());

		int xLeft = width/2-(280*scale/2);
        int xRight = xLeft+280*scale;

        int yTop, yBottom;
        int offset = 0;
        if(ori == Configuration.ORIENTATION_PORTRAIT && height > bgimage.getHeight()/2*scale)
        {
			yTop = height/2-192*scale/2;
            yBottom = yTop+192*scale;
        }else{
            //offset = (bgimage.getHeight()*scale-height)/2;
            //yTop = (bgimage.getHeight()*2-bufferImage.getHeight())*scale/2;//-offset;
            //yBottom = (int)(24 * scale * 8)+(bgimage.getHeight()*2-bufferImage.getHeight())*scale/2;//-offset;
			yTop = height/2-192*scale/2;
			yBottom = yTop+192*scale;
        }

		dest = new Rect(xLeft, yTop, xRight, yBottom);
		//dest = new Rect(0, 0, (int)(bufferImage.getWidth()*scale), (int)(bufferImage.getHeight()*scale));

		int xLeft2 = width/2-bgimage.getWidth()/4*scale;
		int yTop2 = yTop-140/2*scale;
		Log.v("POM1", "windowRect.top - " +(width/2-bgimage.getWidth()/4*scale)+" "+yTop2+" "+(yTop2+bgimage.getHeight()/2*scale));

        bgdest = new Rect(width/2-bgimage.getWidth()/4*scale, yTop2, width/2+bgimage.getWidth()/4*scale, yTop2+bgimage.getHeight()/2*scale);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// TODO Auto-generated method stub

		Log.v("POM1", "surfaceCreated");
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO Auto-generated method stub
		Log.v("POM1", "surfaceDestroyed");

	}

	public static Bitmap fisheye(Bitmap srcimage, float fac) {
		/*
		 *    Fish eye effect
		 *    tejopa, 2012-04-29
		 *    http://popscan.blogspot.com
		 *    http://www.eemeli.de
		 */

		// get image pixels
		double w = srcimage.getWidth();
		double h = srcimage.getHeight();
		int[] srcpixels = new int[(int)(w*h)];
		srcimage.getPixels(srcpixels, 0, (int)w, 0, 0, (int)w, (int)h);

		Bitmap resultimage = srcimage.copy(srcimage.getConfig(), true);

		// create the result data
		int[] dstpixels = new int[(int)(w*h)];
		// for each row
		for (int y=0;y<h;y++) {
			// normalize y coordinate to -1 ... 1
			double ny = ((2*y)/h)-1;
			// pre calculate ny*ny
			double ny2 = ny*ny;
			// for each column
			for (int x=0;x<w;x++) {
				// preset to black
				dstpixels[(int)(y*w+x)] = 0;

				// normalize x coordinate to -1 ... 1
				double nx = ((2*x)/w)-1;
				// pre calculate nx*nx
				double nx2 = nx*nx;
				// calculate distance from center (0,0)
				// this will include circle or ellipse shape portion
				// of the image, depending on image dimensions
				// you can experiment with images with different dimensions
				double r = Math.sqrt(nx2+ny2);
				// discard pixels outside from circle!
				if (0.0<=r&&r<=1.0) {
					double nr = Math.sqrt(1.0-r*r);
					// new distance is between 0 ... 1
					nr = (r + (1.0-nr)) / 2.0;
					// discard radius greater than 1.0
					if (nr<=1.0) {
						// calculate the angle for polar coordinates
						double theta = Math.atan2(ny,nx);
						// calculate new x position with new distance in same angle
						double nxn = nr*Math.cos(theta);
						// calculate new y position with new distance in same angle
						double nyn = nr*Math.sin(theta);
						// map from -1 ... 1 to image coordinates
						int x2 = (int)(((nxn+1)*w)/2.0);
						// map from -1 ... 1 to image coordinates
						int y2 = (int)(((nyn+1)*h)/2.0);
						// find (x2,y2) position from source pixels

						int srcpos = (int)(y2*w+x2);
						// make sure that position stays within arrays
						if (srcpos>=0 & srcpos < w*h) {
							// get new pixel (x2,y2) and put it to target array at (x,y)
							dstpixels[(int)(y*w+x)] = srcpixels[srcpos];
						}
					}
				}
			}

		}

		resultimage.setPixels(dstpixels, 0, (int)w, 0, 0, (int)w, (int)h);
		//return result pixels
		return resultimage;
	}
}