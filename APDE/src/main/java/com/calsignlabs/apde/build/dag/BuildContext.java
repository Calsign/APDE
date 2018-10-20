package com.calsignlabs.apde.build.dag;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.build.CompilerProblem;
import com.calsignlabs.apde.build.ComponentTarget;
import com.calsignlabs.apde.build.Manifest;
import com.calsignlabs.apde.build.Preprocessor;
import com.calsignlabs.apde.build.SketchProperties;
import com.calsignlabs.apde.contrib.Library;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class BuildContext {
	private File buildFolder, sketchFolder, stageFolder, librariesFolder, rootFilesDir;
	private String sketchName;
	private boolean isExample, verbose, external, injectLogBroadcaster, customProblems;
	private ComponentTarget componentTarget;
	private List<SketchCode> sketchFiles;
	private SketchProperties sketchProperties;
	private Manifest manifest;
	private List<CompilerProblem> problems;
	private Map<String, List<Library>> importToLibraryTable;
	private Preprocessor preprocessor;
	private boolean hasData;
	
	private Set<String> completedTasks;
	
	private Resources resources;
	private SharedPreferences preferences;
	
	private long timestamp;
	
	private Handler handler;
	
	private BuildContext() {
		problems = new ArrayList<>();
	}
	
	public static BuildContext create(APDE context) {
		BuildContext buildContext = new BuildContext();
		
		buildContext.buildFolder = context.getBuildFolder();
		buildContext.sketchFolder = context.getSketchLocation();
		buildContext.stageFolder = new File(context.getFilesDir(), "stage");
		buildContext.librariesFolder = context.getLibrariesFolder();
		buildContext.sketchName = context.getSketchName();
		buildContext.isExample = context.isExample();
		buildContext.verbose = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("build_output_verbose", false);
		buildContext.external = false;
		buildContext.injectLogBroadcaster = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("inject_log_broadcaster", true);
		buildContext.customProblems = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_problem_overview_enable", true);
		buildContext.componentTarget = context.getEditor().getComponentTarget();
		buildContext.hasData = true;
		
		buildContext.sketchFiles = new ArrayList<>(context.getEditor().getCodeCount());
		for (int i = 0; i < context.getEditor().getSketchFiles().size(); i++) {
			buildContext.sketchFiles.add(SketchCode.fromSketchFile(context.getEditor().getSketchFiles().get(i), i));
		}
		
		buildContext.completedTasks = Collections.synchronizedSet(new LinkedHashSet<>());
		
		buildContext.resources = context.getResources();
		buildContext.preferences = PreferenceManager.getDefaultSharedPreferences(context);
		
		// Note: infinite loop potential here, tread carefully
		// Because SketchProperties stores a reference to BuildContext
		buildContext.sketchProperties = context.getProperties(buildContext);
		buildContext.importToLibraryTable = context.getImportToLibraryTable();
		
		buildContext.rootFilesDir = context.getFilesDir();
		
		buildContext.timestamp = System.currentTimeMillis();
		
		buildContext.handler = new Handler();
		
		return buildContext;
	}
	
	public File getBuildFolder() {
		return buildFolder;
	}
	
	public File getSketchFolder() {
		return sketchFolder;
	}
	
	public File getStageFolder() {
		return stageFolder;
	}
	
	public File getLibrariesFolder() {
		return librariesFolder;
	}
	
	public String getSketchName() {
		return sketchName;
	}
	
	public String getSketchMainFilename() {
		return getSketchName() + ".java";
	}
	
	public boolean isExample() {
		return isExample;
	}
	
	public boolean isVerbose() {
		return verbose;
	}
	
	public boolean isExternal() {
		return external;
	}
	
	public boolean injectLogBroadcaster() {
		return injectLogBroadcaster;
	}
	
	public boolean isCustomProblems() {
		return customProblems;
	}
	
	public ComponentTarget getComponentTarget() {
		return componentTarget;
	}
	
	public List<SketchCode> getSketchFiles() {
		return sketchFiles;
	}
	
	public SketchProperties getSketchProperties() {
		 return sketchProperties;
	}
	
	public Manifest getManifest() {
		return manifest;
	}
	
	public void setManifest(Manifest manifest) {
		this.manifest = manifest;
	}
	
	public String getPackageName() {
		return getManifest().getPackageName();
	}
	
	public List<Library> getImportedLibraries() {
		return getPreprocessor().getImportedLibraries();
	}
	
	public List<CompilerProblem> getProblems() {
		return problems;
	}
	
	public Map<String, List<Library>> getImportToLibraryTable() {
		return importToLibraryTable;
	}
	
	public Preprocessor getPreprocessor() {
		return preprocessor;
	}
	
	public void setPreprocessor(Preprocessor preprocessor) {
		this.preprocessor = preprocessor;
	}
	
	public void setHasData(boolean hasData) {
		this.hasData = hasData;
	}
	
	public boolean hasData() {
		return hasData;
	}
	
	protected Set<String> getCompletedTasks() {
		return completedTasks;
	}
	
	public boolean isTaskCompleted(BuildTask buildTask) {
		return completedTasks.contains(buildTask.getName());
	}
	
	public Resources getResources() {
		return resources;
	}
	
	public SharedPreferences getPreferences() {
		return preferences;
	}
	
	public File getRootFilesDir() {
		return rootFilesDir;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void post(Runnable runnable) {
		handler.post(runnable);
	}
	
	/**
	 * Gets the number of cores available in this device, across all processors.
	 * Requires: Ability to peruse the filesystem at "/sys/devices/system/cpu"
	 *
	 * From StackOverflow: http://stackoverflow.com/a/10377934
	 *
	 * @return The number of cores, or Runtime.availableProcessors() if failed to get result
	 */
	public static int getNumCores() {
		try {
			// Get directory containing CPU info
			File dir = new File("/sys/devices/system/cpu/");
			// Filter to only list the devices we care about
			File[] files = dir.listFiles(pathname -> Pattern.matches("cpu[0-9]+", pathname.getName()));
			// Return the number of cores (virtual CPU devices)
			return files.length;
		} catch (Exception e) {
			//Default to return 1 core
			return Runtime.getRuntime().availableProcessors();
		}
	}
}
