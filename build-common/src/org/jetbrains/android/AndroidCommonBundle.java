// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.intellij.DynamicBundle;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public class AndroidCommonBundle {
  private static final @NonNls String BUNDLE = "messages.AndroidCommonBundle";
  private static final DynamicBundle INSTANCE = new DynamicBundle(AndroidCommonBundle.class, BUNDLE);

  private AndroidCommonBundle() {}

  public static @NotNull @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }

  public static @NotNull Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}
