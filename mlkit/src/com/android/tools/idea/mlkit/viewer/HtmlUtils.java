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
package com.android.tools.idea.mlkit.viewer;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class to do conversion between plain text and html.
 */
public final class HtmlUtils {
  private static final List<String> CHARACTERS_PLAIN = Arrays.asList("<", ">", "&", "'", "\"", "\r\n", "\n");
  private static final List<String> CHARACTERS_HTML = Arrays.asList("&lt;", "&gt;", "&amp;", "&#39;", "&quot;", "<br>", "<br>");

  /**
   * Converts the given plain text to HTML format.
   *
   * <p>It will escape HTML entities and line breaker, and also linkify all urls. The returned result will be wrapped by &lt;html&gt; tag.
   */
  @NotNull
  public static String plainTextToHtml(@NotNull String text) {
    text = StringUtil.replace(text, CHARACTERS_PLAIN, CHARACTERS_HTML);
    StringBuffer sb = new StringBuffer("<html><body>");
    Matcher matcher = URLUtil.URL_PATTERN.matcher(text);
    while (matcher.find()) {
      if (matcher.groupCount() > 0) {
        String url = matcher.group(0);
        matcher.appendReplacement(sb, "<a href='" + url + "'>" + url + "</a>");
      }
    }
    matcher.appendTail(sb);
    sb.append("</body></html>");
    return sb.toString();
  }
}
