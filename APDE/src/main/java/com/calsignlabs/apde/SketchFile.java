package com.calsignlabs.apde;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import com.calsignlabs.apde.support.documentfile.DocumentFile;

import com.calsignlabs.apde.build.CompilerProblem;
import com.calsignlabs.apde.support.MaybeDocumentFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/*
 * Utility class for storing information about files
 * This started out as a meta... but is has grown to incorporate far more information than that
 */
public class SketchFile extends BareSketchFile implements Parcelable {
	// NOTE: we can't use a recognized mime type (like text/plain) because the system will happily
	// add a .txt extension for us, which is wrong.
	public static String PDE_MIME_TYPE = "application/pde";
	
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
	
	private List<CompilerProblem> compilerProblems;
	
	/**
	 * Used only for java files, the number of characters added to the start of the file for the
	 * import statement.
	 */
	public int javaImportHeaderOffset = 0;
	
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
		
		public static final Parcelable.Creator<FileChange> CREATOR = new Parcelable.Creator<FileChange>() {
			@Override
			public FileChange createFromParcel(Parcel source) {
				return new FileChange(source);
			}
			
			@Override
			public FileChange[] newArray(int size) {
				return new FileChange[size];
			}
		};
		
		public JSONObject toJsonObject() throws JSONException {
			JSONObject json = new JSONObject();
			
			json.put("changeIndex", changeIndex);
			json.put("beforeText", beforeText);
			json.put("afterText", afterText);
			
			json.put("beforeSelectionStart", beforeSelectionStart);
			json.put("beforeSelectionEnd", beforeSelectionEnd);
			
			json.put("afterSelectionStart", afterSelectionStart);
			json.put("afterSelectionEnd", afterSelectionEnd);
			
			json.put("beforeScrollX", beforeScrollX);
			json.put("beforeScrollY", beforeScrollY);
			
			json.put("afterScrollX", afterScrollX);
			json.put("afterScrollY", afterScrollY);
			
			return json;
		}
		
		public FileChange(JSONObject json) throws JSONException {
			changeIndex = json.getInt("changeIndex");
			beforeText = json.getString("beforeText");
			afterText = json.getString("afterText");
			
			beforeSelectionStart = json.getInt("beforeSelectionStart");
			beforeSelectionEnd = json.getInt("beforeSelectionEnd");
			
			afterSelectionStart = json.getInt("afterSelectionStart");
			afterSelectionEnd = json.getInt("afterSelectionEnd");
			
			beforeScrollX = json.getInt("beforeScrollX");
			beforeScrollY = json.getInt("beforeScrollY");
			
			afterScrollX = json.getInt("afterScrollX");
			afterScrollY = json.getInt("afterScrollY");
		}
	}
	
	public SketchFile(String title) {
		initFragment();
		
		setTitle(title);
		setSuffix(".pde");
		
		undo = new LinkedList<>();
		redo = new LinkedList<>();
		
		text = "";
		selectionStart = 0;
		selectionEnd = 0;
		scrollX = 0;
		scrollY = 0;
		
		enabled = true;
		
		compilerProblems = new ArrayList<>();
	}
	
	public SketchFile(String title, String text, int selectionStart, int selectionEnd, int scrollX, int scrollY) {
		initFragment();
		
		setTitle(title);
		setSuffix(".pde");
		
		undo = new LinkedList<>();
		redo = new LinkedList<>();
		
		this.text = text;
		this.selectionStart = selectionStart;
		this.selectionEnd = selectionEnd;
		this.scrollX = scrollX;
		this.scrollY = scrollY;
		
		enabled = true;
		
		compilerProblems = new ArrayList<>();
	}
	
	public static void getTextChange(FileChange change, String oldText, String newText) {
		//Find the difference between the old text and the new text
		//Compare text starting at both ends to find the difference in the middle
		
		int changeStart = 0;
		int changeEnd = 0;
		
		int minLength = Math.min(oldText.length(), newText.length());
		
		while (changeStart < oldText.length() && changeStart < newText.length()
				&& oldText.charAt(changeStart) == newText.charAt(changeStart)) {
			changeStart++;
		}
		
		while (changeEnd < oldText.length() && changeEnd < newText.length()
				&& minLength - changeEnd > changeStart
				&& oldText.charAt(oldText.length() - changeEnd - 1) == newText.charAt(newText.length() - changeEnd - 1)) {
			changeEnd++;
		}
		
		int beforeEnd = oldText.length() - changeEnd;
		int afterEnd = newText.length() - changeEnd;
		
		change.changeIndex = changeStart;
		change.beforeText = beforeEnd - changeStart > 0 ? oldText.substring(changeStart, beforeEnd) : "";
		change.afterText = afterEnd - changeStart > 0 ? newText.substring(changeStart, afterEnd) : "";
	}
	
	public FileChange getFileChange() {
		EditText code = fragment.getCodeEditText();
		
		if (code == null) {
			return null;
		}
		
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
				
				if (code == null) {
					selectionStart = 0;
					selectionEnd = 0;
					
					scrollX = 0;
					scrollY = 0;
				} else {
					selectionStart = code.getSelectionStart();
					selectionEnd = code.getSelectionEnd();
					
					scrollX = scrollerX.getScrollX();
					scrollY = scrollerY.getScrollY();
				}
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
		int limit = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(context).getString("pref_key_undo_redo_keep", context.getResources().getString(R.string.pref_undo_redo_keep_default_value)));
		
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
	
	@Override
	public String getTitle() {
		// If this isn't a PDE file, add the custom suffix to the title
		return suffix.equals(".pde") ? title : title + suffix;
	}
	
	@Override
	public String getRawTitle() {
		return title;
	}
	
	/**
	 * @param title
	 */
	public void setTitle(String title) {
		this.title = title;
	}
	
	@Override
	public String getSuffix() {
		return suffix;
	}
	
	/**
	 * @param suffix
	 */
	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}
	
	@Override
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
	
	protected void updateEditor(EditorActivity context) {
		final CodeEditText code = fragment.getCodeEditText();
		final HorizontalScrollView scrollerX = fragment.getCodeScrollerX();
		final ScrollView scrollerY = fragment.getCodeScroller();
		
		//Update the code area text
		code.setNoUndoText(getText());
		//Update the code area selection
		code.setSelection(getSelectionStart(), getSelectionEnd());
		
		code.post(code::updateBracketMatch);
		
		scrollerX.post(() -> scrollerX.scrollTo(getScrollX(), 0));
		
		scrollerY.post(() -> scrollerY.scrollTo(0, getScrollY()));
		
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
	
	/**
	 * Writes this file in the directory specified by the path
	 *
	 * @param sketchFolder
	 * @return success
	 */
	public boolean writeData(MaybeDocumentFile sketchFolder, ContentResolver contentResolver)
			throws MaybeDocumentFile.MaybeDocumentFileException {
		String filename = getFilename();
		MaybeDocumentFile file = sketchFolder.child(filename, PDE_MIME_TYPE);
		
		try (BufferedOutputStream outputStream = new BufferedOutputStream(file.openOut(contentResolver))) {
			// Write the data
			outputStream.write(getText().getBytes());
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * Loads data from the specified file into this meta object
	 *
	 * @param file
	 * @return
	 */
	public boolean readData(DocumentFile file, ContentResolver contentResolver) {
		try(BufferedInputStream inputStream = new BufferedInputStream(contentResolver.openInputStream(file.getUri()))) {
			// Read the data
			
			StringBuilder output = new StringBuilder();
			byte[] contents = new byte[1024];
			int bytesRead;
			while ((bytesRead = inputStream.read(contents)) != -1) {
				output.append(new String(contents, 0, bytesRead));
			}
			
			// Set the data
			text = handleBadChars(output.toString());
			
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private static String handleBadChars(String string) {
		// Convert all newlines to something we know how to deal with.
		// Detect all possible line breaks - \R doesn't seem to work on older versions of Android,
		// so the below is copied from the reference for \R.
		return string.replaceAll("\\u000D\\u000A|[\\u000A\\u000B\\u000C\\u000D\\u0085\\u2028\\u2029]", "\n");
	}
	
	private void initFragment() {
		if (fragment == null) {
			fragment = CodeAreaFragment.newInstance(this);
		}
	}
	
	public void forceFragmentUpdate() {
		fragment.setSketchFile(this);
		fragment.updateWithSketchFile();
	}
	
	public void forceReloadTextIfInitialized() {
		if (fragment != null && fragment.isInitialized()) {
			fragment.updateWithSketchFile();
		}
	}
	
	public void clearHighlightsIfInitialized() {
		if (fragment != null && fragment.isInitialized()) {
			fragment.getCodeEditText().clearHighlights();
			fragment.getCodeEditText().invalidate();
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
	protected int tabNum = -1;
	
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
		
		undo = new LinkedList<>();
		redo = new LinkedList<>();
		
		source.readList(undo, getClass().getClassLoader());
		source.readList(redo, getClass().getClassLoader());
		
		enabled = source.readByte() != 0;
		
		tabNum = source.readInt();
		
		text = source.readString();
		
		selectionStart = source.readInt();
		selectionEnd = source.readInt();
		
		scrollX = source.readInt();
		scrollY = source.readInt();
		
		compilerProblems = new ArrayList<>();
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
	
	public JSONObject getUndoRedoHistory() throws JSONException, NoSuchAlgorithmException {
		JSONObject history = new JSONObject();
		
		String md5 = getChecksum(getText());
		
		JSONArray undoList = new JSONArray(), redoList = new JSONArray();
		
		// Save in reverse order so that we can populate back-to-front
		Iterator<FileChange> iterator = undo.descendingIterator();
		while (iterator.hasNext()) undoList.put(iterator.next().toJsonObject());
		iterator = redo.descendingIterator();
		while (iterator.hasNext()) redoList.put(iterator.next().toJsonObject());
		
		history.put("checksum", md5);
		history.put("undo", undoList);
		history.put("redo", redoList);
		
		return history;
	}
	
	public void populateUndoRedoHistory(JSONObject history) throws JSONException, NoSuchAlgorithmException {
		if (undo.size() != 0 || redo.size() != 0) {
			throw new IllegalStateException("Trying to populate undo/redo history when there is already history");
		}
		
		// If the user edited the files with a different app *gasp* then undo/redo will be broken.
		// Prevent breaking things by comparing checksums.
		String md5Current = getChecksum(text), md5Previous = history.getString("checksum");
		if (!md5Current.equals(md5Previous)) {
			System.err.println("File " + getFilename() + " has been modified since last saved with APDE.");
			System.err.println("This is perfectly fine, but undo/redo history will be lost.");
			return;
		}
		
		JSONArray undoArray = history.getJSONArray("undo");
		for (int i = 0; i < undoArray.length(); i++) {
			undo.push(new FileChange(undoArray.getJSONObject(i)));
		}
		JSONArray redoArray = history.getJSONArray("redo");
		for (int i = 0; i < redoArray.length(); i++) {
			redo.push(new FileChange(redoArray.getJSONObject(i)));
		}
	}
	
	/**
	 * Calculate the MD5 checksum of the given text. Returns the hash in hexadecimal.
	 *
	 * @param text the text to hash
	 * @return the hex hash
	 * @throws NoSuchAlgorithmException
	 */
	private static String getChecksum(String text) throws NoSuchAlgorithmException {
		MessageDigest hasher = MessageDigest.getInstance("MD5");
		hasher.update(text.getBytes());
		return (new BigInteger(1, hasher.digest())).toString(16);
	}
	
	/**
	 * Process a list of compiler problems: determine which ones belong in this tab and then update
	 * their information in accordance with the text of this tab.
	 *
	 * @param problems
	 */
	public void setCompilerProblems(List<CompilerProblem> problems, int i) {
		compilerProblems.clear();
		for (CompilerProblem problem : problems) {
			if (problem.sketchFile != null && problem.sketchFile.getIndex() == i) {
				compilerProblems.add(problem);
			}
		}
	}
	
	public List<CompilerProblem> getCompilerProblems() {
		return compilerProblems;
	}
}