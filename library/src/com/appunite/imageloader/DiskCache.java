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
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.StatFs;
import android.util.Log;

/**
 * 
 * @author Jacek Marchwicki (jacek.marchwicki@gmail.com)
 * 
 */
public class DiskCache {
	private class LastModifiedComparator implements Comparator<File> {
		@Override
		public int compare(File lhs, File rhs) {
			long lhsLastModified = lhs.lastModified();
			long rhsLastModified = rhs.lastModified();
			if (lhsLastModified > rhsLastModified)
				return -1;
			if (lhsLastModified < rhsLastModified)
				return 1;
			return 0;
		}
	}

	private static final String TAG = DiskCache.class.getCanonicalName();

	private final File mBaseDirectory;
	private final long mMaxCacheSize = 1024 * 1024;
	private MessageDigest mHash;

	private final String mPostfix;

	public DiskCache(Context context, File baseDirectory) {
		this(context, baseDirectory, "");
	}

	public DiskCache(Context context, File baseDirectory, String postfix) {
		this.mBaseDirectory = baseDirectory;
		this.mPostfix = postfix;
		if (!this.mBaseDirectory.exists()) {
			if (!this.mBaseDirectory.mkdir()) {
				String errorMsg = String.format(
						"Problem creating tmp directory: %s",
						this.mBaseDirectory.getAbsolutePath());
				Log.e(TAG, errorMsg);
			}
		}
		this.createDigest();
	}

	public DiskCache(Context context, String prefix) {
		this(context, prefix, "");
	}

	public DiskCache(Context context, String prefix, String postfix) {
		this(context, new File(context.getCacheDir(), prefix), postfix);
	}

	public void clearIfCacheLimitExhaust() {
		List<File> cacheFiles = Arrays.asList(this.mBaseDirectory.listFiles());
		int totalLength = 0;
		for (File cacheFile : cacheFiles) {
			totalLength += this.getFileSize(cacheFile);
		}
		if (totalLength <= this.mMaxCacheSize)
			return;
		Comparator<File> lastModifiedComparator = new LastModifiedComparator();
		Collections.sort(cacheFiles, lastModifiedComparator);

		Iterator<File> iter = cacheFiles.iterator();
		while (iter.hasNext()) {
			File cacheFile = iter.next();
			totalLength -= this.getFileSize(cacheFile);
			iter.remove();
			if (totalLength <= this.mMaxCacheSize)
				break;
		}
	}

	private void createDigest() {
		try {
			this.mHash = MessageDigest.getInstance("SHA-1");

		} catch (final NoSuchAlgorithmException noShaExc) {
			try {
				this.mHash = MessageDigest.getInstance("MD5");
			} catch (final NoSuchAlgorithmException noMd5Exc) {
				throw new RuntimeException("No available sha-1/md5 algorithms");
			}
		}
	}

	public File getCacheFile(String key) {
		this.mHash.update(key.getBytes());
		byte[] digest = this.mHash.digest();
		BigInteger digestBigInteger = new BigInteger(1, digest);
		String hash = digestBigInteger.toString(16);

		return new File(this.mBaseDirectory, hash + this.mPostfix);
	}

	public long getFileSize(File file) {
		if (Build.VERSION.SDK_INT > 9) {
			return this.getFileSize9(file);
		}
		StatFs statFs = new StatFs(file.getAbsolutePath());
		int blockSize = statFs.getBlockSize();
		int blockCount = statFs.getBlockCount();
		return (long) blockCount * (long) blockSize;
	}

	@TargetApi(9)
	public long getFileSize9(File file) {
		return file.getUsableSpace();
	}
}
