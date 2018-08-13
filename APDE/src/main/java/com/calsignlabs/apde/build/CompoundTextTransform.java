package com.calsignlabs.apde.build;

public class CompoundTextTransform extends TextTransform {
	private int[] charOffsets;
	
	public static CompoundTextTransform create(TextHolder holder) {
		StringBuilder combined = new StringBuilder();
		int[] charOffsets = new int[holder.count()];
		for (int section = 0; section < holder.count(); section ++) {
			charOffsets[section] = combined.length();
			combined.append(holder.getText(section));
			combined.append('\n');
		}
		return new CompoundTextTransform(combined, charOffsets);
	}
	
	private CompoundTextTransform(CharSequence text, int[] charOffsets) {
		super(text);
		this.charOffsets = charOffsets;
	}
	
	public void insert(int section, int index, CharSequence text) {
		insert(index + charOffsets[section], text);
	}
	
	public void remove(int section, int index, int length) {
		remove(index + charOffsets[section], length);
	}
	
	public void remove(int section, TextTransform.Range range) {
		remove(section, range.index, range.length);
	}
	
	public void replace(int section, int index, int length, CharSequence text) {
		replace(index + charOffsets[section], length, text);
	}
	
	public void replace(int section, Range range, CharSequence text) {
		replace(section, range.index, range.length, text);
	}
	
	public Range mapForward(int section, int index, int length) throws LockException {
		return mapForward(index + charOffsets[section], length);
	}
	
	public Range mapBackward(int section, int index, int length) throws LockException {
		return mapBackward(index - charOffsets[section], length);
	}
	
	public CompoundRange mapBackward(Range range, boolean shallow) throws LockException {
		return mapBackward(range.index, range.length, shallow);
	}
	
	public CompoundRange mapBackward(int index, int length, boolean shallow) throws LockException {
		Range range = shallow ? new Range(index, length) : super.mapBackward(index, length);
		for (int section = 0; section < charOffsets.length; section ++) {
			if (charOffsets[section] > range.index) {
				return new CompoundRange(range, section - 1);
			}
		}
		
		return new CompoundRange(range, charOffsets.length - 1);
	}
	
	public interface TextHolder {
		int count();
		CharSequence getText(int section);
	}
	
	public class CompoundRange extends Range {
		public int section;
		
		public CompoundRange(Range range, int section) {
			super(range.index, range.length);
			this.section = section;
		}
		
		public CompoundRange(int index, int length, int section) {
			super(index, length);
			this.section = section;
		}
	}
}