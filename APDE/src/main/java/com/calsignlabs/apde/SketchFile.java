package com.calsignlabs.apde;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;

/*
 * Utility class for storing information about files
 * This started out as a meta... but is has grown to incorporate far more information than that
 */
public class SketchFile implements Parcelable {
	//Filename meta
	private String title;
	private String suffix;
	
	//Content of the file
	//Current text is stored at the top of undoText
	//Undo is stored behind current text in undoText
	//Redo is stored in redoText
	private LinkedList<FileChange> undo;
	private LinkedList<FileChange> redo;
	
	//Whether or not we should save this (because we need this for some reason...?)
	private boolean enabled;
	
	//The offset of this file into the pre-processed, combined JAVA file
	private int preprocOffset;
	
	//Current text
	protected String text;
	
	//Current selection
	protected int selectionStart;
	protected int selectionEnd;
	
	//Current scroll position;
	protected int scrollX;
	protected int scrollY;
	
	protected CodeAreaFragment fragment;

	protected boolean isExample;
	
	public static class FileChange implements Parcelable {
		public int changeIndex;
		public String beforeText;
		public String afterText;
		
		//Current selection
		public int beforeSelectionStart;
		public int beforeSelectionEnd;

		public int afterSelectionStart;
		public int afterSelectionEnd;
		
		//Current scroll position;
		public int beforeScrollX;
		public int beforeScrollY;

		public int afterScrollX;
		public int afterScrollY;
		
		public FileChange() {}
		
		@Override
		public int describeContents() {
			return 0;
		}
		
		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeInt(changeIndex);
			dest.writeString(beforeText);
			dest.writeString(afterText);
			
			dest.writeInt(beforeSelectionStart);
			dest.writeInt(beforeSelectionEnd);
			
			dest.writeInt(afterSelectionStart);
			dest.writeInt(afterSelectionEnd);
			
			dest.writeInt(beforeScrollX);
			dest.writeInt(beforeScrollY);
			
			dest.writeInt(afterScrollX);
			dest.writeInt(afterScrollY);
		}
		
		private FileChange(Parcel source) {
			changeIndex = source.readInt();
			beforeText = source.readString();
			afterText = source.readString();
			
			beforeSelectionStart = source.readInt();
			beforeSelectionEnd = source.readInt();
			
			afterSelectionStart = source.readInt();
			afterSelectionEnd = source.readInt();
			
			beforeScrollX = source.readInt();
			beforeScrollY = source.readInt();
			
			afterScrollX = source.readInt();
			afterScrollY = source.readInt();
		}
		
		public static final Parcelable.Creator<FileChange> SOURCE = new Parcelable.Creator<FileChange>() {
			@Override
			public FileChange createFromParcel(Parcel source) {
				return new FileChange(source);
			}
			
			@Override
			public FileChange[] newArray(int size) {
				return new FileChange[size];
			}
		};
	}
	
	public SketchFile(String title) {
		initFragment();
		
		setTitle(title);
		setSuffix(".pde");
		
		undo = new LinkedList<FileChange>();
		redo = new LinkedList<FileChange>();
		
//		FileChange state = new FileChange();
//		state.changeIndex = 0;
//		state.beforeText = "";
//		state.afterText = "";
//		state.selectionStart = 0;
//		state.selectionEnd = 0;
//		state.scrollX = 0;
//		state.scrollY = 0;
//		
//		undo.push(state);
		
		text = "";
		selectionStart = 0;
		selectionEnd = 0;
		scrollX = 0;
		scrollY = 0;
		
		enabled = true;
	}
	
	public SketchFile(String title, EditorActivity context) {
		initFragment();
		
		EditText code = fragment.getCodeEditText();
		HorizontalScrollView scrollerX = fragment.getCodeScrollerX();
		ScrollView scrollerY = fragment.getCodeScroller();
		
		setTitle(title);
		setSuffix(".pde");
		
		undo = new LinkedList<FileChange>();
		redo = new LinkedList<FileChange>();
		
//		FileChange state = new FileChange();
//		state.changeIndex = 0;
//		state.beforeText = code.getText().toString();
//		state.afterText = code.getText().toString();
//		state.selectionStart = code.getSelectionStart();
//		state.selectionEnd = code.getSelectionEnd();
//		state.scrollX = scrollerX.getScrollX();
//		state.scrollY = scrollerY.getScrollY();
//		
//		undo.push(state);
		
		text = code.getText().toString();
		selectionStart = code.getSelectionStart();
		selectionEnd = code.getSelectionEnd();
		scrollX = scrollerX.getScrollX();
		scrollY = scrollerY.getScrollY();
		
		enabled = true;
		
//		System.out.println("setting tab " + title + ", scrollX: " + scrollX + ", scrollY: " + scrollY);
	}
	
	public SketchFile(String title, String text, int selectionStart, int selectionEnd, int scrollX, int scrollY) {
		initFragment();
		
		setTitle(title);
		setSuffix(".pde");
		
		undo = new LinkedList<FileChange>();
		redo = new LinkedList<FileChange>();
		
//		FileChange state = new FileChange();
//		state.changeIndex = 0;
//		state.beforeText = text;
//		state.afterText = text;
//		state.selectionStart = selectionStart;
//		state.selectionEnd = selectionEnd;
//		state.scrollX = scrollX;
//		state.scrollY = scrollY;
//		
//		undo.push(state);
		
		this.text = text;
		this.selectionStart = selectionStart;
		this.selectionEnd = selectionEnd;
		this.scrollX = scrollX;
		this.scrollY = scrollY;
		
		enabled = true;
	}
	
	public static void getTextChange(FileChange change, String oldText, String newText) {
		//Find the difference between the old text and the new text
		//Compare text starting at both ends to find the difference in the middle
		
		int changeStart = 0;
		int changeEnd = 0;
		
		int minLength = Math.min(oldText.length(), newText.length());
		
		while (changeStart < oldText.length() && changeStart < newText.length()
				&& oldText.charAt(changeStart) == newText.charAt(changeStart)) {
			changeStart ++;
		}
		
		while (changeEnd < oldText.length() && changeEnd < newText.length()
				&& minLength - changeEnd > changeStart
				&& oldText.charAt(oldText.length() - changeEnd - 1) == newText.charAt(newText.length() - changeEnd - 1)) {
			changeEnd ++;
		}
		
		int beforeEnd = oldText.length() - changeEnd;
		int afterEnd = newText.length() - changeEnd;
		
		change.changeIndex = changeStart;
		change.beforeText = beforeEnd - changeStart > 0 ? oldText.substring(changeStart, beforeEnd) : "";
		change.afterText = afterEnd - changeStart > 0 ? newText.substring(changeStart, afterEnd) : "";
	}
	
	public FileChange getFileChange() {
		EditText code = fragment.getCodeEditText();
		
		String codeText = code.getText().toString();
		
		if (!text.equals(codeText)) {
			HorizontalScrollView scrollerX = fragment.getCodeScrollerX();
			ScrollView scrollerY = fragment.getCodeScroller();
			
			FileChange change = new FileChange();
			
			getTextChange(change, text, codeText);
			
			change.beforeSelectionStart = selectionStart;
			change.beforeSelectionEnd = selectionEnd;
			
			change.afterSelectionStart = code.getSelectionStart();
			change.afterSelectionEnd = code.getSelectionEnd();
			
			change.beforeScrollX = scrollX;
			change.beforeScrollY = scrollY;
			
			change.afterScrollX = scrollerX.getScrollX();
			change.afterScrollY = scrollerY.getScrollY();
			
			return change;
		}
		
		return null;
	}
	
	public void update(EditorActivity context, boolean undoRedo) {
		if (undoRedo) {
			FileChange change = getFileChange();
			
			if (change != null) {
				clearRedo();
				undo.push(change);
				
				applyUndoRedoLimit(context);
				
				text = fragment.getCodeEditText().getText().toString();
				
				selectionStart = change.afterSelectionStart;
				selectionEnd = change.afterSelectionEnd;
				scrollX = change.afterScrollX;
				scrollY = change.afterScrollY;
				
				context.supportInvalidateOptionsMenu();
			} else {
				EditText code = fragment.getCodeEditText();
				HorizontalScrollView scrollerX = fragment.getCodeScrollerX();
				ScrollView scrollerY = fragment.getCodeScroller();
				
				selectionStart = code.getSelectionStart();
				selectionEnd = code.getSelectionEnd();
				
				scrollX = scrollerX.getScrollX();
				scrollY = scrollerY.getScrollY();
			}
		} else {
			EditText code = fragment.getCodeEditText();
			HorizontalScrollView scrollerX = fragment.getCodeScrollerX();
			ScrollView scrollerY = fragment.getCodeScroller();
			
			text = code.getText().toString();
			
			selectionStart = code.getSelectionStart();
			selectionEnd = code.getSelectionEnd();
			
			scrollX = scrollerX.getScrollX();
			scrollY = scrollerY.getScrollY();
		}
	}
	
	public void update(EditorActivity context, FileChange change) {
		clearRedo();
		undo.push(change);
		
		applyUndoRedoLimit(context);
		
		text = text.substring(0, change.changeIndex) + change.afterText + text.substring(change.changeIndex + change.beforeText.length());
		
		selectionStart = change.afterSelectionStart;
		selectionEnd = change.afterSelectionEnd;
		scrollX = change.afterScrollX;
		scrollY = change.afterScrollY;
		
		context.supportInvalidateOptionsMenu();
	}
	
	public void mergeTop() {
		if (undo.size() >= 2) {
			//Merge the top two changes
			
			FileChange top = undo.pop();
			FileChange bottom = undo.pop();
			
			FileChange result = new FileChange();
			
			//Preserve all of the values
			
			result.beforeSelectionStart = bottom.beforeSelectionStart;
			result.beforeSelectionEnd = bottom.beforeSelectionEnd;
			
			result.afterSelectionStart = top.afterSelectionStart;
			result.afterSelectionEnd = top.afterSelectionEnd;
			
			result.beforeScrollX = bottom.beforeScrollX;
			result.beforeScrollY = bottom.beforeScrollY;
			
			result.afterScrollX = top.afterScrollX;
			result.afterScrollY = top.afterScrollY;
			
			//Merge the changes (this isn't the prettiest way to do it...)
			
			String beforeText = new String(text);
			
			beforeText = beforeText.substring(0, top.changeIndex) + top.beforeText + beforeText.substring(top.changeIndex + top.afterText.length());
			beforeText = beforeText.substring(0, bottom.changeIndex) + bottom.beforeText + beforeText.substring(bottom.changeIndex + bottom.afterText.length());
			
			getTextChange(result, beforeText, text);
			
			//Re-add the change
			undo.push(result);
		}
	}
	
	private void applyUndoRedoLimit(Context context) {
		int limit = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString("pref_key_undo_redo_keep", context.getResources().getString(R.string.undo_redo_keep_default_value)));
		
		if (limit != -1) {
			trimEntries(limit);
		}
	}
	
	public void trimEntries(int limit) {
		//Remove old changes to stay within the limit
		//TODO potentially compact changes in the middle to save more history...
		while (undo.size() + redo.size() > limit && undo.size() > 0) {
			undo.removeLast();
		}
	}
	
	public void clearRedo() {
		redo.clear();
	}
	
	public void clearUndoRedo() {
		undo.clear();
		redo.clear();
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
	
	public boolean canUndo() {
		return !undo.isEmpty();
	}
	
	public boolean canRedo() {
		return !redo.isEmpty();
	}
	
	public void undo(EditorActivity context) {
		if (canUndo()) {
			//Save the current position...
			update(context, true);
			
			FileChange restoreTo = undo.pop();
			redo.push(restoreTo);
			
			text = text.substring(0, restoreTo.changeIndex) + restoreTo.beforeText + text.substring(restoreTo.changeIndex + restoreTo.afterText.length());
			
			selectionStart = restoreTo.beforeSelectionStart;
			selectionEnd = restoreTo.beforeSelectionEnd;
			scrollX = restoreTo.beforeScrollX;
			scrollY = restoreTo.beforeScrollY;
			
			updateEditor(context);
		}
	}
	
	public void redo(EditorActivity context) {
		if (canRedo()) {
			FileChange restoreTo = redo.pop();
			undo.push(restoreTo);
			
			text = text.substring(0, restoreTo.changeIndex) + restoreTo.afterText + text.substring(restoreTo.changeIndex + restoreTo.beforeText.length());
			
			selectionStart = restoreTo.afterSelectionStart;
			selectionEnd = restoreTo.afterSelectionEnd;
			scrollX = restoreTo.afterScrollX;
			scrollY = restoreTo.afterScrollY;
			
			updateEditor(context);
		}
	}
	
	private void updateEditor(EditorActivity context) {
		final CodeEditText code = fragment.getCodeEditText();
		final HorizontalScrollView scrollerX = fragment.getCodeScrollerX();
		final ScrollView scrollerY = fragment.getCodeScroller();
		
		//Update the code area text
		code.setNoUndoText(getText());
		//Update the code area selection
		code.setSelection(getSelectionStart(), getSelectionEnd());
		
		code.post(new Runnable() {
			public void run() {
				code.updateBracketMatch();
			}
		});
		
		scrollerX.post(new Runnable() {
			public void run() {
				scrollerX.scrollTo(getScrollX(), 0);
			}
		});
		
		scrollerY.post(new Runnable() {
			public void run() {
				scrollerY.scrollTo(0, getScrollY());
			}
		});
		
		context.supportInvalidateOptionsMenu();
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
	
//	/**
//	 * @param selection
//	 */
//	private void setSelection(int selection) {
//		selectionStart = selection;
//		selectionEnd = selection;
//	}
//	
//	/**
//	 * @param selectionStart
//	 * @param selectionEnd
//	 */
//	private void setSelection(int selectionStart, int selectionEnd) {
//		this.selectionStart = selectionStart;
//		this.selectionEnd = selectionEnd;
//	}
	
	/**
	 * @return
	 */
	public int getScrollX() {
		return scrollX;
	}
	
	/**
	 * @return
	 */
	public int getScrollY() {
		return scrollY;
	}
	
//	/**
//	 * @param scrollX
//	 * @param scrollY
//	 * /
//	public void setScroll(int scrollX, int scrollY) {
//		this.scrollX = scrollX;
//		this.scrollY = scrollY;
//	}
	
	/**
	 * Writes this file in the directory specified by the path
	 *
	 * @param path
	 * @return success
	 */
	public boolean writeData(String path) {
		//Create the output stream
		BufferedOutputStream outputStream = null;
		String filename = path + title + suffix;
		boolean success;
		
		try {
			//Write the data
			outputStream = new BufferedOutputStream(new FileOutputStream(filename));
			outputStream.write(getText().getBytes());
			
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
	
//	/**
//	 * Write this file to the temp directory
//	 * 
//	 * @param context
//	 * @return success
//	 */
//	public boolean writeDataTemp(Context context) {
//		//Create the output stream
//		BufferedOutputStream outputStream = null;
//		String filename = title + suffix;
//		boolean success;
//		
//		try {
//			//Write the data
//			outputStream = new BufferedOutputStream(context.openFileOutput(filename, Context.MODE_PRIVATE));
//			outputStream.write(getText().getBytes());
//			
//			success = true;
//		} catch (Exception e) { //Errors
//			e.printStackTrace();
//			
//			success = false;
//			
//		} finally {
//			//Close the output stream
//			if(outputStream != null) {
//				try {
//					outputStream.close();
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//		}
//		
//		return success;
//	}
	
	/**
	 * Loads data from the specified path into this meta object
	 *
	 * @param filename
	 * @return
	 */
	public boolean readData(String filename) {
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
	
//	/**
//	 * Loads data from the specified temp file into this meta object
//	 * 
//	 * @param context
//	 * @param filename
//	 * @return
//	 */
//	public boolean readTempData(Context context, String filename) {
//		//Create the input stream
//		BufferedInputStream inputStream = null;
//    	String output = "";
//    	boolean success;
//    	
//		try {
//			//Load the data
//			
//			inputStream = new BufferedInputStream(context.openFileInput(filename));
//			
//			byte[] contents = new byte[1024];
//			int bytesRead = 0;
//			
//			while((bytesRead = inputStream.read(contents)) != -1)
//				output += new String(contents, 0, bytesRead);
//			
//			//Set the data
//			text = output;
//			
//			success = true;
//		} catch(Exception e) { //Errors
//			e.printStackTrace();
//			
//			success = false;
//		} finally {
//			//Close the input stream
//			try {
//				if(inputStream != null)
//					inputStream.close();
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//		
//		return success;
//	}
	
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
	
	private void initFragment() {
		if (fragment == null) {
			fragment = CodeAreaFragment.newInstance(this);
		}
	}
	
	public CodeAreaFragment getFragment() {
		return fragment;
	}
	
	public boolean isExample() {
		return isExample;
	}
	
	public void setExample(boolean isExample) {
		this.isExample = isExample;
	}
	
	//Only used when converting to a parcel
	public int tabNum = -1;
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(title);
		dest.writeString(suffix);
		
		dest.writeList(undo);
		dest.writeList(redo);
		
		dest.writeByte((byte) (enabled ? 1 : 0));
		
		dest.writeInt(preprocOffset);
		
		dest.writeInt(tabNum);
		
		dest.writeString(text);
		
		dest.writeInt(selectionStart);
		dest.writeInt(selectionEnd);
		
		dest.writeInt(scrollX);
		dest.writeInt(scrollY);
	}
	
	private SketchFile(Parcel source) {
		initFragment();
		
		title = source.readString();
		suffix = source.readString();
		
		undo = new LinkedList<FileChange>();
		redo = new LinkedList<FileChange>();
		
		source.readList(undo, null);
		source.readList(redo, null);
		
		enabled = source.readByte() != 0;
		
		preprocOffset = source.readInt();
		
		tabNum = source.readInt();
		
		text = source.readString();
		
		selectionStart = source.readInt();
		selectionEnd = source.readInt();
		
		scrollX = source.readInt();
		scrollY = source.readInt();
	}
	
	public static final Parcelable.Creator<SketchFile> CREATOR = new Parcelable.Creator<SketchFile>() {
		@Override
		public SketchFile createFromParcel(Parcel source) {
			return new SketchFile(source);
		}
		
		@Override
		public SketchFile[] newArray(int size) {
			return new SketchFile[size];
		}
	};
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof SketchFile)) {
			return false;
		}
		
		SketchFile otherSketchFile = (SketchFile) other;
		
		return otherSketchFile.getFilename().equals(getFilename()) && otherSketchFile.getText().equals(getText());
	}
}