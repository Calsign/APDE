package com.calsignlabs.apde.build;

public interface TextMapper {
	TextTransform.Range mapForward(int index, int length) throws TextTransform.LockException;
	TextTransform.Range mapForward(TextTransform.Range range) throws TextTransform.LockException;
	TextTransform.Range mapBackward(int index, int length) throws TextTransform.LockException;
	TextTransform.Range mapBackward(TextTransform.Range range) throws TextTransform.LockException;
	int mapForward(int index) throws TextTransform.LockException;
	int mapBackward(int index) throws TextTransform.LockException;
	boolean isLocked();
}
