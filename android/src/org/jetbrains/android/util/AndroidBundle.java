// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.util;

import com.intellij.DynamicBundle;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public final class AndroidBundle {
  private static final @NonNls String BUNDLE = "messages.AndroidBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(AndroidBundle.class, BUNDLE);

  private AndroidBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
