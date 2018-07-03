package com.calsignlabs.apde;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.widget.AppCompatEditText;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import com.calsignlabs.apde.build.CompilerProblem;
import com.calsignlabs.apde.tool.Tool;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.ParserConfigurationException;

import processing.core.PApplet;
import processing.data.XML;

/**
 * Custom EditText for syntax highlighting, auto-indent, etc.
 */
public class CodeEditText extends AppCompatEditText {
	protected SketchFile sketchFile;
	
	private Context context;
	private float textSize = 14;
	
	// Paints that will always be used
	private static Paint lineHighlight;
	private static Paint bracketMatch;
	private static Paint blackPaint;
	private static Paint whitePaint;
	
	// Compiler problem paints
	private static Paint compilerErrorPaint;
	private static Paint compilerWarningPaint;
	
	// Lists of styles
	public static HashMap<String, TextPaint> styles;
	public static Keyword[] syntax;
	protected static AtomicBoolean syntaxLoaded = new AtomicBoolean(false);
	
	// The default indentation (two spaces)
	public static final String indent = "  ";
	
	// Syntax highlighter information
	protected Token[] tokens;
	protected int matchingBracket;
	
	// Highlight
	ArrayList<Highlight> highlights;
	
	// Whether or not the syntax highlighter is currently running
	private AtomicBoolean updatingTokens;
	// Whether or not we need to update the tokens AGAIN
	private AtomicBoolean flagRefreshTokens;
	
	public class Highlight {
		public int pos;
		public int len;
		public Paint paint;
		
		public Highlight(int pos, int len, Paint paint) {
			this.pos = pos;
			this.len = len;
			this.paint = paint;
		}
	}
	
	public void setSketchFile(SketchFile sketchFile) {
		this.sketchFile = sketchFile;
	}
	
	private enum EditType {
		NONE,
		CHAR,   //The user has pressed one character
		DELETE, //The user has deleted one character
		ENTER,  //The user has pressed enter (includes extra simulated character presses from the auto-indenter)
		TAB,    //The user has pressed tab
		BATCH   //The user has added or removed several characters at once (this is either pasting text or soft-keyboard auto-completion / swipe-typing)
	}
	
	private final static long UNDO_UPDATE_TIME = 400; //Save changes to undo history after user has stopped typing for 400 milliseconds
	
	private boolean flagEnter = false;
	private boolean flagTab = false;
	
	//Used to indicate that the code area is currently being updated in such a way that we should not save the change in the undo history
	private boolean FLAG_NO_UNDO_SNAPSHOT = false;
	
	public CodeEditText(Context context) {
		super(context);
		init();
		
		this.context = context;
	}
	
	public CodeEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
		
		this.context = context;
	}
	
	public CodeEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
		
		this.context = context;
	}
	
	private void init() {
		flagRefreshTokens = new AtomicBoolean();
		updatingTokens = new AtomicBoolean();
		
		//Get rid of extra spacing at the top and bottom
		setIncludeFontPadding(false);
		
		matchingBracket = -1;
		
		highlights = new ArrayList<Highlight>();
		
		// Don't load the syntax again for each tab
		if (syntaxLoaded.get()) {
			return;
		}
		
		//Create the line highlight Paint
		lineHighlight = new Paint();
		lineHighlight.setStyle(Paint.Style.FILL);
		lineHighlight.setColor(0x66AACCFF);
		
		//Create the bracket match Paint
		bracketMatch = new Paint();
		bracketMatch.setStyle(Paint.Style.STROKE);
		bracketMatch.setColor(0xFF000000);
		
		//Create the black (default text) paint
		blackPaint = new Paint();
		blackPaint.setStyle(Paint.Style.FILL);
		blackPaint.setColor(0xFF000000);
		
		//Create the white (cleared) paint
		whitePaint = new Paint();
		whitePaint.setStyle(Paint.Style.FILL);
		whitePaint.setColor(0xFFFFFFFF);
		
		float problemUnderlineSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.5f, getResources().getDisplayMetrics());
		
		// TODO maybe define these compiler problem paints in the syntax highlighting file?
		compilerErrorPaint = new Paint();
		compilerErrorPaint.setStyle(Paint.Style.STROKE);
		compilerErrorPaint.setStrokeWidth(problemUnderlineSize);
		compilerErrorPaint.setColor(0xFFBD1515);
		
		compilerWarningPaint = new Paint();
		compilerWarningPaint.setStyle(Paint.Style.STROKE);
		compilerWarningPaint.setStrokeWidth(problemUnderlineSize);
		compilerWarningPaint.setColor(0xFFEAD61B);
		
		//Initialize the list of styles
		styles = new HashMap<String, TextPaint>();
		
		//Load the default syntax
		try {
			//Processing's XML is easier to work with... TODO maybe implement native XML?
			loadSyntax(new XML(getResources().getAssets().open("default_syntax_colors.xml")));
		} catch (IOException e) { //And here come the exceptions...
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		}
	}
	
	private TextWatcher textListener = null;
	private OnKeyListener keyListener = null;
	
	public void setupTextListener() {
		// We check for null to prevent re-adding the listeners when the fragment is re-created
		
		if (textListener == null) {
			textListener = new TextWatcher() {
				private String oldText;
				private EditType lastEditType = EditType.NONE;
				private long lastUpdate = 0;
				
				@Override
				public void afterTextChanged(Editable editable) {
					// Unfortunately, this appears to be the only way to detect character presses in all situations: reading the text directly...
					
					String text = getText().toString();
					
					// Compare the old text and the new text
					// TODO: Does this check fail in any corner cases (like mass-text insertion / deletion)?
					if (text.length() == oldText.length() + 1 && getSelectionStart() > 0) {
						char pressedChar = text.charAt(getSelectionStart() - 1);
						
						pressKeys(String.valueOf(pressedChar));
					}
					
					EditorActivity editor = ((APDE) context.getApplicationContext()).getEditor();
					
					if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_key_undo_redo", true)) {
						if (!FLAG_NO_UNDO_SNAPSHOT) {
							sketchFile.clearRedo();
							
							EditType editType;
							
							int lenDif = text.length() - oldText.length();
							
							switch (lenDif) {
								case 1:
									editType = flagEnter ? EditType.ENTER : EditType.CHAR;
									break;
								case -1:
									editType = flagEnter ? EditType.ENTER : EditType.DELETE;
									break;
								default:
									editType = flagEnter ? EditType.ENTER : (flagTab ? EditType.TAB : EditType.BATCH);
									break;
							}
							
							sketchFile.update(editor, true);
							
							if (editType.equals(lastEditType) && SystemClock.uptimeMillis() - lastUpdate < UNDO_UPDATE_TIME) {
								//If this is the same edit type (and not too much time has passed), merge it with the last
								//We have to use .uptimeMillis() because .threadTimeMillis() doesn't run continuously - the thread pauses...
								sketchFile.mergeTop();
							}
							
							lastUpdate = SystemClock.uptimeMillis();
							
							lastEditType = editType;
						} else {
							lastEditType = EditType.NONE;
						}
					} else {
						if (!FLAG_NO_UNDO_SNAPSHOT) {
							sketchFile.update(editor, false);
						}
					}
					
					editor.scheduleAutoSave();
				}
				
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
					oldText = getText().toString();
				}
				
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
					updateTokens();
				}
			};
			
			addTextChangedListener(textListener);
		}
		
		if (keyListener == null) {
			//Detect enter key presses... regardless of whether or not the user is using a hardware keyboard
			keyListener = new OnKeyListener() {
				@Override
				public boolean onKey(View view, int keyCode, KeyEvent event) {
					//We don't need this check now... but I'll leave it here just in case...
//				//Retrieve the character that was pressed (hopefully...)
//				//This doesn't work for all cases, though...
//				char pressedChar = (char) event.getUnicodeChar(event.getMetaState());
//				
//				if(pressedChar != 0)
//					pressKeys(String.valueOf(pressedChar));
					
					//We only want to check key down events...
					//...otherwise we get two events for every press because we have down and up
					if (event.getAction() != KeyEvent.ACTION_DOWN)
						return false;
					
					//We don't need this check, either...
//				if(keyCode == KeyEvent.KEYCODE_ENTER) {
//					post(new Runnable() {
//						public void run() {
//							pressEnter();
//						}
//					});
//				}
					
					//Override default TAB key behavior
					if (keyCode == KeyEvent.KEYCODE_TAB && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("override_tab", true)) {
						if (!FLAG_NO_UNDO_SNAPSHOT) {
							flagTab = true;
						}
						getText().insert(getSelectionStart(), "  ");
						flagTab = false;
						
						return true;
					}
					
					return false;
				}
			};
			
			setOnKeyListener(keyListener);
		}
		
		updateTokens();
	}
	
	@SuppressLint("NewApi")
	public void setupCustomActionMode() {
		final ArrayList<Tool> tools = ((APDE) context.getApplicationContext()).getTools();
		final HashMap<MenuItem, Tool> callbacks = new HashMap<MenuItem, Tool>();
		
		setCustomSelectionActionModeCallback(new ActionMode.Callback() {
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				for(Tool tool : tools) {
					MenuItem item = menu.add(tool.getMenuTitle());
					
					if(tool.createSelectionActionModeMenuItem(item)) {
						callbacks.put(item, tool);
					} else {
						//Get rid of it
						//It would be better if it was gone all together instead of just hidden...
						item.setVisible(false);
					}
				}
				
				return true;
			}
			
			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return false;
			}
			
			@Override
			public void onDestroyActionMode(ActionMode mode) {}
			
			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				Tool callback = callbacks.get(item);
				
				if(callback != null) {
					callback.run();
					
					return true;
				}
				
				return false;
			}
		});
	}
	
	public void startSelectionActionMode() {
		//Force the EditText to show the CAB (hacky!)
		MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, 0, 0, 0);
		dispatchTouchEvent(event);
		event.recycle();
	}
	
	/**
	 * Loads the syntax as specified in the XML
	 * 
	 * @param xml
	 */
	public void loadSyntax(XML xml) {
		//Get the list of defined styles
		XML[] styleList = xml.getChild("styles").getChildren();
		for(XML style : styleList) {
			//Make sure that this is a "style" element
			if(!style.getName().equals("style"))
				continue;
			
			//Parse the style
			TextPaint paint = new TextPaint(getPaint());
			String name = style.getContent();
			String hex = style.getString("color", "#FF000000").substring(1);
			boolean bold = style.getString("bold", "false").equals("true") ? true : false;
			
			//Build the TextPaint
			paint.setStyle(TextPaint.Style.FILL);
			paint.setColor(PApplet.unhex(hex));
			paint.setFakeBoldText(bold); //TODO what does "fake" mean? Is this something we should be concerned about?
			
			//Add the style
			styles.put(name, paint);
		}
		
		//Get the list of defined keywords
		XML[] keywords = xml.getChild("keywords").getChildren();
		ArrayList<Keyword> tempSyntax = new ArrayList<>();
		
		syntax = new Keyword[keywords.length];
		for (int i = 0; i < keywords.length; i ++) {
			XML keyword = keywords[i];
			
			//Make sure that this is a "keyword" element
			if(!keyword.getName().equals("keyword"))
				continue;
			
			//Parse the keyword
			String style = keyword.getString("style", "");
			String name = keyword.getContent();
			boolean function = keyword.getString("function", "false").equals("true");
			
			String reference = keyword.getString("reference", "processing");
			boolean noUnderscore = keyword.getString("reference_hint", "").equals("no_underscore");
			
			String parentClass = keyword.getString("class", "");
			boolean staticInParentClass = Boolean.valueOf(keyword.getString("static", "false"));
			
			// If there is a parent class, then open that class... we don't know how to be fancier yet
			// Automatically add an underscore if the keyword is a function and doesn't have the "no_underscore" hint
			String referenceTarget = keyword.getString("reference_target", parentClass.length() > 0 ? parentClass : (name + (function && !noUnderscore ? "_" : "")));
			
			//If this isn't a valid style, bail out
			if(!styles.containsKey(style))
				continue;
			
			//Add the keyword
			tempSyntax.add(new Keyword(name, styles.get(style), function, reference, referenceTarget, parentClass, staticInParentClass));
		}
		
		syntax = new Keyword[tempSyntax.size()];
		
		// We need to do this because more than the half of the reported XML elements are null
		for (int i = 0; i < tempSyntax.size(); i ++) {
			syntax[i] = tempSyntax.get(i);
		}
		
		syntaxLoaded.set(true);
	}
	
	public static Keyword[] getKeywords() {
		return syntax;
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
//		int lastLineNum = getCurrentLine();
		
		//Make sure to forward the result of what would normally happen
		boolean result = super.onKeyDown(keyCode, event);
		
//		if(keyCode == KeyEvent.KEYCODE_ENTER) {
//			//Get the indentation of the previous line
//			String[] lines = getText().toString().split("\n");
//			String lastLine = "";
//			String lastIndent = "";
//			
//			//Calculate the indentation of the previous line
//			if(lines.length > 0) {
//				lastLine = lines[Math.min(lastLineNum, lines.length - 1)];
//				
//				for(int i = 0; i < lastLine.length(); i ++) {
//					if(lastLine.charAt(i) == ' ')
//						lastIndent += ' ';
//					else
//						break;
//				}
//			}
//			
//			//Determine the last character of the previous line (not counting whitespace)
//			char lastChar = ' ';
//			String trimmedLastLine = lastLine.trim();
//			if(trimmedLastLine.length() > 0) {
//				lastChar = trimmedLastLine.charAt(trimmedLastLine.length() - 1);
//			}
//			
//			//Automatically indent
//			if(lastChar == '{') {
//				//Automatically increase the indent if this is a new code block
//				getText().insert(getSelectionStart(), lastIndent + indent);
//				
//				//Automatically press enter again so that everything lines up nicely.. This is incredibly hacky...
//				if(getText().length() > getSelectionStart() && getText().charAt(getSelectionStart()) == '}') {
//					//Add a newline
//					getText().insert(getSelectionStart(), "\n" + lastIndent);
//					//Move the cursor back (hacky...)
//					setSelection(getSelectionStart() - (lastIndent.length() + 1));
//				}
//			} else {
//				//Regular indentation
//				getText().insert(getSelectionStart(), lastIndent);
//			}
//		}
		
    	return result;
    }
	
	public void pressKeys(String pressed) {
		// Detect the ENTER key
		if (pressed.length() == 1 && pressed.charAt(0) == '\n') {
			pressEnter();
		}
		
		// Automatically add a closing brace (if the user has enabled curly brace insertion)
		if (pressed.charAt(0) == '{' && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("curly_brace_insertion", true)) {
			getText().insert(getSelectionStart(), "}");
			setSelection(getSelectionStart() - 1);
		}
	}
	
	public void pressEnter() {
		//Make sure that the user has enabled auto indentation
		if(!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("auto_indent", true))
			return;
		
		int lastLineNum = getCurrentLine() - 1;
		
		//Get the indentation of the previous line
		String[] lines = getText().toString().split("\n");
		String lastLine = "";
		String lastIndent = "";
		
		//Calculate the indentation of the previous line
		if(lines.length > 0) {
			lastLine = lines[Math.min(lastLineNum, lines.length - 1)];
			
			for(int i = 0; i < lastLine.length(); i ++) {
				if(lastLine.charAt(i) == ' ')
					lastIndent += ' ';
				else
					break;
			}
		}
		
		//Determine the last character of the previous line (not counting whitespace)
		char lastChar = ' ';
		String trimmedLastLine = lastLine.trim();
		if(trimmedLastLine.length() > 0) {
			lastChar = trimmedLastLine.charAt(trimmedLastLine.length() - 1);
		}
		
		//Automatically indent
		if(lastChar == '{') {
			if (!FLAG_NO_UNDO_SNAPSHOT) {
				flagEnter = true;
			}
			
			//Automatically increase the indent if this is a new code block
			getText().insert(getSelectionStart(), lastIndent + indent);
			
			//Automatically press enter again so that everything lines up nicely.. This is incredibly hacky...
			//Also make sure that the user has enabled curly brace insertion
			if(getText().length() > getSelectionStart() && getText().charAt(getSelectionStart()) == '}' && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("curly_brace_insertion", true)) {
				//Add a newline (the extra space is so that we don't recursively detect a newline; adding at least two characters at once sidesteps this possibility)
				getText().insert(getSelectionStart(), "\n" + lastIndent + " ");
				//Move the cursor back (hacky...)
				setSelection(getSelectionStart() - (lastIndent.length() + 2));
				//Remove the extra space (see above)
				getText().replace(getSelectionStart() + 1, getSelectionStart() + 2, "");
			}
			
			flagEnter = false;
		} else {
			//Regular indentation
			getText().insert(getSelectionStart(), lastIndent);
		}
	}
	
	@Override 
	protected void onSelectionChanged(int selStart, int selEnd) {
		updateBracketMatch();
		updateCursorCompilerProblem();
	}
	
	public void updateBracketMatch() {
		//"{}", "()", "[]" open / close matching
		//This isn't necessarily optimized, but it doesn't seem to need it...
		
		int caret = getSelectionStart() - 1;
		
		//Make sure there is no text selection...
		if(caret == getSelectionEnd() - 1 && caret > -1) {
			//The character to the left of the cursor
			char left = getText().charAt(caret);
			//The character that we're searching for
			char other;
			//Up or down
			int dir;
			
			//This isn't very elegant...
			if(left == '{') {
				other = '}';
				dir = 1;
			} else if(left == '}') {
				other = '{';
				dir = -1;
			} else if(left == '(') {
				other = ')';
				dir = 1;
			} else if(left == ')') {
				other = '(';
				dir = -1;
			} else if(left == '[') {
				other = ']';
				dir = 1;
			} else if(left == ']') {
				other = '[';
				dir = -1;
			} else {
				matchingBracket = -1;
				
				return;
			}
			
			//Start on the right side (puns!)
			if(dir == 1)
				caret ++;
			//Or the left...
			if(dir == -1)
				caret --;
			
			matchingBracket = -1;
			
			//The total opens / closes
			int dif = 0;
			while(caret < getText().length() && caret > -1) {
				char next = getText().charAt(caret);
				
				if(next == other)
					dif -= 1;
				if(next == left)
					dif += 1;
				
				if(dif < 0) {
					matchingBracket = caret;
					break;
				}
				
				caret += dir;
			}
		}
	}
	
	/**
	 * Display a compiler problem (error or warning) message when the user places the cursor
	 * over the underlined text corresponding to that problem.
	 */
	public void updateCursorCompilerProblem() {
		// Don't do this at all for text selection, only cursor movement
		if (getSelectionStart() == getSelectionEnd() && sketchFile != null && sketchFile.getCompilerProblems() != null) {
			int line = getCurrentLine();
			
			// Find the cursor position into the line by searching left for the first newline
			int newlinePos = getSelectionStart() - 1;
			while (newlinePos > 0 && getText().charAt(newlinePos) != '\n') {
				newlinePos --;
			}
			int cursorInLine = getSelectionStart() - newlinePos;
			
			for (CompilerProblem problem : sketchFile.getCompilerProblems()) {
				if (line == problem.line && cursorInLine > problem.start && cursorInLine <= problem.start + problem.length + 1) {
					EditorActivity editor = ((APDE) context.getApplicationContext()).getEditor();
					
					if (problem.isError()) {
						editor.errorExt(problem.getMessage());
					} else {
						editor.warningExt(problem.getMessage());
					}
					
					break;
				}
			}
		}
	}
	
	public void addHighlight(int pos, int len, Paint paint) {
		highlights.add(new Highlight(pos, len, paint));
	}
	
	public void clearHighlights() {
		highlights.clear();
	}
	
	public void scrollToChar(int pos, EditorActivity editor) {
		float xOffset = getCompoundPaddingLeft();
		
		//Calculate coordinates
		int x = (int) Math.max(xOffset + getLayout().getPrimaryHorizontal(pos), 1);
		int y = getLineHeight() * getLayout().getLineForOffset(pos);
		
		((HorizontalScrollView) editor.findViewById(R.id.code_scroller_x)).smoothScrollTo(x, 0);
		((ScrollView) editor.findViewById(R.id.code_scroller)).smoothScrollTo(0, y);
	}
	
	/**
	 * @return the number of the currently selected line
	 */
	public int getCurrentLine() {
		if (getLayout() != null && getSelectionStart() > -1) {
			return getLayout().getLineForOffset(getSelectionStart());
		}
		
		return -1;
	}
	
	/**
	 * Returns the character offset for the specified line.
	 * This is related to offsetForLineEnd(int)
	 * 
	 * @param line
	 * @return
	 */
	public int offsetForLine(int line) {
		//Get a list of lines
		String[] lines = getText().toString().split("\n");
		
		//Count up to the specified line
		int off = 0;
		for(int i = 0; i < Math.min(lines.length, line); i ++)
			//Add the length of each line
			off += lines[i].length() + 1;
		
		//We don't want to return values that are too big...
		if(off > getText().length())
			off = getText().length();
		//...or to small
		if(off < 0)
			off = 0;
		
		return off;
	}
	
	/**
	 * Returns the character offset for the end of the specified line.
	 * This is related to offsetForLine(int)
	 * 
	 * @param line
	 * @return
	 */
	public int offsetForLineEnd(int line) {
		//Get a list of lines
		String[] lines = getText().toString().split("\n");
		
		//Count up to the specified line, including the specified line
		int off = 0;
		for(int i = 0; i < Math.min(lines.length, line + 1); i ++)
			//Add the length of each line
			off += lines[i].length() + 1;
		
		//We don't want to return values that are too big
		if(off > getText().length())
			off = getText().length();
		//...or to small
		if(off < 0)
			off = 0;
		
		return off;
	}
	
	public int lineForOffset(int offset) {
		//Get a list of lines
		String[] lines = getText().toString().split("\n");
		
		int off = 0;
		for(int i = 0; i < lines.length; i ++) {
			off += lines[i].length() + 1;
			
			if(off > offset) {
				return i;
			}
		}
		
		return lines.length - 1;
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		float lineHeight = getLineHeight();
		int currentLine = getCurrentLine();
		
		if (isFocused()) {
			// Draw line highlight around the line that the cursor is on
			canvas.drawRect(getScrollX(), currentLine * lineHeight, canvas.getWidth() + getScrollX(), (currentLine + 1) * lineHeight, lineHighlight);
		}
		
		// Draw base text
		super.onDraw(canvas);
		
		// If the syntax highlighter hasn't run yet...
		// Make sure this doesn't break
		if (tokens == null) {
			//Check again
			invalidate();
			return;
		}
		
		if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("syntax_highlight", true)) {
			//ScrollView doesn't like to let us know when it has scrolled...
//			ScrollView scroller = (ScrollView) ((APDE) context.getApplicationContext()).getEditor().findViewById(R.id.code_scroller);
			int topVis = 0;//(int) Math.max(scroller.getScrollY() / getLineHeight() - 1, 0); //inclusive
			int bottomVis = getLineCount();//(int) Math.floor(Math.min((scroller.getScrollY() + scroller.getHeight()) / getLineHeight() + 1, getLineCount())); //exclusive
			
			for(int i = 0; i < tokens.length; i ++) {
				//Only draw this if we need to
				if(tokens[i].lineNum >= topVis && tokens[i].isCustomPaint)
					tokens[i].display(canvas);
				else if(tokens[i].lineNum > bottomVis)
					break;
			}
			
			if (sketchFile.getCompilerProblems() != null) {
				for (CompilerProblem problem : sketchFile.getCompilerProblems()) {
					if (problem.line >= topVis && problem.line <= bottomVis) {
						displayCompilerProblem(problem, canvas);
					}
				}
			}
			
			//"{}", "()", "[]" open / close matching
			//Make sure we don't crash if the bracket matcher hasn't updated yet and we are deleting a lot of text...
			if(matchingBracket != -1 && matchingBracket < getText().length()) {
				float xOffset = getCompoundPaddingLeft(); //TODO hopefully no one uses Arabic (right-aligned localities)... because getCompoundPaddingStart() was introduced in a later API level
				float charWidth = getPaint().measureText("m");

				//Calculate coordinates
				float x = Math.max(xOffset + getLayout().getPrimaryHorizontal(matchingBracket), 1);
				float y = lineHeight * getLayout().getLineForOffset(matchingBracket);

				canvas.drawRect(x, y, x + charWidth, y + lineHeight, bracketMatch);
			}
			
			float xOffset = getCompoundPaddingLeft();
			float charWidth = getPaint().measureText("m");
			
			float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, context.getResources().getDisplayMetrics());
			
			//Draw the highlight boxes
			for (Highlight highlight : highlights) {
				if (highlight.pos != -1 && highlight.pos + highlight.len <= getText().length()) {
					//Calculate coordinates
					float x = Math.max(xOffset + getLayout().getPrimaryHorizontal(highlight.pos), 1);
					float y = lineHeight * getLayout().getLineForOffset(highlight.pos);
					
					canvas.drawRoundRect(new RectF(x, y, x + charWidth * highlight.len, y + lineHeight), radius, radius, highlight.paint);
				}
			}
		}
		
		//Now that we've multi-threaded the new syntax highlighter, we don't need the old one
		//It's still here in memory...
	}
	
	/**
	 * Call this function to force the tokens to update AGAIN after the current / next update cycle has completed
	 */
	public void flagRefreshTokens() {
		if (updatingTokens.get()) {
			flagRefreshTokens.set(true);
		} else {
			updateTokens();
		}
	}
	
	public synchronized void updateTokens() {
		if(!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("syntax_highlight", true))
			return;
		
		//Get the text now so that we don't experience any synchronization issues
		final String text = getText().toString();
		
		new Thread(new Runnable() {
			public void run() {
				updatingTokens.set(true);
				
				Token[] tempTokens = splitTokens(text, 0, new char[] {'(', ')', '[', ']', '{', '}', '=', '+', '-', '/', '*', '"', '\'', '%', '&', '|', '?', ':', ';', '<', '>', ',', '.', ' '});
				
				for(int i = 0; i < tempTokens.length; i ++) {
					String nextNonSpace = "";
					for(int j = i + 1; j < tempTokens.length; j ++) {
						String next = tempTokens[j].text;

						if(next.equals(" ") || next.equals("\n"))
							continue;

						nextNonSpace = next;
						break;
					}

					tempTokens[i].updatePaint(nextNonSpace);
				}
				
				boolean multiLineComment = false;
				boolean singleLineComment = false;
				boolean stringLiteral = false;
				boolean charLiteral = false;
				
				int startLiteral = -1;
				
				String prev = "";
				String next;
				
				for(int i = 0; i < tempTokens.length; i ++) {
					Token token = tempTokens[i];
					next = (i < tempTokens.length - 1 ? tempTokens[i + 1].text : "");
					
					if(token.text.equals("\n")) {
						singleLineComment = false;
						stringLiteral = false;
						charLiteral = false;
						
						continue;
					}
					
					if(stringLiteral && prev.equals("\"") && i > startLiteral + 1)
						stringLiteral = false;
					
					if(charLiteral && prev.equals("'") && i > startLiteral + 1)
						charLiteral = false;
					
					if(!multiLineComment && !singleLineComment && !stringLiteral && !charLiteral) {
						//Test for single-line comments
						if(token.text.equals("/") && next.equals("/"))
							singleLineComment = true;
						//Test for multi-line comments
						else if(token.text.equals("/") && next.equals("*"))
							multiLineComment = true;
					}
					
					//TODO Implement incomplete / invalid literals
					
					//Test for String literals
					if(!stringLiteral && !multiLineComment && !singleLineComment && !charLiteral && token.text.equals("\"")) {
						stringLiteral = true;
						startLiteral = i;
					}
					
					//Test for char literals
					if(!charLiteral && !multiLineComment && !singleLineComment && !stringLiteral && token.text.equals("'")) {
						charLiteral = true;
						startLiteral = i;
					}
					
					//Change paint for comments and literals
					if(singleLineComment) {
						token.paint = styles.get("comment_single");
						token.isCustomPaint = true;
					} else if(multiLineComment) {
						token.paint = styles.get("comment_multi");
						token.isCustomPaint = true;
					} else if(stringLiteral) {
						token.paint = styles.get("literal_string");
						token.isCustomPaint = true;
					} else if(charLiteral) {
						token.paint = styles.get("literal_char");
						token.isCustomPaint = true;
					}
					
					//Test for end multi-line comments
					if(multiLineComment)
						if(prev.equals("*") && token.text.equals("/"))
							multiLineComment = false;
					
					prev = token.text;
				}
				
				//Compaction of same-colored tokens - like comments, for example (they were causing serious lag)
				//TODO we're probably still picking up space characters...
				
				ArrayList<Token> finalTokens = new ArrayList<Token>();
				Token activeToken = null;
				
				for(Token token : tempTokens) {
					if(activeToken == null) {
						activeToken = token;
						
						continue;
					}
					
					//Direct reference comparison of paints, should be OK...
					if(activeToken.lineNum != token.lineNum || activeToken.paint != token.paint) {
						finalTokens.add(activeToken);
						activeToken = token;
						
						continue;
					}
					
					activeToken.text += token.text;
				}
				
				//Add the extra token at the end
				finalTokens.add(activeToken);
				
				tokens = finalTokens.toArray(new Token[finalTokens.size()]);
				
				//If there is no text, wipe the tokens
				//TODO ...why do we need this? It seems somewhat counterproductive...
				if(getText().length() == 0)
					clearTokens();
				
				postInvalidate();
				
				updatingTokens.set(false);
				
				//Check to see if we have updated the text AGAIN since starting this update
				//We shouldn't get too much recursion...
				if(flagRefreshTokens.get()) {
					updateTokens();
					flagRefreshTokens.set(false);
				}
			}
		}).start();
	}
	
	//Called internally to get a list of all tokens in an input String, such that each token may be syntax highlighted with a different color
	//NOTE: This is not the same as PApplet.splitTokens()
	private Token[] splitTokens(String input, int lineOffset, char[] tokens) {
		//Create the output list
		ArrayList<Token> output = new ArrayList<Token>();
		output.add(new Token("", 0, 0));
		
		boolean wasToken = false;
		int xOff = 0;
		int currentLine = 0;
		
		//Read each char in the input String
		for(int i = 0; i < input.length(); i ++) {
			char c = input.charAt(i);
			
			if(c == '\n') {
				currentLine ++;
				xOff = 0;
				
				output.add(new Token("\n", xOff, lineOffset + currentLine));
				wasToken = true;
				
				continue;
			}
			
			//If it is a token, split into a new String
			if(isToken(c, tokens)) {
				output.add(new Token("", xOff, lineOffset + currentLine));
				wasToken = true;
			} else if(wasToken) {
				output.add(new Token("", xOff, lineOffset + currentLine));
				wasToken = false;
			}
			
			//Append the char
			output.get(output.size() - 1).text += c;
			
			xOff ++;
		}
		
		//Convert to an array
		Token[] array = output.toArray(new Token[output.size()]);
		return array;
	}
	
	//Called internally from splitTokens()
	//Determines whether or not the specified char is in the array of chars
	private boolean isToken(char token, char[] tokens) {
		for(char c : tokens)
			if(c == token)
				return true;
		
		return false;
	}
	
	//Used internally for the syntax highlighter
	protected class Token {
		protected String text;
		protected int offset;
		protected int lineNum;
		
		protected TextPaint paint;
		//Do we actually we need to draw this over the default text?
		protected boolean isCustomPaint;
		
		protected Token(String text, int offset, int lineNum) {
			this.text = text;
			this.offset = offset;
			this.lineNum = lineNum;
			
			paint = styles.get("base");
			isCustomPaint = false;
		}
		
		protected void updatePaint(String nextNonSpace) {
			Keyword keyword = getKeyword(text, nextNonSpace.equals("("));
			if (keyword != null) {
				paint = keyword.paint();
				isCustomPaint = true;
			} else
				paint = styles.get("base");
		}
		
		protected void display(Canvas canvas) {
			if (paint == null) { // TODO this is SERIOUSLY fishy
				return;
			}
			
			float lineHeight = getLineHeight();
			float lineOffset = -getLayout().getLineDescent(0); //AH-HA! This is the metric that we need...
			float xOffset = getCompoundPaddingLeft(); //TODO hopefully no one uses Arabic (right-aligned localities)... because getCompoundPaddingStart() was introduced in a later API level
			float charWidth = getPaint().measureText("m");
			
			//Calculate coordinates
			float x = (xOffset + offset * charWidth);
			float y = lineOffset + lineHeight * (lineNum + 1);
			
			//Draw highlighted text
			canvas.drawText(text, x, y, paint);
		}
		
		protected void display(Canvas canvas, TextPaint customPaint) {
			float lineHeight = getLineHeight();
			float lineOffset = -getLayout().getLineDescent(0); //AH-HA! This is the metric that we need...
			float xOffset = getCompoundPaddingLeft(); //TODO hopefully no one uses Arabic (right-aligned localities)... because getCompoundPaddingStart() was introduced in a later API level
			float charWidth = getPaint().measureText("m");
			
			//Calculate coordinates
			float x = xOffset + offset * charWidth;
			float y = lineOffset + lineHeight * (lineNum + 1);
			
			//Draw highlighted text
			canvas.drawText(text, x, y, customPaint);
		}
	}
	
	protected void displayCompilerProblem(CompilerProblem problem, Canvas canvas) {
		float lineHeight = getLineHeight();
		float lineOffset = -getLayout().getLineDescent(0);
		float xOffset = getCompoundPaddingLeft();
		float charWidth = getPaint().measureText("m");
		
		float xStart = xOffset + problem.start * charWidth;
		float xEnd = xOffset + (problem.start + problem.length) * charWidth;
		float y = lineOffset + lineHeight * (problem.line + 1.2f);
		
		// TODO this is currently a line. Do we want to make it squiggly like in most IDEs?
		canvas.drawLine(xStart, y, xEnd, y, problem.error ? compilerErrorPaint : compilerWarningPaint);
	}
	
	public void refreshTextSize() {
		textSize = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString("textsize", "14"));
		float scaledTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSize, getResources().getDisplayMetrics());
		
		setTextSize(textSize);
		
		ArrayList<TextPaint> styleList = new ArrayList<TextPaint>(styles.values());
		
		for (TextPaint paint : styleList) {
			paint.setTextSize(scaledTextSize);
		}
		
		for (Keyword keyword : syntax) {
			if (keyword != null) { // TODO figure out what's going on here
				keyword.paint().setTextSize(scaledTextSize);
			}
		}
	}
	
	public void setUpdateText(String text) {
		super.setText(text);
	}
	
	public void setNoUndoText(String text) {
		FLAG_NO_UNDO_SNAPSHOT = true;
		super.setText(text);
		FLAG_NO_UNDO_SNAPSHOT = false;
	}
	
	/**
	 * Clear the list of tokens for the syntax highlighter.
	 * This function is used when tabs are switched so that the old syntax highlighting doesn't briefly show on top of the new code.
	 */
	public void clearTokens() {
		tokens = new Token[0];
		
		//Also clear the matching bracket...
		matchingBracket = -1;
		
		//...and also clear the highlight
		clearHighlights();
	}
	
	public Keyword getKeyword(String text, boolean function) {
		if (syntaxLoaded.get()) {
			for (Keyword keyword : syntax) {
				if (keyword != null) {
					if (keyword.name().equals(text) && keyword.function() == function) {
						return keyword;
					}
				}
			}
		}
		
		return null;
	}
}