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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

/**
 * 
 * @author Jacek Marchwicki (jacek.marchwicki@gmail.com)
 * 
 */
public class RemoteImageLoader {

	private static final int NUMBER_OF_SCREENS_IN_MEMORY = 4;
	private static final int BYTES_PER_PIXEL = 4;

	private class DownloadImageThread extends Thread {

		private boolean mStop = false;

		private RemoteLoader mDownloader;

		public DownloadImageThread() {
			mDownloader = new RemoteLoader(mActivity, mDiskCache);
		}

		synchronized boolean isStopped() {
			return this.mStop;
		}

		@Override
		public void run() {
			while (!this.isStopped()) {
				try {
					String resource = RemoteImageLoader.this.takeToProcess();

					Bitmap bitmap = mDownloader.downloadImage(resource,
							mImageRequestedWidth, mImageRequestedHeight);
					List<ImageHolder> imageHolders = RemoteImageLoader.this
							.finishByResource(resource);
					if (bitmap != null) {
						receivedDrawable(bitmap, resource, imageHolders);
					} else {
						failDownloading(resource, imageHolders);
					}
				} catch (InterruptedException e) {
				}
			}

		}

		synchronized public void stopSelf() {
			this.mStop = true;
		}

	}

	private static class FileCache extends LruCache<String, Bitmap> {
		public FileCache(int maxSize) {
			super(maxSize);
		}

		@TargetApi(12)
		private int getByteCount12(Bitmap value) {
			return value.getByteCount();
		}

		@Override
		protected int sizeOf(String key, Bitmap value) {
			if (Build.VERSION.SDK_INT >= 12) {
				return this.getByteCount12(value);
			} else {
				return value.getWidth() * value.getHeight() * 4;
			}

		}
	}

	public interface ImageHolder {
		void setRemoteBitmap(Bitmap bitmap, boolean immediately);
		void failDownloading(boolean immediately);
		void setPlaceholer(boolean immediately);
	}
	
	public static ImageHolder newImageHolder(ImageView imageView,
			int placeholderDrawable, int errorDrawable) {
		return new ImageViewHolder(imageView, placeholderDrawable, errorDrawable);
	}

	private static class ImageViewHolder implements ImageHolder {
		private final ImageView mImageView;
		private final int mPlaceholderDrawable;
		private final int mErrorDrawable;

		public ImageViewHolder(ImageView imageView, int placeholderDrawable, int errorDrawable) {
			mImageView = imageView;
			mPlaceholderDrawable = placeholderDrawable;
			mErrorDrawable = errorDrawable;
		}

		@Override
		public void setRemoteBitmap(Bitmap bitmap, boolean immediately) {
			mImageView.setImageBitmap(bitmap);
			mImageView.setScaleType(ScaleType.CENTER_CROP);
		}

		@Override
		public void failDownloading(boolean immediately) {
			mImageView.setImageResource(mErrorDrawable);
			mImageView.setScaleType(ScaleType.FIT_XY);
		}

		@Override
		public void setPlaceholer(boolean immediately) {
			mImageView.setImageResource(mPlaceholderDrawable);
			mImageView.setScaleType(ScaleType.FIT_XY);
		}

	}

	public static final String IMAGE_CACHE_DIR_PREFIX = "ImageCache";

	private static final long FAIL_TIME_MILLIS = 10 * 1000;

	private final LruCache<String, Bitmap> mCache;
	private final HashMap<String, Long> mFails;

	private final DiskCache mDiskCache;

	private final Lock mLock = new ReentrantLock();

	private final Condition mNotEmpty = this.mLock.newCondition();
	public Map<ImageHolder, String> mViewResourceMap = new HashMap<ImageHolder, String>();
	public List<String> mResourcesProcessingQueue = new ArrayList<String>();

	public List<String> mResourcesQueue = new ArrayList<String>();

	private final int mImageRequestedHeight;

	private final int mImageRequestedWidth;

	private final Activity mActivity;

	private DownloadImageThread[] mDownloadImageThread;
	
	@SuppressWarnings("deprecation")
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private void getDisplaySize(Activity activity, Point displaySize) {
		Display display = activity.getWindowManager().getDefaultDisplay();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			display.getSize(displaySize);
		} else {
			displaySize.x = display.getWidth();
			displaySize.y = display.getHeight();
		}
	}

	/**
	 * Create class
	 * 
	 * @param activity
	 *            activity that should be owner
	 * @param baseUrl
	 *            base url for all files, can be null
	 * @param placeHolder
	 *            bitmap that should be placed insted of downloaded image, while
	 *            loading begun
	 * @param requestedHeight
	 *            requested height
	 * @param requestedWidth
	 *            requested width
	 * @param maxHeight
	 *            maximal height
	 * @param maxWidth
	 *            maximal width
	 */
	public RemoteImageLoader(Activity activity, int requestedWidth,
			int requestedHeight) {
		this.mActivity = activity;
		this.mImageRequestedWidth = requestedWidth;
		this.mImageRequestedHeight = requestedHeight;
		this.mDiskCache = new DiskCache(activity, IMAGE_CACHE_DIR_PREFIX);
		
		Point displaySize = new Point();
		getDisplaySize(activity, displaySize);
		int displayMemory = displaySize.x * displaySize.y * BYTES_PER_PIXEL;
	

		int cacheSize = NUMBER_OF_SCREENS_IN_MEMORY * displayMemory;
		this.mCache = new FileCache(cacheSize);
		mFails = new HashMap<String, Long>();
		
		int numberOfThreads = 1;
		if (Build.VERSION.SDK_INT >= 10)
			numberOfThreads = 3;
		
		this.mDownloadImageThread = new DownloadImageThread[numberOfThreads];

	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
	}

	private List<ImageHolder> finishByResource(String resource) {
		List<ImageHolder> imageHolders = new ArrayList<ImageHolder>();
		this.mLock.lock();
		try {
			for (ImageHolder imageHolder : this.mViewResourceMap.keySet()) {
				String viewResource = this.mViewResourceMap.get(imageHolder);
				if (viewResource.equals(resource))
					imageHolders.add(imageHolder);
			}
			for (ImageHolder imageHolder : imageHolders) {
				this.mViewResourceMap.remove(imageHolder);
			}

			this.mResourcesProcessingQueue.remove(resource);
		} finally {
			this.mLock.unlock();
		}
		return imageHolders;
	}

	/**
	 * actualy downlad image and display to correct ImageView
	 * 
	 * @param imageView
	 *            ImageView that should display image
	 * @param resource
	 *            url or its tail to download, can be null
	 */
	public synchronized void loadImage(ImageHolder imageHolder, String resource) {
		loadImage(imageHolder, resource, true);
	}

	public synchronized void loadImage(ImageHolder imageHolder,
			String resource, boolean immediately) {
		this.removeFromProcess(imageHolder);
		if (TextUtils.isEmpty(resource)) {
			imageHolder.setPlaceholer(immediately);
			return;
		}
		Bitmap cachedBitmap = this.mCache.get(resource);
		if (cachedBitmap != null) {
			imageHolder.setRemoteBitmap(cachedBitmap, immediately);
			return;
		}
		Long fail = mFails.get(resource);
		if (fail != null && System.currentTimeMillis() - fail < FAIL_TIME_MILLIS) {
			imageHolder.failDownloading(immediately);
			return;
		}
		
		imageHolder.setPlaceholer(immediately);
		try {
			this.putToProcess(resource, imageHolder);
		} catch (InterruptedException e) {
			// Ignore this error
		}
	}

	/**
	 * Call it on activity Pause
	 */
	public void onActivityPause() {
		for (int i = 0; i < this.mDownloadImageThread.length; i++) {
			DownloadImageThread thread = this.mDownloadImageThread[i];
			thread.stopSelf();
			thread.interrupt();
			thread = null;	
			this.mDownloadImageThread[i] = null;
		}
	}
	
	/**
	 * Call it on activity onLowMemory
	 */
	public void onActivityLowMemory() {
		this.mCache.evictAll();
	}

	/**
	 * Call it on activity Resume
	 */
	public void onActivityResume() {
		for (int i = 0; i < this.mDownloadImageThread.length; i++) {
			DownloadImageThread thread = new DownloadImageThread();
			thread.setPriority(Thread.MIN_PRIORITY);
			thread.setName(String.format("DownloadImageThread[%d]", i));
			thread.start();
			this.mDownloadImageThread[i] = thread;
		}
	}

	private boolean putToProcess(String resource, ImageHolder imageHolder)
			throws InterruptedException {
		this.mLock.lock();
		try {
			this.mViewResourceMap.put(imageHolder, resource);
			boolean contains = this.mResourcesQueue.contains(resource);
			if (contains)
				return false;
			contains = this.mResourcesProcessingQueue.contains(resource);
			if (contains)
				return false;
			this.mResourcesQueue.add(resource);
			this.mNotEmpty.signal();
			return true;
		} finally {
			this.mLock.unlock();
		}
	}

	public void failDownloading(String resource, final List<ImageHolder> imageHolders) {
		mFails.put(resource, System.currentTimeMillis());
		this.mActivity.runOnUiThread(new Runnable() {

			@Override
			public void run() {
				for (ImageHolder imageHolder : imageHolders) {
					imageHolder.failDownloading(false);
				}
			}
		});
	}

	private synchronized void receivedDrawable(final Bitmap bitmap,
			String resource, final List<ImageHolder> imageHolders) {
		if (bitmap == null) {
			throw new RuntimeException("Bitmap could not be null");
		}
		mFails.remove(resource);
		mCache.put(resource, bitmap);
		this.mActivity.runOnUiThread(new Runnable() {

			@Override
			public void run() {
				for (ImageHolder imageHolder : imageHolders) {
					imageHolder.setRemoteBitmap(bitmap, false);
				}
			}
		});
	}

	private void removeFromProcess(ImageHolder imageHolder) {
		this.mLock.lock();
		try {
			String resource = this.mViewResourceMap.remove(imageHolder);
			if (resource == null)
				return;
			boolean found = false;
			for (ImageHolder imageHolderIter : this.mViewResourceMap.keySet()) {
				String viewResource = this.mViewResourceMap
						.get(imageHolderIter);
				if (viewResource.equals(resource)) {
					found = true;
					break;
				}
			}
			if (found)
				return;
			this.mResourcesQueue.remove(resource);

		} finally {
			this.mLock.unlock();
		}
	}

	private String takeToProcess() throws InterruptedException {
		this.mLock.lock();
		try {
			while (this.mResourcesQueue.size() == 0)
				this.mNotEmpty.await();
			String resource = this.mResourcesQueue.remove(0);
			this.mResourcesProcessingQueue.add(resource);
			return resource;
		} finally {
			this.mLock.unlock();
		}
	}

	private static int convertDpToPixel(float dp, Resources resources) {
		DisplayMetrics metrics = resources.getDisplayMetrics();
		return (int) (dp * (metrics.densityDpi / 160f));
	}

	public static RemoteImageLoader createUsingDp(Activity activity,
			float requestedWidthDp, float requestedHeightDp) {
		Resources resources = activity.getResources();
		int widthPx = convertDpToPixel(requestedWidthDp, resources);
		int heightPx = convertDpToPixel(requestedHeightDp, resources);
		return new RemoteImageLoader(activity, widthPx, heightPx);
	}

}
