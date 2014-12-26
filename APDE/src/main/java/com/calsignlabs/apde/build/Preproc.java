/*
Taken from the Processing project - http://processing.org

Copyright (c) 2009-10 Ben Fry and Casey Reas

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License version 2
as published by the Free Software Foundation.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software Foundation,
Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package com.calsignlabs.apde.build;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;

import com.calsignlabs.apde.EditorActivity;

import processing.core.PApplet;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.PreprocessorResult;
import antlr.RecognitionException;
import antlr.TokenStreamException;

public class Preproc extends PdePreprocessor {
	String packageName;
	
	public Preproc(String sketchName, final String packageName) throws IOException {
		super(sketchName);
		this.packageName = packageName;
	}
	
	public String[] initSketchSize(String code, EditorActivity editor) throws SketchException {
		String[] info = parseSketchSizeCustom(code, true, editor); //Use our own function to resolve some issues...
		
		if (info == null) {
			System.err.println("More about the size() command on Android can be");
			System.err.println("found here: http://wiki.processing.org/w/Android");
			System.err.println();
			System.err.println();
			
			throw new SketchException("Could not parse the size() command.");
		}
		sizeStatement = info[0];
		sketchWidth = info[1];
		sketchHeight = info[2];
		sketchRenderer = info[3];
		
		return info;
	}
	
	/**
	 * Parse a chunk of code and extract the size() command and its contents.
	 * @param code Usually the code from the main tab in the sketch
	 * @param fussy true if it should show an error message if bad size()
	 * @return null if there was an error, otherwise an array (might contain some/all nulls)
	 */
	static public String[] parseSketchSizeCustom(String code, boolean fussy, EditorActivity editor) {
		//Use our own function to solve some issues (e.g. java.lang.NoClassDefFoundError: java.awt.Frame)
		
		// This matches against any uses of the size() function, whether numbers
		// or variables or whatever. This way, no warning is shown if size() isn't
		// actually used in the applet, which is the case especially for anyone
		// who is cutting/pasting from the reference.
		
		//	    String scrubbed = scrubComments(sketch.getCode(0).getProgram());
		//	    String[] matches = PApplet.match(scrubbed, SIZE_REGEX);
		String[] matches = PApplet.match(scrubComments(code), SIZE_REGEX);
		
		if (matches != null) {
			boolean badSize = false;
			
			if (matches[1].equals("screenWidth") ||
					matches[1].equals("screenHeight") ||
					matches[2].equals("screenWidth") ||
					matches[2].equals("screenHeight")) {
				final String message =
						"The screenWidth and screenHeight variables\n" +
								"are named displayWidth and displayHeight\n" +
								"in this release of Processing.";
//				Base.showWarning("Time for a quick update", message, null);
				System.err.println();
				System.err.println();
				editor.errorExt("Time for a quick update");
				System.err.println("Time for a quick update:\n" + message);
				System.err.println();
				return null;
			}
			
			if (!matches[1].equals("displayWidth") &&
					!matches[1].equals("displayHeight") &&
					PApplet.parseInt(matches[1], -1) == -1) {
				badSize = true;
			}
			if (!matches[2].equals("displayWidth") &&
					!matches[2].equals("displayHeight") &&
					PApplet.parseInt(matches[2], -1) == -1) {
				badSize = true;
			}
			
			if (badSize && fussy) {
				// found a reference to size, but it didn't seem to contain numbers
				final String message =
						"The size of this applet could not automatically\n" +
								"be determined from your code. Use only numeric\n" +
								"values (not variables) for the size() command.\n" +
								"See the size() reference for an explanation.";
//				Base.showWarning("Could not find sketch size", message, null);
				System.err.println();
				System.err.println();
				editor.errorExt("Could not find sketch size");
				System.err.println("Could not find sketch size:\n" + message);
				System.err.println();
				//	        new Exception().printStackTrace(System.out);
				return null;
			}
			
			// Remove additional space 'round the renderer
			matches[3] = matches[3].trim();
			
			// if the renderer entry is empty, set it to null
			if (matches[3].length() == 0) {
				matches[3] = null;
			}
			return matches;
		}
		return new String[] { null, null, null, null };  // not an error, just empty
	}
	
	public PreprocessorResult write(Writer out, String program, String[] codeFolderPackages) throws RecognitionException, TokenStreamException {
		if (sizeStatement != null) {
			int start = program.indexOf(sizeStatement);
			program = program.substring(0, start) +
					program.substring(start + sizeStatement.length());
		}
		// the OpenGL package is back in 2.0a5
		//program = program.replaceAll("import\\s+processing\\.opengl\\.\\S+;", "");
		try {
			return super.write(out, program, codeFolderPackages);
		} catch (processing.app.SketchException e) {
			return null; //TODO not throwing a SketchException to solve build errors
		}
	}
	
	@Override
	protected int writeImports(final PrintWriter out,
			final List<String> programImports,
			final List<String> codeFolderImports) {
		out.println("package " + packageName + ";");
		out.println();
		// add two lines for the package above
		return 2 + super.writeImports(out, programImports, codeFolderImports);
	}
	
	protected void writeFooter(PrintWriter out, String className) {
		if (mode == Mode.STATIC) {
			// close off draw() definition
			out.println("noLoop();");
			out.println(indent + "}");
		}

		if ((mode == Mode.STATIC) || (mode == Mode.ACTIVE)) {
			out.println();

			if (sketchWidth != null) {
				out.println(indent + "public int sketchWidth() { return " + sketchWidth + "; }");
			}
			if (sketchHeight != null) {
				out.println(indent + "public int sketchHeight() { return " + sketchHeight + "; }");
			}
			if (sketchRenderer != null) {
				out.println(indent + "public String sketchRenderer() { return " + sketchRenderer + "; }");
			}

			// close off the class definition
			out.println("}");
		}
	}
	
	/**
	 * Gets the renderer used by the sketch as defined in the size() command
	 * @return the renderer
	 */
	public String getSketchRenderer() {
		return sketchRenderer;
	}
}