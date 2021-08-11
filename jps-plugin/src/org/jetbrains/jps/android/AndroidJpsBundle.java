// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.android;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.jps.api.JpsDynamicBundle;

import java.util.function.Supplier;

public class AndroidJpsBundle extends JpsDynamicBundle {
  @NonNls private static final String BUNDLE = "messages.AndroidJpsBundle";
  private static final AndroidJpsBundle INSTANCE = new AndroidJpsBundle();

  private AndroidJpsBundle() { super(BUNDLE); }

  @NotNull
  public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getMessage(key, params);
  }

  @NotNull
  public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, @NotNull Object... params) {
    return INSTANCE.getLazyMessage(key, params);
  }
}