/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.run.debug;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.google.idea.blaze.common.Label;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Implements {@code KotlinCoroutinesLibFinder} for Bazel projects. This class searches for
 * kotlinx-coroutines-core library of version 1.3.8 or higher as IntelliJ coroutines debugging is
 * not supported for earlier versions.
 */
public class BazelKotlinxCoroutinesLibFinder implements KotlinxCoroutinesLibFinder {
  // Minimum coroutines library version (1.3.7-255) parsed to be used for comparison. Coroutines
  // debugging is not available in earlier versions of the coroutines library.
  private static final int[] MIN_LIB_VERSION = {1, 3, 7, 255};
  private static final Pattern LIB_PATTERN =
      Pattern.compile("(kotlinx-coroutines-core(-jvm)?)-(\\d[\\w.\\-]+)?\\.jar");

  @Override
  public Optional<ArtifactLocation> getKotlinxCoroutinesLib(TargetIdeInfo depInfo) {
    JavaIdeInfo javaIdeInfo = depInfo.getJavaIdeInfo();
    if (javaIdeInfo == null) {
      // The kotlinx-coroutines library jar should be in JavaIdeInfo
      return Optional.empty();
    }
    return javaIdeInfo.getJars().stream()
        .map(j -> j.getClassJar())
        .filter(classJar -> isKotlinxCoroutinesLib(classJar))
        .findFirst();
  }

  @Override
  public boolean isApplicable(Project project) {
    return Blaze.getBuildSystemName(project).equals(BuildSystemName.Bazel);
  }

  @Override
  public boolean dependsOnKotlinxCoroutines(Project project, Label label) {
    // Kotlinx coroutine debugging is not currently supported for query sync in Bazel.
    return false;
  }

  private static boolean isKotlinxCoroutinesLib(@Nullable ArtifactLocation jarPath) {
    if (jarPath != null) {
      Matcher m = LIB_PATTERN.matcher(jarPath.getRelativePath());
      if (m.find() && m.groupCount() >= 3) {
        String version = m.group(3);
        return isValidVersion(version);
      }
    }
    return false;
  }

  private static boolean isValidVersion(String libVersion) {
    ImmutableList<String> versionParts =
        ImmutableList.copyOf(Splitter.onPattern("[\\.-]").split(libVersion));

    int maxLength = Math.max(MIN_LIB_VERSION.length, versionParts.size());
    for (int i = 0; i < maxLength; i++) {
      Integer versionPart = i < versionParts.size() ? Integer.parseInt(versionParts.get(i)) : 0;
      int minVersionPart = i < MIN_LIB_VERSION.length ? MIN_LIB_VERSION[i] : 0;
      int res = versionPart.compareTo(minVersionPart);
      if (res != 0) {
        return res > 0;
      }
    }
    return false;
  }
}
