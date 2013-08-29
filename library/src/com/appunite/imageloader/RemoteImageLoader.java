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
import android.util.Log;
import android.view.Display;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

/**
 * 
 * @author Jacek Marchwicki (jacek.marchwicki@gmail.com)
 * 
 */
public class RemoteImageLoader {

    private static final String TAG = "RemoteImageLoader";
    private static final float MEMORY_THRESHOLD = (1.0f - 0.2f); // Leave always 20% of memory

    public static interface ImageHolder {
        void setRemoteBitmap(Bitmap bitmap, boolean immediately);
        void failDownloading(boolean immediately);
        void setPlaceholder(boolean immediately);
    }

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
					String resource = takeToProcess();

					Bitmap bitmap = null;
                    boolean inLowMemory = false;
                    try {
                        bitmap = mDownloader.downloadImage(resource,
							mImageRequestedWidth, mImageRequestedHeight);
                    } catch (ImageLoader.ImageOutOfMemoryError e) {
                        Log.e(TAG, "Out of memory - clearing memory cache. Resource: " + resource +
                                " error: " + e.getMessage());
                        inLowMemory = true;
                    }
                    receivedDrawable(bitmap, resource, inLowMemory);
				} catch (InterruptedException ignored) {
				}
			}

		}

		synchronized public void stopSelf() {
			this.mStop = true;
		}

	}
	
	@SuppressWarnings("UnusedDeclaration")
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
		public void setPlaceholder(boolean immediately) {
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
     * @param requestedHeight
     *            requested height
     * @param requestedWidth
     *            requested width
     */
    public RemoteImageLoader(Activity activity, int requestedWidth,
                             int requestedHeight) {
        this(activity, null, null, requestedWidth, requestedHeight);
    }

    /**
     * Create class
     *
     * @param activity
     *            activity that should be owner
     * @param diskCache
     *            disk cache instance
     * @param memoryCache
     *            memory cache instance
     * @param requestedHeight
     *            requested height in px
     * @param requestedWidth
     *            requested width in px
     */
    public RemoteImageLoader(Activity activity,
                             DiskCache diskCache,
                             MemoryCache memoryCache,
                             int requestedWidth,
                             int requestedHeight) {
        if (activity == null) {
            throw new IllegalArgumentException("Activity could not be null");
        }
        if (requestedWidth <= 0 || requestedHeight <= 0) {
            throw new IllegalArgumentException("Requested width and height have to be grater then" +
                    "0");
        }

        if (diskCache == null) {
            diskCache = new DiskCache(activity, IMAGE_CACHE_DIR_PREFIX);
        }
        if (memoryCache == null) {
            Point displaySize = new Point();
            getDisplaySize(activity, displaySize);
            int displayMemory = displaySize.x * displaySize.y * MemoryCache.BYTES_PER_PIXEL;
            int cacheSize = MemoryCache.NUMBER_OF_SCREENS_IN_MEMORY * displayMemory;
            memoryCache = new MemoryCache(cacheSize);
        }

        mActivity = activity;
        mImageRequestedWidth = requestedWidth;
        mImageRequestedHeight = requestedHeight;
        mDiskCache = diskCache;
        mCache = memoryCache;
        mFails = new HashMap<String, Long>();

        int numberOfThreads = 1;
        if (Build.VERSION.SDK_INT >= 10) {
            numberOfThreads = 3;
        }

        this.mDownloadImageThread = new DownloadImageThread[numberOfThreads];

    }

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
	}

	private List<ImageHolder> finishByResource(Bitmap bitmap, String resource, boolean inLowMemory) {
		List<ImageHolder> imageHolders = new ArrayList<ImageHolder>();
		this.mLock.lock();

        Runtime runtime = Runtime.getRuntime();
        if(inLowMemory || runtime.maxMemory() * MEMORY_THRESHOLD < runtime.totalMemory()) {
            mCache.evictAll();
            System.gc();
            Log.w(TAG, "Clearing cache because of low memory");
        }
        if (bitmap == null) {
            mFails.put(resource, System.currentTimeMillis());
        } else {
            mFails.remove(resource);
            mCache.put(getInMemoryKey(resource), bitmap);
        }
		try {
			for (ImageHolder imageHolder : this.mViewResourceMap.keySet()) {
				String viewResource = this.mViewResourceMap.get(imageHolder);
				if (viewResource.equals(resource)) {
					imageHolders.add(imageHolder);
                }
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
	 * @param imageHolder
	 *            image holder that should display image
	 * @param resource
	 *            url or its tail to download, can be null
	 */
    @SuppressWarnings("UnusedDeclaration")
	public synchronized void loadImage(ImageHolder imageHolder, String resource) {
		loadImage(imageHolder, resource, true);
	}

    @SuppressWarnings("UnusedDeclaration")
	public synchronized void loadImage(ImageHolder imageHolder,
			String resource, boolean immediately) {
		this.removeFromProcess(imageHolder);
		if (TextUtils.isEmpty(resource)) {
			imageHolder.setPlaceholder(immediately);
			return;
		}
		Bitmap cachedBitmap = mCache.get(getInMemoryKey(resource));
		if (cachedBitmap != null) {
			imageHolder.setRemoteBitmap(cachedBitmap, immediately);
			return;
		}
		Long fail = mFails.get(resource);
		if (fail != null && System.currentTimeMillis() - fail < FAIL_TIME_MILLIS) {
			imageHolder.failDownloading(immediately);
			return;
		}
		
		imageHolder.setPlaceholder(immediately);
		try {
			this.putToProcess(resource, imageHolder);
		} catch (InterruptedException e) {
			// Ignore this error
		}
	}

    private String getInMemoryKey(String resource) {
        return resource + "_" + mImageRequestedWidth + "x" + mImageRequestedHeight;
    }

    /**
	 * Call it on activity Pause
	 */
	@SuppressWarnings("UnusedDeclaration")
    public void onActivityPause() {
		for (int i = 0; i < this.mDownloadImageThread.length; i++) {
			DownloadImageThread thread = this.mDownloadImageThread[i];
			thread.stopSelf();
			thread.interrupt();
			this.mDownloadImageThread[i] = null;
		}
	}
	
	/**
	 * Call it on activity onLowMemory
	 */
    @SuppressWarnings("UnusedDeclaration")
	public void onActivityLowMemory() {
		this.mCache.evictAll();
	}

	/**
	 * Call it on activity Resume
	 */
    @SuppressWarnings("UnusedDeclaration")
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

	private synchronized void receivedDrawable(final Bitmap bitmap,
                                               final String resource,
                                               final boolean inLowMemory) {
		this.mActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
                bitmapReceived(bitmap, resource, inLowMemory);
			}
		});
	}

    private void bitmapReceived(Bitmap bitmap, String resource, boolean inLowMemory) {
        final List<ImageHolder> imageHolders = finishByResource(bitmap, resource, inLowMemory);
        if (bitmap != null) {
            for (ImageHolder imageHolder : imageHolders) {
                imageHolder.setRemoteBitmap(bitmap, false);
            }
        } else {
            for (ImageHolder imageHolder : imageHolders) {
                imageHolder.failDownloading(false);
            }
        }
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

    /**
     *
     * Craate class
     *
     * @param activity
     *            activity that should be owner
     * @param requestedWidthDp
     *            requested height in dp
     * @param requestedHeightDp
     *            requested width in dp
     * @return image loader
     */
    @SuppressWarnings("UnusedDeclaration")
	public static RemoteImageLoader createUsingDp(Activity activity,
                                                  float requestedWidthDp,
                                                  float requestedHeightDp) {
		Resources resources = activity.getResources();
		int widthPx = convertDpToPixel(requestedWidthDp, resources);
		int heightPx = convertDpToPixel(requestedHeightDp, resources);
		return new RemoteImageLoader(activity, widthPx, heightPx);
	}

    /**
     * Create class
     *
     * @param activity
     *            activity that should be owner
     * @param diskCache
     *            disk cache instance
     * @param memoryCache
     *            memory cache instance
     * @param requestedWidthDp
     *            requested height in dp
     * @param requestedHeightDp
     *            requested width in dp
     * @return  image loader
     */
    @SuppressWarnings("UnusedDeclaration")
    public static RemoteImageLoader createUsingDp(Activity activity,
                                                  DiskCache diskCache,
                                                  MemoryCache memoryCache,
                                                  float requestedWidthDp,
                                                  float requestedHeightDp) {
        Resources resources = activity.getResources();
        int widthPx = convertDpToPixel(requestedWidthDp, resources);
        int heightPx = convertDpToPixel(requestedHeightDp, resources);
        return new RemoteImageLoader(activity, diskCache, memoryCache, widthPx, heightPx);
    }


}
