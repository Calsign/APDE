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
	//Filename meta
	private String title;
	private String suffix;
	
	//Content of the file
	private String text;
	
	//Current selection
	private int selectionStart;
	private int selectionEnd;
	
	//Whether or not we should save this (because we need this for some reason...?)
	private boolean enabled;
	
	//The offset of this file into the pre-processed, combined JAVA file
	private int preprocOffset;
	
	public FileMeta(String title, String text, int selectionStart, int selectionEnd) {
		setTitle(title);
		setSuffix(".pde");
		
		this.text = text;
		this.selectionStart = selectionStart;
		this.selectionEnd = selectionEnd;
		
		enabled = true;
	}
	
	/**
	 * Don't save this file (in addition to deleting it)
	 */
	public void disable() {
		enabled = false;
	}
	
	/**
	 * @return whether or not the file is to be saved
	 */
	public boolean enabled() {
		return enabled;
	}
	
	/**
	 * @return the filename as it is saved (name + suffix)
	 */
	public String getFilename() {
		return title + suffix;
	}
	
	/**
	 * @return the tab name as it is displayed in the GUI
	 */
	public String getTitle() {
		//If this isn't a PDE file, add the custom suffix to the title
		return suffix.equals(".pde") ? title : title + suffix;
	}
	
	/**
	 * @param title
	 */
	public void setTitle(String title) {
		this.title = title;
	}
	
	/**
	 * @return
	 */
	public String getSuffix() {
		return suffix;
	}
	
	/**
	 * @param suffix
	 */
	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}
	
	/**
	 * @return
	 */
	public String getText() {
		return text;
	}
	
	/**
	 * @param text
	 */
	public void setText(String text) {
		this.text = text;
	}
	
	/**
	 * @return
	 */
	public int getSelectionStart() {
		return selectionStart;
	}
	
	/**
	 * @return
	 */
	public int getSelectionEnd() {
		return selectionEnd;
	}
	
	/**
	 * @param selection
	 */
	public void setSelection(int selection) {
		selectionStart = selection;
		selectionEnd = selection;
	}
	
	/**
	 * @param selectionStart
	 * @param selectionEnd
	 */
	public void setSelection(int selectionStart, int selectionEnd) {
		this.selectionStart = selectionStart;
		this.selectionEnd = selectionEnd;
	}
	
	/**
	 * Writes this file in the directory specified by the path
	 * 
	 * @param context
	 * @param path
	 * @return success
	 */
	public boolean writeData(Context context, String path) {
		//Create the output stream
		BufferedOutputStream outputStream = null;
		String filename = path + title + suffix;
		boolean success;
		
		try {
			//Write the data
			outputStream = new BufferedOutputStream(new FileOutputStream(filename));
			outputStream.write(text.getBytes());
			
			success = true;
		} catch (Exception e) { //Errors
			e.printStackTrace();
			
			success = false;
			
		} finally {
			//Close the output stream
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
	
	/**
	 * Write this file to the temp directory
	 * 
	 * @param context
	 * @return success
	 */
	public boolean writeDataTemp(Context context) {
		//Create the output stream
		BufferedOutputStream outputStream = null;
		String filename = title + suffix;
		boolean success;
		
		try {
			//Write the data
			outputStream = new BufferedOutputStream(context.openFileOutput(filename, Context.MODE_PRIVATE));
			outputStream.write(text.getBytes());
			
			success = true;
		} catch (Exception e) { //Errors
			e.printStackTrace();
			
			success = false;
			
		} finally {
			//Close the output stream
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
	
	/**
	 * Loads data from the specified path into this meta object
	 * 
	 * @param context
	 * @param filename
	 * @return
	 */
	public boolean readData(Context context, String filename) {
		//Create the input stream
		BufferedInputStream inputStream = null;
    	String output = "";
    	boolean success;
    	
		try {
			//Read the data
			
			inputStream = new BufferedInputStream(new FileInputStream(filename));
			
			byte[] contents = new byte[1024];
			int bytesRead = 0;
			
			while((bytesRead = inputStream.read(contents)) != -1)
				output += new String(contents, 0, bytesRead);
			
			//Set the data
			text = output;
			
			success = true;
		} catch(Exception e) { //Errors
			e.printStackTrace();
			
			success = false;
		} finally {
			//Close the input stream
			try {
				if(inputStream != null)
					inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return success;
	}
	
	/**
	 * Loads data from the specified temp file into this meta object
	 * 
	 * @param context
	 * @param filename
	 * @return
	 */
	public boolean readTempData(Context context, String filename) {
		//Create the input stream
		BufferedInputStream inputStream = null;
    	String output = "";
    	boolean success;
    	
		try {
			//Load the data
			
			inputStream = new BufferedInputStream(context.openFileInput(filename));
			
			byte[] contents = new byte[1024];
			int bytesRead = 0;
			
			while((bytesRead = inputStream.read(contents)) != -1)
				output += new String(contents, 0, bytesRead);
			
			//Set the data
			text = output;
			
			success = true;
		} catch(Exception e) { //Errors
			e.printStackTrace();
			
			success = false;
		} finally {
			//Close the input stream
			try {
				if(inputStream != null)
					inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return success;
	}
	
	/**
	 * @return
	 */
	public int getPreprocOffset() {
		return preprocOffset;
	}
	
	/**
	 * @param preprocOffset
	 */
	public void setPreprocOffset(int preprocOffset) {
		this.preprocOffset = preprocOffset;
	}
	
	/**
	 * @param extra
	 */
	public void addPreprocOffset(int extra) {
		preprocOffset += extra;
	}
}