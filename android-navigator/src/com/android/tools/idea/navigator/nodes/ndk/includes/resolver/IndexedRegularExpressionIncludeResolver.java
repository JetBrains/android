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
package com.android.tools.idea.navigator.nodes.ndk.includes.resolver;

import static com.android.tools.idea.navigator.nodes.ndk.includes.utils.NdkVersionUtilsKt.getNdkVersionName;

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import java.io.File;
import java.util.regex.Matcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A resolver that matches a regular expression with certain well-known elements like library name, relative folder, and home folder.
 */
public class IndexedRegularExpressionIncludeResolver extends RegularExpressionIncludeResolver {
  @NotNull private final PackageType myKind;
  @NotNull private final String myPattern;
  @Nullable private final String myLibraryName;

  IndexedRegularExpressionIncludeResolver(
    @NotNull PackageType kind,
    @NotNull String pattern,
    @Nullable String libraryName) {
    this.myKind = kind;
    this.myPattern = pattern;
    this.myLibraryName = libraryName;
  }

  @Override
  @NotNull
  String getMatchRegexTemplate() {
    return myPattern;
  }

  private String groupOrNull(Matcher match, String name) {
    if (myPattern.contains(String.format("?<%s>", name))) {
      return match.group(name);
    }
    return null;
  }

  @Override
  @Nullable
  public SimpleIncludeValue resolve(@NotNull File includeFolder) {
    Matcher match = LexicalIncludePaths.matchFolderToRegex(getCompiledMatchPattern(), includeFolder);
    if (!match.find()) {
      return null;
    }
    try {
      String relativeFolder = match.group("relative");
      String libraryName = myLibraryName;
      if (libraryName == null) {
        libraryName = match.group("library");
      }
      String homeFolder = match.group("home");
      String description = myKind.myDescription;
      String version = groupOrNull(match, "ndk");
      if (version != null) {
          description += getNdkVersionName(version);
      }
      return new SimpleIncludeValue(myKind, description, libraryName, relativeFolder, includeFolder, new File(homeFolder));
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(String.format("Pattern %s is missing a group name", myPattern), e);
    }
  }
}
