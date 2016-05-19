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
package com.android.tools.idea.editors.gfxtrace.gapi;

import com.android.repository.Revision;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * See $GPUPATH/framework/app/run.go's VersionSpec
 */
public class Version {
  private static final Logger LOG = Logger.getInstance(Version.class);

  // "version <major>.<minor>:<build>", where the .<minor> and :<build> portions are optional.
  private static final Pattern VERSION_REGEX = Pattern.compile("version (\\d+)(?:\\.(\\d+))?(?::(.+))?(?:\\s|$)");

  public static final Version NULL_VERSION = new Version(0, 0, null);
  public static final Version VERSION_1 = new Version(1, 0, null);
  public static final Version VERSION_2 = new Version(2, 0, null);
  public static final Version VERSION_3 = new Version(3, 0, null);

  public final int major;
  public final int minor;
  public final String build;

  public Version(int major, int minor, String build) {
    this.major = major;
    this.minor = minor;
    this.build = build;
  }

  public boolean isAtLeast(Version min) {
    return major >= min.major && minor >= min.minor;
  }

  /**
   * @return whether the given SDK revision has the same major and a minor version at least as high as this version.
   */
  public boolean isCompatible(Revision rev) {
    return rev.getMajor() == major && rev.getMinor() >= minor;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder().append(major).append('.').append(minor);
    if (build != null) {
      result.append(':').append(build);
    }
    return result.toString();
  }

  @NotNull
  public static Version parse(String version) {
    Matcher m = VERSION_REGEX.matcher(version);
    if (!m.find()) {
      return NULL_VERSION;
    }

    String major = m.group(1), minor = m.group(2), build = m.group(3);
    try {
      return new Version(Integer.parseInt(major), (minor == null) ? 0 : Integer.parseInt(minor), build);
    }
    catch (NumberFormatException e) {
      LOG.warn("Malformed version number: " + version, e);
      return NULL_VERSION;
    }
  }
}
