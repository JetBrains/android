/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.run.smrunner;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Converts from pybase/parameterized parameterized output format to plain testcase names. */
public class PyBaseParameterizedNameConverter implements PyParameterizedNameConverter {

  private static final Pattern PATTERN = Pattern.compile("^(\\w+)\\(.*\\)$");

  @Override
  @Nullable
  public String toFunctionName(String testCaseName) {
    Matcher match = PATTERN.matcher(testCaseName);
    return match.matches() ? match.group(1) : null;
  }
}
