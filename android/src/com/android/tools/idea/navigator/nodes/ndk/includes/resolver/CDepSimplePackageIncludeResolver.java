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
import java.util.regex.Pattern;

/**
 * Resolve a CDep simple package component.
 */
public class CDepSimplePackageIncludeResolver extends IncludeResolver {
  @NotNull private final static Pattern PATTERN = Pattern.compile("^(.*/.*?/exploded)(/(.*?)/(.*?)/(.*?)/.*?\\.zip(/.*))$");

  @Override
  @Nullable
  public SimpleIncludeValue resolve(@NotNull File includeFolder) {
    Matcher match = LexicalIncludePaths.matchFolderToRegex(PATTERN, includeFolder);
    if (match.find()) {
      String homeFolder = match.group(1);
      String relativeFolderName = match.group(2);
      String simplePackageName = match.group(4);
      return new SimpleIncludeValue(PackageType.CDepPackage, simplePackageName, relativeFolderName,
                                    includeFolder, new File(homeFolder));
    }
    return null;
  }
}
