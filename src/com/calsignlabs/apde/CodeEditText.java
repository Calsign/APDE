package com.calsignlabs.apde;

import java.io.IOException;
import java.util.HashMap;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import processing.core.PApplet;
import processing.data.XML;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

/*
 * Custom EditText for syntax highlighting and some other stuff
 */
public class CodeEditText extends EditText {
	private static Paint lineHighlight;
	private static Paint whitePaint;
	
	public static HashMap<String, TextPaint> styles;
	public static HashMap<String, TextPaint> syntax;
	
	//The default indentation (two spaces)
	public static final String indent = "  ";
	
	public CodeEditText(Context context) {
		super(context);
		init();
	}
	
	public CodeEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}
	
	public CodeEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}
	
	private void init() {
		//Create the line highlight Paint
		lineHighlight = new Paint();
		lineHighlight.setStyle(Paint.Style.FILL);
		lineHighlight.setColor(0x66AACCFF);
		
		//Create the white (cleared) paint
		whitePaint = new Paint();
		whitePaint.setStyle(Paint.Style.FILL);
		whitePaint.setColor(0xFFFFFFFF);
		
		//Initialize the list of styles
		styles = new HashMap<String, TextPaint>();
		
		//Initialize the syntax map
		syntax = new HashMap<String, TextPaint>();
		
		//Load the default syntax
		try {
			//Processing's XML is easier to work with... TODO maybe implement native XML?
			loadSyntax(new XML(getResources().getAssets().open("default_syntax_colors.xml")));
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		}
	}
	
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
			String hex = style.getString("color", "#00000000").substring(1);
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
			
			//If this isn't a valid style, bail out
			if(!styles.containsKey(style))
				continue;
			
			//Add the keyword
			syntax.put(name, styles.get(style));
		}
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
		int lastLineNum = getCurrentLine();
		
		boolean result = super.onKeyDown(keyCode, event);
		
		//Get the indentation of the previous line
		String[] lines = getText().toString().split("\n");
		String lastLine = lines[Math.min(lastLineNum, lines.length - 1)];
		String lastIndent = "";
		for(int i = 0; i < lastLine.length(); i ++) {
			if(lastLine.charAt(i) == ' ')
				lastIndent += ' ';
			else
				break;
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
	
	//Get the current line
	public int getCurrentLine() {    
		if(getSelectionStart() > -1)
			return getLayout().getLineForOffset(getSelectionStart());
		
		return -1;
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		int lineHeight = getLineHeight();
		int lineOffset = getCompoundPaddingTop() + 6; //TODO this hard-coded offset shouldn't be here, but we need it for some reason
		int currentLine = getCurrentLine();
		int xOffset = getCompoundPaddingLeft(); // TODO hopefully no one uses Arabic (right-aligned localities)
		
		//Get the width of the widest character ("m")... but this is monospace, anyway...
		float charWidth = this.getPaint().measureText("m");
		
		if(isFocused())
			//Draw line highlight around the line that the cursor is on
			canvas.drawRect(getScrollX(), lineOffset + currentLine * lineHeight, canvas.getWidth() + getScrollX(), lineOffset + (currentLine + 1) * lineHeight, lineHighlight);
		
		//Draw the base text
		super.onDraw(canvas);
		
		//Split the text into lines
		String[] lines = getText().toString().split("\n");
		int topVis = (int) Math.floor(getScrollY() / getLineHeight()); //inclusive
		int bottomVis = (int) Math.floor(Math.min((getScrollY() + getHeight()) / getLineHeight() + 1, lines.length)); //exclusive
		
		//Only highlight the text that's visible
		boolean multilineComment = false;
		for(int i = 0; i < bottomVis; i ++) {
			if(i >= topVis) {
				//Get the list of highlight-able items
				String[] items = PApplet.splitTokens(lines[i], "()[]{}=+-/*%&|?:;<>,. "); //TODO is this all of them?
				
				//Highlight words
				syntaxHighlightForKeywords(lines[i], i, items, canvas);
				
				int commentStart = -1;
				
				//Test for comments
				if(lines[i].indexOf('/') != -1) {
					//Test for single-line ("//") comments
					{
						String test = lines[i];
						int offset = 0;
						while(commentStart == -1) {
							//Get the text to the right of the first slash ("/") character
							int first = test.indexOf("/");
							offset += first;
							test = test.substring(first + 1, test.length());
							
							//If there cannot be a comment in this line - breaks out of the loop
							if(test.length() == 0 || test.indexOf('/') == -1)
								break;
							
							//Test to see if the slashes are inside a String literal
							int numQuotesBefore = lines[i].substring(0, offset + 1).split("\"").length - 1;
							if(numQuotesBefore % 2 == 1)
								continue;
							
							//If the next character is also a slash - breaks out of the loop
							if(test.charAt(0) == '/')
								commentStart = offset;
						}
						
						//Highlight single-line comment
						if(commentStart != -1) {
							//Calculate coordinates
							float x = (xOffset + commentStart * charWidth);
							float y = lineOffset - 10 + (i + 1) * lineHeight;
							
							//TODO clear the original text... this is close enough for now
							
							//Draw highlighted text
							canvas.drawText(lines[i].substring(commentStart, lines[i].length()), x, y, styles.get("comment_single"));
						}
					}
					
					//Test for multi-line ("/*") comments //TODO multiple multi-line comments per line
					{
						int multilineCommentStart = -1;
						
						String test = lines[i];
						int offset = 0;
						while(multilineCommentStart == -1) {
							//If there cannot be a comment in this line - breaks out of the loop
							if(test.length() == 0 || test.indexOf("/*") == -1)
								break;
							
							//Get the text to the right of the first slash ("/") character
							int first = test.indexOf("/");
							offset += first;
							test = test.substring(first + 1, test.length());
							
							//Test to see if the slashes are inside a String literal
							int numQuotesBefore = lines[i].substring(0, offset + 1).split("\"").length - 1;
							if(numQuotesBefore % 2 == 1)
								continue;
							
							//If the next character is an asterisk - breaks out of the loop
							if(test.charAt(0) == '*')
								multilineCommentStart = offset;
						}
						
						//Highlight multi-line comment
						if(multilineCommentStart != -1 && (commentStart == -1 ? true : multilineCommentStart < commentStart)) {
							//Check to see if the multi-line comment ends on this line (kind an oxymoron, isn't it?)
							int commentEnd = test.indexOf("*/") + offset;
							
							//Calculate coordinates
							float x = (xOffset + multilineCommentStart * charWidth);
							float y = lineOffset - 10 + (i + 1) * lineHeight;
							
							//TODO clear the original text... this is close enough for now
							
							//Draw highlighted text
							canvas.drawText(lines[i].substring(multilineCommentStart, commentEnd == offset - 1 ? lines[i].length() : commentEnd + 3), x, y, styles.get("comment_multi"));
							
							if(commentEnd == offset - 1)
								multilineComment = true;
							
							commentStart = Math.min(commentStart, multilineCommentStart);
						}
					}
				}
				
				//Highlight all String literals
				if(lines[i].indexOf("\"") != -1) {
					String test = lines[i];
					int offset = 0;
					while(test.indexOf("\"") != -1) {
						//If there cannot be another String literal - breaks out of the loop
						if(test.length() == 0 || (commentStart != -1 && offset >= commentStart))
							break;
						
						//Get the text to the right of the first quote
						int first = test.indexOf("\"");
						int last = 0;
						if(test.length() > first + 1) {
							String rest = test.substring(first + 1, test.length());
							last = rest.indexOf("\"") + first + 1;
						}
						
						//If this String literal doesn't contain a closing quote
						boolean incomplete = false;
						
						if(last <= first) {
							 last = test.length();
							 incomplete = true;
						}
						
						if(last > test.length())
							last = test.length();
						
						//If this is in a comment, bail out
						if(commentStart != -1 && last + offset >= commentStart)
							break;
						
						//Calculate coordinates
						float x = (xOffset + (first + offset) * charWidth);
						float y = lineOffset - 10 + (i + 1) * lineHeight;
						
						//TODO clear the original text... this is close enough for now
						
						//Draw highlighted text
						canvas.drawText(lines[i].substring(first + offset, last + offset + (incomplete ? 0 : 1)), x, y, incomplete ? styles.get("literal_incomplete") : styles.get("literal"));
						
						//Get the remainder of the line
						if(test.length() > last + 1) {
							offset += last + 1;
							test = test.substring(last + 1, test.length());
						} else
							break;
					}
				}
				
				if(multilineComment) {
					//Get the end of the comment (if it's on this line)
					int commentEnd = lines[i].indexOf("*/");
					
					//Calculate coordinates
					float x = xOffset;
					float y = lineOffset - 10 + (i + 1) * lineHeight;
					
					//TODO clear the original text... this is close enough for now
					
					//Draw highlighted text
					canvas.drawText(lines[i].substring(0, commentEnd == -1 ? lines[i].length() : commentEnd + 2), x, y, styles.get("comment_multi"));
					
					if(commentEnd != -1)
						multilineComment = false;
				}
			} else { //This is for the lines that don't show on the screen but still need to be checked for multi-line comments that carry over
				int commentStart = -1;
				
				//Test for single-line comment
				{
					String test = lines[i];
					int offset = 0;
					while(commentStart == -1) {
						//Get the text to the right of the first slash ("/") character
						int first = test.indexOf("/");
						offset += first;
						test = test.substring(first + 1, test.length());
						
						//If there cannot be a comment in this line - breaks out of the loop
						if(test.length() == 0 || test.indexOf('/') == -1)
							break;
						
						//Test to see if the slashes are inside a String literal
						int numQuotesBefore = lines[i].substring(0, offset + 1).split("\"").length - 1;
						if(numQuotesBefore % 2 == 1)
							continue;
						
						//If the next character is also a slash - breaks out of the loop
						if(test.charAt(0) == '/')
							commentStart = offset;
					}
				}
				
				if(lines[i].indexOf("/*") != -1) {
					int multilineCommentStart = -1;
					
					String test = lines[i];
					int offset = 0;
					while(multilineCommentStart == -1) {
						//Get the text to the right of the first slash ("/") character
						int first = test.indexOf("/");
						offset += first;
						test = test.substring(first + 1, test.length());
						
						//If there cannot be a comment in this line - breaks out of the loop
						if(test.length() == 0 || (test.indexOf('/') == -1 && test.indexOf('*') == -1))
							break;
						
						//Test to see if the slashes are inside a String literal
						int numQuotesBefore = lines[i].substring(0, offset + 1).split("\"").length - 1;
						if(numQuotesBefore % 2 == 1)
							continue;
						
						//If the next character is an asterisk - breaks out of the loop
						if(test.charAt(0) == '*')
							multilineCommentStart = offset;
					}
					
					if(multilineCommentStart != -1 && (commentStart == -1 ? true : multilineCommentStart < commentStart)) {
						//Check to see if the multi-line comment ends on this line (kind an oxymoron, isn't it?)
						int commentEnd = test.indexOf("*/") + offset;
						
						if(commentEnd == offset - 1)
							multilineComment = true;
					}
				}
				
				//Check to see if the multi-line comment has ended
				if(multilineComment) {
					//Get the end of the comment (if it's on this line)
					int commentEnd = lines[i].indexOf("*/");
					
					if(commentEnd != -1)
						multilineComment = false;
				}
			}
		}
	}
	
	private void syntaxHighlightForKeywords(String line, int lineNum, String[] items, Canvas canvas) {
		int lineHeight = getLineHeight();
		int lineOffset = getCompoundPaddingTop() + 6; //TODO this hard-coded offset shouldn't be here, but we need it for some reason
		int xOffset = getCompoundPaddingLeft(); // TODO hopefully no one uses Arabic (right-aligned localities)
		
		//Get the width of the widest character ("m")... but this is monospace, anyway...
		float charWidth = this.getPaint().measureText("m");
		
		//Iterations of each keyword
		HashMap<String, Integer> iter = new HashMap<String, Integer>();
		
		for(int i = 0; i < items.length; i ++) {
			String item = items[i];
			
			if(syntax.containsKey(item)) {
				TextPaint paint;
				
				try {
					paint = syntax.get(item);
				} catch(Exception e) {
					//A custom syntax template is badly formed
					//TODO send message to user
					e.printStackTrace();
					continue;
				}
					
				int it;
				
				if(iter.containsKey(item))
					it = iter.get(item);
				else
					it = 0;
				
				//Get the offset of the keyword
				int pos = recursiveSubstringIndexOf(line, it, item);
				
				//Calculate coordinates
				float x = (xOffset + pos * charWidth);
				float y = lineOffset - 10 + (lineNum + 1) * lineHeight;
				
				//TODO clear the original text... this is close enough for now
				
				//Draw highlighted text
				canvas.drawText(item, x, y, paint);
				
				Integer num = Integer.valueOf(it + 1);
				iter.put(item, num);
			}
		}
	}
	
	//Find the index of the given sequence "ind" in the String "string" at offset "iter"
	private int recursiveSubstringIndexOf(String string, int iter, String ind) {
		String str = string;
		int it = iter;
		
		int offset = 0;
		while(it > -1) {
			int pos = str.indexOf(ind) + 1;
			offset += pos;
			str = str.substring(pos);
			it --;
		}
		
		return offset - 1;
	}
}