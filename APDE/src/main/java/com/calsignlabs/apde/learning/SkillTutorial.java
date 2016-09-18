package com.calsignlabs.apde.learning;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.CodeEditText;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.sandbox.SandboxFragment;
import com.calsignlabs.apde.tool.ColorSelector;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import processing.data.XML;

public class SkillTutorial {
	protected ArrayList<Segment> segments;
	
	private String skillTutorialName;
	
	public SkillTutorial(Activity activity, Fragment fragment, String name) {
		segments = new ArrayList<>();
		
		skillTutorialName = name;
		
		try {
			XML xml = new XML(new File(new File(((APDE) activity.getApplication()).getLearningFolder(), "skills"), name + ".xml"));
			
			for (XML child : xml.getChildren()) {
				if (child.getName().equals("segment")) {
					segments.add(new Segment(child, activity, fragment));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public String getName() {
		return skillTutorialName;
	}
	
	public void loadPage(LinearLayout container, final ScrollView scroller, final int page, int previousPage) {
		if (previousPage != -1) {
			segments.get(previousPage).scrollOffset = scroller.getScrollY();
		}
		container.removeAllViews();
		segments.get(page).loadViews(container);
		scroller.scrollTo(0, 0);
		container.requestLayout();
		// The code displays are loaded after, so scroll position isn't accurate
//		container.post(new Runnable() {
//			@Override
//			public void run() {
//				scroller.scrollTo(0, segments.get(page).scrollOffset);
//			}
//		});
	}
	
	public int getPageCount() {
		return segments.size();
	}
	
	public static class Segment {
		protected ArrayList<Component> components;
		protected HashMap<String, CodeDisplayComponent> codeDisplays;
		
		private Activity activity;
		private Fragment fragment;
		
		protected int scrollOffset = 0;
		
		public Segment(XML xml, Activity activity, Fragment fragment) {
			components = new ArrayList<>();
			codeDisplays = new HashMap<>();
			
			this.activity = activity;
			this.fragment = fragment;
			
			for (XML child : xml.getChildren()) {
				if (child.getName().equals(TextComponent.XML_NAME)) components.add(new TextComponent(child, this));
				if (child.getName().equals(ImageComponent.XML_NAME)) components.add(new ImageComponent(child, this));
				if (child.getName().equals(CodeComponent.XML_NAME)) components.add(new CodeComponent(child, this));
				if (child.getName().equals(QuizComponent.XML_NAME)) components.add(new QuizComponent(child, this));
				if (child.getName().equals(ExamplesListComponent.XML_NAME)) components.add(new ExamplesListComponent(child, this));
				
				if (child.getName().equals(CodeDisplayComponent.XML_NAME)) {
					CodeDisplayComponent codeDisplayComponent = new CodeDisplayComponent(child, this);
					
					components.add(codeDisplayComponent);
					codeDisplays.put(codeDisplayComponent.getId(), codeDisplayComponent);
				}
			}
		}
		
		public Activity getContext() {
			return activity;
		}
		
		public Fragment getFragment() {
			return fragment;
		}
		
		public CodeDisplayComponent getCodeDisplayComponent(String id) {
			return codeDisplays.get(id);
		}
		
		public void loadViews(LinearLayout container) {
			boolean first = true;
			for (Component component : components) {
				if (first) {
					first = false;
				} else {
					addSpacer(container);
				}
				
				container.addView(component.getView(container, this));
			}
		}
		
		public int measureHeight() {
			int spacer = (int) dpToPixels(16, getContext());
			
			int availableWidth = getAvailableWidth();
			
			int total = 0;
			boolean first = true;
			for (Component component : components) {
				if (first) {
					first = false;
				} else {
					total += spacer;
				}
				
				total += component.measureHeight(availableWidth, this);
			}
			
			return total;
		}
		
		public int getAvailableWidth() {
			int deviceWidth = getContext().getResources().getDisplayMetrics().widthPixels;
			int padding = (int) dpToPixels(16, getContext());
			
			return deviceWidth - padding * 2;
		}
		
		public void addSpacer(LinearLayout container) {
			container.addView(((LayoutInflater) container.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.learning_skill_tutorial_component_spacer, container, false));
		}
	}
	
	public interface Component {
		float measureHeight(float width, Segment segment);
		View getView(ViewGroup root, Segment segment);
	}
	
	public static class TextComponent implements Component {
		public static final String XML_NAME = "text";
		
		private SpannableStringBuilder text;
		
		public TextComponent(XML xml, Segment segment) {
			if (xml.getName().equals(XML_NAME)) {
				text = (new Builder()).build(xml.getContent());
			}
		}
		
		public static class Builder {
			String raw;
			SpannableStringBuilder builder = new SpannableStringBuilder();
			
//			int codeBackgroundColor = Color.parseColor("#AAAAAA");
			
			boolean code = false;
			boolean italics = false;
			boolean bold = false;
			
			int breakStart = 0;
			
			int droppedCharCount = 0;
			
			/**
			 * Parses pseudo-markdown
			 * 
			 * @param _raw the raw Markdown text
			 */
			public SpannableStringBuilder build(String _raw) {
				raw = _raw;
				
				for (int i = 0; i < raw.length(); i ++) {
					char c = raw.charAt(i);
					
					switch (c) {
						case '\n':
						case '\r':
						case '\t':
							// Ignore these characters
							breakAppend(i);
							continue;
						case '`':
							breakAppend(i);
							code = !code;
							continue;
						case '*':
							if (!code) {
								breakAppend(i);
								italics = !italics;
								continue;
							}
						case '_':
							if (!code) {
								breakAppend(i);
								bold = !bold;
								continue;
							}
						default:
							if (breakStart == -1) {
								breakStart = i;
							}
							break;
					}
				}
				
				breakAppend(raw.length());
				
				return builder;
			}
			
			private void breakAppend(int breakEnd) {
				if (breakStart == -1 || breakEnd - breakStart == 0) {
					breakStart = -1;
					droppedCharCount ++;
					return;
				}
				
				builder.append(raw, breakStart, breakEnd);
				if (code) {
					builder.setSpan(new TypefaceSpan("monospace"), breakStart - droppedCharCount, breakEnd - droppedCharCount, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
//					builder.setSpan(new BackgroundColorSpan(codeBackgroundColor), breakStart - droppedCharCount, breakEnd - droppedCharCount, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
				}
				if (italics) builder.setSpan(new StyleSpan(Typeface.ITALIC), breakStart - droppedCharCount, breakEnd - droppedCharCount, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
				if (bold) builder.setSpan(new StyleSpan(Typeface.BOLD), breakStart - droppedCharCount, breakEnd - droppedCharCount, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
				
				droppedCharCount ++;
				breakStart = -1;
			}
		}
		
		@Override
		public float measureHeight(float width, Segment segment) {
			// http://stackoverflow.com/a/20087258/
			
			TextView textView = new TextView(segment.getContext());
			textView.setText(text);
			textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			
			int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec((int) width, View.MeasureSpec.AT_MOST);
			int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			textView.measure(widthMeasureSpec, heightMeasureSpec);
			
			return textView.getMeasuredHeight();
		}
		
		@Override
		public View getView(ViewGroup root, Segment segment) {
			TextView textView = new TextView(segment.getContext());
			textView.setText(text);
			textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			
			return textView;
		}
	}
	
	public static class ImageComponent implements Component {
		public static final String XML_NAME = "image";
		
		private Bitmap bitmap;
		
		public ImageComponent(XML xml, Segment segment) {
			if (xml.getName().equals(XML_NAME)) {
				String path = xml.getString("path");
				
				bitmap = BitmapFactory.decodeFile(new File(new File(((APDE) segment.getContext().getApplication()).getLearningFolder(), "skills"), path).getAbsolutePath());
			}
		}
		
		@Override
		public float measureHeight(float width, Segment segment) {
			return bitmap != null ? bitmap.getHeight() : 0;
		}
		
		@Override
		public View getView(ViewGroup root, Segment segment) {
			if (bitmap == null) {
				return null;
			}
			
			ImageView imageView = new ImageView(segment.getContext());
			imageView.setImageBitmap(bitmap);
			
			return imageView;
		}
	}
	
	public static class CodeComponent implements Component {
		public static final String XML_NAME = "code";
		
		private Activity activity;
		
		private SpannableStringBuilder code;
		private boolean runnable, pickable;
		private String displayId;
		
		private ArrayList<Picker> pickers;
		
		public CodeComponent(XML xml, Segment segment) {
			this.activity = segment.getContext();
			
			if (xml.getName().equals(XML_NAME)) {
				pickers = new ArrayList<>();
				
				code = (new Builder()).build(xml.getContent(), pickers, new TextViewGetter() {
					@Override
					public TextView getTextView() {
						return textView;
					}
				}, segment, this);
				runnable = Boolean.valueOf(xml.getString("runnable", "false"));
				pickable = Boolean.valueOf(xml.getString("pickable", "false"));
				displayId = xml.getString("display", "");
				
				String codeToString = code.toString();
				
				if (codeToString.startsWith("\n") || codeToString.startsWith("\r")) {
					code.delete(0, 1);
				} else if (codeToString.startsWith("\r\n")) {
					code.delete(0, 2);
				}
				
				if (codeToString.endsWith("\n") || codeToString.endsWith("\r")) {
					code.delete(code.length() - 1, code.length());
				} else if (codeToString.endsWith("\r\n")) {
					code.delete(code.length() - 2, code.length());
				}
			}
		}
		
		public static class Builder {
			String raw;
			SpannableStringBuilder builder = new SpannableStringBuilder();
			
			int pickableColor = Color.parseColor("#555555");
			
			int breakStart = 0;
			int droppedCharCount = 0;
			
			/**
			 * Parses pseudo-markdown
			 *
			 * @param _raw the raw Markdown text
			 */
			public SpannableStringBuilder build(String _raw, ArrayList<Picker> pickerList, final TextViewGetter textViewGetter, final Segment segment, final CodeComponent codeComponent) {
				raw = _raw;
				
				for (int i = 0; i < raw.length(); i ++) {
					char c = raw.charAt(i);
					
					switch (c) {
						case '\t':
							// Ignore these characters
							breakAppend(i);
							continue;
						case '@':
							if (i < raw.length() - 1) {
								String afterAtSign = _raw.substring(i + 1);
								int openParen = afterAtSign.indexOf('(');
								if (openParen != -1) {
									String annotationName = afterAtSign.substring(0, openParen);
									if (annotationName.equals("Picker") && openParen < afterAtSign.length() - 1) {
										int closeParen = afterAtSign.indexOf(')');
										if (closeParen != -1) {
											String parameters = afterAtSign.substring(openParen + 1, closeParen);
											final Picker picker = Picker.fromString(i - droppedCharCount - 1, parameters);
											pickerList.add(picker);
											String pickerCodeValue = picker.getCodeValue();
											
											// Super hacky, but necessary
											breakAppend(i);
											builder.append(pickerCodeValue);
//											builder.setSpan(new BackgroundColorSpan(pickableColor), i - droppedCharCount + 1, i - droppedCharCount + pickerCodeValue.length() + 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
											builder.setSpan(new ClickableSpan() {
												@Override
												public void onClick(View view) {
													picker.showPicker(textViewGetter, segment, codeComponent);
												}
											}, i - droppedCharCount + 1, i - droppedCharCount + pickerCodeValue.length() + 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
											droppedCharCount += closeParen + 1 - pickerCodeValue.length();
											i += closeParen + 1;
											continue;
										}
									}
								}
							}
							
							// If this isn't an @Picker, then do the default
						default:
							if (breakStart == -1) {
								breakStart = i;
							}
							break;
					}
				}
				
				breakAppend(raw.length());
				
				return builder;
			}
			
			private void breakAppend(int breakEnd) {
				if (breakStart == -1 || breakEnd - breakStart == 0) {
					breakStart = -1;
					droppedCharCount ++;
					return;
				}
				
				builder.append(raw, breakStart, breakEnd);
				
				droppedCharCount ++;
				breakStart = -1;
			}
		}
		
		@Override
		public float measureHeight(float width, Segment segment) {
			// http://stackoverflow.com/a/20087258/
			
			TextView textView = new TextView(segment.getContext());
			textView.setText(code);
			textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			
			int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			textView.measure(widthMeasureSpec, heightMeasureSpec);
			
			return textView.getMeasuredHeight();
		}
		
		private TextView textView;
		
		@Override
		public View getView(ViewGroup root, Segment segment) {
			FrameLayout layout = (FrameLayout) ((LayoutInflater) segment.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.learning_skill_tutorial_component_code, root, false);
			
			final CodeEditText codeArea = (CodeEditText) layout.findViewById(R.id.learning_skill_container_component_code_area);
			final ImageButton runButton = (ImageButton) layout.findViewById(R.id.learning_skill_container_component_code_area_run);
			
			codeArea.setMovementMethod(LinkMovementMethod.getInstance());
			codeArea.setFocusable(false);
			codeArea.setFocusableInTouchMode(false);
			codeArea.setLongClickable(false);
			codeArea.setClickable(true);
			
			codeArea.setText(code);
			
			codeArea.post(new Runnable() {
				@Override
				public void run() {
					codeArea.updateTokens();
				}
			});
			
			if (runnable && !isInlineDisplay()) {
				runButton.setVisibility(View.VISIBLE);
				
				runButton.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						((LearningActivity) activity).loadExampleCode(code.toString());
					}
				});
			} else {
				runButton.setVisibility(View.GONE);
			}
			
			if (isInlineDisplay()) {
				CodeDisplayComponent codeDisplayComponent = segment.getCodeDisplayComponent(displayId);
				if (codeDisplayComponent != null) {
					codeDisplayComponent.setCode(code.toString());
				} else {
					System.err.println("Error: Invalid code display ID \"" + displayId + "\"");
				}
			}
			
			textView = codeArea;
			
			return layout;
		}
		
		public interface TextViewGetter {
			TextView getTextView();
		}
		
		public void updatePickerValue(Segment segment, Picker picker, String before) {
			String after = picker.getCodeValue();
			int lenDif = after.length() - before.length();
			
			code.replace(picker.getPos(), picker.getPos() + picker.getLen(), after);
			textView.setText(code);
			((CodeEditText) textView).updateTokens();
			
			picker.len += lenDif;
			
			for (Picker pick : pickers) {
				if (pick == picker) {
					continue;
				}
				
				if (pick.getPos() > picker.getPos()) {
					pick.pos += lenDif;
				}
			}
			
			CodeDisplayComponent codeDisplayComponent = segment.getCodeDisplayComponent(displayId);
			if (codeDisplayComponent != null) {
				codeDisplayComponent.setCode(code.toString());
			} else {
				System.err.println("Error: Invalid code display ID \"" + displayId + "\"");
			}
		}
		
		public boolean isInlineDisplay() {
			return displayId.length() > 0;
		}
		
		public static abstract class Picker<V> {
			protected int pos, len;
			
			public static Picker fromString(int pos, String paramString) {
				String[] params = paramString.split(",");
				String typeName = params[0].trim();
				typeName = typeName.substring(1, typeName.length() - 1); // Remove quotes
				String defaultValue = params[1].trim();
				
				String[] otherParams;
				if (params.length > 2) {
					otherParams = new String[params.length - 2];
					for (int i = 2; i < params.length; i ++) {
						otherParams[i - 2] = params[i].trim();
					}
				} else {
					otherParams = null;
				}
				
				if (typeName.equals("number")) return new NumberPicker(pos, defaultValue, otherParams);
				if (typeName.equals("color")) return new ColorPicker(pos, defaultValue, otherParams);
				
				return null;
			}
			
			public int getPos() {
				return pos;
			}
			
			public int getLen() {
				return len;
			}
			
			private int[] location = {0, 0};
			
			public void getSpanRect(Rect output, TextView textView) {
				float xOffset = textView.getCompoundPaddingLeft();
				float charWidth = textView.getPaint().measureText("m");
				float lineHeight = textView.getLineHeight();
				
				textView.getLocationInWindow(location);
				
				output.left = (int) (location[0] + Math.max(xOffset + textView.getLayout().getPrimaryHorizontal(getPos()), 1));
				output.top = (int) (location[1] + lineHeight * (textView.getLayout().getLineForOffset(getPos()) - 1.5));
				output.right = output.left + (int) ((charWidth * getLen()));
				output.bottom = output.top + (int) lineHeight;
			}
			
			public abstract V getValue();
			public abstract String getCodeValue();
			
			public abstract void showPicker(TextViewGetter textViewGetter, Segment segment, CodeComponent codeComponent);
		}
		
		public static class NumberPicker extends Picker<Integer> {
			private int value;
			
			private boolean hasMin, hasMax;
			private int minValue, maxValue;
			
			public NumberPicker(int pos, String defaultValue, String[] otherParams) {
				value = Integer.parseInt(defaultValue);
				this.pos = pos;
				len = getCodeValue().length();
				
				if (otherParams != null) {
					if (otherParams.length >= 1) {
						hasMin = true;
						minValue = Integer.parseInt(otherParams[0]);
					}
					if (otherParams.length >= 2) {
						hasMax = true;
						maxValue = Integer.parseInt(otherParams[1]);
					}
				}
			}
			
			public Integer getValue() {
				return value;
			}
			
			public String getCodeValue() {
				return Integer.toString(value);
			}
			
			public void showPicker(final TextViewGetter textViewGetter, final Segment segment, final CodeComponent codeComponent) {
				getSpanRect(getTempDisplayRect(), textViewGetter.getTextView());
				
				final View sliderView = new View(segment.getContext());
				sliderView.setBackgroundResource(R.drawable.skill_tutorial_picker_number_slider);
				sliderView.setClickable(true);
				
				float width = dpToPixels(40, segment.getContext());
				float height = dpToPixels(30, segment.getContext());
				
				float toolbarHeight = segment.getContext().findViewById(R.id.toolbar).getHeight();
				
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams((int) width, (int) height);
				params.leftMargin = (int) ((getTempDisplayRect().left + getTempDisplayRect().right) / 2 - width / 2);
				params.topMargin = (int) (getTempDisplayRect().top - toolbarHeight - height);
				
				final FrameLayout rootView = ((SkillTutorialFragment) segment.getFragment()).getRootView();
				final View mask = rootView.findViewById(R.id.picker_mask);
				
				mask.setVisibility(View.VISIBLE);
				
				rootView.addView(sliderView, params);
				
				mask.setOnTouchListener(new View.OnTouchListener() {
					@Override
					public boolean onTouch(View view, MotionEvent motionEvent) {
						if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
							mask.setVisibility(View.GONE);
							rootView.removeView(sliderView);
						}
						
						// Propagate event
						return false;
					}
				});
				
				sliderView.setOnTouchListener(new View.OnTouchListener() {
					private int startValue;
					private int startLeftMargin;
					private float startX;
					
					@Override
					public boolean onTouch(View view, MotionEvent motionEvent) {
						FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) sliderView.getLayoutParams();
						
						switch (motionEvent.getAction()) {
							case MotionEvent.ACTION_DOWN:
								startValue = value;
								startLeftMargin = layoutParams.leftMargin;
								// The event's x-coordinate is relative to the position of the view, which moves
								startX = startLeftMargin + motionEvent.getX();
								
								return true;
							case MotionEvent.ACTION_MOVE:
								// The event's x-coordinate is relative to the position of the view, which moves
								float difX = (layoutParams.leftMargin + motionEvent.getX()) - startX;
								
								float dpi = segment.getContext().getResources().getDisplayMetrics().density;
								
								if (hasMin && startValue + (difX / dpi) < minValue) {
									difX = (minValue - startValue) * dpi;
								}
								if (hasMax && startValue + (difX / dpi) > maxValue) {
									difX = (maxValue - startLeftMargin) * dpi;
								}
								
								layoutParams.leftMargin = startLeftMargin + (int) difX;
								sliderView.requestLayout();
								
								String before = getCodeValue();
								value = startValue + (int) (difX / dpi);
								codeComponent.updatePickerValue(segment, NumberPicker.this, before);
								
								return true;
							case MotionEvent.ACTION_UP:
								return true;
						}
						
						return false;
					}
				});
			}
		}
		
		public static class ColorPicker extends Picker<String> {
			private String value;
			
			private static ColorSelector colorSelector = null;
			
			public ColorPicker(int pos, String defaultValue, String[] otherParams) {
				value = defaultValue;
				this.pos = pos;
				len = getCodeValue().length();
			}
			
			@Override
			public String getValue() {
				return value;
			}
			
			@Override
			public String getCodeValue() {
				return value;
			}
			
			@Override
			public void showPicker(TextViewGetter textViewGetter, final Segment segment, final CodeComponent codeComponent) {
				if (colorSelector == null) {
					colorSelector = new ColorSelector();
					colorSelector.init((APDE) segment.getContext().getApplication());
				}
				
				colorSelector.setActivity(segment.getContext());
				
				colorSelector.setOnDissmissListener(new DialogInterface.OnDismissListener() {
					@Override
					public void onDismiss(DialogInterface dialogInterface) {
						String before = getCodeValue();
						value = colorSelector.getHex();
						codeComponent.updatePickerValue(segment, ColorPicker.this, before);
					}
				});
				
				colorSelector.setOnShowListener(new DialogInterface.OnShowListener() {
					@Override
					public void onShow(DialogInterface dialogInterface) {
						colorSelector.setHex(value);
					}
				});
				
				colorSelector.run();
			}
		}
	}
	
	public static class CodeDisplayComponent implements Component {
		public static String XML_NAME = "codeDisplay";
		
		private String id;
		private int width, height;
		
		private FrameLayout frame;
		private SandboxFragment sandboxFragment;
		
		private String code = null;
		
		public CodeDisplayComponent(XML xml, Segment segment) {
			if (xml.getName().equals(XML_NAME)) {
				id = xml.getString("id");
				
				width = (int) Math.min(segment.getAvailableWidth(), dpToPixels(400, segment.getContext()));
				height = (int) Math.round(width / 1.6); // 16:10
			}
		}
		
		@Override
		public float measureHeight(float width, Segment segment) {
			return 0;
		}
		
		@Override
		public View getView(ViewGroup root, final Segment segment) {
			frame = (FrameLayout) ((LayoutInflater) segment.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.learning_skill_tutorial_component_code_display, root, false);
			final int frameId = frame.getId();
			frame.requestLayout();
			
			frame.post(new Runnable() {
				@Override
				public void run() {
					frame.getLayoutParams().width = width;
					frame.getLayoutParams().height = height;
					
					FragmentManager fragmentManager = segment.getFragment().getChildFragmentManager();
					
					sandboxFragment = SandboxFragment.newInstance();
					
					FragmentTransaction transaction = fragmentManager.beginTransaction();
					transaction.add(frameId, sandboxFragment);
					transaction.commit();
					
					if (code != null) {
						fragmentManager.executePendingTransactions();
						
						sandboxFragment.updateCode(code);
					}
				}
			});
			
			return frame;
		}
		
		public String getId() {
			return id;
		}
		
		public void setCode(final String code) {
			if (sandboxFragment == null) {
				this.code = code;
			} else {
				this.code = code;
				
				sandboxFragment.updateCode(code);
			}
		}
	}
	
	public static class ExamplesListComponent implements Component {
		public static final String XML_NAME = "examplesList";
		
		private SpannableStringBuilder text;
		
		public ExamplesListComponent(XML xml, final Segment segment) {
			String[] lines = xml.getContent().split("\\r?\\n|\\r");
			text = new SpannableStringBuilder();
			text.append(segment.getContext().getResources().getString(R.string.skill_tutorial_recommended_examples) + "\n\n");
			int pos = text.length();
			for (int i = 0; i < lines.length; i ++) {
				final String line = lines[i].trim();
				if (line.length() > 0) {
					text.append(line + "\n\n");
					text.setSpan(new ClickableSpan() {
						@Override
						public void onClick(View view) {
							((APDE) segment.getContext().getApplication()).getEditor().loadSketch(line, APDE.SketchLocation.EXAMPLE);
							segment.getContext().finish();
						}
					}, pos, pos + line.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
					pos += line.length() + 2;
				}
			}
		}
		
		@Override
		public float measureHeight(float width, Segment segment) {
			// http://stackoverflow.com/a/20087258/
			
			TextView textView = new TextView(segment.getContext());
			textView.setText(text);
			textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			
			int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
			textView.measure(widthMeasureSpec, heightMeasureSpec);
			
			return textView.getMeasuredHeight();
		}
		
		@Override
		public View getView(ViewGroup root, Segment segment) {
			TextView textView = new TextView(segment.getContext());
			
			textView.setMovementMethod(LinkMovementMethod.getInstance());
			textView.setClickable(true);
			
			textView.setText(text);
			
			return textView;
		}
	}
	
	public static class QuizComponent implements Component {
		public static final String XML_NAME = "quiz";
		
		public QuizComponent(XML xml, Segment segment) {
			
		}
		
		@Override
		public float measureHeight(float width, Segment segment) {
			return 0;
		}
		
		@Override
		public View getView(ViewGroup root, Segment segment) {
			return null;
		}
	}
	
	public static float dpToPixels(float dp, Context context) {
		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
	}
	
	public static Rect tempDisplayRect = new Rect();
	
	public static Rect getTempDisplayRect() {
		return tempDisplayRect;
	}
}
