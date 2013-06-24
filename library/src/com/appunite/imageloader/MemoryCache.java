package com.appunite.imageloader;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;

/**
 *
 * @author Jacek Marchwicki (jacek.marchwicki@gmail.com)
 *
 */
public class MemoryCache extends LruCache<String, Bitmap> {

    static final int NUMBER_OF_SCREENS_IN_MEMORY = 4;
    public static final int BYTES_PER_PIXEL = 4;

    public MemoryCache(int maxSize) {
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
            return value.getWidth() * value.getHeight() * BYTES_PER_PIXEL;
        }

    }
}
