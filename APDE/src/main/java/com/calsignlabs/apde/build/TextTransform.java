package com.calsignlabs.apde.build;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class TextTransform implements TextMapper {
	private static final String EMPTY = "";
	
	public static class Edit {
		private int index, beforeLength, afterLength;
		private CharSequence before, after;
		private int weight;
		private Edit moveLink;
		
		public int index() {
			return index;
		}
		
		public int beforeLength() {
			return beforeLength;
		}
		
		public int afterLength() {
			return afterLength;
		}
		
		public CharSequence before() {
			return before;
		}
		
		public CharSequence after() {
			return after;
		}
		
		private Edit(int index, CharSequence before, CharSequence after) {
			this.index = index;
			this.before = before;
			this.after = after;
			this.beforeLength = before.length();
			this.afterLength = after.length();
			weight = 0;
		}
		
		public static Edit insert(int index, CharSequence text) {
			return new Edit(index, EMPTY, text);
		}
		
		public static Edit remove(int index, CharSequence text) {
			return new Edit(index, text, EMPTY);
		}
		
		public static Edit replace(int index, CharSequence before, CharSequence after) {
			return new Edit(index, before, after);
		}
		
		public static MoveEdit move(int index, CharSequence text, int dest) {
			Edit remove = remove(index, text);
			Edit insert = insert(dest, text);
			remove.moveLink = insert;
			insert.moveLink = remove;
			return new MoveEdit(remove, insert);
		}
		
		public boolean hasMoveLink() {
			return moveLink != null;
		}
		
		public Edit getMoveLink() {
			return moveLink;
		}
		
		public void apply(StringBuilder text, boolean forward) {
			text.replace(index(), index() + directionalLength(!forward), directionalText(forward).toString());
		}
		
		public int directionalLength(boolean forward) {
			return forward ? afterLength() : beforeLength();
		}
		
		public int directionlLengthDiff(boolean forward) {
			return directionalLength(forward) - directionalLength(!forward);
		}
		
		public CharSequence directionalText(boolean forward) {
			return forward ? after() : before();
		}
		
		public Edit weight(int weight) {
			this.weight = weight;
			return this;
		}
		
		public int getWeight() {
			return weight;
		}
		
		@Override
		public Edit clone() {
			return new Edit(index, before, after);
		}
		
		@Override
		public String toString() {
			return "Edit: " + index + ": '" + before + "' -> '" + after + "'";
		}
	}
	
	public static class MoveEdit extends ArrayList<Edit> {
		public MoveEdit(Edit remove, Edit insert) {
			add(remove);
			add(insert);
		}
		
		public Edit getRemove() {
			if (size() != 2) {
				throw new RuntimeException("MoveEdit tampered with");
			}
			return get(0);
		}
		
		public Edit getInsert() {
			if (size() != 2) {
				throw new RuntimeException("MoveEdit tampered with");
			}
			return get(1);
		}
	}
	
	// StringBuffer would be thread-safe
	private StringBuilder baseText, workingText;
	private List<Edit> edits;
	
	// Unlock when adding items
	// Lock when applying changes
	// Must be locked in order to map ranges
	private boolean lock;
	
	public TextTransform(CharSequence text) {
		baseText = new StringBuilder(text);
		edits = new ArrayList<>();
		lock = false;
	}
	
	public CharSequence getBaseText() {
		return baseText;
	}
	
	public Edit edit(Edit edit) {
		unlock();
		edits.add(edit);
		return edit;
	}
	
	public <T extends Collection<Edit>> T edit(T edits) {
		unlock();
		this.edits.addAll(edits);
		return edits;
	}
	
	public Edit insert(int index, CharSequence text) {
		return edit(makeInsert(index, text));
	}
	
	public Edit makeInsert(int index, CharSequence text) {
		return Edit.insert(index, text);
	}
	
	public Edit remove(int index, int length) {
		return edit(makeRemove(index, length));
	}
	
	public Edit remove(Range range) {
		return remove(range.index, range.length);
	}
	
	public Edit makeRemove(int index, int length) {
		return Edit.remove(index, baseText.subSequence(index, index + length));
	}
	
	public Edit makeRemove(Range range) {
		return makeRemove(range.index, range.length);
	}
	
	public Edit replace(int index, int length, CharSequence text) {
		return edit(makeReplace(index, length, text));
	}
	
	public Edit replace(Range range, CharSequence text) {
		return replace(range.index, range.length, text);
	}
	
	public Edit makeReplace(int index, int length, CharSequence text) {
		return Edit.replace(index, baseText.subSequence(index, index + length), text);
	}
	
	public Edit makeReplace(Range range, CharSequence text) {
		return makeReplace(range.index, range.length, text);
	}
	
	public MoveEdit move(int index, int length, int dest) {
		return edit(makeMove(index, length, dest));
	}
	
	public MoveEdit move(Range range, int dest) {
		return edit(makeMove(range, dest));
	}
	
	public MoveEdit makeMove(int index, int length, int dest) {
		return Edit.move(index, baseText.subSequence(index, index + length), dest);
	}
	
	public MoveEdit makeMove(Range range, int dest) {
		return Edit.move(range.index, baseText.subSequence(range.index, range.index + range.length), dest);
	}
	
	public StringBuilder applyForward() throws OverlappingEditException {
		return apply(baseText, true);
	}
	
	// Note: applying backward will not work with the current implementation, but everything else should
	public StringBuilder applyBackward(CharSequence text) throws OverlappingEditException {
		return apply(text, false);
	}
	
	@Override
	public Range mapForward(int index, int length) throws LockException {
		return map(index, length, true);
	}
	
	@Override
	public Range mapBackward(int index, int length) throws LockException {
		return map(index, length, false);
	}
	
	@Override
	public Range mapForward(Range range) throws LockException {
		return mapForward(range.index, range.length);
	}
	
	@Override
	public Range mapBackward(Range range) throws LockException {
		return mapBackward(range.index, range.length);
	}
	
	@Override
	public int mapForward(int index) throws LockException {
		return mapForward(index, 0).index;
	}
	
	@Override
	public int mapBackward(int index) throws LockException {
		return mapBackward(index, 0).index;
	}
	
	@Override
	public boolean isLocked() {
		return lock;
	}
	
	public void lock() {
		lock = true;
	}
	
	private void unlock() {
		lock = false;
	}
	
	private void checkLock(boolean check) throws LockException {
		if (lock != check) {
			throw new LockException();
		}
	}
	
	private StringBuilder apply(CharSequence base, boolean forward) throws OverlappingEditException {
		if (!lock) {
			lock();
			
			workingText = new StringBuilder(base);
			
			// Start with edits closest to the back
			// This way we preserve indices
			Collections.sort(edits, (edit1, edit2) -> edit2.index() == edit1.index()
					// needed for functions with color as the return type (potentially other cases)
					//   color fcn() {} -> public int fcn() {}
					// 'color' -> 'int' and '' -> 'public ' both occur at the same index
					? edit2.getWeight() - edit1.getWeight()
					: edit2.index() - edit1.index());
			
			int block = workingText.length();
			for (Edit edit : edits) {
				if (edit.index() + edit.directionlLengthDiff(!forward) > block) {
					System.err.println(edit);
					System.err.println("block: " + block);
					System.err.println("directional length diff: " + edit.directionlLengthDiff(!forward));
					System.err.println(workingText.subSequence(Math.max(edit.index() - 20, 0), Math.min(edit.index() + edit.directionalLength(forward) + 20, workingText.length())));
					throw new OverlappingEditException();
				}
				
				edit.apply(workingText, forward);
				block = edit.index();
			}
			
			// Go first to last for reverse-mapping
			Collections.reverse(edits);
		}
		
		return workingText;
	}
	
	private Range map(int index, int length, boolean forward) throws LockException {
		checkLock(true);
		
		// First to last
		for (Edit edit : edits) {
			if (edit.hasMoveLink() && edit.index() <= index && edit.index() + edit.directionalLength(!forward) >= index + length) {
				// This is a move edit, so get the linked edit
				// Map the range from the original to the linked edit range
				return constrainRange(new Range(edit.getMoveLink().index() + (index - edit.index()), length), forward);
			} else if (edit.index() >= index) {
				if (edit.index() >= index + length) {
					// Entirely after
					return constrainRange(new Range(index, length), forward);
				} else {
					// Overlapping in some form
					
					// For now we will assume that it is identical
					// Why? Because it should work for our purposes
					return constrainRange(new Range(edit.index(), edit.directionalLength(forward)), forward);
				}
			}
			
			index += edit.directionlLengthDiff(forward);
		}
		
		// Before all edits
		return constrainRange(new Range(index, length), forward);
	}
	
	private Range constrainRange(Range range, boolean forward) {
		int index = Math.max(range.index, 0);
		int length = Math.min(range.length - (index - range.index), (forward ? workingText : baseText).length() - 1);
		return new Range(index, length);
	}
	
	public static class Range {
		public int index, length;
		
		public Range(int index, int length) {
			this.index = index;
			this.length = length;
		}
		
		public int index() {
			return index;
		}
		
		public int length() {
			return length;
		}
		
		@Override
		public String toString() {
			return "Range: " + index + ", " + length;
		}
	}
	
	public static class OverlappingEditException extends Exception {}
	public static class LockException extends Exception {}
}
