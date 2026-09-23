package net.noctilis.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Картинки для отправки на сервер: скриншот → JPEG до 1600 px → base64. */
object Images {
    fun encode(bytes: ByteArray, maxSide: Int = 1600, quality: Int = 85): String? {
        val bmp = decodeScaled(bytes, maxSide) ?: return null
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    fun decodeScaled(bytes: ByteArray, maxSide: Int = 1600): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
