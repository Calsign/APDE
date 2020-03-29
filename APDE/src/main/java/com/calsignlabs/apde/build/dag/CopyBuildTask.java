package com.calsignlabs.apde.build.dag;

import com.calsignlabs.apde.R;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Supports the following combination of in types, out types, and zip methods:
 *  - FILE FILE COPY
 *  - FILE STREAM COPY
 *  - STREAM FILE COPY
 *  - STREAM STREAM COPY
 *  - STREAM FOLDER EXTRACT
 *  - FILE FOLDER EXTRACT
 *  - FOLDER STREAM COMPRESS
 *  - FOLDER FILE COMPRESS
 *
 *  Note that ASSET for input is equivalent to STREAM.
 */
public class CopyBuildTask extends BuildTask {
	private static final int BUFFER_SIZE = 8192;
	
	private static final int NONE     = 0x00000000;
	private static final int FILE     = 0x00000001;
	private static final int FOLDER   = 0x00000002;
	private static final int STREAM   = 0x00000004;
	
	private static final int COPY     = 0x00000100;
	private static final int EXTRACT  = 0x00000200;
	private static final int COMPRESS = 0x00000400;
	
	private int inputType, outputType;
	private Getter<File> inputFile, outputFile;
	private Getter<InputStream> inputStreamGetter;
	private Getter<OutputStream> outputStreamGetter;
	private int zipMethod;
	
	private boolean vacuousSuccess = false;
	
	public CopyBuildTask(BuildTask... deps) {
		super(deps);
		inputType = NONE;
		outputType = NONE;
		zipMethod = COPY;
	}
	
	// TODO add support for functional folder copies
	public CopyBuildTask inFile(Getter<File> in) {
		inputType = FILE;
		inputFile = in;
		orGetterChangeNoticer(in);
		return this;
	}
	
	public CopyBuildTask inFolder(Getter<File> in) {
		inputType = FOLDER;
		inputFile = in;
		orGetterChangeNoticer(in);
		return this;
	}
	
	public CopyBuildTask inStream(Getter<InputStream> in) {
		inputType = STREAM;
		inputStreamGetter = in;
		orGetterChangeNoticer(in);
		return this;
	}
	
	public CopyBuildTask inAsset(String assetsPath) {
		setName("copy asset: " + assetsPath);
		
		return inStream(context -> {
			try {
				return context.getResources().getAssets().open(assetsPath);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
		});
	}
	
	public CopyBuildTask outFile(Getter<File> out, boolean detectChange) {
		outputType = FILE;
		outputFile = out;
		if (detectChange && (inputType == FILE || inputType == STREAM)) {
			populateStreamsForFiles();
			orChangeNoticer((new ChecksumChangeNoticer()).sourceDestStream(inputStreamGetter, fis(out)));
		}
		orGetterChangeNoticer(out);
		return this;
	}
	
	public CopyBuildTask outFile(Getter<File> out) {
		return outFile(out, true);
	}
	
	public CopyBuildTask outFolder(Getter<File> out) {
		outputType = FOLDER;
		outputFile = out;
		// It's better to just do diffs on the source
//		if (inputType == FOLDER) {
//			orChangeNoticer((new ChecksumChangeNoticer()).sourceDestFile(inputFile, out));
//		}
		orGetterChangeNoticer(out);
		return this;
	}
	
	public CopyBuildTask outStream(Getter<OutputStream> out) {
		outputType = STREAM;
		outputStreamGetter = out;
		orGetterChangeNoticer(out);
		return this;
	}
	
	public CopyBuildTask extract() {
		zipMethod = EXTRACT;
		return this;
	}
	
	public CopyBuildTask compress() {
		zipMethod = COMPRESS;
		return this;
	}
	
	/**
	 * Default success if the file to copy doesn't exist
	 *
	 * @param vacuousSuccess
	 * @return
	 */
	public CopyBuildTask setVacuousSuccess(boolean vacuousSuccess) {
		this.vacuousSuccess = vacuousSuccess;
		return this;
	}
	
	@Override
	public CharSequence getTitle() {
		return getBuildContext().getResources().getString(R.string.build_copying);
	}
	
	@Override
	public void run() throws InterruptedException {
		switch (inputType | outputType << 4 | zipMethod) {
			case FILE | FILE << 4 | COPY:
			case FILE | STREAM << 4 | COPY:
			case STREAM | FILE << 4 | COPY:
			case STREAM | STREAM << 4 | COPY:
				populateStreamsForFiles();
				streamCopy();
				break;
			case FOLDER | FOLDER << 4 | COPY:
				folderCopy();
				break;
			case STREAM | FOLDER << 4 | EXTRACT:
			case FILE | FOLDER << 4 | EXTRACT:
				populateStreamsForFiles();
				zipExtract();
				break;
			case FOLDER | STREAM << 4 | COMPRESS:
			case FOLDER | FILE << 4 | COMPRESS:
				populateStreamsForFiles();
				zipCompress();
				break;
			default:
				throw new RuntimeException("Invalid copy task parameters: " + inputType + ", " + outputType + ", " + zipMethod);
		}
	}
	
	private void populateStreamsForFiles() {
		// We convert files into streams so that we can use the same method for interchangeably
		// copying streams and files.
		
		if (inputStreamGetter == null) {
			inputStreamGetter = fis(inputFile);
		}
		
		if (outputStreamGetter == null) {
			outputStreamGetter = fos(outputFile);
		}
	}
	
	private void streamCopy() {
		finish(handleStreamCopy(inputStreamGetter.get(getBuildContext()), outputStreamGetter.get(getBuildContext())));
	}
	
	private void folderCopy() {
		finish(copyFolder(inputFile.get(getBuildContext()), outputFile.get(getBuildContext()), vacuousSuccess));
	}
	
	private void zipExtract() {
		finish(handleExtract(inputStreamGetter.get(getBuildContext()), outputFile.get(getBuildContext())));
	}
	
	private void zipCompress() {
		finish(handleCompress(inputFile.get(getBuildContext()), outputStreamGetter.get(getBuildContext())));
	}
	
	private static boolean handleStreamCopy(InputStream in, OutputStream out) {
		return handleStreamCopy(in, out, true, true);
	}
	
	private static boolean handleStreamCopy(InputStream inputStream, OutputStream outputStream, boolean closeIn, boolean closeOut) {
		try {
			copyStream(inputStream, outputStream);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (closeIn && inputStream != null) inputStream.close();
				if (closeOut && outputStream != null) outputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
		byte[] buf = new byte[BUFFER_SIZE];
		int len;
		while (-1 != (len = inputStream.read(buf))) {
			outputStream.write(buf, 0, len);
		}
	}
	
	private static boolean copyFolder(File source, File target, boolean vacuousSuccess) {
		if (!source.exists()) return vacuousSuccess;
		if (source.equals(target)) return false;
		if (!target.exists() && !target.mkdirs()) return false;
		
		try {
			for (String file : source.list()) {
				File sourceFile = new File(source, file);
				File targetFile = new File(target, file);
				if (sourceFile.isDirectory()) {
					if (!copyFolder(sourceFile, targetFile, vacuousSuccess)) return false;
				} else {
					if (!handleStreamCopy(new FileInputStream(sourceFile), new FileOutputStream(targetFile)))
						return false;
				}
			}
			return true;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private static boolean handleExtract(InputStream inputStream, File folder) {
		if (folder.exists() && !folder.isDirectory()) return false;
		if (!folder.exists() && !folder.mkdirs()) return false;
		
		ZipInputStream zipIn = null;
		
		try {
			zipIn = new ZipInputStream(inputStream);
			
			ZipEntry zipEntry;
			while ((zipEntry = zipIn.getNextEntry()) != null) {
				File file = new File(folder, zipEntry.getName());
				
				if (zipEntry.isDirectory()) {
					if (!file.exists() && !file.mkdirs()) return false;
				} else {
					if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) return false;
					if (!handleStreamCopy(zipIn, new FileOutputStream(file), false, true)) return false;
					zipIn.closeEntry();
				}
			}
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (zipIn != null) {
					zipIn.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static boolean handleCompress(File folder, OutputStream outputStream) {
		ZipOutputStream out = null;
		
		try {
			List<File> files = new ArrayList<>();
			buildFileList(files, folder);
			
			String absPath = folder.getAbsolutePath();
			int folderLength = absPath.endsWith("/") ? absPath.length() : absPath.length() + 1;
			
			out = new ZipOutputStream(new BufferedOutputStream(outputStream));
			
			for (File file : files) {
				ZipEntry entry = new ZipEntry(file.getAbsolutePath().substring(folderLength));
				out.putNextEntry(entry);
				if (!handleStreamCopy(new FileInputStream(file), out, true, false)) return false;
				out.closeEntry();
			}
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		} finally {
			try {
				if (out != null) {
					out.close();
				}
			} catch (IOException e2) {
				e2.printStackTrace();
			}
		}
	}
	
	private static void buildFileList(List<File> ret, File dir) {
		if (dir != null && dir.exists()) {
			if (dir.isDirectory()) {
				for (File file : dir.listFiles()) {
					buildFileList(ret, file);
				}
			} else if (dir.isFile()) {
				ret.add(dir);
			}
		}
	}
	
	private static Getter<InputStream> fis(Getter<File> file) {
		return context -> {
			try {
				return new FileInputStream(file.get(context));
			} catch (FileNotFoundException e) {
				return null;
			}
		};
	}
	
	private static Getter<OutputStream> fos(Getter<File> file) {
		return context -> {
			try {
				return new FileOutputStream(file.get(context));
			} catch (FileNotFoundException e) {
				return null;
			}
		};
	}
}
