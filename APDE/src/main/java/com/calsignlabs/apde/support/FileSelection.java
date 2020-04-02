package com.calsignlabs.apde.support;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class FileSelection {
	public enum Mode {
		READ, READ_WRITE
	}
	
	@NonNull
	public static Intent createFileSelectorIntent(boolean allowMultiple, @Nullable String[] mimeTypes) {
		if (mimeTypes == null || mimeTypes.length == 0) {
			mimeTypes = new String[] {"*/*"};
		}
		
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType(mimeTypes[0]);
		intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
		if (allowMultiple) {
			intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		}
		return intent;
	}
	
	@NonNull
	public static Intent createFileCreatorIntent(@Nullable CharSequence title) {
		Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"*/*"});
		if (title != null) {
			intent.putExtra(Intent.EXTRA_TITLE, title);
		}
		return intent;
	}
	
	@Nullable
	public static ParcelFileDescriptor openUri(Context context, @Nullable Uri uri, Mode mode, boolean silent) {
		if (uri != null) {
			try {
				String modeStr;
				switch (mode) {
					case READ:
						modeStr = "r";
						break;
					case READ_WRITE:
						modeStr = "w";
						break;
					default:
						throw new RuntimeException();
				}
				return context.getContentResolver().openFileDescriptor(uri, modeStr);
			} catch (IOException e) {
				if (!silent) {
					e.printStackTrace();
				}
			} catch (SecurityException ignored) {
				// trying to read a URI to which we no longer have access
			}
		}
		
		return null;
	}
	
	@Nullable
	public static ParcelFileDescriptor openUri(Context context, @Nullable Uri uri, Mode mode) {
		return openUri(context, uri, mode, false);
	}
	
	@Nullable
	public static Uri getSelectedUri(Intent intent) {
		return intent.getData();
	}
	
	@Nullable
	public static ParcelFileDescriptor getSelectedFd(Context context, Intent intent, Mode mode) {
		return openUri(context, getSelectedUri(intent), mode);
	}
	
	@NonNull
	public static List<Uri> getSelectedUris(Intent intent) {
		List<Uri> list = new ArrayList<>();
		
		{
			Uri uri = getSelectedUri(intent);
			if (uri != null) {
				list.add(uri);
			}
		}
		
		if (intent.getClipData() != null) {
			for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
				ClipData.Item item = intent.getClipData().getItemAt(i);
				Uri uri = item.getUri();
				if (uri != null) {
					list.add(uri);
				}
			}
		}
		
		return list;
	}
	
	@NonNull
	public static List<ParcelFileDescriptor> getSelectedFds(Context context, Intent intent, Mode mode) {
		List<Uri> uris = getSelectedUris(intent);
		List<ParcelFileDescriptor> fds = new ArrayList<>();
		for (Uri uri : uris) {
			ParcelFileDescriptor fd = openUri(context, uri, mode);
			if (fd != null) {
				fds.add(fd);
			}
		}
		return fds;
	}
	
	@NonNull
	public static InputStream fdIn(@NonNull ParcelFileDescriptor fd) {
		return new FileInputStream(fd.getFileDescriptor());
	}
	
	@NonNull
	public static OutputStream fdOut(@NonNull ParcelFileDescriptor fd) {
		return new FileOutputStream(fd.getFileDescriptor());
	}
	
	@NonNull
	public static Uri pathToUri(@NonNull String path) {
		return Uri.fromFile(new File(path));
	}
	
	@Nullable
	public static String uriToFilename(Context context, @NonNull Uri uri) {
		String result = null;
		if (uri.getScheme() != null && uri.getScheme().equals("content")) {
			Cursor cursor = context.getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
			if (cursor != null) {
				try {
					if (cursor.moveToFirst()) {
						result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
					}
				} finally {
					cursor.close();
				}
			}
		}
		if (result == null) {
			result = uri.getPath();
			if (result != null) {
				int lastIndex = result.lastIndexOf('/');
				if (lastIndex != -1) {
					result = result.substring(lastIndex + 1);
				}
			}
		}
		return result;
	}
	
	public static void streamToStream(@NonNull InputStream inputStream, @NonNull OutputStream outputStream) {
		try (InputStream is = new BufferedInputStream(inputStream); OutputStream os = new BufferedOutputStream(outputStream)) {
			byte[] buf = new byte[8192];
			int len;
			while (-1 != (len = is.read(buf))) {
				os.write(buf, 0, len);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void fileToStream(@NonNull File inputFile, @NonNull OutputStream outputStream) {
		try {
			streamToStream(new FileInputStream(inputFile), outputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void streamToFile(@NonNull InputStream inputStream, @NonNull File outputFile) {
		try {
			streamToStream(inputStream, new FileOutputStream(outputFile));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void closeFd(@Nullable ParcelFileDescriptor fd) {
		try {
			if (fd != null) {
				fd.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Try to parse uriStr as a Uri; if it is not a valid Uri, then parse it as a raw path
	 * (i.e. prefixed by "file://").
	 *
	 * Note that using this method is not in general a good idea. We are using it in a couple of
	 * places that we alternatively allow the user to type in a path to a file, and this allows
	 * them to not type the "file://" at the beginning.
	 *
	 * @param uriStr
	 * @return
	 */
	@Nullable
	public static Uri uriStringOrPathToUri(String uriStr) {
		// This is not perfect because filenames can contain ":", but it should be good enough
		if (uriStr.contains(":")) {
			return Uri.parse(uriStr);
		} else {
			return Uri.parse("file://" + uriStr);
		}
	}
}
