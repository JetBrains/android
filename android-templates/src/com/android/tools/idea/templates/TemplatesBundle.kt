// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.templates

import com.intellij.DynamicBundle
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_NAME = "messages.TemplatesBundle"

class TemplatesBundle private constructor() {
  companion object {
    private val ourBundle = DynamicBundle(TemplatesBundle::class.java, BUNDLE_NAME)

    @NlsContexts.Label
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any?): String {
      return ourBundle.getMessage(key, *params)
    }
  }
}
