package ru.orangesoftware.financisto.utils

import android.content.Context
import android.os.Environment
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

object PicturesUtil {

    private val PICTURES_DIR: File =
        File(Environment.getExternalStorageDirectory(), "financisto/pictures")
    private val LEGACY_PICTURES_DIR: File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    private val PICTURE_FILE_NAME_FORMAT: SimpleDateFormat =
        SimpleDateFormat("yyyyMMddHHmmssSS")

    @JvmStatic
    fun createEmptyImageFile(): File = pictureFile(
        pictureFileName = "${PICTURE_FILE_NAME_FORMAT.format(Date())}.jpg",
        fallbackToLegacy = false,
    )

    @JvmStatic
    fun pictureFile(pictureFileName: String, fallbackToLegacy: Boolean): File {
        if (!PICTURES_DIR.exists()) PICTURES_DIR.mkdirs()

        var file = File(PICTURES_DIR, pictureFileName)
        if (fallbackToLegacy && !file.exists()) {
            file = File(LEGACY_PICTURES_DIR, pictureFileName)
        }
        return file
    }

    @JvmStatic
    fun showImage(context: Context, imageView: ImageView, pictureFileName: String?) {
        Glide.with(context)
            .load(pictureFile(pictureFileName ?: "", true))
            .transition(DrawableTransitionOptions().crossFade())
            //.override(320, 320)
            .into(imageView);
    }
}
