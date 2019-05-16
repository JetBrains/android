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

import com.android.repository.Revision;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.LexicalIncludePaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.regex.Matcher;

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
        // Convert NDK revision like 19.2.5345600 to standard NDK release name like r19c
        Revision revision = Revision.parseRevision(version);
        if (revision.getMinor() == 0) {
          // Don't show 'a' in the NDK version. It should be r20 not r20a
          description += String.format(" r%s", revision.getMajor());
        } else {
          char minor = (char)((int)'a' + revision.getMinor());
          description += String.format(" r%s%s", revision.getMajor(), minor);
        }
      }
      return new SimpleIncludeValue(myKind, description, libraryName, relativeFolder, includeFolder, new File(homeFolder));
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(String.format("Pattern %s is missing a group name", myPattern), e);
    }
  }
}
