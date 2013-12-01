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
	
	public String[] initSketchSize(String code) throws SketchException {
		String[] info = parseSketchSize(code, true);
		if (info == null) {
			System.err.println("More about the size() command on Android can be");
			System.err.println("found here: http://wiki.processing.org/w/Android");
			throw new SketchException("Could not parse the size() command.");
		}
		sizeStatement = info[0];
		sketchWidth = info[1];
		sketchHeight = info[2];
		sketchRenderer = info[3];
		return info;
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
}