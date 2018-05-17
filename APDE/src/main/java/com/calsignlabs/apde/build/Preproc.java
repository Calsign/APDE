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

import android.content.Context;

import com.calsignlabs.apde.R;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import processing.core.PApplet;
import processing.data.StringList;
import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.SurfaceInfo;

public class Preproc extends PdePreprocessor {
	/** Used to grab the start of setup() so we can mine it for size() */
	static private final Pattern VOID_SETUP_REGEX = Pattern.compile("(?:^|\\s|;)void\\s+setup\\s*\\(", Pattern.MULTILINE);
	static private final Pattern CLOSING_BRACE = Pattern.compile("\\}");
	public static final String SMOOTH_REGEX = "(?:^|\\s|;)smooth\\s*\\(\\s*([^\\s,]+)\\s*\\)\\s*\\;";
	
	protected String packageName;
	protected String smoothStatement;
	protected String sketchQuality;
	
	public Preproc(final String sketchName, final String packageName) throws IOException {
		super(sketchName);
		this.packageName = packageName;
	}
	
	public SurfaceInfo initSketchSize(String code, Context context, int comp) throws SketchException {
		SurfaceInfo surfaceInfo = parseSketchSizeCustom(code, true, context, comp);
		if (surfaceInfo == null) {
			System.err.println(context.getResources().getString(R.string.preproc_bad_size_command_more_info));
			throw new SketchException(context.getResources().getString(R.string.preproc_bad_size_command));
		}
		
		try {
			Field field = PdePreprocessor.class.getDeclaredField("sizeInfo");
			field.setAccessible(true);
			field.set((PdePreprocessor) this, surfaceInfo);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return surfaceInfo;
	}
	
	public String[] initSketchSmooth(String code, Context context) throws SketchException {
		String[] info = parseSketchSmooth(code, true, context);
		if (info == null) {
			System.err.println(context.getResources().getString(R.string.preproc_bad_size_command_more_info));
			throw new SketchException(context.getResources().getString(R.string.preproc_bad_size_command));
		}
		smoothStatement = info[0];
		sketchQuality = info[1];
		return info;
	}
	
	/**
	 * Parse a chunk of code and extract the size() command and its contents.
	 * Also goes after fullScreen(), smooth(), and noSmooth().
	 * @param code The code from the main tab in the sketch
	 * @param fussy true if it should show an error message if bad size()
	 * @return null if there was an error, otherwise an array (might contain some/all nulls)
	 */
	static public SurfaceInfo parseSketchSizeCustom(String code, boolean fussy, Context context, int comp) throws SketchException {
		// This matches against any uses of the size() function, whether numbers
		// or variables or whatever. This way, no warning is shown if size() isn't
		// actually used in the applet, which is the case especially for anyone
		// who is cutting/pasting from the reference.

//    String scrubbed = scrubComments(sketch.getCode(0).getProgram());
//    String[] matches = PApplet.match(scrubbed, SIZE_REGEX);
//    String[] matches = PApplet.match(scrubComments(code), SIZE_REGEX);

      /*
   1. no size() or fullScreen() method at all
      will use the non-overridden settings() method in PApplet
   2. size() or fullScreen() found inside setup() (static mode sketch or otherwise)
      make sure that it uses numbers (or displayWidth/Height), copy into settings
   3. size() or fullScreen() already in settings()
      don't mess with the sketch, don't insert any defaults
   really only need to deal with situation #2.. nothing to be done for 1 and 3
   */
		// if static mode sketch, all we need is regex
		// easy proxy for static in this case is whether [^\s]void\s is present
		
		String uncommented = scrubComments(code);
		
		Mode mode = parseMode(uncommented);
		
		String searchArea = null;
		
		switch (mode) {
			case JAVA:
				// it's up to the user
				searchArea = null;
				break;
			case ACTIVE:
				// active mode, limit scope to setup
				
				// Find setup() in global scope
				MatchResult setupMatch = findInCurrentScope(VOID_SETUP_REGEX, uncommented);
				if (setupMatch != null) {
					int start = uncommented.indexOf("{", setupMatch.end());
					if (start >= 0) {
						// Find a closing brace
						MatchResult match = findInCurrentScope(CLOSING_BRACE, uncommented, start);
						if (match != null) {
							searchArea = uncommented.substring(start + 1, match.end() - 1);
						} else {
							throw new SketchException(context.getResources().getString(R.string.preproc_missing_right_brace), false);
						}
					}
				}
				break;
			case STATIC:
				// static mode, look everywhere
				searchArea = uncommented;
				break;
		}
		
		if (searchArea == null) {
			return new SurfaceInfo();
		}
		
		StringList extraStatements = new StringList();
		
		// First look for noSmooth() or smooth(N) so we can hoist it into settings.
		String[] smoothContents = matchMethod("smooth", searchArea);
		if (smoothContents != null) {
			extraStatements.append(smoothContents[0]);
		}
		String[] noContents = matchMethod("noSmooth", searchArea);
		if (noContents != null) {
			if (extraStatements.size() != 0) {
				throw new SketchException(context.getResources().getString(R.string.preproc_smooth_and_nosmooth));
			} else {
				extraStatements.append(noContents[0]);
			}
		}
		String[] pixelDensityContents = matchMethod("pixelDensity", searchArea);
		if (pixelDensityContents != null) {
			extraStatements.append(pixelDensityContents[0]);
		} else {
			pixelDensityContents = matchDensityMess(searchArea);
			if (pixelDensityContents != null) {
				extraStatements.append(pixelDensityContents[0]);
			}
		}
		
		String[] sizeContents = matchMethod("size", searchArea);
		String[] fullContents = matchMethod("fullScreen", searchArea);
		// First check and make sure they aren't both being used, otherwise it'll
		// throw a confusing state exception error that one "can't be used here".
		if (sizeContents != null && fullContents != null) {
			throw new SketchException(context.getString(R.string.preproc_size_and_fullscreen), false);
		}
		
		// Get everything inside the parens for the size() method
		//String[] contents = PApplet.match(searchArea, SIZE_CONTENTS_REGEX);
		if (sizeContents != null) {
			StringList args = breakCommas(sizeContents[1]);
			SurfaceInfo info = new SurfaceInfo();
//      info.statement = sizeContents[0];
			String rendererArg = (args.size() >= 3) ? args.get(2).trim() : null;
			
			switch (comp) {
				case Build.APP:
				case Build.WATCHFACE:
				case Build.VR:
					// Use size like normal
					info.addStatement(sizeContents[0]);
					break;
				case Build.WALLPAPER:
					// Replace size with fullScreen - for some reason size breaks things
					// This is hacky but it will let old examples work
					info.addStatement("fullScreen(" + rendererArg + ");");
					break;
				default:
					throw new IllegalStateException("Illegal app comp: " + comp);
			}
			
			setPrivateSurfaceInfoField(info, "width", (args.size() >= 1) ? args.get(0).trim() : null);
			setPrivateSurfaceInfoField(info, "height", (args.size() >= 2) ? args.get(1).trim() : null);
			setPrivateSurfaceInfoField(info, "renderer", rendererArg);
			setPrivateSurfaceInfoField(info, "path", (args.size() >= 4) ? args.get(3).trim() : null);
			
			// Trying to remember why we wanted to allow people to use displayWidth
			// as the height or displayHeight as the width, but maybe it's for
			// making a square sketch window? Not going to
			
//			if (info.hasOldSyntax()) {
			if (hasOldSyntax(info, context)) {
//        return null;
				throw new SketchException(context.getResources().getString(R.string.preproc_old_syntax), false);
			}
			
//			if (info.hasBadSize() && fussy) {
			if (hasBadSize(info) && fussy) {
				// found a reference to size, but it didn't seem to contain numbers
//				final String message =
//						"The size of this sketch could not be determined from your code.\n" +
//								"Use only numbers (not variables) for the size() command.\n" +
//								"Read the size() reference for more details.";
//				Messages.showWarning("Could not find sketch size", message, null);
				showWarning(context.getResources().getString(R.string.preproc_no_size), context.getResources().getString(R.string.preproc_no_size_message));
//        new Exception().printStackTrace(System.out);
//        return null;
				throw new SketchException(context.getResources().getString(R.string.preproc_bad_size), false);
			}
			
			info.addStatements(extraStatements);
//			info.checkEmpty();
			invokePrivateSurfaceInfoMethod(info, "checkEmpty", null);
			return info;
			//return new String[] { contents[0], width, height, renderer, path };
		}
		// if no size() found, check for fullScreen()
		//contents = PApplet.match(searchArea, FULL_SCREEN_CONTENTS_REGEX);
		if (fullContents != null) {
			SurfaceInfo info = new SurfaceInfo();
//      info.statement = fullContents[0];
			info.addStatement(fullContents[0]);
			StringList args = breakCommas(fullContents[1]);
			if (args.size() > 0) {  // might have no args
				String args0 = args.get(0).trim();
				if (args.size() == 1) {
					// could be either fullScreen(1) or fullScreen(P2D), figure out which
					if (args0.equals("SPAN") || PApplet.parseInt(args0, -1) != -1) {
						// it's the display parameter, not the renderer
//						info.display = args0;
						setPrivateSurfaceInfoField(info, "display", args0);
					} else {
//						info.renderer = args0;
						setPrivateSurfaceInfoField(info, "renderer", args0);
					}
				} else if (args.size() == 2) {
//					info.renderer = args0;
//					info.display = args.get(1).trim();
					setPrivateSurfaceInfoField(info, "renderer", args0);
					setPrivateSurfaceInfoField(info, "display", args.get(1).trim());
				} else {
					throw new SketchException(context.getResources().getString(R.string.preproc_bad_fullscreen));
				}
			}
//			info.width = "displayWidth";
//			info.height = "displayHeight";
			setPrivateSurfaceInfoField(info, "width", "displayWidth");
			setPrivateSurfaceInfoField(info, "height", "displayHeight");
//      if (extraStatements.size() != 0) {
//        info.statement += extraStatements.join(" ");
//      }
			info.addStatements(extraStatements);
//			info.checkEmpty();
			invokePrivateSurfaceInfoMethod(info, "checkEmpty", null);
			
			return info;
		}
		
		// Lint is telling me that this statement is never true... but I beg to differ
		if (sizeContents == null && fullContents == null) {
			/*
			 * Default to fullscreen
			 * 
			 * This isn't in Processing's implementation, but for some reason the prior version
			 * of APDE also defaulted to fullscreen, so include this to keep it the same
			 * 
			 * Plus, it makes more sense to default to fullscreen - who wants a 100 x 100 sketch
			 * display area on a mobile device?
			 */
			
			SurfaceInfo info = new SurfaceInfo();
			
			info.addStatement("fullScreen();");
			
			setPrivateSurfaceInfoField(info, "width", "displayWidth");
			setPrivateSurfaceInfoField(info, "height", "displayHeight");
			
			info.addStatements(extraStatements);
			invokePrivateSurfaceInfoMethod(info, "checkEmpty", null);
			
			return info;
		}
		
		// Made it this far, but no size() or fullScreen(), and still
		// need to pull out the noSmooth() and smooth(N) methods.
		if (extraStatements.size() != 0) {
			SurfaceInfo info = new SurfaceInfo();
//      info.statement = extraStatements.join(" ");
			info.addStatements(extraStatements);
			return info;
		}
		
		// not an error, just no size() specified
		//return new String[] { null, null, null, null, null };
		return new SurfaceInfo();
	}
	
	private static <T> void setPrivateSurfaceInfoField(SurfaceInfo surfaceInfo, String fieldName, T value) {
		try {
			Field field = SurfaceInfo.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(surfaceInfo, value);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static <T> T getPrivateSurfaceInfoField(SurfaceInfo surfaceInfo, String fieldName, Class<T> type) {
		try {
			Field field = SurfaceInfo.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			Object result = field.get(surfaceInfo);
			return result != null ? type.cast(result) : null;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private static <T> T invokePrivateSurfaceInfoMethod(SurfaceInfo surfaceInfo, String methodName, Class<T> type) {
		try {
			Method method = SurfaceInfo.class.getDeclaredMethod(methodName);
			method.setAccessible(true);
			Object result = method.invoke(surfaceInfo);
			return result != null && type != null ? type.cast(result) : null;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}
	
	private static boolean hasOldSyntax(SurfaceInfo surfaceInfo, Context context) {
		String width = getPrivateSurfaceInfoField(surfaceInfo, "width", String.class);
		String height = getPrivateSurfaceInfoField(surfaceInfo, "height", String.class);
		
		if (width == null || height == null) {
			return false;
		}
		
		if (width.equals("screenWidth") ||
				width.equals("screenHeight") ||
				height.equals("screenHeight") ||
				height.equals("screenWidth")) {
//			final String message =
//					"The screenWidth and screenHeight variables are named\n" +
//							"displayWidth and displayHeight in Processing 3.\n" +
//							"Or you can use the fullScreen() method instead of size().";
//			Messages.showWarning("Time for a quick update", message, null);
			showWarning(context.getResources().getString(R.string.preproc_display_variables_quick_update), context.getResources().getString(R.string.preproc_display_variables_screen_quick_update));
			return true;
		}
		if (width.equals("screen.width") ||
				width.equals("screen.height") ||
				height.equals("screen.height") ||
				height.equals("screen.width")) {
//			final String message =
//					"The screen.width and screen.height variables are named\n" +
//							"displayWidth and displayHeight in Processing 3.\n" +
//							"Or you can use the fullScreen() method instead of size().";
//			Messages.showWarning("Time for a quick update", message, null);
			showWarning(context.getResources().getString(R.string.preproc_display_variables_quick_update), context.getResources().getString(R.string.preproc_display_variables_screen_dot_quick_update));
			return true;
		}
		return false;
	}
	
	private static boolean hasBadSize(SurfaceInfo surfaceInfo) {
		String width = getPrivateSurfaceInfoField(surfaceInfo, "width", String.class);
		String height = getPrivateSurfaceInfoField(surfaceInfo, "height", String.class);
		
		if (width == null || height == null) {
			return true;
		}
		
		if (!width.equals("displayWidth") &&
				!width.equals("displayHeight") &&
				PApplet.parseInt(width, -1) == -1) {
			return true;
		}
		if (!height.equals("displayWidth") &&
				!height.equals("displayHeight") &&
				PApplet.parseInt(height, -1) == -1) {
			return true;
		}
		return false;
	}
	
	static public String[] parseSketchSmooth(String code, boolean fussy, Context context) {
		String[] matches = PApplet.match(scrubComments(code), SMOOTH_REGEX);
		
		if (matches != null) {
			boolean badSmooth = false;
			
			if (PApplet.parseInt(matches[1], -1) == -1) {
				badSmooth = true;
			}
			
			if (badSmooth && fussy) {
				// found a reference to smooth, but it didn't seem to contain numbers
//				final String message =
//						"The smooth level of this applet could not automatically\n" +
//								"be determined from your code. Use only a numeric\n" +
//								"value (not variables) for the smooth() command.\n" +
//								"See the smooth() reference for an explanation.";
//				Messages.showWarning("Could not find smooth level", message, null);
				showWarning(context.getResources().getString(R.string.preproc_no_smooth), context.getResources().getString(R.string.preproc_no_smooth_message));
//        new Exception().printStackTrace(System.out);
				return null;
			}
			
			return matches;
		}
		return new String[] { null, null };  // not an error, just empty
	}
	
	static private void showWarning(String title, String message) {
		System.err.println();
		System.err.println(title);
		System.err.println();
		System.err.println(message);
	}
	
	/**
	 * Break on commas, except those inside quotes,
	 * e.g.: size(300, 200, PDF, "output,weirdname.pdf");
	 * No special handling implemented for escaped (\") quotes.
	 */
	static private StringList breakCommas(String contents) {
		StringList outgoing = new StringList();
		
		boolean insideQuote = false;
		// The current word being read
		StringBuilder current = new StringBuilder();
		char[] chars = contents.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			char c = chars[i];
			if (insideQuote) {
				current.append(c);
				if (c == '\"') {
					insideQuote = false;
				}
			} else {
				if (c == ',') {
					if (current.length() != 0) {
						outgoing.append(current.toString());
						current.setLength(0);
					}
				} else {
					current.append(c);
					if (c == '\"') {
						insideQuote = true;
					}
				}
			}
		}
		if (current.length() != 0) {
			outgoing.append(current.toString());
		}
		return outgoing;
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
}