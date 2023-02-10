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
package com.android.tools.idea.devicemanager;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtils;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import org.jetbrains.annotations.NotNull;

public final class Targets {
  private Targets() {
  }

  public static @NotNull String toString(@NotNull AndroidVersion version) {
    return toString(version, SystemImage.DEFAULT_TAG);
  }

  public static @NotNull String toString(@NotNull AndroidVersion version, @NotNull IdDisplay tag) {
    StringBuilder builder = new StringBuilder();

    builder.append(AndroidVersionUtils.getFullReleaseName(version, false, false));

    if (tag.equals(SystemImage.DEFAULT_TAG)) {
      return builder.toString();
    }

    builder
      .append(' ')
      .append(tag.getDisplay());

    return builder.toString();
  }
}
