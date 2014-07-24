package com.calsignlabs.apde.tool;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
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
	
	private EditText r, g, b, h, s, v;
	private TextView hex;
	private int rv, gv, bv;
	private float hv, sv, vv;
	
	private GradientDrawable selectionBackground;
	
	private boolean FLAG_EDITING_VALUES = false;
	private boolean FLAG_FIRST_TIME = true;
	
	@Override
	public void init(APDE context) {
		this.context = context;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.color_selector);
	}
	
	@SuppressLint("InlinedApi")
	@Override
	public void run() {
		if(dialog == null) {
			AlertDialog.Builder builder = new AlertDialog.Builder(context.getEditor());
			builder.setTitle(R.string.color_selector);
			
			if(android.os.Build.VERSION.SDK_INT >= 11) {
				layout = (LinearLayout) View.inflate(new ContextThemeWrapper(context, android.R.style.Theme_Holo_Dialog), R.layout.color_selector, null);
			} else {
				layout = (LinearLayout) View.inflate(new ContextThemeWrapper(context, android.R.style.Theme_Dialog), R.layout.color_selector, null);
			}
				
			colorSquare = (ColorSquare) layout.findViewById(R.id.color_square);
			hueStrip = (HueStrip) layout.findViewById(R.id.hue_strip);
			
			r = (EditText) layout.findViewById(R.id.red);
			g = (EditText) layout.findViewById(R.id.green);
			b = (EditText) layout.findViewById(R.id.blue);
			
			h = (EditText) layout.findViewById(R.id.hue);
			s = (EditText) layout.findViewById(R.id.saturation);
			v = (EditText) layout.findViewById(R.id.value);
			
			hex = (TextView) layout.findViewById(R.id.hex);
			
			setHSB(360.0f, 0.0f, 1.0f);
			
			//Make the hue strip the same height as the color square
			layout.requestLayout();
			layout.post(new Runnable() {
				public void run() {
					LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) hueStrip.getLayoutParams();
					params.height = colorSquare.getWidth();
					hueStrip.setLayoutParams(params);
					
					layout.requestLayout();
					
					hueStrip.FLAG_REFRESH_GRADIENT = true;
				}
			});
			
			hueStrip.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View view, MotionEvent event) {
					setHue((1.0f - Math.min(Math.max(0.0f, (event.getY() - 12.0f) / (hueStrip.getHeight() - 24.0f)), 1.0f)) * 360);
					
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
					if(android.os.Build.VERSION.SDK_INT >= 11) {
						((android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE))
								.setPrimaryClip(android.content.ClipData.newPlainText(context.getResources().getString(R.string.hex_color), hex.getText()));
					} else {
						((android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setText(hex.getText());
					}
					
					Toast.makeText(context.getEditor(), R.string.hex_color_copied_to_clipboard, Toast.LENGTH_SHORT).show();
				}
			});
			
			//Detect changes so that we can update everything else
			//Yes, this is really messy - but it's the best we have right now
			
			r.addTextChangedListener(new TextWatcher() {
				@Override
				public void afterTextChanged(Editable text) {
					if(!FLAG_EDITING_VALUES) {
						setRed(text.toString().length() == 0 ? 0 : Integer.parseInt(text.toString()));
					}
				}
				
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}
			});
			
			g.addTextChangedListener(new TextWatcher() {
				@Override
				public void afterTextChanged(Editable text) {
					if(!FLAG_EDITING_VALUES) {
						setGreen(text.toString().length() == 0 ? 0 : Integer.parseInt(text.toString()));
					}
				}
				
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}
			});
			
			b.addTextChangedListener(new TextWatcher() {
				@Override
				public void afterTextChanged(Editable text) {
					if(!FLAG_EDITING_VALUES) {
						setBlue(text.toString().length() == 0 ? 0 : Integer.parseInt(text.toString()));
					}
				}
				
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}
			});
			
			h.addTextChangedListener(new TextWatcher() {
				@Override
				public void afterTextChanged(Editable text) {
					if(!FLAG_EDITING_VALUES) {
						setHue(text.toString().length() == 0 ? 0 : Integer.parseInt(text.toString()));
					}
				}
				
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}
			});
			
			s.addTextChangedListener(new TextWatcher() {
				@Override
				public void afterTextChanged(Editable text) {
					if(!FLAG_EDITING_VALUES) {
						setSaturation(text.toString().length() == 0 ? 0 : Integer.parseInt(text.toString()) / 100.0f);
					}
				}
				
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}
			});
			
			v.addTextChangedListener(new TextWatcher() {
				@Override
				public void afterTextChanged(Editable text) {
					if(!FLAG_EDITING_VALUES) {
						setBrightness(text.toString().length() == 0 ? 0 : Integer.parseInt(text.toString()) / 100.0f);
					}
				}
				
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {}
			});
			
			builder.setView(layout);
			dialog = builder.create();
		}
		
		dialog.show();
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return null;
	}
	
	@Override
	public boolean showInToolsMenu() {
		return true;
	}
	
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		return false;
	}
	
	public void setRGB(int red, int green, int blue) {
		red = Math.min(Math.max(red, 0), 255);
		green = Math.min(Math.max(green, 0), 255);
		blue = Math.min(Math.max(blue, 0), 255);
		
		float[] hsb = new float[3];
		Color.RGBToHSV(red, green, blue, hsb);
		
		setHSB(hsb[0], hsb[1], hsb[2]);
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
		
		hv = hue;
		sv = saturation;
		vv = brightness;
		
		rv = Color.red(color);
		gv = Color.green(color);
		bv = Color.blue(color);
		
		FLAG_EDITING_VALUES = true;
		
		//Yes, this is really ugly
		
		if(!(r.getText().toString().equals(Integer.toString(rv)) || (r.getText().toString().equals("") && r.isFocused())) || FLAG_FIRST_TIME)
			r.setText(Integer.toString(rv));
		if(!(g.getText().toString().equals(Integer.toString(gv)) || (g.getText().toString().equals("") && g.isFocused())) || FLAG_FIRST_TIME)
			g.setText(Integer.toString(gv));
		if(!(b.getText().toString().equals(Integer.toString(bv)) || (b.getText().toString().equals("") && b.isFocused())) || FLAG_FIRST_TIME)
			b.setText(Integer.toString(bv));
		
		if(!(h.getText().toString().equals(Integer.toString((int) hv)) || (h.getText().toString().equals("") && h.isFocused())) || FLAG_FIRST_TIME)
			h.setText(Integer.toString((int) hv));
		if(!(s.getText().toString().equals(Integer.toString((int) (sv * 100))) || (s.getText().toString().equals("") && s.isFocused())) || FLAG_FIRST_TIME)
			s.setText(Integer.toString((int) (sv * 100)));
		if(!(v.getText().toString().equals(Integer.toString((int) (vv * 100))) || (v.getText().toString().equals("") && v.isFocused())) || FLAG_FIRST_TIME)
			v.setText(Integer.toString((int) (vv * 100)));
		
		FLAG_EDITING_VALUES = false;
		FLAG_FIRST_TIME = false;
		
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
	
	public void setRed(int red) {
		setRGB(red, gv, bv);
	}
	
	public void setGreen(int green) {
		setRGB(rv, green, bv);
	}
	
	public void setBlue(int blue) {
		setRGB(rv, gv, blue);
	}
	
	public void setHue(float hue) {
		setHSB(hue, sv, vv);
	}
	
	public void setSaturation(float saturation) {
		setHSB(hv, saturation, vv);
	}
	
	public void setBrightness(float brightness) {
		setHSB(hv, sv, brightness);
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
			if(android.os.Build.VERSION.SDK_INT >= 11) {
				//Fixes problems on Android 4.0+ that come about with hardware acceleration
				//More information:
				//StackOverflow: http://stackoverflow.com/questions/17228717/compose-two-shaders-color-picker
				setLayerType(View.LAYER_TYPE_SOFTWARE, null);
			}
			
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
				boxPaint.setStrokeWidth(4);
			}
			
			boxPaint.setColor(y > getMeasuredHeight() / 2 ? Color.WHITE : Color.BLACK);
			
			canvas.drawRect(x - 10, y - 10, x + 10, y + 10, boxPaint);
		}
		
		@Override
		public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			//Make this into an actual square
			super.onMeasure(widthMeasureSpec, widthMeasureSpec);
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
				gradient = new LinearGradient(0.0f, 12.0f, 0.0f, getMeasuredHeight() - 12.0f, new int[] {0xFFFF0000, 0xFFFF00FF, 0xFF0000FF, 0xFF00FFFF, 0xFF00FF00, 0xFFFFFF00, 0xFFFF0000}, null, Shader.TileMode.CLAMP);
				
				paint.setShader(gradient);
			}
			
			canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
			
			if(boxPaint == null) {
				boxPaint = new Paint();
				boxPaint.setStyle(Style.STROKE);
				boxPaint.setStrokeWidth(4);
				boxPaint.setColor(Color.WHITE);
			}
			
			if(FLAG_REFRESH_HUE) {
				//Keep the box displayable
				float drawHue = Math.min(Math.max((1.0f - hue) * getMeasuredHeight(), 12.0f), getMeasuredHeight() - 12.0f);
				
				canvas.drawRect(2.0f, drawHue - 10.0f, getMeasuredWidth() - 2.0f, drawHue + 10.0f, boxPaint);
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
}