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
  private final int myLibraryNameIndex;
  private final int myRelativeFolderIndex;
  private final int myHomeFolderIndex;

  IndexedRegularExpressionIncludeResolver(@NotNull PackageType kind, @NotNull String pattern) {
    myKind = kind;
    myPattern = pattern;
    myLibraryName = null;
    myLibraryNameIndex = 3;
    myRelativeFolderIndex = 2;
    myHomeFolderIndex = 1;
  }

  IndexedRegularExpressionIncludeResolver(@NotNull PackageType kind, @NotNull String pattern, @NotNull String libraryName) {
    myKind = kind;
    myPattern = pattern;
    myLibraryName = libraryName;
    myLibraryNameIndex = 0;
    myRelativeFolderIndex = 2;
    myHomeFolderIndex = 1;
  }

  @Override
  @NotNull
  String getMatchRegexTemplate() {
    return myPattern;
  }

  @Override
  @Nullable
  public SimpleIncludeValue resolve(@NotNull File includeFolder) {
    Matcher match = LexicalIncludePaths.matchFolderToRegex(getCompiledMatchPattern(), includeFolder);
    if (!match.find()) {
      return null;
    }
    String relativeFolder = match.group(myRelativeFolderIndex);
    String libraryName = myLibraryName;
    if (libraryName == null) {
      libraryName = match.group(myLibraryNameIndex);
    }
    String homeFolder = match.group(myHomeFolderIndex);
    return new SimpleIncludeValue(myKind, libraryName, relativeFolder, includeFolder, new File(homeFolder));
  }
}
