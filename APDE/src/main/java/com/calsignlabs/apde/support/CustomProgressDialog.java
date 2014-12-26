package com.calsignlabs.apde.support;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/*
 * This custom implementation allows us to change the progress numbers below the progress bar
 * 
 * Based on...
 * StackOverflow: http://stackoverflow.com/questions/2797894/progressdialog-how-to-remove-the-numbers
 */
public class CustomProgressDialog extends ProgressDialog {
	private int progressPercentVisibility = View.GONE;
	private int progressNumberVisibility = View.GONE;
	
	public CustomProgressDialog(Context context, int progressPercentVisibility, int progressNumberVisibility) {
		super(context);
		
		this.progressPercentVisibility = progressPercentVisibility;
		this.progressNumberVisibility = progressNumberVisibility;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setFieldVisibility("mProgressPercent", progressPercentVisibility);
		setFieldVisibility("mProgressNumber", progressNumberVisibility);
	}
	
	private void setFieldVisibility(String fieldName, int visibility) {
		try {
			Method method = TextView.class.getMethod("setVisibility", Integer.TYPE);
			
			TextView textView = (TextView) getField(fieldName).get(this);
			method.invoke(textView, visibility);
		} catch(Exception e) {}
	}
	
	private Field getField(String fieldName) {
		try {
			Field[] fields = this.getClass().getSuperclass().getDeclaredFields();
			
			for(Field field : fields) {
				if(field.getName().equalsIgnoreCase(fieldName)) {
					field.setAccessible(true);
					return field;
				}
			}
		} catch(Exception e) {}
		
		return null;
	}
	
	public void setProgressText(final String text) {
		if(text.length() > 0) {
			try {
				setFieldVisibility("mProgressPercent", View.VISIBLE);
				
				final TextView textView = (TextView) getField("mProgressPercent").get(this);
				
				textView.post(new Runnable() {
					public void run() {
						textView.setText(text);
					}
				});
			} catch(Exception e) {
			}
		} else {
			setFieldVisibility("mProgressPercent", progressPercentVisibility);
		}
	}
}