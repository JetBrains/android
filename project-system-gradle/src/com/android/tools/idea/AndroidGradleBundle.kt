// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea

import com.intellij.AbstractBundle
import com.intellij.openapi.util.NlsContexts
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.ResourceBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE_NAME = "messages.AndroidGradleBundle"

class AndroidGradleBundle private constructor() {
  companion object {
    private var ourBundle: Reference<ResourceBundle?>? = null

    private fun getBundle(): ResourceBundle {
      return ourBundle?.get() ?: ResourceBundle.getBundle(BUNDLE_NAME).also {
        ourBundle = SoftReference(it)
      }
    }

    @NlsContexts.Checkbox
    @JvmStatic
    fun message(
      @PropertyKey(resourceBundle = BUNDLE_NAME) key: String,
      vararg params: Any?
    ): String {
      return AbstractBundle.message(getBundle(), key, *params)
    }
  }
}