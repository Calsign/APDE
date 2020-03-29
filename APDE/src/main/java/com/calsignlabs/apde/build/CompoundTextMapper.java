package com.calsignlabs.apde.build;

public interface CompoundTextMapper extends TextMapper {
	TextTransform.Range mapForward(int section, int index, int length) throws TextTransform.LockException;
	TextTransform.Range mapForward(CompoundTextTransform.CompoundRange range) throws TextTransform.LockException;
	TextTransform.Range mapBackward(int section, int index, int length) throws TextTransform.LockException;
	TextTransform.Range mapBackward(CompoundTextTransform.CompoundRange range) throws TextTransform.LockException;
	CompoundTextTransform.CompoundRange mapBackward(int index, int length, boolean shallow) throws TextTransform.LockException;
	CompoundTextTransform.CompoundRange mapBackward(TextTransform.Range range, boolean shallow) throws TextTransform.LockException;
}
