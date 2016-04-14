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
package com.android.tools.idea.sdk;

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.sdklib.repository.PkgProps;
import com.google.common.io.Closeables;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles the version check for the Android SDK.
 */
public final class VersionCheck {
  private static final Logger LOG = Logger.getInstance(VersionCheck.class);

  /**
   * The minimum version of the SDK Tools that this version of Android Studio requires.
   */
  public static final Revision MIN_TOOLS_REV = new Revision(24, 0, 2, 0);

  private static final Pattern SOURCE_PROPERTY_PATTERN = Pattern.compile("^" + PkgProps.PKG_REVISION + "=(.*)$");

  private VersionCheck() {
  }

  /**
   * Indicates whether the Android SDK Tools revision is at least {@link #MIN_TOOLS_REV}
   *
   * @param sdkDir the root directory of the Android SDK.
   * @return {@code true} if the Android SDK Tools revision is at least {@link #MIN_TOOLS_REV}; {@code false} otherwise.
   */
  public static boolean isCompatibleVersion(@NotNull File sdkDir) {
    if (!sdkDir.isDirectory()) {
      return false;
    }
    return isCompatibleVersion(sdkDir.getAbsolutePath());
  }

  /**
   * Indicates whether the Android SDK Tools revision is at least {@link #MIN_TOOLS_REV}
   *
   * @param sdkPath the path of the Android SDK.
   * @return {@code true} if the Android SDK Tools revision is at least {@link #MIN_TOOLS_REV}; {@code false} otherwise.
   */
  public static boolean isCompatibleVersion(@Nullable String sdkPath) {
    if (sdkPath == null) {
      return false;
    }
    return checkVersion(sdkPath).isCompatibleVersion();
  }

  /**
   * Verifies that the Android SDK Tools revision is at least {@link #MIN_TOOLS_REV}
   *
   * @param sdkPath the path of the Android SDK.
   * @return the result of the check.
   */
  @NotNull
  public static VersionCheckResult checkVersion(@NotNull String sdkPath) {
    File toolsDir = new File(sdkPath, SdkConstants.OS_SDK_TOOLS_FOLDER);
    Revision toolsRevision = new Revision(Integer.MAX_VALUE);
    BufferedReader reader = null;
    try {
      File sourceProperties = new File(toolsDir, SdkConstants.FN_SOURCE_PROP);
      //noinspection IOResourceOpenedButNotSafelyClosed
      reader = new BufferedReader(new FileReader(sourceProperties));
      String line;
      while ((line = reader.readLine()) != null) {
        Matcher m = SOURCE_PROPERTY_PATTERN.matcher(line);
        if (m.matches()) {
          try {
            toolsRevision = Revision.parseRevision(m.group(1));
          } catch (NumberFormatException ignore) {}
          break;
        }
      }
    } catch (IOException e) {
      String msg = String.format("Failed to read file: '%1$s' for Android SDK at '%2$s'", SdkConstants.FN_SOURCE_PROP, sdkPath);
      LOG.info(msg, e);
    } finally {
      try {
        Closeables.close(reader, true /* swallowIOException */);
      } catch (IOException e) {
        // Cannot happen
      }
    }
    return new VersionCheckResult(toolsRevision);
  }

  public static class VersionCheckResult {
    @NotNull private final Revision myRevision;
    private final boolean myCompatibleVersion;

    VersionCheckResult(@NotNull Revision revision) {
      myRevision = revision;
      myCompatibleVersion = isCompatible(revision);
    }

    private static boolean isCompatible(@NotNull Revision revision) {
      if (revision.getMajor() == Integer.MAX_VALUE) {
        return false;
      }
      return revision.compareTo(MIN_TOOLS_REV, Revision.PreviewComparison.IGNORE) >= 0;
    }

    @NotNull
    public Revision getRevision() {
      return myRevision;
    }

    public boolean isCompatibleVersion() {
      return myCompatibleVersion;
    }
  }
}
