package com.calsignlabs.apde.tool;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.view.MenuItem;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.CodeEditText;
import com.calsignlabs.apde.KeyBinding;
import com.calsignlabs.apde.Keyword;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.ReferenceActivity;

import java.util.Locale;

public class FindInReference implements Tool {
	public static final String PACKAGE_NAME = "com.calsignlabs.apde.tool.FindInReference";
	
	private APDE context;
	
	@Override
	public void init(APDE context) {
		this.context = context;
	}
	
	@SuppressLint("NewApi")
	@Override
	public void run() {
		// https://github.com/processing/processing/blob/516ceebf5ae199dd8d36e4ba7612bf1afc414994/app/src/processing/app/ui/Editor.java#L2268
		
		CodeEditText code = context.getCodeArea();
		char[] codeText = code.getText().toString().toCharArray();
		int start = code.getSelectionStart();
		int stop = code.getSelectionEnd();
		
		if (start == -1 || stop == -1) {
			return;
		}
		
		if (start == stop) {
			while (start > 0 && functionable(codeText[start - 1])) {
				start --;
			}
			while (stop < codeText.length && functionable(codeText[stop])) {
				stop ++;
			}
		}
		
		String text = new String(codeText, start, stop - start).trim();
		boolean function = checkParen(codeText, stop, codeText.length);
		
		String readableName = text + (function ? "()" : "");
		
		Keyword foundKeyword = null;
		
		// If the target is "" then the reference page doesn't exist
		if (text.length() > 0) {
			for (Keyword keyword : CodeEditText.getKeywords()) {
				if (keyword != null && keyword.name().equals(text) && keyword.function() == function) {
					foundKeyword = keyword;
					break;
				}
			}
		}
		
		if (foundKeyword == null || foundKeyword.getReferenceTarget().length() == 0) {
			context.getEditor().messageExt(String.format(Locale.US, context.getResources().getString(R.string.tool_find_in_reference_failure_not_found), readableName));
			return;
		}
		
		String urlBase = "";
		if (foundKeyword.getReferenceType().equals("processing")) urlBase = "http://processing.org/reference/";
		if (foundKeyword.getReferenceType().equals("processing_android")) urlBase = "http://android.processing.org/reference/environment/";
		
		final String uri = urlBase + foundKeyword.getReferenceTarget() + ".html";
		
		context.getEditor().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Intent intent = new Intent(context.getEditor(), ReferenceActivity.class);
				intent.putExtra("uri", uri);
				context.getEditor().startActivity(intent);
			}
		});
	}
	
	private boolean functionable(char c) {
		return (c == '_') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}
	
	private boolean checkParen(char[] array, int index, int stop) {
		while (index < stop) {
			switch (array[index]) {
				case '(':
					return true;
				case ' ':
				case '\t':
				case '\n':
				case '\r':
					index ++;
					break;
				default:
					return false;
			}
		}
		
		return false;
	}
	
	@Override
	public String getMenuTitle() {
		return context.getResources().getString(R.string.tool_find_in_reference);
	}
	
	@Override
	public KeyBinding getKeyBinding() {
		return null;
	}
	
	@Override
	public boolean showInToolsMenu(APDE.SketchLocation sketchLocation) {
		return false;
	}
	
	@SuppressLint("NewApi")
	@Override
	public boolean createSelectionActionModeMenuItem(MenuItem convert) {
		convert.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		
		return true;
	}
}
