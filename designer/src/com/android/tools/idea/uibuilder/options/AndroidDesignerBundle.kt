// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package com.android.tools.idea.uibuilder.options

import com.intellij.DynamicBundle
import java.util.function.Supplier
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

object AndroidDesignerBundle {
  @NonNls private const val BUNDLE = "messages.AndroidDesignerActionsBundle"
  private val INSTANCE = DynamicBundle(AndroidDesignerBundle::class.java, BUNDLE)

  @Nls
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return INSTANCE.getMessage(key, *params)
  }

  fun messagePointer(
    @PropertyKey(resourceBundle = BUNDLE) key: String,
    vararg params: Any
  ): Supplier<String> {
    return INSTANCE.getLazyMessage(key, *params)
  }
}
