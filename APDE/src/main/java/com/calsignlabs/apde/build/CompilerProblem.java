package com.calsignlabs.apde.build;

import com.calsignlabs.apde.SketchFile;

/**
 * Stores information about a compiler problem within APDE.
 *
 * Similar to ECJ's CategorizedProblem, but that type proved insufficient for our needs.
 *
 * Note that this class contains information that is used in transition (arg, javaLine, posInLine)
 * as well as the permanent store for compiler problem information.
 */
public class CompilerProblem {
	// Used in the intermediate stage, before the SketchFile figures out where this error goes
	public String arg;
	public int javaLine, posInLine;
	
	public SketchFile sketchFile;
	public int line, start, length;
	public boolean error;
	public String message;
	
	public CompilerProblem(int javaLine, int posInLine, String arg, boolean error, String message) {
		this.javaLine = javaLine;
		this.posInLine = posInLine;
		this.arg = arg;
		this.error = error;
		this.message = message;
	}
	
	public boolean isError() {
		return error;
	}
	
	public String getMessage() {
		return message;
	}
	
	public int getJavaLine() {
		return javaLine;
	}
	
	public int getLine() {
		return line;
	}
}
