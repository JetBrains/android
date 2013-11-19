/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.rendering.ClassConverter.*;

/**
 * Exception thrown when we attempt to load a class that cannot be converted by
 * the {@link com.android.tools.idea.rendering.RenderClassLoader}
 */
public class InconvertibleClassError extends UnsupportedClassVersionError {
  private final String myFqcn;
  private final int myMinor;
  private final int myMajor;

  public InconvertibleClassError(@Nullable Throwable cause, @NotNull String fqcn, int major, int minor) {
    super(fqcn);
    myFqcn = fqcn;
    myMajor = major;
    myMinor = minor;
    if (cause != null) {
      initCause(cause);
    }
  }

  @NotNull
  public String getClassName() {
    return myFqcn;
  }

  public int getMinor() {
    return myMinor;
  }

  public int getMajor() {
    return myMajor;
  }

  @NotNull
  public static UnsupportedClassVersionError wrap(@NotNull UnsupportedClassVersionError error, @NotNull String fqcn, byte[] data) {
    if (!isValidClassFile(data)) {
      return error;
    }
    int minor = getMinorVersion(data);
    int major = getMajorVersion(data);
    return new InconvertibleClassError(error, fqcn, major, minor);
  }
}
