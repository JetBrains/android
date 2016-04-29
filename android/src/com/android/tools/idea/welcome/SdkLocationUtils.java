/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.repository.io.FileOp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class SdkLocationUtils {
  private SdkLocationUtils() {
  }

  /**
   * Returns true if the SDK wizards will write into the SDK location. If the location exists and is a directory, returns true if the
   * directory is writable. If the location doesn't exist, returns true if one of the parent directories is writable.
   */
  public static boolean isWritable(@NotNull FileOp fileOp, @Nullable File sdkLocation) {
    if (sdkLocation == null) {
      return false;
    }
    else if (fileOp.exists(sdkLocation)) {
      return fileOp.isDirectory(sdkLocation) && fileOp.canWrite(sdkLocation);
    }
    else {
      File parent = getFirstExistentParent(fileOp, sdkLocation);
      return parent != null && fileOp.canWrite(parent);
    }
  }

  @Nullable
  private static File getFirstExistentParent(@NotNull FileOp fileOp, @NotNull File file) {
    for (File parent = file.getParentFile(); parent != null; parent = parent.getParentFile()) {
      if (fileOp.exists(parent)) {
        return parent;
      }
    }

    return null;
  }
}
