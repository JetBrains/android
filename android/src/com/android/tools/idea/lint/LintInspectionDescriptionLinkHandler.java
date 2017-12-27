/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.TextFormat;
import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LintInspectionDescriptionLinkHandler extends TooltipLinkHandler {
  @Override
  public String getDescription(@NotNull final String refSuffix, @NotNull final Editor editor) {
    Issue issue = new BuiltinIssueRegistry().getIssue(refSuffix);
    if (issue != null) {
      String html = issue.getExplanation(TextFormat.HTML);

      // IntelliJ seems to treat newlines in the HTML as needing to also be converted to <br> (whereas
      // Lint includes these for HTML readability but they shouldn't add additional lines since it has
      // already added <br> as well) so strip these out
      html = html.replace("\n", "");

      List<String> urls = issue.getMoreInfo();
      if (!urls.isEmpty()) {
        StringBuilder sb = new StringBuilder(html);
        sb.append("<br><br>More info:<br>");
        for (String url : urls) {
          sb.append("<a href=\"").append(url).append("\">").append(url).append("</a><br>");
        }
        html = sb.toString();
      }

      return html;
    }

    return null;
  }
}