/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import java.util.ArrayList;
import java.util.List;

public final class ParametersListUtil {
  public static final Function<String, List<String>> COMMA_LINE_PARSER = text -> {
    ArrayList<String> result = new ArrayList<>();
    for (String token : text.split(",")) {
      String trimmedToken = token.trim();
      if (!trimmedToken.isEmpty()) {
        result.add(trimmedToken);
      }
    }
    return result;
  };
  public static final Function<List<String>, String> COMMA_LINE_JOINER = strings -> StringUtil.join(strings, ", ");
}
