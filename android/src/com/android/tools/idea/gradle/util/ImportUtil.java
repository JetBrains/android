/*
 * Copyright (C) 2020 The Android Open Source Project
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

import org.jetbrains.annotations.NotNull;

public final class ImportUtil {
  public static final String SUPPORT_GROUP_ID = "com.android.support";
  public static final String CORE_KTX_GROUP_ID = "androidx.core";
  public static final String APPCOMPAT_ARTIFACT = "appcompat-v7";
  public static final String SUPPORT_ARTIFACT = "support-v4";
  public static final String GRIDLAYOUT_ARTIFACT = "gridlayout-v7";
  @SuppressWarnings("SpellCheckingInspection")
  public static final String MEDIA_ROUTER_ARTIFACT = "mediarouter-v7";
  public static final String IMPORT_SUMMARY_TXT = "import-summary.txt";

  @NotNull
  public static String escapeGroovyStringLiteral(@NotNull String s) {
    StringBuilder sb = new StringBuilder(s.length() + 5);
    for (int i = 0, n = s.length(); i < n; i++) {
      char c = s.charAt(i);
      if (c == '\\' || c == '\'') {
        sb.append('\\');
      }
      sb.append(c);
    }
    return sb.toString();
  }
}
