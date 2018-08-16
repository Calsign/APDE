package com.calsignlabs.apde.build;

import com.calsignlabs.apde.SketchFile;

/**
 * Stores information about a compiler problem within APDE.
 *
 * Similar to ECJ's CategorizedProblem, but that type proved insufficient for our needs.
 */
public class CompilerProblem {
	public SketchFile sketchFile;
	public int line, start, length;
	public boolean error;
	public String message;
	
	public CompilerProblem(SketchFile sketchFile, int line, int start, int length, boolean error, String message) {
		this.sketchFile = sketchFile;
		this.line = line;
		this.start = start;
		this.length = length;
		this.error = error;
		this.message = message;
	}
	
	public boolean isError() {
		return error;
	}
	
	public String getMessage() {
		return message;
	}
	
	public int getLine() {
		return line;
	}
}
