package com.calsignlabs.apde;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

/*
 * Custom EditText for syntax highlighting and some other stuff
 */
public class CodeEditText extends EditText {
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
		
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
		//TODO
		
		//Split text into lines
		/*
		String[] lines = (getText().toString()).split("\n");
		String output = "";
		
		for(int i = 0; i < lines.length; i ++) {
			String line = lines[i];
			
			output += line;
		}
		
		setText(Html.fromHtml(output), BufferType.SPANNABLE);
		*/
		
    	return super.onKeyDown(keyCode, event);
    }
	
	//Get the current line
	public int getCurrentLine() {    
		if(getSelectionStart() > -1)
			return getLayout().getLineForOffset(getSelectionStart());
		
		return -1;
	}
}