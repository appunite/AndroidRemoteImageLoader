/*
 * Copyright (C) 2012 Appunite.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appunite.imageloader;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

/**
 * 
 * @author Jacek Marchwicki (jacek.marchwicki@gmail.com)
 * 
 */
public class FileUtils {
	private static final String TAG = FileUtils.class.getCanonicalName();
	private static DateFormat sDateFormat = null;

	@TargetApi(8)
	public static String getDirectoryMovies() {
		if (Build.VERSION.SDK_INT >= 8)
			return Environment.DIRECTORY_MOVIES;
		else
			return "Movies";
	}

	@TargetApi(8)
	public static String getDirectoryPictures() {
		if (Build.VERSION.SDK_INT >= 8)
			return Environment.DIRECTORY_PICTURES;
		else
			return "Pictures";
	}

	@TargetApi(8)
	private static File getExternalStoragePublicDirectory8(String type,
			String suffix) {
		File mediaStorageDir = Environment
				.getExternalStoragePublicDirectory(type);
		if (suffix != null)
			mediaStorageDir = new File(mediaStorageDir, suffix);
		return mediaStorageDir;
	}
	
	private static DateFormat getDateFormat() {
		if (sDateFormat != null) {
			return sDateFormat;
		}
		sDateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
		return sDateFormat;
	}

	public static File getFileWithTimestamp(File directory, String filePrefix,
			String fileExtendsion) {
		String timeStamp = getDateFormat().format(new Date());
		String fileName = String.format("%s_%s.%s", filePrefix, timeStamp,
				fileExtendsion);
		return new File(directory, fileName);
	}

	/**
	 * 
	 * @param context
	 * @param type
	 *            can by something like getDirectoryMovie or null
	 * @param suffix
	 *            can be applicaion name or null
	 * @return
	 */
	public static File getStorageDirectory(Context context, String type,
			String suffix) {
		File mediaStorageDir = null;

		if (Environment.MEDIA_MOUNTED.equals(Environment
				.getExternalStorageState())) {

			if (Build.VERSION.SDK_INT >= 8 && type != null) {
				mediaStorageDir = getExternalStoragePublicDirectory8(type,
						suffix);
			} else {
				mediaStorageDir = Environment.getExternalStorageDirectory();
				if (type != null)
					mediaStorageDir = new File(mediaStorageDir, type);
				if (suffix != null)
					mediaStorageDir = new File(mediaStorageDir, suffix);
			}

			if (!mediaStorageDir.exists()) {
				if (!mediaStorageDir.mkdirs()) {
					Log.d(TAG, "failed to create directory");
					mediaStorageDir = null;
				}
			}
		} else {
			mediaStorageDir = context.getFilesDir();
			Log.d(TAG, "external storage not mounted");
		}
		if (mediaStorageDir.exists()) {
			if (!mediaStorageDir.isDirectory())
				return null;
		} else {
			if (!mediaStorageDir.mkdirs())
				return null;
		}
		return mediaStorageDir;
	}
}
