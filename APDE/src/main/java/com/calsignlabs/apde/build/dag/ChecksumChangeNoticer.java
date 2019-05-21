package com.calsignlabs.apde.build.dag;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO will we have problems with large files? (images, etc.)
public class ChecksumChangeNoticer implements BuildTask.ChangeNoticer {
	private static final int BUFFER_SIZE = 8192;
	
	private Getter<File> fileGetter;
	private Getter<InputStream> inputStreamGetter;
	private Getter<InputStream> baseGetter;
	
	private List<BuildTask> deps;
	
	private boolean vacuousChange = true;
	
	public ChecksumChangeNoticer(Getter<File> fileGetter) {
		this.fileGetter = fileGetter;
		deps = fileGetter.getDependencies();
	}
	
	public ChecksumChangeNoticer(Getter<InputStream> inputStreamGetter, Getter<InputStream> baseGetter) {
		this.inputStreamGetter = inputStreamGetter;
		this.baseGetter = baseGetter;
		
		deps = new ArrayList<>(inputStreamGetter.getDependencies().size() + baseGetter.getDependencies().size());
		deps.addAll(inputStreamGetter.getDependencies());
		deps.addAll(baseGetter.getDependencies());
	}
	
	/**
	 * Whether or not a change should be reported in the event that the input file does not exist.
	 * Default is true.
	 *
	 * @param vacuousChange
	 * @return
	 */
	public ChecksumChangeNoticer setVacuousChange(boolean vacuousChange) {
		this.vacuousChange = vacuousChange;
		return this;
	}
	
	@Override
	public List<BuildTask> getDependencies() {
		return deps;
	}
	
	private static BigInteger calculateChecksum(File file) {
		InputStream inputStream = null;
		
		if (!file.exists()) {
			System.out.println("CHK doesn't exist: " + file.getAbsolutePath());
			return null;
		}
		
		try {
			// Handle md5-ing directories
			List<InputStream> sequence = new ArrayList<>();
			buildFileStreamList(file, sequence);
			inputStream = new SequenceInputStream(Collections.enumeration(sequence));
			return calculateChecksum(inputStream);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static BigInteger calculateChecksum(Getter<InputStream> getter, BuildContext context) {
		try {
			InputStream inputStream = getter.get(context);
			return inputStream != null ? calculateChecksum(inputStream) : null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private static BigInteger calculateChecksum(InputStream inputStream) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			
			byte[] buf = new byte[BUFFER_SIZE];
			int len;
			while (-1 != (len = inputStream.read(buf))) {
				md.update(buf, 0, len);
			}
			
			return new BigInteger(1, md.digest());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		} finally {
			try {
				if (inputStream != null) {
					inputStream.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static void buildFileStreamList(File file, List<InputStream> children) throws FileNotFoundException {
		// Track file names
		children.add(new ByteArrayInputStream(file.getName().getBytes()));
		
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				buildFileStreamList(f, children);
			}
		} else {
			children.add(new FileInputStream(file));
		}
	}
	
	private BigInteger checksum;
	
	@Override
	public BuildTask.ChangeStatus hasChanged(BuildContext context) {
		if (fileGetter != null) {
			File file = fileGetter.get(context);
			if (!vacuousChange && !file.exists()) {
				return BuildTask.ChangeStatus.UNCHANGED;
			}
			BigInteger newChecksum = calculateChecksum(file);
			System.out.println("OLD CHECKSUM: " + (checksum == null ? "null" : checksum.toString()));
			System.out.println("NEW CHECKSUM: " + (newChecksum == null ? "null" : newChecksum.toString()));
			boolean changed = checksum == null || !checksum.equals(newChecksum);
			checksum = newChecksum;
			return BuildTask.ChangeStatus.bool(changed);
		} else if (inputStreamGetter != null && baseGetter != null) {
			BigInteger test = calculateChecksum(inputStreamGetter, context);
			BigInteger base = calculateChecksum(baseGetter, context);
			System.out.println("BASE CHECKSUM: " + (base == null ? "null" : base.toString()));
			System.out.println("TEST CHECKSUM: " + (test == null ? "null" : test.toString()));
			return BuildTask.ChangeStatus.bool(test == null || !test.equals(base));
		}
		
		return BuildTask.ChangeStatus.UNCHANGED;
	}
}
