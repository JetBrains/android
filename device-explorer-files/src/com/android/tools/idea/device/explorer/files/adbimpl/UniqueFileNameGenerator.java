/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.adbimpl;

import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class UniqueFileNameGenerator {
  @NotNull
  private static UniqueFileNameGenerator myInstance = new UniqueFileNameGenerator();
  @Nullable
  private static UniqueFileNameGenerator myInstanceOverride = null;

  @NotNull
  public static UniqueFileNameGenerator getInstance() {
    if (myInstanceOverride != null) {
      return myInstanceOverride;
    }
    return myInstance;
  }

  @TestOnly
  public static void setInstanceOverride(@Nullable UniqueFileNameGenerator generator) {
    myInstanceOverride = generator;
  }

  /**
   * Returns a statistically unique file name of the form "{@code prefix unique-id suffix}" (without spaces)
   */
  @NotNull
  public String getUniqueFileName(@NotNull String prefix, @NotNull String suffix) {
    // Note: UUID.randomUUID() uses SecureRandom with 128 bits entropy, which
    //       is more than good enough for our requirements.
    return String.format("%s%s%s", prefix, UUID.randomUUID().toString(), suffix);
  }
}
