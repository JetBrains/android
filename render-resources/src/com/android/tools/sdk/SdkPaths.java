/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.sdk;

import com.android.SdkConstants;
import com.android.io.CancellableFileIo;
import com.google.common.base.Strings;
import java.io.File;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods for SDK paths.
 */
public class SdkPaths {
  private SdkPaths() {
  }

  /**
   * Indicates whether the given path belongs to a valid Android SDK.
   *
   * @param sdkPath              the given path.
   * @param includePathInMessage indicates whether the given path should be included in the result message.
   * @return the validation result.
   */
  @NotNull
  public static ValidationResult validateAndroidSdk(@Nullable Path sdkPath, boolean includePathInMessage) {
    return validatedSdkPath(sdkPath, "SDK", false, includePathInMessage);
  }

  /** @see #validateAndroidSdk(Path, boolean) */
  @Deprecated
  @NotNull
  public static ValidationResult validateAndroidSdk(@Nullable File sdkFile, boolean includePathInMessage) {
    return validateAndroidSdk(sdkFile == null ? null : sdkFile.toPath(), includePathInMessage);
  }

  @NotNull
  public static ValidationResult validatedSdkPath(@Nullable Path sdkPath,
                                                  @NotNull String sdkName,
                                                  boolean checkForWritable,
                                                  boolean includePathInMessage) {
    if (sdkPath == null) {
      return ValidationResult.error("");
    }

    String cause = null;
    if (!CancellableFileIo.isDirectory(sdkPath)) {
      cause = "does not belong to a directory.";
    }
    else if (!CancellableFileIo.isReadable(sdkPath)) {
      cause = "is not readable.";
    }
    else if (checkForWritable && !CancellableFileIo.isWritable(sdkPath)) {
      cause = "is not writable.";
    }
    if (!Strings.isNullOrEmpty(cause)) {
      String message;
      if (includePathInMessage) {
        message = String.format("The %1$s path\n'%2$s'\n%3$s", sdkName, sdkPath, cause);
      }
      else {
        message = String.format("The %1$s path %2$s", sdkName, cause);
      }
      return ValidationResult.error(message);
    }

    Path platformsDirPath = sdkPath.resolve(SdkConstants.FD_PLATFORMS);
    if (!CancellableFileIo.isDirectory(platformsDirPath)) {
      String message;
      if (includePathInMessage) {
        message = String.format("The %1$s at\n'%2$s'\ndoes not contain any platforms.", sdkName, sdkPath);
      }
      else {
        message = String.format("%1$s does not contain any platforms.", sdkName);
      }
      return ValidationResult.error(message);
    }

    return ValidationResult.SUCCESS;
  }

  public static class ValidationResult {
    @NotNull public static final ValidationResult SUCCESS = new ValidationResult(true, null);

    public final boolean success;
    @Nullable public final String message;

    @NotNull
    public static ValidationResult error(@NotNull String message) {
      return new ValidationResult(false, message);
    }

    private ValidationResult(boolean success, @Nullable String message) {
      this.success = success;
      this.message = message;
    }
  }
}
