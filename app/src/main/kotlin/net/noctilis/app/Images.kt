package net.noctilis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Картинки для отправки на сервер: скриншот → JPEG до 1600 px → base64.
 * Файл из галереи никогда не читается целиком в память: сначала только размеры,
 * потом декодирование с inSampleSize. Нехватка памяти (OutOfMemoryError) — это не
 * Exception, ловим её отдельно и отдаём null вместо падения.
 */
object Images {
    fun encode(bytes: ByteArray, maxSide: Int = 1600, quality: Int = 85): String? =
        toJpeg(bytes, maxSide, quality)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    /** JPEG-байты уменьшенной картинки — их можно хранить до отправки вместо исходного файла. */
    fun toJpeg(bytes: ByteArray, maxSide: Int = 1600, quality: Int = 85): ByteArray? {
        val bmp = decodeScaled(bytes, maxSide) ?: return null
        return try { compress(bmp, quality) } finally { bmp.recycle() }
    }

    /** То же для файла из галереи по Uri: читаем поток дважды (размеры, потом картинка). */
    fun toJpeg(ctx: Context, uri: Uri, maxSide: Int = 1600, quality: Int = 85): ByteArray? {
        val bmp = decodeScaled(ctx, uri, maxSide) ?: return null
        return try { compress(bmp, quality) } finally { bmp.recycle() }
    }

    fun decodeScaled(bytes: ByteArray, maxSide: Int = 1600): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) null
        else BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample(opts, maxSide) })
    } catch (_: OutOfMemoryError) { null } catch (_: Exception) { null }

    fun decodeScaled(ctx: Context, uri: Uri, maxSide: Int = 1600): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) null
        else ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample(opts, maxSide) })
        }
    } catch (_: OutOfMemoryError) { null } catch (_: Exception) { null }

    private fun sample(opts: BitmapFactory.Options, maxSide: Int): Int {
        var sample = 1
        while (opts.outWidth / sample > maxSide || opts.outHeight / sample > maxSide) sample *= 2
        return sample
    }

    private fun compress(bmp: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
