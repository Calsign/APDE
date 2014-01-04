package com.calsignlabs.apde;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;

/*
 * Utility class for storing information about files
 * This started out as a meta... but is has grown to incorporate far more information than that
 */
public class FileMeta {
	private String title;
	private String suffix;
	
	private String text;
	
	private int selectionStart;
	private int selectionEnd;
	
	private boolean enabled;
	
	private int preprocOffset;
	
	public FileMeta(String title, String text, int selectionStart, int selectionEnd) {
		this.setTitle(title);
		setSuffix(".pde");
		
		this.text = text;
		this.selectionStart = selectionStart;
		this.selectionEnd = selectionEnd;
		
		enabled = true;
	}
	
	public void disable() {
		enabled = false;
	}
	
	public boolean enabled() {
		return enabled;
	}
	
	public String getFilename() {
		return title + suffix;
	}
	
	public String getTitle() {
		//If this isn't a PDE file, add the custom suffix to the title
		return suffix.equals(".pde") ? title : title + suffix;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSuffix() {
		return suffix;
	}

	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}
	
	public String getText() {
		return text;
	}
	
	public void setText(String text) {
		this.text = text;
	}

	public int getSelectionStart() {
		return selectionStart;
	}

	public int getSelectionEnd() {
		return selectionEnd;
	}
	
	public void setSelection(int selection) {
		selectionStart = selection;
		selectionEnd = selection;
	}
	
	public void setSelection(int selectionStart, int selectionEnd) {
		this.selectionStart = selectionStart;
		this.selectionEnd = selectionEnd;
	}
	
	public boolean writeData(Context context, String path) {
		BufferedOutputStream outputStream = null;
		String filename = path + title + suffix;
		boolean success;
		
		try {
			outputStream = new BufferedOutputStream(new FileOutputStream(filename));
			outputStream.write(text.getBytes());
			
			success = true;
		} catch (Exception e) {
			e.printStackTrace();
			
			success = false;
			
		} finally {
			if(outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return success;
	}
	
	public boolean writeDataTemp(Context context) {
		BufferedOutputStream outputStream = null;
		String filename = title + suffix;
		boolean success;
		
		try {
			outputStream = new BufferedOutputStream(context.openFileOutput(filename, Context.MODE_PRIVATE));
			outputStream.write(text.getBytes());
			
			success = true;
		} catch (Exception e) {
			e.printStackTrace();
			
			success = false;
			
		} finally {
			if(outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return success;
	}
	
	public boolean readData(Context context, String filename) {
		BufferedInputStream inputStream = null;
    	String output = "";
    	boolean success;
    	
		try {
			inputStream = new BufferedInputStream(new FileInputStream(filename));
			
			byte[] contents = new byte[1024];
			int bytesRead = 0;
			
			while((bytesRead = inputStream.read(contents)) != -1)
				output += new String(contents, 0, bytesRead);
			
			text = output;
			success = true;
		} catch(Exception e) {
			e.printStackTrace();
			
			success = false;
		} finally {
			try {
				if(inputStream != null)
					inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return success;
	}
	
	public boolean readTempData(Context context, String filename) {
		BufferedInputStream inputStream = null;
    	String output = "";
    	boolean success;
    	
		try {
			inputStream = new BufferedInputStream(context.openFileInput(filename));
			
			byte[] contents = new byte[1024];
			int bytesRead = 0;
			
			while((bytesRead = inputStream.read(contents)) != -1)
				output += new String(contents, 0, bytesRead);
			
			text = output;
			success = true;
		} catch(Exception e) {
			e.printStackTrace();
			
			success = false;
		} finally {
			try {
				if(inputStream != null)
					inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return success;
	}

	public int getPreprocOffset() {
		return preprocOffset;
	}

	public void setPreprocOffset(int preprocOffset) {
		this.preprocOffset = preprocOffset;
	}
	
	public void addPreprocOffset(int extra) {
		preprocOffset += extra;
	}
}