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

package com.example.imageloader;

import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.appunite.imageloader.RemoteImageLoader;
import com.appunite.imageloader.RemoteImageLoader.ImageHolder;

public class Main extends Activity {


    /**
     * Example of animating drawable alpha
     *
     * Can be only used on post honeycomb devices.
     * Can be used with NineOldAndroid but is not recommended because performance.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static class AnimatedHoneycombLoadableDrawable implements RemoteImageLoader.ImageHolder {

        private final Resources mResources;
        private final Drawable mErrorDrawable;
        private ImageView mImageView;
        private Bitmap mBitmap;
        private android.animation.ObjectAnimator mHideAnimation;
        private android.animation.ObjectAnimator mShowAnimation;

        public AnimatedHoneycombLoadableDrawable(ImageView imageView, int errorPlaceholder) {

            final Context context = imageView.getContext();
            assert context != null;
            mResources = context.getResources();
            int duration = mResources
                    .getInteger(android.R.integer.config_mediumAnimTime);

            mImageView = imageView;
            mErrorDrawable = mResources.getDrawable(errorPlaceholder);

            mShowAnimation = android.animation.ObjectAnimator.ofInt(null, "alpha", 0, 255);
            mShowAnimation.setDuration(duration);
            mHideAnimation = android.animation.ObjectAnimator.ofInt(null, "alpha", 255, 0);
            mHideAnimation.setDuration(duration);
        }


        @Override
        public void setRemoteBitmap(Bitmap bitmap, boolean immediately) {
            if (bitmap == mBitmap) {
                return;
            }
            mBitmap = bitmap;
            if (bitmap == null) {
                failDownloading(immediately);
                return;
            }
            final BitmapDrawable bitmapDrawable = new BitmapDrawable(mResources, bitmap);
            mImageView.setImageDrawable(bitmapDrawable);
            changeDisplay(bitmapDrawable, immediately, true);
        }

        private void changeDisplay(Drawable drawable, boolean immediately, boolean show) {
            mShowAnimation.cancel();
            mHideAnimation.cancel();
            mShowAnimation.setTarget(drawable);
            mHideAnimation.setTarget(drawable);
            if (immediately) {
                drawable.setAlpha(show ? 255 : 0);
            } else {
                if (show) {
                    mShowAnimation.start();
                } else {
                    mHideAnimation.start();
                }
            }

        }

        @Override
        public void failDownloading(boolean immediately) {
            mBitmap = null;
            mShowAnimation.cancel();
            mHideAnimation.cancel();
            mImageView.setImageDrawable(mErrorDrawable);
        }

        @Override
        public void setPlaceholder(boolean immediately) {
            mBitmap = null;
            mShowAnimation.cancel();
            mHideAnimation.cancel();
            mImageView.setImageDrawable(null);
        }
    }

    /**
     * Example of animating view alpha
     *
     * Can be only used on post honeycomb devices.
     * Can be used with NineOldAndroid but is not recommended because performance.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static class AnimatedHoneycombImageLoader implements RemoteImageLoader.ImageHolder {

        private ImageView mImageView;
        private int mErrorPlaceholder;
        private Bitmap mBitmap;
        private ObjectAnimator mHideAnimation;
        private ObjectAnimator mShowAnimation;

        public AnimatedHoneycombImageLoader(ImageView imageView, int errorPlaceholder) {

            final Context context = imageView.getContext();
            assert context != null;
            int duration = context.getResources()
                    .getInteger(android.R.integer.config_mediumAnimTime);

            mImageView = imageView;
            mErrorPlaceholder = errorPlaceholder;

            mShowAnimation = ObjectAnimator.ofFloat(imageView, "alpha", 0.0f, 1.0f);
            mShowAnimation.setDuration(duration);
            mHideAnimation = ObjectAnimator.ofFloat(imageView, "alpha", 1.0f, 0.0f);
            mHideAnimation.setDuration(duration);
        }


        @Override
        public void setRemoteBitmap(Bitmap bitmap, boolean immediately) {
            if (bitmap == mBitmap) {
                return;
            }
            mBitmap = bitmap;
            if (bitmap == null) {
                failDownloading(immediately);
                return;
            }
            mImageView.setImageBitmap(bitmap);
            changeDisplay(immediately, true);
        }

        private void changeDisplay(boolean immediately, boolean show) {
            mShowAnimation.cancel();
            mHideAnimation.cancel();
            if (immediately) {
                mImageView.setAlpha(show ? 1.0f : 0.0f);
            } else {
                if (show) {
                    mShowAnimation.start();
                } else {
                    mHideAnimation.start();
                }
            }

        }

        @Override
        public void failDownloading(boolean immediately) {
            mBitmap = null;
            mImageView.setImageResource(mErrorPlaceholder);
            changeDisplay(immediately, true);
        }

        @Override
        public void setPlaceholder(boolean immediately) {
            mBitmap = null;
            changeDisplay(immediately, false);
        }
    }

	private static class SampleCursorAdapter extends CursorAdapter {

		private static class ViewHolder {
			TextView textView;
			ImageHolder imageHolder;
		}

		private final RemoteImageLoader mRemoteImageLoader;

		public SampleCursorAdapter(Context context, Cursor c,
				RemoteImageLoader remoteImageLoader) {
			super(context, c, false);
			this.mRemoteImageLoader = remoteImageLoader;

		}

		@Override
		public void bindView(View view, Context context, Cursor cursor) {
			ViewHolder viewHolder = (ViewHolder) view.getTag();

			String name = SampleCursorHelper.getName(cursor);
			viewHolder.textView.setText(name);

			String imageUrl = SampleCursorHelper.getImageUrl(cursor);
			this.mRemoteImageLoader.loadImage(viewHolder.imageHolder, imageUrl);
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			ViewHolder viewHolder = new ViewHolder();

			LayoutInflater inflater = LayoutInflater.from(context);
			View view = inflater.inflate(R.layout.main_item, null);
            assert view != null;
			view.setTag(viewHolder);

			ImageView imageView = (ImageView) view
					.findViewById(android.R.id.icon);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                // Using honeycomb animated remote image loader

                // Choose one:
                // animating ImageView
                viewHolder.imageHolder = new AnimatedHoneycombImageLoader(imageView,
                    R.drawable.ic_contact_picture_holo_dark);
                // animating Drawable
//                viewHolder.imageHolder = new AnimatedHoneycombLoadableDrawable(imageView,
//                        R.drawable.ic_contact_picture_holo_dark);
            } else {
                // Using not animated normal image loader on pre honeycomb devices
                viewHolder.imageHolder = RemoteImageLoader.newImageHolder(
                        imageView,R.drawable.ic_contact_picture_holo_dark,
                        R.drawable.ic_contact_picture_holo_dark);
            }
			viewHolder.textView = (TextView) view
					.findViewById(android.R.id.text1);

			return view;
		}

	}

	private static class SampleCursorHelper {
		private static final String COL_ID = "_id";
		private static final String COL_IMAGE = "image";
		private static final String COL_NAME = "name";

		public static SampleCursorHelper create() {
			MatrixCursor matrixCursor = new MatrixCursor(new String[] { COL_ID,
					COL_NAME, COL_IMAGE });
			return new SampleCursorHelper(matrixCursor);
		}

		public static String getImageUrl(Cursor cursor) {
			return cursor.getString(2);
		}

		public static String getName(Cursor cursor) {
			return cursor.getString(1);
		}

		long counter = 0;

		private final MatrixCursor cursor;

		private SampleCursorHelper(MatrixCursor cursor) {
			this.cursor = cursor;
		}

		public void add(String string, String url) {
			this.cursor.addRow(new Object[] {this.counter, string,
					url });
			this.counter++;
		}

		public Cursor getCursor() {
			return this.cursor;
		}

	}

	private static final float REQUESTED_SIZE = 80;

	private ListView mListView;

	private RemoteImageLoader remoteImageLoader;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.main);
		this.mListView = (ListView) this.findViewById(android.R.id.list);

		this.setAdapter();
	}

	@Override
	protected void onPause() {
		super.onPause();
		this.remoteImageLoader.onActivityPause();
	}

	@Override
	protected void onResume() {
		this.remoteImageLoader.onActivityResume();
		super.onResume();
	}

	@Override
	public void onLowMemory() {
		this.remoteImageLoader.onActivityLowMemory();
		super.onLowMemory();
	}

	private void setAdapter() {
		SampleCursorHelper cursorHelper = SampleCursorHelper.create();
		String[] urls = this.getResources().getStringArray(R.array.urls);
		for (String url : urls) {
			cursorHelper.add("Franek", url);
		}

		this.remoteImageLoader = RemoteImageLoader.createUsingDp(this,
                REQUESTED_SIZE, REQUESTED_SIZE);

		SampleCursorAdapter sampleCursorAdapter = new SampleCursorAdapter(this,
				cursorHelper.getCursor(), this.remoteImageLoader);
		this.mListView.setAdapter(sampleCursorAdapter);
	}
}
