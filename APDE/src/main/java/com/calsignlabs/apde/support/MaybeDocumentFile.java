package com.calsignlabs.apde.support;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import com.calsignlabs.apde.support.documentfile.DocumentFile;
import com.calsignlabs.apde.support.documentfile.TreeDocumentFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A wrapper around DocumentFile that doesn't have to be backed by a real file/uri.
 * Calling `resolve` will create the underlying DocumentFile if needed.
 */
public class MaybeDocumentFile {
	// Invariant: either file or parent is non-null.
	private DocumentFile file;
	final private MaybeDocumentFile parent;
	final private String name, type;
	
	public static class MaybeDocumentFileException extends Exception {
		public MaybeDocumentFileException(String message) {
			super(message);
		}
	}
	
	public static class ResolutionException extends MaybeDocumentFileException {
		public final MaybeDocumentFile file;
		
		public ResolutionException(MaybeDocumentFile file, String msg) {
			super("Failed to resolve: " + file.toString() + ", " + msg);
			this.file = file;
		}
	}
	
	public MaybeDocumentFile(DocumentFile file, String mimeType) {
		if (file == null) {
			throw new NullPointerException("file cannot be null");
		}
		this.file = file;
		parent = null;
		name = file.getName();
		type = mimeType;
	}
	
	private static String getDocumentFileMimeType(DocumentFile file) {
		String mimeType = file.getType();
		
		if (mimeType == null) {
			if (file.isDirectory() || file instanceof TreeDocumentFile) {
				mimeType = DocumentsContract.Document.MIME_TYPE_DIR;
			} else {
				throw new NullPointerException("type for file is null:" + file.getName() + ", " + file + ", " + file.getUri() + ", " + file);
			}
		}
		
		return mimeType;
	}
	
	public MaybeDocumentFile(DocumentFile file) {
		this(file, getDocumentFileMimeType(file));
	}
	
	public MaybeDocumentFile(MaybeDocumentFile parent, String name, String type) throws MaybeDocumentFileException {
		if (parent == null) {
			throw new NullPointerException("parent cannot be null");
		}
		if (!parent.isDirectory()) {
			throw new MaybeDocumentFileException("Parent MaybeDocumentFile is not a directory: " + parent);
		}
		if (name == null) {
			throw new NullPointerException("name cannot be null");
		}
		if (type == null) {
			throw new NullPointerException("mime type cannot be null");
		}
		this.file = null;
		this.parent = parent;
		this.name = name;
		this.type = type;
		
		if (name.length() == 0) {
			throw new RuntimeException("attempt to create MaybeDocumentFile with empty name: " + this);
		}
	}
	
	public MaybeDocumentFile(DocumentFile parent, String name, String type) throws MaybeDocumentFileException {
		this(new MaybeDocumentFile(parent), name, type);
	}
	
	public static MaybeDocumentFile directory(MaybeDocumentFile parent, String name) throws MaybeDocumentFileException {
		return new MaybeDocumentFile(parent, name, DocumentsContract.Document.MIME_TYPE_DIR);
	}
	
	public static MaybeDocumentFile directory(DocumentFile parent, String name) throws MaybeDocumentFileException {
		return directory(new MaybeDocumentFile(parent), name);
	}
	
	public static MaybeDocumentFile fromFile(File file) {
		return new MaybeDocumentFile(DocumentFile.fromFile(file));
	}
	
	public static MaybeDocumentFile fromDirectory(File file, Context context) throws MaybeDocumentFileException {
		if (!file.exists() && !file.mkdirs()) {
			throw new MaybeDocumentFileException("failed to create directory: " + file.getAbsolutePath());
		}
		return new MaybeDocumentFile(DocumentFile.fromFile(file));
	}
	
	public String getName() {
		return name;
	}
	
	public String getType() {
		return type;
	}
	
	public boolean isDirectory() {
		return type.equals(DocumentsContract.Document.MIME_TYPE_DIR);
	}
	
	public boolean isResolved() {
		return file != null;
	}
	
	public DocumentFile resolve() throws ResolutionException {
		if (file == null) {
			DocumentFile resolvedParent = parent.resolve();
			file = resolvedParent.findFile(name);
			if (file == null) {
				if (isDirectory()) {
					file = resolvedParent.createDirectory(name);
				} else {
					file = resolvedParent.createFile(type, name);
				}
				if (file == null) {
					throw new ResolutionException(this, "failed to create new file with type: " + type);
				}
			} else {
				// It would be nice to be able to validate that the mime type matches the expected
				// mime type, but this isn't possible in general. We may be given files with the
				// wrong mime type, and raw files don't have mime types, so they always end up
				// being null or application/octet-stream.
				if (isDirectory() != file.isDirectory()) {
					throw new ResolutionException(this, "existing file has wrong directory status: "
							+ file.isDirectory() + ", expected " + isDirectory());
				}
			}
		}
		return file;
	}
	
	public MaybeDocumentFile child(String name, String type) throws MaybeDocumentFileException {
		return new MaybeDocumentFile(this, name, type);
	}
	
	public MaybeDocumentFile childDirectory(String name) throws MaybeDocumentFileException {
		return directory(this, name);
	}
	
	public MaybeDocumentFile childPath(String path, String type) throws MaybeDocumentFileException {
		MaybeDocumentFile root = this;
		String[] split = path.split("/");
		for (int i = 0; i < split.length - 1; i++) {
			if (split[i].length() != 0) {
				root = root.childDirectory(split[i]);
			}
		}
		if (split[split.length - 1].length() != 0) {
			return root.child(split[split.length - 1], type);
		} else {
			// if there's a trailing slash, this must be a directory
			if (!type.equals(DocumentsContract.Document.MIME_TYPE_DIR)) {
				throw new MaybeDocumentFileException("path ended in a trailing slash, but not a directory; path: " + path + ", type: " + type);
			}
			return root;
		}
	}
	
	public MaybeDocumentFile childPathDirectory(String path) throws MaybeDocumentFileException {
		return childPath(path, DocumentsContract.Document.MIME_TYPE_DIR);
	}
	
	public boolean exists() {
		if (file != null) {
			return file.exists();
		} else if (parent.exists()) {
			try {
				DocumentFile resolvedParent = parent.resolve();
				DocumentFile child = resolvedParent.findFile(name);
				return child != null;
			} catch (ResolutionException e) {
				return false;
			}
		} else {
			return false;
		}
	}
	
	public void delete() throws MaybeDocumentFileException {
		if (exists()) {
			// TODO: do we have to recursively delete children if this is a directory?
			if (!resolve().delete()) {
				throw new MaybeDocumentFileException("Failed to delete document: " + this);
			}
		}
	}
	
	public InputStream openIn(ContentResolver resolver) throws MaybeDocumentFileException, FileNotFoundException {
		if (isDirectory()) {
			throw new MaybeDocumentFileException("Cannot call openIn on a directory: " + this);
		}
		return resolver.openInputStream(resolve().getUri());
	}
	
	public OutputStream openOut(ContentResolver resolver) throws MaybeDocumentFileException, FileNotFoundException {
		if (isDirectory()) {
			throw new MaybeDocumentFileException("Cannot call openOut on a directory: " + this);
		}
		return resolver.openOutputStream(resolve().getUri());
	}
	
	@Override
	@NonNull
	public String toString() {
		if (parent != null) {
			return parent + "/" + name;
		} else {
			return "Root: " + name;
		}
	}
}
