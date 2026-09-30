package com.elder.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 把相册里选的照片缩小、摆正、裁成 3:4 竖图后保存到 App 私有目录。 */
public final class PhotoUtil {
    private PhotoUtil() {}

    public static boolean importPhoto(Context c, Uri uri, File dest) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) return false;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= 700 && o.outHeight / (sample * 2) >= 700) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bm;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                bm = BitmapFactory.decodeStream(in, null, o2);
            }
            if (bm == null) return false;

            int rotate = 0;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                ExifInterface ex = new ExifInterface(in);
                int ori = ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) rotate = 90;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rotate = 180;
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rotate = 270;
            } catch (Exception ignored) {}
            if (rotate != 0) {
                Matrix m = new Matrix();
                m.postRotate(rotate);
                bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            }

            // 居中裁成 3:4（宽:高），偏上一点保住脸
            int w = bm.getWidth(), h = bm.getHeight();
            int cw = w, ch = h;
            if (w * 4 > h * 3) cw = h * 3 / 4; else ch = w * 4 / 3;
            int x = (w - cw) / 2;
            int y = Math.max(0, (h - ch) / 3);
            bm = Bitmap.createBitmap(bm, x, y, cw, ch);
            if (cw > 600) bm = Bitmap.createScaledBitmap(bm, 600, 800, true);

            File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                bm.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            return tmp.renameTo(dest);
        } catch (Exception e) {
            return false;
        }
    }
}
