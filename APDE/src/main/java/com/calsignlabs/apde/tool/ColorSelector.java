package com.calsignlabs.apde.tool;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import androidx.appcompat.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.R;

/**
 * Displays a color selector
 */
public class ColorSelector implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.ColorSelector";
	
	private APDE context;
	
	private AlertDialog dialog;
	
	private LinearLayout layout;
	private ColorSquare colorSquare;
	private HueStrip hueStrip;
	
	private ValueEditText[] rgb = new ValueEditText[3];
	private ValueEditText[] hsv = new ValueEditText[3];
	private TextView hex;
	
	private GradientDrawable selectionBackground;
	
	private static float DIP;
	
	@Override
	public void init(APDE context) {
		this.context = context;
		
		// "0.5f" scaling factor is neccessary because the graphics were originally created for a higher-resolution screen...
		DIP = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0.5f, context.getResources().getDisplayMetrics());
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.tool_color_selector);
	}
	
	@SuppressLint("InlinedApi")
	@Override
	public void run() {
		if(dialog == null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());
			builder.setTitle(R.string.tool_color_selector);
			
			layout = (LinearLayout) View.inflate(new ContextThemeWrapper(context, R.style.Theme_AppCompat_Dialog), R.layout.color_selector, null);
				
			colorSquare = (ColorSquare) layout.findViewById(R.id.color_square);
			hueStrip = (HueStrip) layout.findViewById(R.id.hue_strip);
			
			rgb[0] = (ValueEditText) layout.findViewById(R.id.red);
			rgb[1] = (ValueEditText) layout.findViewById(R.id.green);
			rgb[2] = (ValueEditText) layout.findViewById(R.id.blue);
			
			hsv[0] = (ValueEditText) layout.findViewById(R.id.hue);
			hsv[1] = (ValueEditText) layout.findViewById(R.id.saturation);
			hsv[2] = (ValueEditText) layout.findViewById(R.id.value);
			
			rgb[0].setCallback(new ValueEditText.Callback() {
				@Override
				public void onValueChanged(float value) {
					setRed(value);
				}
			});
			
			rgb[1].setCallback(new ValueEditText.Callback() {
				@Override
				public void onValueChanged(float value) {
					setGreen(value);
				}
			});
			
			rgb[2].setCallback(new ValueEditText.Callback() {
				@Override
				public void onValueChanged(float value) {
					setBlue(value);
				}
			});
			
			hsv[0].setCallback(new ValueEditText.Callback() {
				@Override
				public void onValueChanged(float value) {
					setHue(value);
				}
			});
			
			hsv[1].setCallback(new ValueEditText.Callback() {
				@Override
				public void onValueChanged(float value) {
					setSaturation(value);
				}
			});
			
			hsv[2].setCallback(new ValueEditText.Callback() {
				@Override
				public void onValueChanged(float value) {
					setBrightness(value);
				}
			});
			
			hsv[1].setScale(100.0f);
			hsv[2].setScale(100.0f);
			
			hex = (TextView) layout.findViewById(R.id.hex);
			
			setHSB(360.0f, 0.0f, 1.0f);
			
			//Make the hue strip the same height as the color square
			layout.requestLayout();
			layout.post(new Runnable() {
				public void run() {
					LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) hueStrip.getLayoutParams();
					params.height = colorSquare.getWidth();
					if(context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
						params.width = (int) (colorSquare.getWidth() / 5.0);
					}
					hueStrip.setLayoutParams(params);
					
					layout.requestLayout();
					
					hueStrip.FLAG_REFRESH_GRADIENT = true;
				}
			});
			
			hueStrip.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View view, MotionEvent event) {
					setHue((1.0f - Math.min(Math.max(0.0f, (event.getY() - 12.0f * DIP) / (hueStrip.getHeight() - 24.0f * DIP)), 1.0f)) * 360);
					
					return true;
				}
			});
			
			colorSquare.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View view, MotionEvent event) {
					setSaturation(Math.min(Math.max(event.getX() / colorSquare.getWidth(), 0.0f), 1.0f));
					setBrightness(1.0f - Math.min(Math.max(event.getY() / colorSquare.getHeight(), 0.0f), 1.0f));
					
					return true;
				}
			});
			
			ImageButton copyHex = (ImageButton) layout.findViewById(R.id.hex_copy);
			
			copyHex.setOnClickListener(new View.OnClickListener() {
				@SuppressWarnings("deprecation")
				@SuppressLint("NewApi")
				@Override
				public void onClick(View view) {
					((android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE))
							.setPrimaryClip(android.content.ClipData.newPlainText(context.getResources().getString(R.string.tool_color_selector_hex_color), hex.getText()));
					
					Toast.makeText(context.getEditor(), R.string.tool_color_selector_copy_hex_to_clipboard_success, Toast.LENGTH_SHORT).show();
				}
			});
			
			builder.setView(layout);
			dialog = builder.create();
		}
		
		dialog.show();
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return context.getEditor().getKeyBindings().get("color_selector");
	}
	
	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return true;
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
	
	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public void setRGB(float red, float green, float blue) {
		red = Math.min(Math.max(red, 0), 255);
		green = Math.min(Math.max(green, 0), 255);
		blue = Math.min(Math.max(blue, 0), 255);
		
		float[] hsb = new float[3];
		Color.RGBToHSV((int) red, (int) green, (int) blue, hsb);
		
		int color = Color.rgb((int) red, (int) green, (int) blue);
		
		hueStrip.setHue(hsb[0] / 360.0f);
		colorSquare.setHue(hsb[0] / 360.0f);
		colorSquare.setPoint(hsb[1] * colorSquare.getWidth(), (1.0f - hsb[2]) * colorSquare.getHeight());
		
		hsv[0].setValue(hsb[0]);
		hsv[1].setValue(hsb[1]);
		hsv[2].setValue(hsb[2]);
		
		rgb[0].setValue(red);
		rgb[1].setValue(green);
		rgb[2].setValue(blue);
		
		//Hex conversion
		hex.setText(String.format("#%06X", (0xFFFFFF & color)));
		//Make the text readable
		hex.setTextColor(hsb[2] < 0.5 ? Color.WHITE : Color.BLACK);
		
		//Show the selected color
		
		if(selectionBackground == null) {
			selectionBackground = ((GradientDrawable) context.getResources().getDrawable(R.drawable.color_selector_selection_background));
		}
		
		selectionBackground.setColor(color);
		
		if(android.os.Build.VERSION.SDK_INT >= 16) {
			hex.setBackground(selectionBackground);
		} else {
			hex.setBackgroundDrawable(selectionBackground);
		}
	}
	
	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	public void setHSB(float hue, float saturation, float brightness) {
		hue = Math.min(Math.max(hue, 0.0f), 360.0f);
		saturation = Math.min(Math.max(saturation, 0.0f), 1.0f);
		brightness = Math.min(Math.max(brightness, 0.0f), 1.0f);
		
		hueStrip.setHue(hue / 360.0f);
		colorSquare.setHue(hue / 360.0f);
		colorSquare.setPoint(saturation * colorSquare.getWidth(), (1.0f - brightness) * colorSquare.getHeight());
		
		int color = Color.HSVToColor(new float[] {hue, saturation, brightness});
		
		hsv[0].setValue(hue);
		hsv[1].setValue(saturation);
		hsv[2].setValue(brightness);
		
		rgb[0].setValue(Color.red(color));
		rgb[1].setValue(Color.green(color));
		rgb[2].setValue(Color.blue(color));
		
		//Hex conversion
		hex.setText(String.format("#%06X", (0xFFFFFF & color)));
		//Make the text readable
		hex.setTextColor(brightness < 0.5 ? Color.WHITE : Color.BLACK);
		
		//Show the selected color
		
		if(selectionBackground == null) {
			selectionBackground = ((GradientDrawable) context.getResources().getDrawable(R.drawable.color_selector_selection_background));
		}
		
		selectionBackground.setColor(color);
		
		if(android.os.Build.VERSION.SDK_INT >= 16) {
			hex.setBackground(selectionBackground);
		} else {
			hex.setBackgroundDrawable(selectionBackground);
		}
	}
	
	public void setRed(float red) {
		setRGB(red, rgb[1].getValue(), rgb[2].getValue());
	}
	
	public void setGreen(float green) {
		setRGB(rgb[0].getValue(), green, rgb[2].getValue());
	}
	
	public void setBlue(float blue) {
		setRGB(rgb[0].getValue(), rgb[1].getValue(), blue);
	}
	
	public void setHue(float hue) {
		setHSB(hue, hsv[1].getValue(), hsv[2].getValue());
	}
	
	public void setSaturation(float saturation) {
		setHSB(hsv[0].getValue(), saturation, hsv[2].getValue());
	}
	
	public void setBrightness(float brightness) {
		setHSB(hsv[0].getValue(), hsv[1].getValue(), brightness);
	}
	
	public static class ColorSquare extends View {
		private Paint paint;
		private LinearGradient vertical, horizontal;
		private ComposeShader shader;
		
		private float[] color;
		private boolean FLAG_REFRESH_HUE;
		
		private float x, y;
		
		private Paint boxPaint;
		
		public ColorSquare(Context context) {
			super(context);
			
			init();
		}
		
		public ColorSquare(Context context, AttributeSet attrs) {
			super(context, attrs);
			
			init();
		}
		
		public ColorSquare(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
			
			init();
		}
		
		@SuppressLint("NewApi")
		private void init() {
			// Fixes problems on Android 4.0+ that come about with hardware acceleration
			// More information:
			// StackOverflow: http://stackoverflow.com/questions/17228717/compose-two-shaders-color-picker
			setLayerType(View.LAYER_TYPE_SOFTWARE, null);
			
			color = new float[] {0.0f, 1.0f, 1.0f};
			FLAG_REFRESH_HUE = false;
			
			x = 0;
			y = 0;
		}
		
		@Override
		public void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			
			if(paint == null) {
				paint = new Paint();
				vertical = new LinearGradient(0.0f, 0.0f, 0.0f, getMeasuredHeight(), 0xffffffff, 0xff000000, Shader.TileMode.CLAMP);
			}
			
			if(horizontal == null || FLAG_REFRESH_HUE) {
				horizontal = new LinearGradient(0.0f, 0.0f, getMeasuredWidth(), 0.0f, 0xffffffff, Color.HSVToColor(color), Shader.TileMode.CLAMP);
				shader = new ComposeShader(vertical, horizontal, PorterDuff.Mode.MULTIPLY);
				
				paint.setShader(shader);
				
				FLAG_REFRESH_HUE = false;
			}
			
			canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
			
			if(boxPaint == null) {
				boxPaint = new Paint();
				boxPaint.setStyle(Style.STROKE);
				boxPaint.setStrokeWidth(4.0f * DIP);
			}
			
			boxPaint.setColor(y > getMeasuredHeight() / 2 ? Color.WHITE : Color.BLACK);
			
			canvas.drawRect(x - 10.0f * DIP, y - 10.0f * DIP, x + 10.0f * DIP, y + 10.0f * DIP, boxPaint);
		}
		
		@Override
		public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			//Make this into an actual square
			switch(getResources().getConfiguration().orientation) {
			case Configuration.ORIENTATION_LANDSCAPE:
				super.onMeasure(heightMeasureSpec, heightMeasureSpec);
				break;
			case Configuration.ORIENTATION_PORTRAIT:
			default:
				super.onMeasure(widthMeasureSpec, widthMeasureSpec);
				break;
			}
		}
		
		/**
		 * Sets the hue
		 * 
		 * @param hue [0 .. 1]
		 */
		public void setHue(float hue) {
			color[0] = hue * 360.0f;
			FLAG_REFRESH_HUE = true;
			
			invalidate();
		}
		
		public void setPoint(float x, float y) {
			this.x = x;
			this.y = y;
			
			invalidate();
		}
	}
	
	public static class HueStrip extends View {
		private Paint paint;
		private LinearGradient gradient;
		
		private float hue;
		private boolean FLAG_REFRESH_HUE;
		
		public boolean FLAG_REFRESH_GRADIENT;
		
		private Paint boxPaint;
		
		public HueStrip(Context context) {
			super(context);
		}
		
		public HueStrip(Context context, AttributeSet attrs) {
			super(context, attrs);
		}
		
		public HueStrip(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
		}
		
		@Override
		public void onDraw(Canvas canvas) {
			super.onDraw(canvas);
			
			if(paint == null || FLAG_REFRESH_GRADIENT) {
				paint = new Paint();
				gradient = new LinearGradient(0.0f, 12.0f * DIP, 0.0f, getMeasuredHeight() - 12.0f * DIP,
						new int[] {0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00, 0xFFFFFF00, 0xFFFF0000}, null, Shader.TileMode.CLAMP);
				
				paint.setShader(gradient);
			}
			
			canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
			
			if(boxPaint == null) {
				boxPaint = new Paint();
				boxPaint.setStyle(Style.STROKE);
				boxPaint.setStrokeWidth(4.0f * DIP);
				boxPaint.setColor(Color.WHITE);
			}
			
			if(FLAG_REFRESH_HUE) {
				//Keep the box displayable
				float drawHue = Math.min(Math.max((1.0f - hue) * getMeasuredHeight(), 12.0f * DIP), getMeasuredHeight() - 12.0f * DIP);
				
				canvas.drawRect(2.0f * DIP, drawHue - 10.0f * DIP, getMeasuredWidth() - 2.0f * DIP, drawHue + 10.0f * DIP, boxPaint);
			}
		}
		
		/**
		 * Sets the hue
		 * 
		 * @param hue [0 .. 1]
		 */
		public void setHue(float hue) {
			this.hue = hue;
			FLAG_REFRESH_HUE = true;
			
			invalidate();
		}
	}
	
	public static class ValueEditText extends EditText {
		private boolean FLAG_INTERNAL_MODIFICATION;
		private boolean FLAG_MODIFIED;
		private float value;
		private float scale;
		private Callback callback;
		
		public ValueEditText(Context context) {
			super(context);
			
			init();
		}
		
		public ValueEditText(Context context, AttributeSet attrs) {
			super(context, attrs);
			
			init();
		}
		
		public ValueEditText(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
			
			init();
		}
		
		private void init() {
			addTextChangedListener(new TextWatcher() {
				@Override
				public void afterTextChanged(Editable text) {
					if(!FLAG_INTERNAL_MODIFICATION) {
						callback.onValueChanged(text.toString().length() == 0 ? 0.0f : Integer.parseInt(text.toString()) / scale);
					}
				}
				
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}
			});
			
			FLAG_MODIFIED = false;
			scale = 1.0f;
		}
		
		public void setCallback(Callback callback) {
			this.callback = callback;
		}
		
		public void setScale(float scale) {
			this.scale = scale;
		}
		
		public void setValue(float value) {
			this.value = value;
			
			FLAG_INTERNAL_MODIFICATION = true;
			
			int selectionStart = getSelectionStart();
			int selectionEnd = getSelectionEnd();
			
			String text = getText().toString();
			if(!FLAG_MODIFIED																	//Make sure we set the text initially
					|| !((text.equals("") && hasFocus())										//Don't change the text to "0" if it's empty (if it has focus)
					|| (text.length() > 0 && Integer.parseInt(text) == (int) (value * scale)))	//Don't change the text if it will make no difference
					|| (text.length() > 1 && text.startsWith("0"))) {							//Trim starting "0"s
				
				setText(Integer.toString((int) (value * scale)));
				
				//Refresh...
				text = getText().toString();
				
				selectionStart = Math.min(selectionStart, text.length());
				selectionEnd = Math.min(selectionEnd, text.length());
				
				//Keep the selection in the same place
				setSelection(selectionStart, selectionEnd);
			}
			
			FLAG_INTERNAL_MODIFICATION = false;
			FLAG_MODIFIED = true;
		}
		
		public float getValue() {
			return value;
		}
		
		public static interface Callback {
			public void onValueChanged(float value);
		}
	}
}