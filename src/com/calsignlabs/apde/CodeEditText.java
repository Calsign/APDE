package com.calsignlabs.apde;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import processing.core.PApplet;
import processing.data.XML;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.ScrollView;

/**
 * Custom EditText for syntax highlighting, auto-indent, etc.
 */
public class CodeEditText extends EditText {
	private Context context;
	private float textSize = 14;
	
	//Paints that will always be used
	private static Paint lineHighlight;
	private static Paint blackPaint;
	private static Paint whitePaint;
	
	//Lists of styles
	public static HashMap<String, TextPaint> styles;
	public static ArrayList<Keyword> syntax;
	
	//The default indentation (two spaces)
	public static final String indent = "  ";
	
	protected Token[] tokens;
	
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
		//Get rid of extra spacing at the top and bottom
		setIncludeFontPadding(false);
		
		//Create the line highlight Paint
		lineHighlight = new Paint();
		lineHighlight.setStyle(Paint.Style.FILL);
		lineHighlight.setColor(0x66AACCFF);
		
		//Create the black (default text) paint
		blackPaint = new Paint();
		blackPaint.setStyle(Paint.Style.FILL);
		blackPaint.setColor(0xFF000000);
		
		//Create the white (cleared) paint
		whitePaint = new Paint();
		whitePaint.setStyle(Paint.Style.FILL);
		whitePaint.setColor(0xFFFFFFFF);
		
		//Initialize the list of styles
		styles = new HashMap<String, TextPaint>();
		
		//Initialize the syntax map
		syntax = new ArrayList<Keyword>();
		
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
	
	public void setupTextListener() {
		addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable editable) {}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				
			}
			
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				updateTokens();
			}
		});
		
		updateTokens();
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
		for(XML keyword : keywords) {
			//Make sure that this is a "keyword" element
			if(!keyword.getName().equals("keyword"))
				continue;
			
			//Parse the keyword
			String style = keyword.getString("style", "");
			String name = keyword.getContent();
			boolean function = keyword.getString("function", "false").equals("true");
			
			//If this isn't a valid style, bail out
			if(!styles.containsKey(style))
				continue;
			
			//Add the keyword
			syntax.add(new Keyword(name, styles.get(style), function));
		}
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
		int lastLineNum = getCurrentLine();
		
		//Make sure to forward the result of what would normally happen
		boolean result = super.onKeyDown(keyCode, event);
		
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
		
		//Determine the last character of the previous line
		char lastChar = ' ';
		if(lastLine.length() > 0)
			lastChar= lastLine.charAt(lastLine.length() - 1);
		
		//Automatically increase the indent if this is a new code block
		if(lastChar == '{')
			lastIndent += indent;
		
		//Automatically indent
		if(keyCode == KeyEvent.KEYCODE_ENTER)
			getText().insert(getSelectionStart(), lastIndent);
		
    	return result;
    }
	
	/**
	 * @return the number of the currently selected line
	 */
	public int getCurrentLine() {
		if(getSelectionStart() > -1)
			return getLayout().getLineForOffset(getSelectionStart());
		
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
		
		//We don't want to return values that are too big
		if(off >= getText().length())
			off = getText().length() - 1;
		
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
		if(off >= getText().length())
			off = getText().length() - 1;
		
		return off;
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		float lineHeight = getLineHeight();
		int currentLine = getCurrentLine();
		
		if(isFocused())
			//Draw line highlight around the line that the cursor is on
			canvas.drawRect(getScrollX(), currentLine * lineHeight, canvas.getWidth() + getScrollX(), (currentLine + 1) * lineHeight, lineHighlight);
		
		//Draw base text
		super.onDraw(canvas);
		
		boolean newSyntaxHighlighter = true;
		
		if(newSyntaxHighlighter && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("syntax_highlight", true)) {
			ScrollView scroller = (ScrollView) ((APDE) context.getApplicationContext()).getEditor().findViewById(R.id.code_scroller);
			int topVis = (int) Math.max(scroller.getScrollY() / getLineHeight() - 1, 0); //inclusive
			int bottomVis = (int) Math.floor(Math.min((scroller.getScrollY() + scroller.getHeight()) / getLineHeight() + 1, getLineCount())); //exclusive
			
			for(int i = 0; i < tokens.length; i ++) {
				//Only draw this if we need to
				if(tokens[i].lineNum >= topVis && tokens[i].isCustomPaint)
					tokens[i].display(canvas);
				else if(tokens[i].lineNum > bottomVis)
					break;
			}
		}
		
		//Below, commented out, is the old (broken in spots, but faster) syntax highlighter
		
//		float xOffset = getCompoundPaddingLeft(); //TODO hopefully no one uses Arabic (right-aligned localities)... because getCompoundPaddingStart() was introduced in a later API level
//		float lineOffset = -getLayout().getLineDescent(0); //AH-HA! This is the metric that we need...
		//Get the width of the widest character ("m")... but this is monospace, anyway...
//		float charWidth = this.getPaint().measureText("m");
		
//		//Syntax highlight TODO this is still very buggy
//		if(!newSyntaxHighlighter && PreferenceManager.getDefaultSharedPreferences(context).getBoolean("syntax_highlight", true)) {
//			//Split the text into lines
//			String[] lines = getText().toString().split("\n");
//			int topVis = (int) Math.floor(getScrollY() / getLineHeight()); //inclusive
//			int bottomVis = (int) Math.floor(Math.min((getScrollY() + getHeight()) / getLineHeight() + 1, lines.length)); //exclusive
//			
//			//Only highlight the text that's visible
//			boolean multilineComment = false;
//			for(int i = 0; i < bottomVis; i ++) {
//				if(i >= topVis) {
//					//Get the list of highlight-able items
//					String[] items = PApplet.splitTokens(lines[i], "()[]{}=+-/*%&|?:;<>,. "); //TODO is this all of them?
//					
//					//Highlight words
//					syntaxHighlightForKeywords(lines[i], i, items, canvas);
//					
//					int commentStart = -1;
//					
//					//Test for comments
//					if(lines[i].indexOf('/') != -1) {
//						//Test for single-line ("//") comments
//						{
//							String test = lines[i];
//							int offset = 0;
//							while(commentStart == -1) {
//								//Get the text to the right of the first slash ("/") character
//								int first = test.indexOf("/");
//								offset += first;
//								test = test.substring(first + 1, test.length());
//								
//								//If there cannot be a comment in this line - breaks out of the loop
//								if(test.length() == 0 || test.indexOf('/') == -1)
//									break;
//								
//								//Test to see if the slashes are inside a String literal
//								int numQuotesBefore = lines[i].substring(0, offset + 1).split("\"").length - 1;
//								if(numQuotesBefore % 2 == 1)
//									continue;
//								
//								//If the next character is also a slash - breaks out of the loop
//								if(test.charAt(0) == '/')
//									commentStart = offset;
//							}
//							
//							//Highlight single-line comment
//							if(commentStart != -1) {
//								//Calculate coordinates
//								float x = (xOffset + commentStart * charWidth);
//								float y = lineOffset - 10 + (i + 1) * lineHeight;
//								
//								//TODO clear the original text... this is close enough for now
//								
//								//Draw highlighted text
//								canvas.drawText(lines[i].substring(commentStart, lines[i].length()), x, y, styles.get("comment_single"));
//							}
//						}
//						
//						//Test for multi-line ("/*") comments //TODO multiple multi-line comments per line
//						{
//							int multilineCommentStart = -1;
//							
//							String test = lines[i];
//							int offset = 0;
//							while(multilineCommentStart == -1) {
//								//If there cannot be a comment in this line - breaks out of the loop
//								if(test.length() == 0 || test.indexOf("/*") == -1)
//									break;
//								
//								//Get the text to the right of the first slash ("/") character
//								int first = test.indexOf("/");
//								offset += first;
//								test = test.substring(first + 1, test.length());
//								
//								//Test to see if the slashes are inside a String literal
//								int numQuotesBefore = lines[i].substring(0, offset + 1).split("\"").length - 1;
//								if(numQuotesBefore % 2 == 1)
//									continue;
//								
//								//If the next character is an asterisk - breaks out of the loop
//								if(test.charAt(0) == '*')
//									multilineCommentStart = offset;
//							}
//							
//							//Highlight multi-line comment
//							if(multilineCommentStart != -1 && (commentStart == -1 ? true : multilineCommentStart < commentStart)) {
//								//Check to see if the multi-line comment ends on this line (kind an oxymoron, isn't it?)
//								int commentEnd = test.indexOf("*/") + offset;
//								
//								//Calculate coordinates
//								float x = (xOffset + multilineCommentStart * charWidth);
//								float y = lineOffset - 10 + (i + 1) * lineHeight;
//								
//								//TODO clear the original text... this is close enough for now
//								
//								//Draw highlighted text
//								canvas.drawText(lines[i].substring(multilineCommentStart, commentEnd == offset - 1 ? lines[i].length() : commentEnd + 3), x, y, styles.get("comment_multi"));
//								
//								if(commentEnd == offset - 1)
//									multilineComment = true;
//								
//								commentStart = Math.min(commentStart, multilineCommentStart);
//							}
//						}
//					}
//					
//					//Highlight all String literals
//					if(lines[i].indexOf("\"") != -1) {
//						String test = lines[i];
//						int offset = 0;
//						while(test.indexOf("\"") != -1) {
//							//If there cannot be another String literal - breaks out of the loop
//							if(test.length() == 0 || (commentStart != -1 && offset >= commentStart))
//								break;
//							
//							//Get the text to the right of the first quote
//							int first = test.indexOf("\"");
//							int last = 0;
//							if(test.length() > first + 1) {
//								String rest = test.substring(first + 1, test.length());
//								last = rest.indexOf("\"") + first + 1;
//							}
//							
//							//If this String literal doesn't contain a closing quote
//							boolean incomplete = false;
//							
//							if(last <= first) {
//								 last = test.length();
//								 incomplete = true;
//							}
//							
//							if(last > test.length())
//								last = test.length();
//							
//							//If this is in a comment, bail out
//							if(commentStart != -1 && last + offset >= commentStart)
//								break;
//							
//							//Calculate coordinates
//							float x = (xOffset + (first + offset) * charWidth);
//							float y = lineOffset - 10 + (i + 1) * lineHeight;
//							
//							//TODO clear the original text... this is close enough for now
//							
//							//Draw highlighted text
//							canvas.drawText(lines[i].substring(first + offset, last + offset + (incomplete ? 0 : 1)), x, y, incomplete ? styles.get("literal_string_incomplete") : styles.get("literal_string"));
//							
//							//Get the remainder of the line
//							if(test.length() > last + 1) {
//								offset += last + 1;
//								test = test.substring(last + 1, test.length());
//							} else
//								break;
//						}
//					}
//					
//					if(multilineComment) {
//						//Get the end of the comment (if it's on this line)
//						int commentEnd = lines[i].indexOf("*/");
//						
//						//Calculate coordinates
//						float x = xOffset;
//						float y = lineOffset - 10 + (i + 1) * lineHeight;
//						
//						//TODO clear the original text... this is close enough for now
//						
//						//Draw highlighted text
//						canvas.drawText(lines[i].substring(0, commentEnd == -1 ? lines[i].length() : commentEnd + 2), x, y, styles.get("comment_multi"));
//						
//						if(commentEnd != -1)
//							multilineComment = false;
//					}
//				} else { //This is for the lines that don't show on the screen but still need to be checked for multi-line comments that carry over
//					int commentStart = -1;
//					
//					//Test for single-line comment
//					{
//						String test = lines[i];
//						int offset = 0;
//						while(commentStart == -1) {
//							//Get the text to the right of the first slash ("/") character
//							int first = test.indexOf("/");
//							offset += first;
//							test = test.substring(first + 1, test.length());
//							
//							//If there cannot be a comment in this line - breaks out of the loop
//							if(test.length() == 0 || test.indexOf('/') == -1)
//								break;
//							
//							//Test to see if the slashes are inside a String literal
//							int numQuotesBefore = lines[i].substring(0, offset + 1).split("\"").length - 1;
//							if(numQuotesBefore % 2 == 1)
//								continue;
//							
//							//If the next character is also a slash - breaks out of the loop
//							if(test.charAt(0) == '/')
//								commentStart = offset;
//						}
//					}
//					
//					if(lines[i].indexOf("/*") != -1) {
//						int multilineCommentStart = -1;
//						
//						String test = lines[i];
//						int offset = 0;
//						while(multilineCommentStart == -1) {
//							//Get the text to the right of the first slash ("/") character
//							int first = test.indexOf("/");
//							offset += first;
//							test = test.substring(first + 1, test.length());
//							
//							//If there cannot be a comment in this line - breaks out of the loop
//							if(test.length() == 0 || (test.indexOf('/') == -1 && test.indexOf('*') == -1))
//								break;
//							
//							//Test to see if the slashes are inside a String literal
//							int numQuotesBefore = lines[i].substring(0, offset + 1).split("\"").length - 1;
//							if(numQuotesBefore % 2 == 1)
//								continue;
//							
//							//If the next character is an asterisk - breaks out of the loop
//							if(test.charAt(0) == '*')
//								multilineCommentStart = offset;
//						}
//						
//						if(multilineCommentStart != -1 && (commentStart == -1 ? true : multilineCommentStart < commentStart)) {
//							//Check to see if the multi-line comment ends on this line (kind an oxymoron, isn't it?)
//							int commentEnd = test.indexOf("*/") + offset;
//							
//							if(commentEnd == offset - 1)
//								multilineComment = true;
//						}
//					}
//					
//					//Check to see if the multi-line comment has ended
//					if(multilineComment) {
//						//Get the end of the comment (if it's on this line)
//						int commentEnd = lines[i].indexOf("*/");
//						
//						if(commentEnd != -1)
//							multilineComment = false;
//					}
//				}
//			}
//		}
	}
	
	public void updateTokens() {
		if(!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("syntax_highlight", true))
			return;
		
		tokens = splitTokens(getText().toString(), 0, new char[] {'(', ')', '[', ']', '{', '}', '=', '+', '-', '/', '*', '"', '\'', '%', '&', '|', '?', ':', ';', '<', '>', ',', '.', ' '});
		
		for(int i = 0; i < tokens.length; i ++) {
			String nextNonSpace = "";
			for(int j = i + 1; j < tokens.length; j ++) {
				String next = tokens[j].text;
				
				if(next.equals(" ") || next.equals("\n"))
					continue;
				
				nextNonSpace = next;
				break;
			}
						
			tokens[i].updatePaint(nextNonSpace);
		}
		
		boolean multiLineComment = false;
		boolean singleLineComment = false;
		boolean stringLiteral = false;
		boolean charLiteral = false;
		
		int startLiteral = -1;
		
		String prev = "";
		String next;
		
		for(int i = 0; i < tokens.length; i ++) {
			Token token = tokens[i];
			next = (i < tokens.length - 1 ? tokens[i + 1].text : "");
			
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
	}
	
	//Unfortunately, I don't think this can work...
	//...but I seem to be leaving a lot of old code around, so what the heck
//	public void updateTokens(int start, int before, int count) {
//		if(!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("syntax_highlight", true))
//			return;
//		
//		int firstIndex = -1;
//		int lastIndex = -1;
//		
//		//Update all affected lines TODO Theoretically, we can do better than this... but that's a challenge for later
//		int firstPos = getText().toString().substring(0, start).lastIndexOf('\n') + 1;
//		int lastPos = getText().toString().substring(start).indexOf('\n');
//		
//		if(firstPos < 0)
//			firstPos = 0;
//		if(lastPos < 0 || lastPos < firstPos)
//			lastPos = getText().toString().length();
//		
//		int off = 0;
//		for(int i = 0; i < tokens.length; i ++) {
//			off += tokens[i].text.length();
//			
//			if(off >= firstPos && firstIndex < 0)
//				firstIndex = i;
//			if(off > lastPos) {
//				lastIndex = i;
//				break;
//			}
//		}
//		
//		if(firstIndex < 0)
//			firstIndex = 0;
//		if(lastIndex < 0)
//			lastIndex = tokens.length - 1;
//		
//		String newText = getText().toString().substring(firstPos, lastPos);
//		
//		Token[] firstHalf = Arrays.copyOfRange(tokens, 0, firstIndex);
//		Token[] newTokens = splitTokens(newText, tokens[firstIndex].lineNum, new char[] {'(', ')', '[', ']', '{', '}', '=', '+', '-', '/', '*', '"', '\'', '%', '&', '|', '?', ':', ';', '<', '>', ',', '.', ' '});
//		Token[] lastHalf = Arrays.copyOfRange(tokens, lastIndex, tokens.length);
//		
//		for(int i = 0; i < newTokens.length; i ++) {
//			String nextNonSpace = "";
//			for(int j = i + 1; j < newTokens.length; j ++) {
//				String next = newTokens[j].text;
//				
//				if(next.equals(" ") || next.equals("\n"))
//					continue;
//				
//				nextNonSpace = next;
//				break;
//			}
//						
//			newTokens[i].updatePaint(nextNonSpace);
//		}
//		
//		tokens = concat(concat(firstHalf, newTokens), lastHalf);
//		
//		System.out.println("Updated: " + firstPos + " to " + lastPos);
//	}
	
	//Concatenate two Token arrays
//	private Token[] concat(Token[] a, Token[] b) {
//		int aLen = a.length;
//		int bLen = b.length;
//		Token[] c = new Token[aLen + bLen];
//		System.arraycopy(a, 0, c, 0, aLen);
//		System.arraycopy(b, 0, c, aLen, bLen);
//		return c;
//	}
	
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
			if(keyword != null) {
				paint = keyword.paint();
				isCustomPaint = true;
			} else
				paint = styles.get("base");
		}
		
		protected void display(Canvas canvas) {
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
			float x = (xOffset + offset * charWidth);
			float y = lineOffset + lineHeight * (lineNum + 1);
			
			//Draw highlighted text
			canvas.drawText(text, x, y, customPaint);
		}
	}
	
//	private void highlightToken(Token token, float offsetY, Canvas canvas) {
//		int xOffset = getCompoundPaddingLeft(); //TODO hopefully no one uses Arabic (right-aligned localities)
//		float charWidth = getPaint().measureText("m");
//		
//		//Set default paint
//		TextPaint paint;
//		
//		//Load custom paint
//		TextPaint highlightPaint = syntax.get(token.text);
//		if(highlightPaint != null)
//			paint = highlightPaint;
//		else
//			paint = styles.get("base");
//		
//		//Calculate coordinates
//		float x = (xOffset + token.offset * charWidth);
//		float y = offsetY;
//		
//		//Draw highlighted text
//		canvas.drawText(token.text, x, y, paint);
//	}
//	
//	private void highlightToken(Token token, float offsetY, Canvas canvas, TextPaint paint) {
//		int xOffset = getCompoundPaddingLeft(); //TODO hopefully no one uses Arabic (right-aligned localities)
//		float charWidth = this.getPaint().measureText("m");
//		
//		//Calculate coordinates
//		float x = (xOffset + token.offset * charWidth);
//		float y = offsetY;
//		
//		//Draw highlighted text
//		canvas.drawText(token.text, x, y, paint);
//	}
	
//	/**
//	 * Utility function, highlights a specific line with all matches of all keywords
//	 * 
//	 * @param line
//	 * @param lineNum
//	 * @param items
//	 * @param canvas
//	 */
//	private void syntaxHighlightForKeywords(String line, int lineNum, String[] items, Canvas canvas) {
//		int lineHeight = getLineHeight();
//		int lineOffset = Math.round(PApplet.map(textSize, 14, 32, 6, 0)); //TODO why does this work?
//		int xOffset = getCompoundPaddingLeft(); //TODO hopefully no one uses Arabic (right-aligned localities)
//		
//		//Get the width of the widest character ("m")... but this is monospace, anyway...
//		float charWidth = this.getPaint().measureText("m");
//		
//		//Iterations of each keyword
//		HashMap<String, Integer> iter = new HashMap<String, Integer>();
//		
//		for(int i = 0; i < items.length; i ++) {
//			String item = items[i];
//			
//			if(syntax.containsKey(item)) {
//				TextPaint paint;
//				
//				try {
//					paint = syntax.get(item);
//				} catch(Exception e) {
//					//A custom syntax template is badly formed
//					//TODO send message to user
//					e.printStackTrace();
//					continue;
//				}
//					
//				int it;
//				
//				if(iter.containsKey(item))
//					it = iter.get(item);
//				else
//					it = 0;
//				
//				//Get the offset of the keyword
//				int pos = recursiveSubstringIndexOf(line, it, item);
//				
//				//Calculate coordinates
//				float x = (xOffset + pos * charWidth);
//				float y = lineOffset - 10 + (lineNum + 1) * lineHeight;
//				
//				//TODO clear the original text... this is close enough for now
//				
//				//Draw highlighted text
//				canvas.drawText(item, x, y, paint);
//				
//				Integer num = Integer.valueOf(it + 1);
//				iter.put(item, num);
//			}
//		}
//	}
	
//	//Find the index of the given sequence "ind" in the String "string" at offset "iter"
//	private int recursiveSubstringIndexOf(String string, int iter, String ind) {
//		String str = string;
//		int it = iter;
//		
//		int offset = 0;
//		while(it > -1) {
//			int pos = str.indexOf(ind) + 1;
//			offset += pos;
//			str = str.substring(pos);
//			it --;
//		}
//		
//		return offset - 1;
//	}
	
	public void refreshTextSize() {
		textSize = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString("textsize", "14"));
		float scaledTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSize, getResources().getDisplayMetrics());
		
		setTextSize(textSize);
		
		ArrayList<TextPaint> styleList = new ArrayList<TextPaint>(styles.values());
		
		for(TextPaint paint : styleList)
			paint.setTextSize(scaledTextSize);
		
		for(Keyword keyword : syntax)
			keyword.paint().setTextSize(scaledTextSize);
	}
	
	public void commentSelection() {
		//TODO
	}
	
	public void shiftLeft() {
		//TODO
	}
	
	public void shiftRight() {
		//TODO
	}
	
	public void setUpdateText(String text) {
		super.setText(text);
	}
	
	public Keyword getKeyword(String text, boolean function) {
		for(Keyword keyword : syntax)
			if(keyword.name().equals(text) && keyword.function() == function)
				return keyword;
		
		return null;
	}
}