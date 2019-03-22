package test.langdb.bitmaps

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.databinding.BindingAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target

interface ImageListener {
    fun onLoadFailed()
    fun onLoaded()
}

@BindingAdapter("imageUrl", "placeholder", "imageListener", requireAll = false)
fun ImageView.bindImageWithGlide(imageUrl: String, placeholder: Drawable?, imageListener: ImageListener?) {
    val requestOptions = RequestOptions().placeholder(placeholder ?: context.resources.getDrawable(R.drawable.bitmaps_empty_photo))
    Glide.with(context)
        .load(imageUrl)
        .apply(requestOptions)
        .listener(object : RequestListener<Drawable> {
            override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                imageListener?.onLoadFailed()
                return false // Allow Glide to handle failure
            }

            override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                imageListener?.onLoaded()
                return false // Allow Glid to handle success
            }
        })
        .into(this)
}

/**
 * @param index The index of the corresponding [imageEntries] entry.
 */
data class Image(val index: Int) {
    val thumbUrl: String get() = imageEntries[index].toThumbUrl()
    val imageUrl: String get() = imageEntries[index].toImageUrl()
}