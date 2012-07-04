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

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
			view.setTag(viewHolder);

			ImageView imageView = (ImageView) view
					.findViewById(android.R.id.icon);
			viewHolder.imageHolder = new RemoteImageLoader.ImageViewHolder(
					imageView);
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
			this.cursor.addRow(new Object[] { new Long(this.counter), string,
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

		Bitmap placeHolder = BitmapFactory.decodeResource(this.getResources(),
				R.drawable.ic_contact_picture_holo_dark);
		this.remoteImageLoader = new RemoteImageLoader(this, placeHolder,
				REQUESTED_SIZE, REQUESTED_SIZE);

		SampleCursorAdapter sampleCursorAdapter = new SampleCursorAdapter(this,
				cursorHelper.getCursor(), this.remoteImageLoader);
		this.mListView.setAdapter(sampleCursorAdapter);
	}
}
