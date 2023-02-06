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
package com.android.tools.idea.navigator.nodes.ndk.includes.utils;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a set of include folders in order with duplicates removed.
 */
public class IncludeSet {
  @NotNull private static final String TAKE_NEXT_SENTINEL = "TAKE_NEXT_SENTINEL";
  @NotNull private static final Pattern PATTERN = Pattern.compile("://", Pattern.LITERAL);
  @NotNull private final LinkedHashSet<String> myIncludes = new LinkedHashSet<>();

  /**
   * When passed a --sysroot flag clang or gcc append "usr/include" to it. We need to find the same path.
   */
  @NotNull
  private static String getSysrootEquivalentPath(@NotNull String compilerFlag) {
    String result = compilerFlag;
    result = FilenameUtils.concat(result, "usr");
    result = FilenameUtils.concat(result, "include");
    return result;
  }

  /**
   * Given a GCC-style flag that may be an include flag, return the path to the referenced include.
   * If the flag should be taken from the next argument then return TAKE_NEXT_SENTINEL.
   */
  @Nullable
  private static String analyzeFlagPattern(@NotNull String flag, @NotNull IncludeFlags checkFlag) {
    String check = checkFlag.myFlag;
    if (flag.equals("-" + check)) {
      return TAKE_NEXT_SENTINEL;
    }
    if (flag.equals("--" + check)) {
      return TAKE_NEXT_SENTINEL;
    }
    if (flag.startsWith("-" + check + "=")) {
      return flag.substring(check.length() + 2);
    }
    if (flag.startsWith("--" + check + "=")) {
      return flag.substring(check.length() + 3);
    }
    if (flag.startsWith("-" + check)) {
      return flag.substring(check.length() + 1);
    }
    if (flag.startsWith("--" + check)) {
      return flag.substring(check.length() + 2);
    }
    return null;
  }

  /**
   * @return the list of includes in the order they were seen on the command-line.
   */
  @NotNull
  public List<File> getIncludesInOrder() {
    return ContainerUtil.map(myIncludes, File::new);
  }

  /**
   * Add a set of includes given a set of compiler flags.
   *
   * @param compilerFlags         the set of flags provided by the build system
   * @param compilerWorkingFolder the working folder of the compiler
   */
  public void addIncludesFromCompilerFlags(@NotNull Collection<String> compilerFlags, @Nullable File compilerWorkingFolder) {
    List<String> includeFolders = new ArrayList<>();
    boolean useNextFlagAsInclude = false;
    boolean appendUsrInclude = false;
    for (String compilerFlag : compilerFlags) {
      if (useNextFlagAsInclude) {
        if (appendUsrInclude) {
          compilerFlag = getSysrootEquivalentPath(compilerFlag);
        }
        // b/132348328 -- compilerFlag can be null for as-yet unknown reasons. Guard against it here but also:
        // TODO(jomof) review model creation code to figure out why settings array value may be null
        if (compilerFlag != null) {
          includeFolders.add(compilerFlag);
        }
        useNextFlagAsInclude = false;
        continue;
      }
      for (IncludeFlags test : IncludeFlags.values()) {
        String analysis = analyzeFlagPattern(compilerFlag, test);
        // Intentionally comparing instances instead of value because it is a sentinel value returned by analyzeFlagPattern
        if (Strings.areSameInstance(analysis, TAKE_NEXT_SENTINEL)) {
          useNextFlagAsInclude = true;
          appendUsrInclude = test.myAppendUsrInclude;
          break;
        }
        if (analysis != null) {
          if (test.myAppendUsrInclude) {
            analysis = getSysrootEquivalentPath(analysis);
          }
          includeFolders.add(analysis);
          break;
        }
      }
    }
    for (String include : includeFolders) {
      add(include, compilerWorkingFolder);
    }
  }

  /**
   * Add a single include to the set. Convert to full path if it is relative. Remove end separator if present.
   */
  @VisibleForTesting
  void add(@NotNull String include, @Nullable File compilerWorkingFolder) {
    String includePath = include;
    if (compilerWorkingFolder != null) {
      includePath = FilenameUtils.concat(compilerWorkingFolder.getAbsolutePath(), includePath);
    }
    includePath = FilenameUtils.normalizeNoEndSeparator(includePath, true);
    // Gradle plugin supplies paths with doubled backslashes (\\) on Windows. Then call to
    // FilenameUtils.normalizeNoEndSeparator changes most of these to single forward-slash (/)
    // However, it leaves one next to the drive letter like so: D://path/to/include
    // Get rid of that extra forward slash now.
    includePath = PATTERN.matcher(includePath).replaceAll(Matcher.quoteReplacement(":/"));
    myIncludes.add(includePath);
  }

  @SuppressWarnings("SpellCheckingInspection")
  private enum IncludeFlags {
    I_FLAG("I", false),
    ISYSTEM_FLAG("isystem", false),
    SYSROOT_FLAG("sysroot", true);
    @NotNull final String myFlag;
    final boolean myAppendUsrInclude;

    IncludeFlags(@NotNull String flag, boolean appendUsrInclude) {
      myFlag = flag;
      myAppendUsrInclude = appendUsrInclude;
    }
  }

  @NotNull
  @Override
  public String toString() {
    return myIncludes.toString();
  }

  @Override
  final public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof IncludeSet)) {
      return false;
    }
    IncludeSet that = (IncludeSet)obj;
    return Objects.equals(this.myIncludes, that.myIncludes);
  }

  @Override
  final public int hashCode() {
    return this.myIncludes.hashCode();
  }
}
