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
package com.android.tools.rendering.log;

import com.android.tools.rendering.api.RenderModelModuleLoggingId;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods used to anonymize information going to the local logs
 */
public class LogAnonymizerUtil {
  private static final String SALT = Integer.toString((new SecureRandom()).nextInt());

  /**
   * Returns a hash for the given module. The hash value will be consistent only for the current session. Once the IDE is shutdown,
   * the hash for the module will change.
   */
  @NotNull
  public static String anonymize(@Nullable RenderModelModuleLoggingId module) {
    if (module == null) {
      return "null";
    }

    if (module.isDisposed()) {
      return "<disposed>";
    }

    return "module:" + Hashing.sha256().hashString(SALT + module.getName(), StandardCharsets.UTF_8).toString();
  }
}
