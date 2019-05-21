package com.calsignlabs.apde.build.dag;

import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.Preprocessor;

import java.io.File;
import java.io.IOException;

import processing.core.PApplet;

public class PreprocessBuildTask extends BuildTask {
	private Getter<File> srcFile;
	
	private Preprocessor previous;
	
	public PreprocessBuildTask(Getter<File> srcFile, BuildTask... deps) {
		super(deps);
		
		this.srcFile = srcFile;
		
		orGetterChangeNoticer(srcFile);
	}
	
	@Override
	public void run() throws InterruptedException {
		if (previous == null || hasChanged(getBuildContext()).changed()) {
			Preprocessor preprocessor = new Preprocessor(getBuildContext());
			preprocessor.preprocess();
			
			try {
				writePreprocessedFiles(preprocessor, getBuildContext(), srcFile.get(getBuildContext()));
				previous = preprocessor;
			} catch (IOException e) {
				e.printStackTrace();
				fail();
			}
		}
		
		getBuildContext().setPreprocessor(previous);
		
		succeed();
	}
	
	@Override
	public String getTitle() {
		return getBuildContext().getResources().getString(R.string.build_preprocessing);
	}
	
	@Override
	public boolean shouldRunIfNotUpdated() {
		// We need to set the preprocessor even if this hasn't changed
		return true;
	}
	
	public static void writePreprocessedFiles(Preprocessor preprocessor, BuildContext context,
											  File srcFolder) throws IOException {
		// Write main .java file
		writePreprocessedFile(preprocessor.getPreprocessedText().toString(), context.getSketchMainFilename(), context.getPackageName(), srcFolder);
		
		// Write all .java files
		for (SketchCode sketchFile : context.getSketchFiles()) {
			if (sketchFile.isJava()) {
				sketchFile.javaImportHeaderOffset = writePreprocessedFile(sketchFile.getText().toString(),
						sketchFile.getFilename(), context.getPackageName(), srcFolder);
			}
		}
	}
	
	@SuppressWarnings("ResultOfMethodCallIgnored")
	private static int writePreprocessedFile(String code, String filename, String packageName, File srcFolder) throws IOException {
		String packageMatch = Preprocessor.extractPackageName(code);
		int added = 0;
		if (packageMatch == null) {
			String toInsert = "package " + packageName + ";\n";
			code = toInsert + code;
			packageMatch = packageName;
			added = toInsert.length();
		}
		
		File pkgFolder = new File(srcFolder, packageMatch.replace('.', '/'));
		pkgFolder.mkdirs();
		saveFile(code, new File(pkgFolder, filename));
		
		return added;
	}
	
	private static void saveFile(String str, File file) throws IOException {
		File temp = File.createTempFile(file.getName(), null, file.getParentFile());
		try {
			// fix from cjwant to prevent symlinks from being destroyed.
			File canon = file.getCanonicalFile();
			file = canon;
		} catch(IOException e) {
			throw new IOException("Could not resolve canonical representation of " + file.getAbsolutePath());
		}
		PApplet.saveStrings(temp, new String[] { str });
		if (file.exists()) {
			boolean result = file.delete();
			if (!result) {
				throw new IOException("Could not remove old version of " + file.getAbsolutePath());
			}
		}
		boolean result = temp.renameTo(file);
		if (!result) {
			throw new IOException("Could not replace " + file.getAbsolutePath());
		}
	}
}
