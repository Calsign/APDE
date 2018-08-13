package com.calsignlabs.apde.build;

import android.preference.PreferenceManager;

import com.calsignlabs.apde.EditorActivity;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.SketchFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.SimpleType;

/**
 * Custom preprocessor for APDE. Uses the JDT DOM library. Borrows heavily from PDEX and the Android
 * mode preprocessor.
 */
public class Preprocessor {
	private Build build;
	private EditorActivity editor;
	
	private final ASTParser parser = ASTParser.newParser(AST.JLS8);
	
	private CompoundTextTransform transform;
	
	private Mode mode;
	private String packageName, className;
	
	private String[] codeFolderPackages;
	
	private boolean isOpenGL;
	
	public Preprocessor(Build build, File srcFolder, String packageName, String className, String[] codeFolderPackages) {
		this.build = build;
		this.editor = build.editor;
		this.packageName = packageName;
		this.className = className;
		this.codeFolderPackages = codeFolderPackages;
	}
	
	protected void addCompilerProblem(CompilerProblem compilerProblem) {
		editor.compilerProblems.add(compilerProblem);
	}
	
	public CompilerProblem buildCompilerProblem(TextTransform.Range range, boolean error, String message, boolean shallow) throws TextTransform.LockException {
		CompoundTextTransform.CompoundRange mapped = transform.mapBackward(range, true);
		SketchFile sketchFile = editor.getSketchFiles().get(mapped.section);
		int line = sketchFile.lineForOffset(mapped.index);
		System.out.println("mapped: " + line + ", " + mapped.index);
		int start = mapped.index - sketchFile.offsetForLine(line);
		return new CompilerProblem(editor.getSketchFiles().get(mapped.section), line, start, mapped.length, error, message);
	}
	
	public void preprocess() {
		transform = CompoundTextTransform.create(new CompoundTextTransform.TextHolder() {
			@Override
			public int count() {
				return editor.getSketchFiles().size();
			}
			
			@Override
			public CharSequence getText(int section) {
				// Only preprocess .pde files
				SketchFile sketchFile = editor.getSketchFiles().get(section);
				return sketchFile.isPde() ? fixNewlines(sketchFile.getText()) : "";
			}
		});
		
		try {
			StringBuilder scrubbed = new StringBuilder(transform.getBaseText());
			scrubCommentsAndStrings(scrubbed);
			
			List<String> imports = new ArrayList<>();
			addBaseImports(imports);
			addCodeFolderImports(imports);
			extractImports(scrubbed, imports);
			
			mode = getMode(scrubbed);
			
			List<String> settingsStatements = extractSettings(scrubbed);
			replaceTypeConstructors(scrubbed);
			replaceHexLiterals(scrubbed);
			
			writeHeader(imports); // imports get written here
			writeFooter(settingsStatements);
			
			advancedPreprocess();
			
			findSyntaxProblems();
		} catch (TextTransform.OverlappingEditException | TextTransform.LockException e) {
			e.printStackTrace();
		}
	}
	
	public boolean isOpenGL() {
		return isOpenGL;
	}
	
	private String fixNewlines(String string) {
		return string.replace("\r\n", "\n");
	}
	
	private List<String> extractSettings(CharSequence scrubbed) throws TextTransform.LockException {
		List<String> statements = new ArrayList<>();
		
		CharSequence searchArea = null;
		int offset = 0;
		switch (mode) {
			case ACTIVE:
				Matcher setupMatcher = SETUP_REGEX.matcher(scrubbed);
				if (setupMatcher.find()) {
					offset = scrubbed.toString().indexOf("{", setupMatcher.end());
					if (offset >= 0) {
						Matcher closeMatcher = CLOSING_BRACE.matcher(scrubbed);
						if (closeMatcher.find(offset)) {
							searchArea = scrubbed.subSequence(offset, closeMatcher.end());
						}
					}
				}
				break;
			case STATIC:
				searchArea = scrubbed;
			case JAVA:
			default:
				break;
		}
		
		if (searchArea != null) {
			extractSizeFullScreen(searchArea, offset, statements, build.getAppComponent());
			extractSmooth(searchArea, offset, statements);
		}
		
		return statements;
	}
	
	private static final Pattern SETUP_REGEX = Pattern.compile(
			"(?:^|\\s|;)void\\s+setup\\s*\\(", Pattern.MULTILINE);
	
	private static final Pattern CLOSING_BRACE = Pattern.compile("\\}");
	
	protected static class MethodMatch {
		protected String text;
		protected int start, length;
		protected String[] arguments;
		
		protected MethodMatch(String text, int start, String[] arguments) {
			this.text = text;
			this.start = start;
			this.length = text.length();
			this.arguments = arguments;
			
			System.out.println();
			System.out.println(text);
			System.out.println(start);
			System.out.println(length);
			System.out.println();
		}
		
		protected String getArg(int i) {
			return arguments[i];
		}
		
		@SuppressWarnings("ResultOfMethodCallIgnored")
		protected String getIntArg(int i, String passThrough) throws NumberFormatException {
			String arg = getArg(i);
			if (!passThrough.equals(arg)) {
				 Integer.parseInt(getArg(i));
			}
			return arg;
		}
		
		protected TextTransform.Range toRange() {
			return new TextTransform.Range(start, length);
		}
	}
	
	private static MethodMatch findMethod(CharSequence code, String name) {
		final String left = "(?:^|\\s|;)";
		final String right = "\\s*\\(([^\\)]*)\\)\\s*;";
		Pattern pattern = Pattern.compile(left + name + right, Pattern.MULTILINE | Pattern.DOTALL);
		Matcher matcher = pattern.matcher(code);
		if (matcher.find()) {
			// Get arguments
			String[] groups = matcher.group(1).split(",");
			for (int i = 0; i < groups.length; i++) {
				groups[i] = groups[i].trim();
			}
			// We don't want an empty string from no arguments
			if (groups.length > 0 && groups[0].equals("")) {
				groups = new String[] {};
			}
			return new MethodMatch(matcher.group(), matcher.start(), groups);
		} else {
			return null;
		}
	}
	
	private String getDefaultVrRenderer() {
		return PreferenceManager.getDefaultSharedPreferences(editor).getString("pref_vr_default_renderer", editor.getResources().getString(R.string.pref_vr_default_renderer_default_value));
	}
	
	private static final List<String> RENDERERS;
	private static final List<String> VR_RENDERERS;
	static {
		RENDERERS = new ArrayList<>();
		RENDERERS.add("JAVA2D");
		RENDERERS.add("P2D");
		RENDERERS.add("P3D");
		RENDERERS.add("OPENGL");
		// TODO PDF, SVG, FX2D?
		
		VR_RENDERERS = new ArrayList<>();
		VR_RENDERERS.add("STEREO");
		VR_RENDERERS.add("MONO");
		
		RENDERERS.addAll(VR_RENDERERS);
	}
	
	private void extractSizeFullScreen(CharSequence code, int offset, List<String> statements, ComponentTarget componentTarget) throws TextTransform.LockException {
		MethodMatch sizeStatement = findMethod(code, "size");
		MethodMatch fullScreenStatement = findMethod(code, "fullScreen");
		
		if (sizeStatement != null && fullScreenStatement != null) {
			// Can't have both
			addCompilerProblem(buildCompilerProblem(sizeStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_both_size_fullscreen), true));
			addCompilerProblem(buildCompilerProblem(fullScreenStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_both_size_fullscreen), true));
		}
		
		// We default to fullScreen, even if nothing is specified
		boolean fullScreen = true;
		String renderer = null, width = null, height = null;
		
		if (sizeStatement != null) {
			try {
				switch (sizeStatement.arguments.length) {
					case 2:
						width = sizeStatement.getIntArg(0, "displayWidth");
						height = sizeStatement.getIntArg(1, "displayHeight");
						renderer = null;
						fullScreen = false;
						break;
					case 3:
						width = sizeStatement.getIntArg(0, "displayWidth");
						height = sizeStatement.getIntArg(1, "displayHeight");
						renderer = sizeStatement.getArg(2);
						fullScreen = false;
						break;
					default:
						// Poorly formed size statement
						addCompilerProblem(buildCompilerProblem(sizeStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_size_argument_number), true));
				}
			} catch (NumberFormatException e) {
				// Bad size arguments
				addCompilerProblem(buildCompilerProblem(sizeStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_size_bad_number), true));
			}
		}
		
		// fullScreen will override size
		// We have an error if both are used, but we want to get additional errors, so pick one
		if (fullScreenStatement != null) {
			fullScreen = true;
			if (fullScreenStatement.arguments.length == 1 || fullScreenStatement.arguments.length == 2) {
				// Second argument might be the screen, which we ignore on Android
				renderer = fullScreenStatement.getArg(0);
			} else if (fullScreenStatement.arguments.length > 2) {
				// Too many args
				addCompilerProblem(buildCompilerProblem(fullScreenStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_fullscreen_arguement_number), true));
			}
			// "SPAN" is not a renderer, discard it
			if ("SPAN".equals(renderer)) {
				renderer = null;
			}
		}
		
		if (sizeStatement == null && fullScreenStatement == null) {
			fullScreen = true;
			renderer = null;
		}
		
		if (renderer != null && !RENDERERS.contains(renderer)) {
			// Invalid renderer
			// If renderer is not null, then we have either a size() or fullScreen() statement
			addCompilerProblem(buildCompilerProblem(fullScreen ? fullScreenStatement.toRange() : sizeStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_invalid_renderer), true));
			renderer = null;
		}
		
		switch (componentTarget) {
			case VR:
				fullScreen = true;
				if (!VR_RENDERERS.contains(renderer)) {
					renderer = getDefaultVrRenderer();
				}
				break;
			case WALLPAPER:
				fullScreen = true;
				break;
		}
		
		isOpenGL =  renderer != null && (renderer.equals("P2D") || renderer.equals("P3D") || renderer.equals("OPENGL"));
		
		StringBuilder builder = new StringBuilder();
		
		builder.append(fullScreen ? "fullScreen(" : "size(");
		if (!fullScreen) {
			builder.append(width);
			builder.append(", ");
			builder.append(height);
		}
		if (!fullScreen && renderer != null) {
			builder.append(", ");
		}
		if (renderer != null) {
			builder.append(renderer);
		}
		builder.append(");");
		
		if (fullScreenStatement != null) {
			transform.remove(fullScreenStatement.start + offset, fullScreenStatement.length);
		}
		if (sizeStatement != null) {
			transform.remove(sizeStatement.start + offset, sizeStatement.length);
		}
		statements.add(builder.toString());
	}
	
	private void extractSmooth(CharSequence code, int offset, List<String> statements) throws TextTransform.LockException {
		MethodMatch smoothStatement = findMethod(code, "smooth");
		MethodMatch noSmoothStatement = findMethod(code, "noSmooth");
		
		if (smoothStatement != null && noSmoothStatement != null) {
			// Can't have both
			addCompilerProblem(buildCompilerProblem(smoothStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_both_smooth_nosmooth), true));
			addCompilerProblem(buildCompilerProblem(noSmoothStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_both_smooth_nosmooth), true));
		}
		
		if (noSmoothStatement != null) {
			if (noSmoothStatement.arguments.length > 0) {
				// Too many args
				addCompilerProblem(buildCompilerProblem(noSmoothStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_nosmooth_argument_number), true));
			}
			
			transform.remove(noSmoothStatement.start + offset, noSmoothStatement.length);
			if (smoothStatement == null) {
				// Default to smooth() over noSmooth()
				// We still have an error if both are specified, but we want to correct as many
				// errors as possible so that we can run further preprocessing and compiling in
				// order to find all errors
				statements.add("noSmooth();");
			}
		}
		
		if (smoothStatement != null) {
			String arg = null;
			if (smoothStatement.arguments.length == 1) {
				try {
					arg = smoothStatement.getIntArg(0, "");
				} catch (NumberFormatException e) {
					// Bad arg
					addCompilerProblem(buildCompilerProblem(smoothStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_smooth_bad_number), true));
				}
			} else if (smoothStatement.arguments.length > 1) {
				// Too many args
				addCompilerProblem(buildCompilerProblem(smoothStatement.toRange(), true, editor.getResources().getString(R.string.preprocessor_problem_smooth_argument_number), true));
			}
			
			transform.remove(smoothStatement.start + offset, smoothStatement.length);
			statements.add("smooth(" + (arg != null ? arg : "") + ");");
		}
	}
	
	private void addBaseImports(List<String> imports) {
		for (String pkg : getBaseImports()) {
			imports.add("import " + pkg + ";");
		}
	}
	
	private void addCodeFolderImports(List<String> imports) {
		if (codeFolderPackages != null) {
			for (String pkg : codeFolderPackages) {
				imports.add("import " + pkg + ";");
			}
		}
	}
	
	private static final Pattern IMPORT_REGEX = Pattern.compile(
			"(?:^|;)\\s*(import\\s+(?:(static)\\s+)?((?:\\w+\\s*\\.)*)\\s*(\\S+)\\s*;)",
			Pattern.MULTILINE | Pattern.DOTALL);
	
	private void extractImports(CharSequence scrubbed, List<String> imports) {
		Matcher matcher = IMPORT_REGEX.matcher(scrubbed);
		while (matcher.find()) {
			transform.remove(matcher.start(1), matcher.end(1) - matcher.start(1));
			imports.add(matcher.group(1));
		}
	}
	
	private static final Pattern TYPE_CONSTRUCTOR_REGEX = Pattern.compile("(?<=^|\\W)(int|char|float|boolean|byte)(?=\\s*\\()", Pattern.MULTILINE);
	
	private void replaceTypeConstructors(CharSequence scrubbed) {
		Matcher matcher = TYPE_CONSTRUCTOR_REGEX.matcher(scrubbed);
		while (matcher.find()) {
			// Converts int() to PApplet.parseInt()
			transform.replace(matcher.start(1), matcher.group(1).length(),
					"parse" + Character.toUpperCase(matcher.group(1).charAt(0)) + matcher.group(1).substring(1));
		}
	}
	
	private static final Pattern HEX_LITERAL_REGEX = Pattern.compile("(?<=^|\\W)(#[A-Fa-f0-9]{6})(?=\\W|$)");
	
	private void replaceHexLiterals(CharSequence scrubbed) {
		Matcher matcher = HEX_LITERAL_REGEX.matcher(scrubbed);
		while (matcher.find()) {
			transform.replace(matcher.start(1), 1, "0xff");
		}
	}
	
	private static final Pattern PUBLIC_CLASS = Pattern.compile(
			"(^|;)\\s*public\\s+class\\s+\\S+\\s+extends\\s+PApplet", Pattern.MULTILINE);
	
	private static final Pattern FUNCTION_DECL = Pattern.compile(
			"(^|;)\\s*((public|private|protected|final|static)\\s+)*(void|int|float|double|String|char|byte)(\\s*\\[\\s*\\])?\\s+[a-zA-Z0-9]+\\s*\\(",
			Pattern.MULTILINE);
	
	public enum Mode {
		JAVA, ACTIVE, STATIC;
	}
	
	private Mode getMode(CharSequence scrubbed) {
		if (PUBLIC_CLASS.matcher(scrubbed).find()) {
			return Mode.JAVA;
		} else if (FUNCTION_DECL.matcher(scrubbed).find()) {
			return Mode.ACTIVE;
		} else {
			return Mode.STATIC;
		}
	}
	
	private void writeHeader(List<String> imports) {
		StringBuilder builder = new StringBuilder();
		
		for (String imp : imports) {
			builder.append(imp);
			builder.append('\n');
		}
		
		if (mode != Mode.JAVA) {
			builder.append("\npublic class ");
			builder.append(className);
			builder.append(" extends PApplet {\n");
			
			if (mode == Mode.STATIC) {
				builder.append("public void setup() {\n");
			}
		}
		
		transform.insert(0, builder);
	}
	
	private void writeFooter(List<String> settingsStatements) {
		StringBuilder builder = new StringBuilder();
		
		if (mode != Mode.JAVA) {
			if (mode == Mode.STATIC) {
				builder.append("\n}");
			}
			if (settingsStatements.size() > 0) {
				builder.append("\npublic void settings() {\n");
				for (String statement : settingsStatements) {
					builder.append("  ");
					builder.append(statement);
					builder.append('\n');
				}
				builder.append("}");
			}
			builder.append("\n}\n");
		}
		
		transform.insert(transform.getBaseText().length(), builder);
	}
	
	private CompilationUnit parse(CharSequence source) {
		parser.setSource(source.toString().toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(COMPILER_OPTIONS);
		parser.setStatementsRecovery(true);
		
		return (CompilationUnit) parser.createAST(null);
	}
	
	private CompilationUnit parse(CharSequence source, String className, String[] classpath) {
		parser.setSource(source.toString().toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setCompilerOptions(COMPILER_OPTIONS);
		parser.setStatementsRecovery(true);
		parser.setUnitName(className);
		parser.setEnvironment(classpath, null, null, false);
		parser.setResolveBindings(true);
		
		return (CompilationUnit) parser.createAST(null);
	}
	
	// Verifies that whole input String is floating point literal. Can't be used for searching.
	// https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-DecimalFloatingPointLiteral
	public static final Pattern FLOATING_POINT_LITERAL_VERIFIER;
	static {
		final String DIGITS = "(?:[0-9]|[0-9][0-9_]*[0-9])";
		final String EXPONENT_PART = "(?:[eE][+-]?" + DIGITS + ")";
		FLOATING_POINT_LITERAL_VERIFIER = Pattern.compile(
				"(?:^" + DIGITS + "\\." + DIGITS + "?" + EXPONENT_PART + "?[fFdD]?$)|" +
						"(?:^\\." + DIGITS + EXPONENT_PART + "?[fFdD]?$)|" +
						"(?:^" + DIGITS + EXPONENT_PART + "[fFdD]?$)|" +
						"(?:^" + DIGITS + EXPONENT_PART + "?[fFdD]$)");
	}
	
	// Mask to quickly resolve whether there are any access modifiers present
	private static final int ACCESS_MODIFIERS_MASK =
			Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED;
	
	private void advancedPreprocess() throws TextTransform.OverlappingEditException {
		CompilationUnit intermediate = parse(transform.applyForward());
		// Save edits so that we wait to unlock after we apply them
		final List<TextTransform.Edit> edits = new ArrayList<>();
		
		intermediate.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleType node) {
				// Replace 'color' with 'int'
				if ("color".equals(node.getName().toString())) {
					try {
						edits.add(transform.makeReplace(transform.mapBackward(node.getStartPosition(), node.getLength()), "int"));
					} catch (TextTransform.LockException e) {
						e.printStackTrace();
					}
				}
				return super.visit(node);
			}
			
			@Override
			public boolean visit(NumberLiteral node) {
				// Add 'f' to floats
				String token = node.getToken().toLowerCase();
				if (FLOATING_POINT_LITERAL_VERIFIER.matcher(token).matches() && !token.endsWith("f") && !token.endsWith("d")) {
					try {
					edits.add(transform.makeInsert(transform.mapBackward(node.getStartPosition() + node.getLength()), "f"));
					} catch (TextTransform.LockException e) {
						e.printStackTrace();
					}
				}
				return super.visit(node);
			}
			
			@Override
			public boolean visit(MethodDeclaration node) {
				// Add 'public' to methods with default visibility
				if ((node.getModifiers() & ACCESS_MODIFIERS_MASK) == 0) {
					try {
						edits.add(transform.makeInsert(transform.mapBackward(node.getStartPosition()), "public "));
					} catch (TextTransform.LockException e) {
						e.printStackTrace();
					}
				}
				return super.visit(node);
			}
		});
		
		transform.edit(edits);
	}
	
	private void findSyntaxProblems() throws TextTransform.OverlappingEditException {
		StringBuilder out = transform.applyForward();
		CompilationUnit compilable = parse(out);
		boolean hasSyntaxErrors = false;
		for (IProblem problem : compilable.getProblems()) {
			System.err.println(problem);
			if (problem.isError()) {
				hasSyntaxErrors = true;
				break;
			}
		}
		
//		List<IProblem> problems = Arrays
		
		if (hasSyntaxErrors) {
			System.err.println("Syntax problems found");
		} else {
			System.out.println("No syntax problems found");
		}
		
		System.out.println();
		System.out.println(out.toString());
	}
	
	public String[] getBaseImports() {
		return new String[] {
				"processing.core.*",
				"processing.data.*",
				"processing.event.*",
				"processing.opengl.*",
				
				"java.util.HashMap",
				"java.util.ArrayList",
				"java.io.File",
				"java.io.BufferedReader",
				"java.io.PrintWriter",
				"java.io.InputStream",
				"java.io.OutputStream",
				"java.io.IOException",
		};
	}
	
	// Taken straight from PDEX
	public static void scrubCommentsAndStrings(StringBuilder p) {
		final int length = p.length();
		
		final int OUT = 0;
		final int IN_BLOCK_COMMENT = 1;
		final int IN_EOL_COMMENT = 2;
		final int IN_STRING_LITERAL = 3;
		final int IN_CHAR_LITERAL = 4;
		
		int blockStart = -1;
		
		int prevState = OUT;
		int state = OUT;
		
		for (int i = 0; i <= length; i++) {
			char ch = (i < length) ? p.charAt(i) : 0;
			char pch = (i == 0) ? 0 : p.charAt(i - 1);
			// Get rid of double backslash immediately, otherwise
			// the second backslash incorrectly triggers a new escape sequence
			if (pch == '\\' && ch == '\\') {
				p.setCharAt(i - 1, ' ');
				p.setCharAt(i, ' ');
				pch = ' ';
				ch = ' ';
			}
			switch (state) {
				case OUT:
					switch (ch) {
						case '\'':
							state = IN_CHAR_LITERAL;
							break;
						case '"':
							state = IN_STRING_LITERAL;
							break;
						case '*':
							if (pch == '/') state = IN_BLOCK_COMMENT;
							break;
						case '/':
							if (pch == '/') state = IN_EOL_COMMENT;
							break;
					}
					break;
				case IN_BLOCK_COMMENT:
					if (pch == '*' && ch == '/' && (i - blockStart) > 0) {
						state = OUT;
					}
					break;
				case IN_EOL_COMMENT:
					if (ch == '\r' || ch == '\n') {
						state = OUT;
					}
					break;
				case IN_STRING_LITERAL:
					if ((pch != '\\' && ch == '"') || ch == '\r' || ch == '\n') {
						state = OUT;
					}
					break;
				case IN_CHAR_LITERAL:
					if ((pch != '\\' && ch == '\'') || ch == '\r' || ch == '\n') {
						state = OUT;
					}
					break;
			}
			
			// Terminate ongoing block at last char
			if (i == length) {
				state = OUT;
			}
			
			// Handle state changes
			if (state != prevState) {
				if (state != OUT) {
					// Entering block
					blockStart = i + 1;
				} else {
					// Exiting block
					int blockEnd = i;
					if (prevState == IN_BLOCK_COMMENT && i < length)
						blockEnd--; // preserve star in '*/'
					for (int j = blockStart; j < blockEnd; j++) {
						char c = p.charAt(j);
						if (c != '\n' && c != '\r') p.setCharAt(j, ' ');
					}
				}
			}
			
			prevState = state;
		}
	}
	
	// --------
	
	private static final Map<String, String> COMPILER_OPTIONS;
	static {
		Map<String, String> options = new HashMap<>();
		
		// Not sure whether or not we can use 1.8
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_7, options);
		
		COMPILER_OPTIONS = Collections.unmodifiableMap(options);
	}
}
