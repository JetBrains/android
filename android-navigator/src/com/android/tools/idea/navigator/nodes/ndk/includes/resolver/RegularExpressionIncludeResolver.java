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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Represents an include resolver that uses a regular expression to match
 * the given include path.
 */
abstract public class RegularExpressionIncludeResolver extends IncludeResolver {

  @Nullable
  protected Pattern myPattern;

  @NotNull
  abstract String getMatchRegexTemplate();

  @NotNull
  Pattern getCompiledMatchPattern() {
    if (myPattern == null) {
      myPattern = Pattern.compile(getMatchRegexTemplate());
    }
    return myPattern;
  }
}
