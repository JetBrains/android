/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * An APK to install on a device or emulator, along with information required to install it.
 */
public final class ApkInfo {
  /** The APK file. */
  @NotNull
  private final File myFile;
  /** The manifest package name for the APK (the app ID). */
  @NotNull
  private final String myApplicationId;

  public ApkInfo(@NotNull File file, @NotNull String applicationId) {
    myFile = file;
    myApplicationId = applicationId;
  }

  @NotNull
  public File getFile() {
    return myFile;
  }

  @NotNull
  public String getApplicationId() {
    return myApplicationId;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ApkInfo)) {
      return false;
    }
    ApkInfo that = (ApkInfo) o;
    return myFile.equals(that.getFile()) && myApplicationId.equals(that.getApplicationId());
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myFile, myApplicationId);
  }
}
