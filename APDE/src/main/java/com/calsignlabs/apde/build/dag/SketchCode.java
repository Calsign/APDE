package com.calsignlabs.apde.build.dag;

import com.calsignlabs.apde.BareSketchFile;
import com.calsignlabs.apde.CodeEditText;

public class SketchCode extends BareSketchFile {
	private String title, suffix;
	private CharSequence code;
	private int index;
	
	/**
	 * Used only for java files, the number of characters added to the start of the file for the
	 * import statement.
	 */
	public int javaImportHeaderOffset = 0;
	
	public SketchCode(String title, String suffix, CharSequence code, int index) {
		this.title = title;
		this.suffix = suffix;
		this.code = code;
		this.index = index;
	}
	
	public static SketchCode fromSketchFile(BareSketchFile sketchFile, int index) {
		// Need to call toString() on the text in order to make a copy of it
		return new SketchCode(sketchFile.getRawTitle(), sketchFile.getSuffix(), sketchFile.getText().toString(), index);
	}
	
	@Override
	public String getTitle() {
		return getRawTitle();
	}
	
	@Override
	public String getRawTitle() {
		return title;
	}
	
	@Override
	public String getSuffix() {
		return suffix;
	}
	
	@Override
	public CharSequence getText() {
		return code;
	}
	
	public int getIndex() {
		return index;
	}
	
	public int lineForOffset(int offset) {
		return CodeEditText.lineForOffset(code.toString(), offset);
	}
	
	public int offsetForLine(int line) {
		return CodeEditText.offsetForLine(code.toString(), line);
	}
	
	@Override
	public boolean equals(Object other) {
		if (other instanceof SketchCode) {
			SketchCode otherCode = (SketchCode) other;
			
			return getRawTitle().equals(otherCode.getRawTitle())
					&& getSuffix().equals(otherCode.getSuffix())
					&& getText().equals(otherCode.getText());
		} else {
			return false;
		}
	}
}
