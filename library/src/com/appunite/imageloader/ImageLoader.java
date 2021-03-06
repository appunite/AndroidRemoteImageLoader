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

import java.io.InputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * 
 * @author Jacek Marchwicki (jacek.marchwicki@gmail.com)
 * 
 */
public class ImageLoader {

    public static class ImageOutOfMemoryError extends Error {
        public ImageOutOfMemoryError(OutOfMemoryError e, String fileName, int scaleFactor) {
            super("Could not load image: " + fileName + " (scale: " + scaleFactor + ")", e);
        }
    }

	/**
	 * Calculate scale factor for image from file with requested size Scaling
	 * will be done by slicing image by two Returned image will never be grater
	 * than maximal height/width and will be scaled to be as little grater as it
	 * can to satisfy requested height/width. Image can be smaller from
	 * requested height/width source file is smaller, or if maximal value has
	 * been excesed.
	 * 
	 * @param imageFilePath
	 *            file path to file
	 * @param requestedHeight
	 *            requested height
	 * @param requestedWidth
	 *            requested width
	 * @param maxHeight
	 *            maximal height
	 * @param maxWidth
	 *            maximal width
	 * @return scale factor for file, or -1 if file could not be opened. Scale
	 *         factor should be 1 or multiple of 2
	 */
	public static int getImageScaleFactor(String imageFilePath,
                                          float requestedHeight,
                                          float requestedWidth,
                                          float maxHeight,
                                          float maxWidth) {
		BitmapFactory.Options fileOptions = new BitmapFactory.Options();
		fileOptions.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(imageFilePath, fileOptions);
		if (fileOptions.outWidth <= 0) {
			return -1;
		}

		return getScaleFactorFromFileOptions(requestedHeight, requestedWidth,
                maxHeight, maxWidth, fileOptions);
	}
	
	@SuppressWarnings("UnusedDeclaration")
    public static int getImageScaleFactor(InputStream inputStream,
                                          float requestedHeight,
                                          float requestedWidth,
                                          float maxHeight,
                                          float maxWidth) {
		BitmapFactory.Options fileOptions = new BitmapFactory.Options();
		fileOptions.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(inputStream, null, fileOptions);
		if (fileOptions.outWidth <= 0) {
			return -1;
		}

		return getScaleFactorFromFileOptions(requestedHeight, requestedWidth,
                maxHeight, maxWidth, fileOptions);
	}

	private static int getScaleFactorFromFileOptions(float requestedHeight,
                                                     float requestedWidth,
                                                     float maxHeight,
                                                     float maxWidth,
                                                     BitmapFactory.Options fileOptions) {
		if (fileOptions.outHeight > requestedHeight
				&& fileOptions.outWidth > requestedWidth
				|| fileOptions.outHeight > maxHeight
				|| fileOptions.outWidth > maxWidth) {
			double log2 = Math.log(2.0d);
			double requestedScaleFactor = Math.min(fileOptions.outHeight
					/ requestedHeight, fileOptions.outWidth / requestedWidth);
			int requestedScale = (int) Math.pow(2,
					Math.floor(Math.log(requestedScaleFactor) / log2));

			double maxScaleFactor = Math.max(fileOptions.outHeight / maxHeight,
					fileOptions.outWidth / maxHeight);
			int maxScale = (int) Math.pow(2,
					Math.ceil(Math.log(maxScaleFactor) / log2));

			int scale = requestedScale > maxScale ? requestedScale : maxScale; // Min(x,y)
			return scale > 0 ? scale : 1;
		}
		return 1;
	}

	public static Bitmap loadImage(String imageFilePath,
			float requestedHeight, float requestedWidth) {
		return loadImage(imageFilePath, requestedHeight, requestedWidth,
				2.0f * requestedHeight, 2.0f * requestedWidth);
	}

	/**
	 * Load image from file with requested size Scaling will be done by slicing
	 * image by two Returned image will never be grater than maximal
	 * height/width and will be scaled to be as little grater as it can to
	 * satisfy requested height/width. Image can be smaller from requested
	 * height/width source file is smaller, or if maximal value has been
	 * excesed.
	 * 
	 * @param imageFilePath
	 *            file path to file
	 * @param requestedHeight
	 *            requested height
	 * @param requestedWidth
	 *            requested width
	 * @param maxHeight
	 *            maximal height
	 * @param maxWidth
	 *            maximal width
	 * @return bitmap image or null if it can not be opened
	 */
	public static Bitmap loadImage(String imageFilePath,
			float requestedHeight, float requestedWidth, float maxHeight,
			float maxWidth) {

		int scale = getImageScaleFactor(imageFilePath, requestedHeight,
                requestedWidth, maxHeight, maxWidth);

		if (scale < 1)
			return null;

		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inSampleSize = scale;

        try {
		    return BitmapFactory.decodeFile(imageFilePath, o);
        } catch (OutOfMemoryError e) {
            throw new ImageOutOfMemoryError(e, imageFilePath, scale);
        }
	}
	
	@SuppressWarnings("UnusedDeclaration")
    public static Bitmap loadImage(InputStream inputStream,
			int scale) {

		if (scale < 1)
			return null;

		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inSampleSize = scale;

		return BitmapFactory.decodeStream(inputStream, null, o);
	}
}
