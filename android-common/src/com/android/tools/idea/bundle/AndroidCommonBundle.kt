// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.bundle;

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

internal object AndroidCommonBundle {
  const val BUNDLE: String = "messages.AndroidCommonBundle";
  val INSTANCE: DynamicBundle  = DynamicBundle(AndroidCommonBundle::class.java, BUNDLE);

  @JvmStatic
  fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
    return INSTANCE.getMessage(key, params);
  }

  fun  messagePointer(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): Supplier<String> {
    return INSTANCE.getLazyMessage(key, params);
  }
}