package com.calsignlabs.apde.build.dag;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.core.content.FileProvider;
import com.calsignlabs.apde.support.documentfile.DocumentFile;

import com.calsignlabs.apde.APDE;
import com.calsignlabs.apde.R;
import com.calsignlabs.apde.build.Build;
import com.calsignlabs.apde.build.SketchPreviewerBuilder;
import com.calsignlabs.apde.contrib.Library;
import com.calsignlabs.apde.support.MaybeDocumentFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModularBuild {
	private static AtomicBoolean BUILD_LOCK = new AtomicBoolean(false);
	
	private APDE global;
	
	private Map<String, Boolean> previousTaskStatus;
	private BuildTaskRunner runner;
	
	// Prettier name aliases
	private interface SketchFile extends Getter<MaybeDocumentFile> {}
	private interface BuildFile extends Getter<File> {}
	
	private static SketchFile makeSketchFile(SketchFile parent, String name, String mimeType) {
		return context -> {
			try {
				return parent.get(context).child(name, mimeType);
			} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
				// TODO: probably need better handling
				throw new RuntimeException(e);
			}
		};
	}
	
	private static BuildFile makeBuildFile(BuildFile parent, String name) {
		return context -> new File(parent.get(context), name);
	}
	
	private static SketchFile SKETCH = BuildContext::getSketchFolder;
	private static SketchFile SKETCH_PROPERTIES = makeSketchFile(SKETCH, "sketch.properties", "text/plain");
	private static SketchFile SKETCH_DATA = makeSketchFile(SKETCH, "data", DocumentsContract.Document.MIME_TYPE_DIR);
	private static SketchFile SKETCH_CODE_FOLDER = makeSketchFile(SKETCH, "code", DocumentsContract.Document.MIME_TYPE_DIR);
	private static SketchFile SKETCH_CODE_DEX_FOLDER = makeSketchFile(SKETCH, "code-dex", DocumentsContract.Document.MIME_TYPE_DIR);
	private static SketchFile SKETCH_RES = makeSketchFile(SKETCH, "res", DocumentsContract.Document.MIME_TYPE_DIR);
	
	private static SketchFile SKETCHBOOK_LIBRARIES = BuildContext::getLibrariesFolder;
	
	private static BuildFile BUILD = BuildContext::getBuildFolder;
	
	private static BuildFile SRC = makeBuildFile(BUILD, "src");
	private static BuildFile GEN = makeBuildFile(BUILD, "gen");
	private static BuildFile LIBS = makeBuildFile(BUILD, "libs");
	private static BuildFile ASSETS = makeBuildFile(BUILD, "assets");
	private static BuildFile RES = makeBuildFile(BUILD, "res");
	
	private static BuildFile BIN = makeBuildFile(BUILD, "bin");
	private static BuildFile DEXED_LIBS = makeBuildFile(BIN, "dexedLibs");
	
	private static BuildFile STAGE = BuildContext::getStageFolder;
	
	private static BuildFile SUPPORT_RES = makeBuildFile(BUILD, "support-res");
	private static BuildFile SUPPORT_WEARABLE_RES = makeBuildFile(BUILD, "support-wearable-res");
	private static BuildFile VR_RES = makeBuildFile(BUILD, "vr-res");
	
	private static BuildFile GVR_BINARY_ZIP = makeBuildFile(BUILD, "vr-lib.zip");
	private static BuildFile GLSL_ZIP = makeBuildFile(BUILD, "glsl-processing.zip");
	
	private static BuildFile RES_LAYOUT = makeBuildFile(RES, "layout");
	private static BuildFile RES_VALUES = makeBuildFile(RES, "values");
	private static BuildFile RES_XML = makeBuildFile(RES, "xml");
	private static BuildFile[] RES_DRAWABLE;
	
	private static BuildFile AAPT = makeBuildFile(STAGE, "aapt");
	private static BuildFile ANDROID_JAR = makeBuildFile(STAGE, "android.jar");
	
	private static BuildFile MANIFEST = makeBuildFile(BUILD, "AndroidManifest.xml");
	private static BuildFile SKETCH_APK_RES = context -> new File(BIN.get(context), context.getSketchName() + ".apk.res");
	
	private static IconSet[] ICONS;
	
	private static BuildFile BIN_CLASSES = makeBuildFile(BIN, "classes");
	private static BuildFile BIN_SKETCH_CLASSES = makeBuildFile(BIN, "sketch-classes.dex");
	
	private static BuildFile ROOT_FILES_INTERNAL = BuildContext::getRootFilesDir;
	
	private static BuildFile PREVIEW_SKETCH_DEX = makeBuildFile(ROOT_FILES_INTERNAL, "sketch.dex");
	private static BuildFile PREVIEW_SKETCH_DATA = makeBuildFile(ROOT_FILES_INTERNAL, "preview_sketch_data.zip");
	
	private static class IconSet {
		protected SketchFile sketchIcon;
		protected String assetsPath;
		protected BuildFile resIcon;
		
		private IconSet(BuildFile drawableFolder, String iconSize) {
			String iconFile = "icon-" + iconSize + ".png";
			sketchIcon = makeSketchFile(SKETCH, iconFile, "image/png");
			assetsPath = "icons/" + iconFile;
			resIcon = makeBuildFile(drawableFolder, "icon.png");
		}
	}
	
	static {
		String[] folderExtensions = {"-ldpi", "",     "-hdpi", "-xhdpi", "-xxhdpi", "-xxxhdpi"};
		String[] iconSizes        = {"36",    "48",   "72",    "96",     "144",     "192"};
		RES_DRAWABLE = new BuildFile[folderExtensions.length];
		ICONS = new IconSet[iconSizes.length];
		for (int i = 0; i < RES_DRAWABLE.length; i ++) {
			RES_DRAWABLE[i] = makeBuildFile(RES, "drawable" + folderExtensions[i]);
			ICONS[i] = new IconSet(RES_DRAWABLE[i], iconSizes[i]);
		}
	}
	
	private String previewDexJarPrefix = "preview_dexed_lib_";
	
	private BuildTask RUN, COMPILE, CLEAN;
	
	public ModularBuild(APDE global) {
		this.global = global;
		
		previousTaskStatus = new HashMap<>();
		
		makeDag();
	}
	
	private void makeDag() {
		// INIT
		
		BuildContext throwawayContext;
		try {
			throwawayContext = BuildContext.create(global);
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
			return;
		}
		
		BuildTask makeFolders = new MkdirBuildTask(BUILD, SRC, GEN, LIBS, ASSETS, RES, BIN, DEXED_LIBS, STAGE).setName("make folders");
		
		String[] libs = { "processing-core", "support-core-utils", "support-compat", "appcompat",
				"support-fragment", "support-wearable", "percent", "gvr", "vr" };
		String[] dexedLibs = { "all-lib-dex", "support-wearable-dex", "vr-dex" };
		
		List<BuildTask> copyAssetsTasks = new ArrayList<>();
		
		copyAssetsTasks.add(makeAssetsExtractor(SUPPORT_RES, throwawayContext, makeFolders).setName("support-res"));
		copyAssetsTasks.add(makeAssetsExtractor(SUPPORT_WEARABLE_RES, throwawayContext, makeFolders).setName("support-wearable-res"));
		copyAssetsTasks.add(makeAssetsExtractor(VR_RES, throwawayContext, makeFolders).setName("vr-res"));
		
		for (String lib : libs) {
			copyAssetsTasks.add(new CopyBuildTask(makeFolders).inAsset("libs/" + lib + ".jar")
					.outFile(makeBuildFile(LIBS, lib + ".jar")).setName("copy: " + lib));
		}
		
		for (String dexedLib : dexedLibs) {
			copyAssetsTasks.add(new CopyBuildTask(makeFolders).inAsset("libs-dex/" + dexedLib + ".jar")
					.outFile(makeBuildFile(DEXED_LIBS, dexedLib + ".jar")).setName("copy: " + dexedLib));
		}
		
		copyAssetsTasks.add(makeAssetsCopier(GVR_BINARY_ZIP, throwawayContext, makeFolders).setName("gvr binary"));
		copyAssetsTasks.add(makeAssetsCopier(GLSL_ZIP, throwawayContext, makeFolders).setName("glsl"));
		
		copyAssetsTasks.add(new LambdaBuildTask(context -> AAPT.get(context).setExecutable(true, true), "",
				new CopyBuildTask(makeFolders).inAsset(getAaptName()).outFile(AAPT).setName("copy aapt")).setName("aapt executable"));
		
		copyAssetsTasks.add(makeAssetsCopier(ANDROID_JAR, throwawayContext, makeFolders).setName("android.jar"));
		
		BuildTask init = new CompoundBuildTask(copyAssetsTasks).setName("init");
		
		// SKETCH FOLDER NOTICERS
		
		BuildTask sketchName = new ChangeNoticerWrapper<>(
				(ChangeNoticerWrapper.MemoryChangeNoticer<String>) (ref, context) -> {
			boolean match = !ref.stored() || !ref.retrieve().equals(context.getSketchName());
			ref.store(context.getSketchName());
			return match;
		}).setName("sketch name");
		BuildTask sketchProperties = makeDocumentChecksummer(SKETCH_PROPERTIES).setName("sketch properties");
		BuildTask dataFolder = makeDocumentChecksummer(SKETCH_DATA).setName("sketch data");
		BuildTask codeFolder = makeDocumentChecksummer(SKETCH_CODE_FOLDER).setName("sketch code folder");
		BuildTask codeDexFolder = makeDocumentChecksummer(SKETCH_CODE_DEX_FOLDER).setName("sketch code dex folder");
		BuildTask resFolder = makeDocumentChecksummer(SKETCH_RES).setName("sketch res");
		BuildTask sketchbookLibrariesFolder = makeDocumentChecksummer(SKETCHBOOK_LIBRARIES).setName("sketchbook libraries");
		
		List<BuildTask> sketchIconChecksumList = new ArrayList<>();
		
		for (IconSet icon : ICONS) {
			sketchIconChecksumList.add(makeDocumentChecksummer(icon.sketchIcon).setName("icon checksum: " + icon.assetsPath));
		}
		
		// TODO watch face icons
		
		BuildTask sketchIcons = new CompoundBuildTask(sketchIconChecksumList).setName("sketch icons");
		
		BuildTask sketchCode = new ChangeNoticerWrapper(new SketchCodeChangeNoticer()).setName("sketch code");
		
		// STUFF
		
		BuildTask reloadLibraries = new LambdaBuildTask(context -> {
			context.reloadLibraries();
			return true;
		}, "Reload libraries", sketchbookLibrariesFolder).setName("reload libraries");
		
		BuildTask generateManifest = new GenerateManifestBuildTask(MANIFEST, sketchName, sketchProperties, init).setName("generate manifest");
		List<BuildTask> manifestDepList = Collections.singletonList(generateManifest);
		
		BuildTask deleteOldSrc = new DeleteFileTask(SRC, sketchCode, generateManifest)
				.setContentsOnly(true).setDependencyOnly(true).setName("delete old src");
		// TODO libraries can put files into assets too
		BuildTask deleteOldAssets = new DeleteFileTask(ASSETS, dataFolder).setContentsOnly(true)
				.setContentsOnly(true).setDependencyOnly(true).setName("delete old assets");
		BuildTask deleteOldRes = new DeleteFileTask(RES, init, resFolder, sketchIcons)
				.setContentsOnly(true).setDependencyOnly(true).setName("delete old res");
		
		// We only need to delete this if the package name changes
		// If there is no sketch.properties file, we won't detect change there, but we can watch
		// the sketch name instead (for switching between sketches)
		BuildTask deleteOldGen = new DeleteFileTask(GEN, sketchProperties, sketchName)
				.setContentsOnly(true).setDependencyOnly(true).setName("delete old gen");
		
		BuildTask deleteOldApkRes = new CleanOldBuildTask(BIN, ".*\\.apk\\.res",
				(context, file, filename) -> file.equals(SKETCH_APK_RES.get(context)))
				.setName("delete old apk res");
		
		BuildTask copySketchCode = new CopyBuildTask(codeFolder, init).inDocument(SKETCH_CODE_FOLDER).outFolder(LIBS).setVacuousSuccess(true).setName("copy sketch code");
		BuildTask copySketchCodeDex = new CopyBuildTask(codeDexFolder, init).inDocument(SKETCH_CODE_DEX_FOLDER).outFolder(DEXED_LIBS).setVacuousSuccess(true).setName("copy sketch code dex");
		
		BuildTask copySketchRes = new CopyBuildTask(resFolder, init, deleteOldRes).inDocument(SKETCH_RES).outFolder(RES).setVacuousSuccess(true).setName("copy sketch res");
		BuildTask copySketchData = new CopyBuildTask(dataFolder, deleteOldAssets, init).inDocument(SKETCH_DATA).outFolder(ASSETS).setVacuousSuccess(true).setName("copy sketch data");
		
		BuildTask makeSketchClassFolder = new MkdirBuildTask(getSketchClassLocation("", manifestDepList), generateManifest, deleteOldSrc, init).setName("make sketch class location");
		BuildTask makeResDirs = new MkdirBuildTask(Arrays.asList(RES_LAYOUT, RES_VALUES, RES_XML), init, deleteOldRes).setName("make res dirs");
		BuildTask makeResDrawableDirs = new MkdirBuildTask(Arrays.asList(RES_DRAWABLE), init, deleteOldRes).setName("make res drawable dirs");
		
		// TODO respect "inject log broadcaster" settting
		BuildTask writeLogBroadcasterUtil = new WriteTemplateBuildTask(
				Getter.wrap("APDEInternalLogBroadcasterUtil.java.tmpl"),
				getSketchClassLocation("APDEInternalLogBroadcasterUtil.java", manifestDepList),
				context -> {
					Map<String, String> replaceMap = new HashMap<>();
					replaceMap.put("@@package_name@@", context.getPackageName());
					return replaceMap;
				}, makeSketchClassFolder, generateManifest, deleteOldSrc, init).setName("write log broadcaster util");
		
		// RES
		
		BuildTask writeRes = new ContextualCompoundBuildTask((context, tasks) -> {
			switch (context.getComponentTarget()) {
				case APP:
				case PREVIEW:
					tasks.add(new WriteTemplateBuildTask(Getter.wrap("LayoutActivity.xml.tmpl"),
							makeBuildFile(RES_LAYOUT, "main.xml"),
							x -> {
								Map<String, String> replaceMap = new HashMap<>();
								replaceMap.put("@@sketch_class_name@@", context.getSketchName());
								return replaceMap;
							}, makeResDirs, sketchName).setName("write layout main xml"));
					tasks.add(new WriteTemplateBuildTask(Getter.wrap("StylesFragment.xml.tmpl"),
							makeBuildFile(RES_VALUES, "styles.xml"), Getter.getNull(), makeResDirs)
							.setName("write styles fragment xml"));
					break;
				case WALLPAPER:
					tasks.add(new WriteTemplateBuildTask(Getter.wrap("XMLWallpaper.xml.tmpl"),
							makeBuildFile(RES_XML, "wallpaper.xml"), Getter.getNull(), makeResDirs)
							.setName("write xml wallpaper"));
					tasks.add(new WriteTemplateBuildTask(Getter.wrap("StringsWallpaper.xml.tmpl"),
							makeBuildFile(RES_VALUES, "strings.xml"),
							x -> {
								Map<String, String> replaceMap = new HashMap<>();
								replaceMap.put("@@sketch_class_name@@", context.getSketchName());
								return replaceMap;
							}, makeResDirs, sketchName)
							.setName("write strings wallpaper"));
					break;
				case WATCHFACE:
					tasks.add(new WriteTemplateBuildTask(Getter.wrap("XMLWatchFace.xml.tmpl"),
							makeBuildFile(RES_XML, "watch_face.xml"), Getter.getNull(), makeResDirs)
							.setName("write xml watch face"));
					break;
				case VR:
					tasks.add(new WriteTemplateBuildTask(Getter.wrap("StylesVR.xml.tmpl"),
							makeBuildFile(RES_VALUES, "styles.xml"), Getter.getNull(), makeResDirs)
							.setName("write styles vr"));
					break;
			}
		}).setName("write res");
		
		BuildTask copyIcons = new ContextualCompoundBuildTask((context, tasks) -> {
			for (IconSet icon : ICONS) {
				// TODO need to add sketchIcons as a change dependency?
				if (icon.sketchIcon.get(context).exists()) {
					tasks.add(new CopyBuildTask(deleteOldRes, makeResDrawableDirs, sketchIcons)
							.inDocument(icon.sketchIcon).outFile(icon.resIcon, false)
							.setName("copy user icon " + icon.assetsPath));
				} else {
					tasks.add(new CopyBuildTask(deleteOldRes, makeResDrawableDirs, sketchIcons)
							.inAsset(icon.assetsPath).outFile(icon.resIcon, false)
							.setName("copy assets icon " + icon.assetsPath));
				}
			}
			
			// TODO watch face icons
		}).setName("copy icons");
		
		// THE MEAT OF THE BUILD
		
		BuildTask preprocess = new PreprocessBuildTask(SRC, init, makeSketchClassFolder,
				generateManifest, sketchCode, deleteOldSrc, reloadLibraries).setName("preprocess");
		
		BuildTask writeMainClass = new WriteTemplateBuildTask(
				context -> context.getComponentTarget().getMainClassTemplate(),
				context -> getSketchClassLocation(
						context.getComponentTarget().getMainClassName(), manifestDepList).get(context),
				context -> {
					Map<String, String> replaceMap = new HashMap<String, String>();
					replaceMap.put("@@package_name@@", context.getPackageName());
					replaceMap.put("@@sketch_class_name@@", context.getSketchName());
					replaceMap.put("@@external@@", context.isExternal()
							? "sketch.setExternal(true);" : "");
					replaceMap.put("@@log_broadcaster@@", context.injectLogBroadcaster()
							? getLogBroadcasterInsert(context) : "");
					replaceMap.put("@@watchface_classs@@", context.getPreprocessor().isOpenGL()
							? "PWatchFaceGLES" : "PWatchFaceCanvas");
					return replaceMap;
				}, sketchName, generateManifest, preprocess, makeSketchClassFolder)
				.setName("write main class");
		
		BuildTask aapt = new AaptBuildTask(AAPT, RES, SUPPORT_RES, SUPPORT_WEARABLE_RES, VR_RES,
				GEN, ASSETS, MANIFEST, ANDROID_JAR, SKETCH_APK_RES, init, generateManifest,
				writeRes, copyIcons, copySketchRes, copySketchData, deleteOldApkRes, deleteOldGen)
				.setName("run aapt");
		
		BuildTask copyLibraryFiles = new ContextualCompoundBuildTask((context, tasks) -> {
			tasks.add(preprocess);
			tasks.add(init);
			
			// This can be called before the preprocessing has been done, but we won't run until
			// the preprocessor is ready
			if (context.getPreprocessor() != null) {
				List<DocumentFile> libraryLibs = new ArrayList<>();
				List<DocumentFile> libraryDexedLibs = new ArrayList<>();
				
				for (Library library : context.getImportedLibraries()) {
					try {
						for (DocumentFile exportFile : library.getAndroidExports(context.getLibrariesFolder())) {
							String exportName = exportFile.getName();
							
							if (!exportFile.exists()) {
								System.err.println(String.format(Locale.US, context.getResources()
										.getString(R.string.build_export_library_file_missing), exportName));
							} else if (exportFile.isDirectory()) {
								// Copy native library folders
								if (exportName.equals("armeabi") ||
										exportName.equals("armeabi-v7a") ||
										exportName.equals("x86")) {
									
									tasks.add(new CopyBuildTask(preprocess, sketchbookLibrariesFolder)
											.inDocument(Getter.wrap(new MaybeDocumentFile(exportFile)))
											.outFolder(makeBuildFile(LIBS, exportName))
											.setName("copy library export native folder: " + exportName));
								} else {
									tasks.add(new CopyBuildTask(preprocess, sketchbookLibrariesFolder)
											.inDocument(Getter.wrap(new MaybeDocumentFile(exportFile)))
											.outFolder(makeBuildFile(ASSETS, exportName))
											.setName("copy library export folder: " + exportName));
								}
							} else if (exportFile.isFile()) {
								// Note: we dropped support for zip files (treated as jars)
								// Just rename them to jar
								
								if (exportName.toLowerCase(Locale.US).endsWith("-dex.jar")) {
									libraryDexedLibs.add(exportFile);
								} else if (exportName.toLowerCase(Locale.US).endsWith(".jar")) {
									libraryLibs.add(exportFile);
								} else {
									tasks.add(new CopyBuildTask(preprocess, sketchbookLibrariesFolder)
											.inDocument(Getter.wrap(new MaybeDocumentFile(exportFile)))
											.outFile(makeBuildFile(ASSETS, exportName))
											.setName("copy library export unknown/asset: " + exportName));
								}
							}
						}
					} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
						e.printStackTrace();
					}
				}
				
				context.setLibraryLibs(libraryLibs);
				context.setLibraryDexedLibs(libraryDexedLibs);
			}
		}).setName("copy library files");
		
		BuildTask deleteOldLibs = new CleanOldBuildTask(LIBS, ".*",
				(context, file, filename) -> {
					for (String lib : libs) {
						if (filename.equals(lib + ".jar")) {
							return true;
						}
					}
					for (DocumentFile lib : context.getLibraryLibs()) {
						if (filename.equals(lib.getName())) {
							return true;
						}
					}
					return false;
				}, init, preprocess, copyLibraryFiles).setName("delete old libs");
		
		BuildTask deleteOldDexLibs = new CleanOldBuildTask(DEXED_LIBS, ".*",
				(context, file, filename) -> {
					for (String lib : dexedLibs) {
						if (filename.equals(lib + ".jar")) {
							return true;
						}
					}
					for (DocumentFile dexLib : context.getLibraryDexedLibs()) {
						if (filename.equals(dexLib.getName())) {
							return true;
						}
					}
					return false;
				}, init, preprocess, copyLibraryFiles).setName("delete old dexed libs");
		
		BuildTask copyLibraryLibs = new ContextualCompoundBuildTask((context, tasks) -> {
			tasks.add(deleteOldLibs);
			tasks.add(deleteOldDexLibs);
			tasks.add(copyLibraryFiles);
			tasks.add(preprocess);
			tasks.add(init);
			
			if (context.getLibraryLibs() != null) {
				for (DocumentFile lib : context.getLibraryLibs()) {
					tasks.add(new CopyBuildTask(copyLibraryFiles)
							.inDocument(Getter.wrap(new MaybeDocumentFile(lib)))
							.outFile(makeBuildFile(LIBS, lib.getName()))
							.setName("copy library export jar: " + lib.getName()));
				}
				for (DocumentFile dexLib : context.getLibraryDexedLibs()) {
					tasks.add(new CopyBuildTask(copyLibraryFiles)
							.inDocument(Getter.wrap(new MaybeDocumentFile(dexLib)))
							.outFile(makeBuildFile(DEXED_LIBS, dexLib.getName()))
							.setName("copy library export dex jar: " + dexLib.getName()));
				}
			}
		}).setName("copy imported libraries");
		
		BuildTask deleteOldBinClasses = new DeleteFileTask(BIN_CLASSES, init, preprocess, aapt)
				.setContentsOnly(true).setDependencyOnly(true).setName("delete old bin classes");
		
		BuildTask compile = new CompileBuildTask(LIBS, ANDROID_JAR, SRC, GEN, BIN_CLASSES,
				init, preprocess, aapt, writeMainClass, writeLogBroadcasterUtil, copySketchCode,
				copySketchCodeDex, copyLibraryFiles, copyLibraryLibs, deleteOldSrc, reloadLibraries,
				deleteOldBinClasses).setName("compile");
		
		BuildTask dxDex = new DxDexBuildTask(BIN_SKETCH_CLASSES, BIN_CLASSES, compile).setName("dx dex");
		
		BuildTask checkPreviewerInstalled = new CheckPreviewerInstalledBuildTask(generateManifest)
				.setName("check previewer installed");
		
		BuildTask previewSketchDex = new CopyBuildTask(dxDex)
				.inFile(BIN_SKETCH_CLASSES).outFile(PREVIEW_SKETCH_DEX)
				.setName("preview copy sketch dex");
		BuildTask previewDataZip = new CopyBuildTask(dataFolder)
				.inDocument(SKETCH_DATA).outFile(PREVIEW_SKETCH_DATA).compress()
				.setName("preview copy sketch data");
		
		// TODO this isn't being run every build like it should be
		BuildTask deleteOldDexedLibsPreview = new CleanOldBuildTask(ROOT_FILES_INTERNAL,
				previewDexJarPrefix + ".*",
				(context, file, filename) -> {
					String realName = file.getName().substring(previewDexJarPrefix.length());
					for (DocumentFile newDexedLib : context.getLibraryDexedLibs()) {
						// We compare names and then checksums
						// If names are the same, file might not necessarily be the same;
						// e.g. the user may have updated the library
						if (realName.equals(newDexedLib.getName())
								&& ChecksumChangeNoticer.sameChecksum(file, new MaybeDocumentFile(newDexedLib), true)) {
							return true;
						}
					}
					return false;
				}, copyLibraryFiles).setName("delete old preview dexed libs");
		
		BuildTask prepareDexedLibsPreview = new ContextualCompoundBuildTask((context, tasks) -> {
			tasks.add(copyLibraryFiles);
			tasks.add(deleteOldDexedLibsPreview);
			
			if (context.getLibraryDexedLibs() != null) {
				for (DocumentFile newDexedLib : context.getLibraryDexedLibs()) {
					
					tasks.add(new CopyBuildTask(deleteOldDexedLibsPreview)
							.inDocument(Getter.wrap(new MaybeDocumentFile(newDexedLib)))
							.outFile(makeBuildFile(ROOT_FILES_INTERNAL, previewDexJarPrefix + newDexedLib.getName()))
							.setName("copy preview sketch dex jar: " + newDexedLib.getName()));
				}
			}
		}).setName("preview copy dexed libs");
		
		BuildTask preparePreview = new ContextualCompoundBuildTask((context, tasks) -> {
			try {
				tasks.add(previewSketchDex);
				
				if (context.getLibraryDexedLibs() != null && !context.getLibraryDexedLibs().isEmpty()) {
					tasks.add(prepareDexedLibsPreview);
				}
				
				MaybeDocumentFile dataFolderFile = SKETCH_DATA.get(context);
				context.setHasData(dataFolderFile.isDirectory() && dataFolderFile.resolve().listFiles().length > 0);
				if (context.hasData()) {
					tasks.add(previewDataZip);
				}
			} catch (MaybeDocumentFile.ResolutionException e) {
				e.printStackTrace();
			}
		}).setName("prepare preview");
		
		// TODO implement other component targets
		
		RUN = new ContextualCompoundBuildTask(((context, tasks) -> {
			switch (context.getComponentTarget()) {
				case PREVIEW:
					tasks.add(checkPreviewerInstalled);
					tasks.add(preparePreview);
					break;
				case APP:
					break;
				case WALLPAPER:
					break;
				case WATCHFACE:
					break;
				case VR:
					break;
			}
		})).setName("run");
		
		COMPILE = compile;
		
		BuildTask deleteBuild = new DeleteFileTask(BUILD).setName("delete build");
		BuildTask deleteAlternateBuild = new DeleteFileTask(BuildContext::getAlternateBuildFolder).setName("delete alternate build");
		BuildTask deleteStage = new DeleteFileTask(STAGE).setName("delete stage");
		BuildTask deletePreviewDex = new DeleteFileTask(PREVIEW_SKETCH_DEX).setName("delete preview dex");
		BuildTask deletePreviewData = new DeleteFileTask(PREVIEW_SKETCH_DATA).setName("delete preview data");
		
		BuildTask deletePreviewLibraryDex = new ContextualCompoundBuildTask((context, tasks) -> {
			for (File file : ROOT_FILES_INTERNAL.get(context).listFiles()) {
				if (file.getName().startsWith(previewDexJarPrefix)) {
					tasks.add(new DeleteFileTask(Getter.wrap(file))
							.setName("delete preview sketch dex jar: " + file.getName()));
				}
			}
		});
		
		CLEAN = new CompoundBuildTask(deleteBuild, deleteAlternateBuild, deleteStage,
				deletePreviewDex, deletePreviewData, deletePreviewLibraryDex).setName("clean");
	}
	
	private static BuildTask makeAssetsExtractor(Getter<File> file, BuildContext context, BuildTask... deps) {
		return new CopyBuildTask(deps).inAsset(file.get(context).getName() + ".zip").outFolder(file).extract();
	}
	
	private static BuildTask makeAssetsCopier(Getter<File> file, BuildContext context, BuildTask... deps) {
		return new CopyBuildTask(deps).inAsset(file.get(context).getName()).outFile(file);
	}
	
	private static BuildTask makeChecksummer(Getter<File> file) {
		return new ChangeNoticerWrapper((new ChecksumChangeNoticer()).fileDiff(file));
	}
	
	private static BuildTask makeDocumentChecksummer(Getter<MaybeDocumentFile> file) {
		return new ChangeNoticerWrapper((new ChecksumChangeNoticer()).documentDiff(file));
	}
	
	private Getter<File> getSketchClassLocation(String filename, List<BuildTask> deps) {
		return new Getter<File>() {
			@Override
			public File get(BuildContext context) {
				return new File(SRC.get(context).getAbsolutePath() + "/"
						+ context.getPackageName().replace('.', '/') + "/" + filename);
			}
			
			@Override
			public List<BuildTask> getDependencies() {
				return deps;
			}
		};
	}
	
	private String getLogBroadcasterInsert(BuildContext buildContext) {
		try {
			InputStream stream = buildContext.getResources().getAssets().open("log-broadcaster/LogBroadcasterInsert.java");
			
			int size = stream.available();
			byte[] buffer = new byte[size];
			
			//noinspection ResultOfMethodCallIgnored
			stream.read(buffer);
			stream.close();
			
			return new String(buffer);
		} catch (IOException e) {
			System.err.println(buildContext.getResources().getString(R.string.build_inject_log_broadcaster_failed));
			e.printStackTrace();
			
			return "";
		}
	}
	
	public static String getAaptName() {
		String arch = android.os.Build.CPU_ABI.substring(0, 3).toLowerCase(Locale.US);
		
		// We no longer support Android 4.0 or below, so all devices now use the PIE AAPT binaries
		
		// Get the correct AAPT binary for this processor architecture
		switch (arch) {
			case "x86":
				return "aapt-binaries/aapt-x86-pie";
			case "arm":
			default:
				return "aapt-binaries/aapt-arm-pie";
		}
	}
	
	public void halt() {
		// Unstable
//		if (runner != null) {
//			runner.halt();
//			BUILD_LOCK.set(false);
//
//			runner.addOnCompleteListener(success -> {
//				if (!success) {
//					// Undo partial progress that potentially breaks things
//					global.getEditor().runOnUiThread(this::clean);
//				}
//				return true;
//			});
//		}
	}
	
	/**
	 * Perform full build and launch sketch, using whichever component target is currently selected.
	 *
	 * @param listeners
	 */
	public void build(ContextualizedOnCompleteListener... listeners) {
		buildInternal(RUN, new ContextualizedOnCompleteListener() {
			@Override
			public boolean onComplete(boolean success) {
				global.getEditor().showProblems(getContext().getProblems());
				
				if (success) {
					switch (getContext().getComponentTarget()) {
						case PREVIEW:
							launchPreview(getContext());
							break;
						case APP:
						case WALLPAPER:
						case VR:
							break;
						case WATCHFACE:
							break;
					}
				} else if (getContext().getPreviewAdditionalRequiredPermissions() != null) {
					promptInstallPreviewer(getContext().getPreviewAdditionalRequiredPermissions());
				}
				
				return true;
			}
		}, listeners);
	}
	
	private void promptInstallPreviewer(List<String> newPermissions) {
		global.getEditor().runOnUiThread(() -> {
			if (global.getEditor().isFinishing()) {
				return;
			}
			
			AlertDialog.Builder builder = new AlertDialog.Builder(global.getEditor());
			
			StringBuilder message = new StringBuilder(global.getResources()
					.getString(R.string.preview_sketch_previewer_install_dialog_message));
			if (newPermissions.size() > 0) {
				// Tell the user which new permissions are being installed
				message.append("\n\n");
				for (String permission : newPermissions) {
					message.append(permission);
					message.append("\n");
				}
			}
			
			builder.setTitle(R.string.preview_sketch_previewer_install_dialog_title);
			builder.setMessage(message.toString());
			
			String[] newPermissionsArr = new String[newPermissions.size()];
			
			builder.setPositiveButton(R.string.preview_sketch_previewer_install_dialog_install_button,
					(dialogInterface, i) -> {
						global.getTaskManager().launchTask("sketchPreviewBuild", false, null, false,
								new SketchPreviewerBuilder(global.getEditor(),
										newPermissions.toArray(newPermissionsArr), true, false));
					});
			builder.setNegativeButton(R.string.cancel, (dialogInterface, i) -> {});
			
			builder.show();
		});
	}
	
	/**
	 * Compile sketch with ECJ to produce error output.
	 *
	 * @param listeners
	 */
	public void compile(ContextualizedOnCompleteListener... listeners) {
		buildInternal(COMPILE, new ContextualizedOnCompleteListener() {
			@Override
			public boolean onComplete(boolean success) {
				global.getEditor().showProblems(getContext().getProblems());
				return true;
			}
		}, listeners);
	}
	
	public void clean(ContextualizedOnCompleteListener... listeners) {
		buildInternal(CLEAN, null, listeners);
	}
	
	private void buildInternal(BuildTask buildTask, ContextualizedOnCompleteListener listener,
							   ContextualizedOnCompleteListener... listeners) {
		
		if (!BUILD_LOCK.compareAndSet(false, true)) {
			Logger.writeLog("Build already running");
			return;
		}
		
		Logger.setLogLevelFromPrefs(global);
		
		long start = System.currentTimeMillis();
		
		BuildContext context = null;
		try {
			context = BuildContext.create(global);
		} catch (MaybeDocumentFile.MaybeDocumentFileException e) {
			e.printStackTrace();
			return;
		}
		context.setPreviousTaskSucess(previousTaskStatus);
		runner = new BuildTaskRunner(global, buildTask, context);
		
		runner.addOnCompleteListener(success -> {
			Logger.writeLog(String.format(Locale.US, "Finished in %1$dms", System.currentTimeMillis() - start));
			// Make some space in the console
//			for (int i = 0; i < 10; i++) {
//				System.out.println();
//			}
			BUILD_LOCK.set(false);
			return true;
		});
		
		if (listener != null) {
			listener.setContext(context);
			runner.addOnCompleteListener(listener);
		}
		
		for (ContextualizedOnCompleteListener item : listeners) {
			item.setContext(context);
			runner.addOnCompleteListener(item);
		}
		
		runner.run();
	}
	
	public boolean isBuilding() {
		return BUILD_LOCK.get();
	}
	
	private void launchPreview(BuildContext context) {
		// Stop the old sketch
		global.sendBroadcast(new Intent("com.calsignlabs.apde.STOP_SKETCH_PREVIEW"));
		
		String dexUri = makeFileAvailableToPreview(PREVIEW_SKETCH_DEX.get(context)).toString();
		String dataUri = context.hasData() ?
				makeFileAvailableToPreview(PREVIEW_SKETCH_DATA.get(context)).toString() : "";
		
		String[] libUris = new String[context.getLibraryDexedLibs().size()];
		
		for (int i = 0; i < libUris.length; i ++) {
			libUris[i] = makeFileAvailableToPreview(new File(context.getRootFilesDir(),
					previewDexJarPrefix + context.getLibraryDexedLibs().get(i).getName())).toString();
		}
		
		// Build intent specifically for sketch previewer
		Intent intent = new Intent("com.calsignlabs.apde.RUN_SKETCH_PREVIEW");
		intent.setPackage("com.calsignlabs.apde.sketchpreview");
		intent.putExtra("SKETCH_DEX", dexUri);
		intent.putExtra("SKETCH_DATA_FOLDER", dataUri);
		intent.putExtra("SKETCH_DEXED_LIBS", libUris);
		intent.putExtra("SKETCH_ORIENTATION", context.getManifest().getOrientation());
		intent.putExtra("SKETCH_PACKAGE_NAME", context.getPackageName());
		intent.putExtra("SKETCH_CLASS_NAME", context.getSketchName());
		
		// Launch in multi-window mode if available
		if (Build.shouldLaunchSplitScreen(global)) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
		} else {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		if (intent.resolveActivity(global.getPackageManager()) != null) {
			global.startActivity(intent);
		}
	}
	
	/**
	 * Use FileProvider to share the given file with the sketch previewer.
	 *
	 * @param file the file to make externally accessible
	 * @return the URI corresponding to the externally-accessible file
	 */
	@SuppressLint("SetWorldReadable")
	protected Uri makeFileAvailableToPreview(File file) {
		Uri uri;
		if (android.os.Build.VERSION.SDK_INT >= 24) {
			uri = FileProvider.getUriForFile(global, "com.calsignlabs.apde.fileprovider", file);
			global.grantUriPermission("com.calsignlabs.apde.sketchpreview", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} else {
			if (!file.setReadable(true, false)) {
				System.err.println("failed to make file readable: " + file.getAbsolutePath());
			}
			uri = Uri.fromFile(file);
		}
		return uri;
	}
	
	public static abstract class ContextualizedOnCompleteListener implements BuildTask.OnCompleteListener {
		private BuildContext context;
		
		public void setContext(BuildContext context) {
			this.context = context;
		}
		
		public BuildContext getContext() {
			return context;
		}
	}
}
