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
package com.android.tools.idea.gradle.structure.configurables.issues;

import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsPath;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.android.tools.idea.gradle.structure.model.PsPath.TexType.HTML;

public class IssuesRenderer {
  @NotNull
  public String render(@NotNull Collection<PsIssue> issues) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<html><body><ol>");

    for (PsIssue issue : issues) {
      buffer.append("<li>")
            .append(issue.getPath().toText(HTML)).append(": ").append(issue.getText());
      PsPath quickFixPath = issue.getQuickFixPath();
      if (quickFixPath != null) {
        buffer.append(" ").append(quickFixPath.toText(HTML));
      }
      buffer.append("</li>");
    }

    buffer.append("</ul></body></html>");
    return buffer.toString();
  }
}
