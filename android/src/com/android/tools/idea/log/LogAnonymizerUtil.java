/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.log;

import com.android.tools.idea.rendering.RenderModelModule;
import com.google.common.hash.Hashing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_SUPPORT_ARTIFACT_PREFIX;

/**
 * Utility methods used to anonymize information going to the local logs
 */
public class LogAnonymizerUtil {
  private static final String SALT = Integer.toString((new SecureRandom()).nextInt());

  /**
   * Returns whether a given class name belong to a namespace that does not need to be anonymized
   */
  public static boolean isPublicClass(@Nullable String className) {
    if (className == null) {
      return false;
    }

    className = className.replace("/", ".");
    return className.startsWith("java.") || className.startsWith("javax.") ||
           className.startsWith(ANDROID_SUPPORT_ARTIFACT_PREFIX) ||
           className.startsWith(ANDROID_PKG_PREFIX) ||
           className.startsWith("com.google.");
  }

  /**
   * Returns a hash for the given class name. The hash value will be consistent only for the current session. Once the IDE is shutdown,
   * the hash for the class name will change.
   */
  @NotNull
  public static String anonymizeClassName(@Nullable String className) {
    if (className == null) {
      return "null";
    }

    className = className.replace("/", ".");
    return isPublicClass(className) ?
           className :
           "class:" + Hashing.sha256().hashString(SALT + className, StandardCharsets.UTF_8).toString();
  }

  /**
   * Returns a hash for the given module. The hash value will be consistent only for the current session. Once the IDE is shutdown,
   * the hash for the module will change.
   */
  @NotNull
  public static String anonymize(@Nullable RenderModelModule module) {
    if (module == null) {
      return "null";
    }

    if (module.isDisposed()) {
      return "<disposed>";
    }

    return "module:" + Hashing.sha256().hashString(SALT + module.getName(), StandardCharsets.UTF_8).toString();
  }
}
