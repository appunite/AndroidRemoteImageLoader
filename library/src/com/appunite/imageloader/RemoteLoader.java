package com.appunite.imageloader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.TextUtils;

public class RemoteLoader {

	private final DiskCache mDiskCache;
	private byte[] mBuffer;
	private final Context mContext;

	private byte[] getBuffer() {
		if (mBuffer == null) {
			mBuffer = new byte[1024];
		}
		return mBuffer;
	}

	public RemoteLoader(Context context, DiskCache diskCache) {
		this.mContext = context;
		mDiskCache = diskCache;
	}

	public File download(String resource) {
		Uri uri = Uri.parse(resource);
		String scheme = uri.getScheme();
		if (scheme == null) {
			return new File(resource);
		} else if (scheme.equals("http") || scheme.equals("https")) {
			return downloadFromHttp(resource);
		} else if (scheme.equals("content")) {
			return downloadFromContentProvider(resource, uri);
		} else if (scheme.equals("file")) {
			return new File(uri.getPath());
		} else {
			return null;
		}
	}

	private File loadFromCache(String resource) {
		synchronized (mDiskCache) {
			File file = mDiskCache.getCacheFile(resource);
			if (file.exists()) {
				return file;
			} else {
				return null;
			}
		}
	}

	private File downloadFromContentProvider(String resource, Uri uri) {
		File cached = loadFromCache(resource);
		if (cached != null) {
			return cached;
		}
		ContentResolver cr = mContext.getContentResolver();
		try {
			InputStream inputStream = cr.openInputStream(uri);
			try {
				return saveInDiskCache(inputStream, resource);
			} finally {
				inputStream.close();
			}
		} catch (FileNotFoundException e) {
			return null;
		} catch (IOException e) {
			return null;
		}
	}

	private File downloadFromHttp(String resource) {
		File cached = loadFromCache(resource);
		if (cached != null) {
			return cached;
		}
		try {
			URL url = new URL(resource);
			URLConnection connection;
			connection = url.openConnection();
			connection.connect();
			InputStream inputStream = connection.getInputStream();
			try {
				return saveInDiskCache(inputStream, resource);
			} finally {
				inputStream.close();
			}
		} catch (IOException e) {
			return null;
		}
	}

	private File saveInDiskCache(InputStream reader, String resource)
			throws IOException {
		boolean success = false;
		File diskCacheFile;
		synchronized (mDiskCache) {
			diskCacheFile = mDiskCache.getCacheFile(resource);
		}
		try {

			int bytesRead = 0;

			OutputStream outputStream = new FileOutputStream(diskCacheFile);
			try {
				byte[] buffer = getBuffer();
				while ((bytesRead = reader.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
				}

				outputStream.flush();
			} finally {
				outputStream.close();
			}
			success = true;
			return diskCacheFile;
		} finally {
			if (!success) {
				diskCacheFile.delete();
			}
		}
	}

	private int getImageOrientation(Uri uri) {
		ContentResolver cr = mContext.getContentResolver();
		Cursor cursor = cr.query(uri, new String[] {
				MediaStore.Images.ImageColumns.ORIENTATION,
				MediaStore.Images.ImageColumns.DATA }, null, null, null);
		try {
			if (!cursor.moveToFirst())
				return 0;

			int rotation = cursor.getInt(0);
			if (rotation != 0)
				return rotation;

			String filePath = cursor.getString(1);
			if (TextUtils.isEmpty(filePath))
				return rotation;

			return getImageOrientation(filePath);
		} finally {
			cursor.close();
		}
	}

	public int getImageOrientation(String filePath) {
		try {
			ExifInterface exifReader = new ExifInterface(filePath);
			int exifRotation = exifReader.getAttributeInt(
					ExifInterface.TAG_ORIENTATION,
					ExifInterface.ORIENTATION_NORMAL);
			switch (exifRotation) {
			case ExifInterface.ORIENTATION_NORMAL:
				return 0;
			case ExifInterface.ORIENTATION_ROTATE_90:
				return 90;
			case ExifInterface.ORIENTATION_ROTATE_180:
				return 180;
			case ExifInterface.ORIENTATION_ROTATE_270:
				return 270;
			}

		} catch (IOException e) {
		}
		return 0;
	}

	private Bitmap getThumbFromMediaStore(Uri uri) {
		ContentResolver cr = mContext.getContentResolver();
		String contentType = cr.getType(uri);
		if (contentType != null && contentType.startsWith("video/")) {
			Cursor cursor = cr.query(uri,
					new String[] { MediaStore.Video.VideoColumns._ID }, null,
					null, null);
			try {
				if (cursor.moveToFirst()) {
					return null;
				}
				long originId = cursor.getLong(0);
				Bitmap curThumb = MediaStore.Video.Thumbnails.getThumbnail(cr,
						originId, MediaStore.Video.Thumbnails.MINI_KIND, null);
				return curThumb;
			} finally {
				cursor.close();
			}
		} else if (contentType != null && contentType.startsWith("image/")) {
			Cursor cursor = cr.query(uri,
					new String[] { MediaStore.Images.ImageColumns._ID }, null,
					null, null);
			try {
				if (!cursor.moveToFirst()) {
					return null;
				}
				long originId = cursor.getLong(0);
				Bitmap curThumb = MediaStore.Images.Thumbnails.getThumbnail(cr,
						originId, MediaStore.Images.Thumbnails.MINI_KIND, null);
				if (curThumb != null) {
					return getRotatedBitmap(curThumb, getImageOrientation(uri));
				}
			} finally {
				cursor.close();
			}
		}
		return null;
	}

	public Bitmap downloadImage(String resource, int requestedWidth,
			int requestedHeight) {
		Uri uri = Uri.parse(resource);
		String scheme = uri.getScheme();
		if (scheme.equals("content")) {
			Bitmap bitmap = getThumbFromMediaStore(uri);
			if (bitmap != null) {
				return bitmap;
			}
		}
		File image = download(resource);
		if (image == null) {
			return null;
		}

		String filePath = image.getAbsolutePath();
		Bitmap bitmap = ImageLoader.loadImage(filePath, requestedHeight,
				requestedWidth);
		if (bitmap == null) {
			return null;
		}
		int imageOrientation = getImageOrientation(filePath);
		return getRotatedBitmap(bitmap, imageOrientation);
	}

	private Bitmap getRotatedBitmap(Bitmap bitmap, int imageOrientation) {
		if (imageOrientation == 0)
			return bitmap;
		if (bitmap == null)
			return null;
		Matrix matrix = new Matrix();
		matrix.postRotate(imageOrientation);
		return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
				bitmap.getHeight(), matrix, true);
	}
}
