package com.android.tools.screensharing.benchmark

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.children
import kotlin.properties.Delegates

/**
 * A custom Android View that can display an integer encoded as colors.
 *
 * Functions as a [LinearLayout] of [TextView]s that dynamically resizes as more are needed to encode
 * integers with the given number of total bits and bits per color channel. Configure initial values using
 * the [maxBits] and [bitsPerChannel] custom attribute values. Must also contain at least one [TextView] child
 * in the layout which will be used as a prototype for additional [TextView]s if necessary.
 */
open class EncodedIntegerView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
  private val textViewChildren
    get() = children.map { it as TextView }

  var configDirty: Boolean = true
  var maxBits: Int by Delegates.observable(0) { _, _, _ -> configDirty = true }
  var bitsPerChannel: Int by Delegates.observable(0) { _, _, _ -> configDirty = true }

  init {
    context.theme.obtainStyledAttributes(attrs, R.styleable.EncodedIntegerView, 0, 0).apply {
      try {
        maxBits = getInt(R.styleable.EncodedIntegerView_maxBits, Int.SIZE_BITS)
        bitsPerChannel = getInt(R.styleable.EncodedIntegerView_bitsPerChannel, 8)
      }
      finally {
        recycle()
      }
    }
  }

  /** Shows the color values as strings in each child [TextView] iff [textVisible] is [true]. */
  fun setTextVisible(textVisible: Boolean) {
    textViewChildren.forEach {
      if (textVisible) it.setTextToBackgroundColor() else it.text = null
    }
  }

  /** Displays the given integer as colors in the child [TextView]s. */
  fun displayAsColors(n: Int) {
    if (configDirty) reloadConfiguration()
    displayColors(computeColors(n))
  }

  protected fun displayError(msg: String) {
    textViewChildren.forEach { it.displayError(msg) }
  }

  /** Computes the colors to use to display the given integer. */
  protected open fun computeColors(n: Int) : List<Int>? = n.toColors(maxBits, bitsPerChannel)

  protected open fun onConfigurationReloaded() {}

  private fun ensureChildren() {
    val targetNumChildren = if (bitsPerChannel == 0) maxBits else (maxBits - 1) / (bitsPerChannel * 3) + 1
    if (childCount > targetNumChildren) {
      removeViews(targetNumChildren, childCount - targetNumChildren)
    } else {
      val prototype = getChildAt(0) as TextView
      repeat(targetNumChildren - childCount) {
        addView(TextView(context, /* attrs = */ null, /* defStyleAttr = */ 0, R.style.encoded_integer_view_block).apply {
          layoutParams = prototype.layoutParams
          gravity = prototype.gravity
        })
      }
    }
  }

  private fun displayColors(data: Iterable<Int>? = null) {
    if (data == null) {
      displayError("Invalid")
      return
    }
    textViewChildren.zip(data.asSequence()).forEach {
      it.first.setBackgroundColor(it.second)
      it.first.text = null
    }
  }

  private fun TextView.displayError(msg: String) {
    setBackgroundColor(Color.WHITE)
    setTextColor(Color.BLACK)
    text = msg
  }

  private fun TextView.setTextToBackgroundColor() {
    (background as? ColorDrawable)?.color?.let {
      text = it.toHexColorString()
      setTextColor(it.contrastingColor())
    }
  }

  private fun reloadConfiguration() {
    ensureChildren()
    onConfigurationReloaded()
    configDirty = false
  }
}
