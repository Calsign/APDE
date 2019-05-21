package com.calsignlabs.apde.build.dag;

import com.calsignlabs.apde.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Map;

public class WriteTemplateBuildTask extends BuildTask {
	private Getter<String> templateGetter;
	private Getter<File> fileGetter;
	private Getter<Map<String, String>> replaceMapGetter;
	
	public WriteTemplateBuildTask(Getter<String> templateGetter, Getter<File> fileGetter, Getter<Map<String, String>> replaceMapGetter, BuildTask... deps) {
		super(deps);
		this.templateGetter = templateGetter;
		this.fileGetter = fileGetter;
		this.replaceMapGetter = replaceMapGetter;
		
		orGetterChangeNoticer(templateGetter, fileGetter, replaceMapGetter);
	}
	
	@Override
	public void run() throws InterruptedException {
		finish(createFileFromTemplate(templateGetter.get(getBuildContext()), fileGetter.get(getBuildContext()), replaceMapGetter.get(getBuildContext()), getBuildContext()));
	}
	
	@Override
	public CharSequence getTitle() {
		return getBuildContext().getResources().getString(R.string.build_copying);
	}
	
	public static boolean createFileFromTemplate(String template, File file, Map<String, String> replaceMap, BuildContext context) {
		BufferedReader reader = null;
		PrintStream writer = null;
		
		try {
			if (!file.getParentFile().mkdirs()) {
				System.err.println("Failed to make parent directory");
				return false;
			}
			
			reader = new BufferedReader(new InputStreamReader(context.getResources().getAssets().open("templates/" + template)));
			writer = new PrintStream(new FileOutputStream(file));
			
			String line;
			while ((line = reader.readLine()) != null) {
				// From AndroidUtil.java in processing/processing-android
				if (line.contains("@@") && replaceMap != null) {
					StringBuilder sb = new StringBuilder(line);
					int index;
					for (String key : replaceMap.keySet()) {
						String val = replaceMap.get(key);
						while ((index = sb.indexOf(key)) != -1) {
							sb.replace(index, index + key.length(), val);
						}
					}
					line = sb.toString();
				}
				
				writer.println(line);
			}
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
				if (writer != null) {
					writer.flush();
					writer.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
