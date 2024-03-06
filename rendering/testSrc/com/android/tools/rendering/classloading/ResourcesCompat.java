/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.rendering.classloading;

import static com.android.tools.rendering.classloading.Build.VERSION.SDK_INT;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.TypedValue;
public final class ResourcesCompat {
  public static int sdkVersion = SDK_INT;

  public static Typeface loadFont(Context context,
                                  Resources wrapper,
                                  TypedValue value,
                                  int id,
                                  int style,
                                  FontCallback fontCallback,
                                  Handler handler,
                                  boolean isRequestFromLayoutInflator,
                                  boolean isCachedOnly) {
    final String file = value.string.toString();
    if (!file.startsWith("res/")) {
      throw new RuntimeException("Font file not found");
    }

    return null;
  }

  public static int getSdkVersion() {
    return SDK_INT;
  }

  public ResourcesCompat() { }

  public abstract static class FontCallback {}
}