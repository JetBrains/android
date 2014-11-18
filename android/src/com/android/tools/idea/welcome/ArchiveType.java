/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;

/**
 * Different archive types welcome wizard can download and unpack.
 */
public enum ArchiveType {
  ZIP, TAR, NOT_AN_ARCHIVE;

  private static final String[] TAR_EXTENSIONS = {"tgz", "tar", "tar.gz"};
  private static final String[] ZIP_EXTENSIONS = {SdkConstants.EXT_ZIP};

  @NotNull
  public static ArchiveType fromFileName(@NotNull String fileName) {
    String lowerCaseName = fileName.toLowerCase();
    if (extensionIsOneOf(lowerCaseName, TAR_EXTENSIONS)) {
      return TAR;
    }
    else if (extensionIsOneOf(lowerCaseName, ZIP_EXTENSIONS)) {
      return ZIP;
    }
    else {
      return NOT_AN_ARCHIVE;
    }
  }

  private static boolean extensionIsOneOf(@NotNull String name, @NotNull String[] extensions) {
    for (String extension : extensions) {
      if (FileUtilRt.extensionEquals(name, extension)) {
        return true;
      }
    }
    return false;
  }
}
