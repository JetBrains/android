/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.documentation;

import com.google.idea.blaze.base.lang.buildfile.psi.DocStringOwner;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.intellij.openapi.util.text.StringUtil;

/** Formats docstrings for use in quick doc pop-ups. */
public class DocStringFormatter {

  /** For now, present docstring almost verbatim, with a minimal indentation handling. */
  public static String formatDocString(StringLiteral docstring, DocStringOwner owner) {
    // TODO: Handle Google python docstring style specifically.
    // (see https://google.github.io/styleguide/pyguide.html#Comments)
    String raw = docstring.getStringContents();
    String[] lines = raw.split("\n");
    StringBuilder output = new StringBuilder();
    output.append("<pre>");

    int initialIndent = -1;
    for (String line : lines) {
      if (initialIndent == -1 && line.startsWith(" ")) {
        initialIndent = StringUtil.countChars(line, ' ', 0, true);
      }
      line = trimStart(line, initialIndent);
      if (!line.isEmpty()) {
        output.append(line);
      }
      output.append("<br>");
    }
    output.append("</pre>");
    return output.toString();
  }

  private static String trimStart(String string, int trimLimit) {
    int index = 0;
    while (index < trimLimit && index < string.length() && string.charAt(index) == ' ') {
      index++;
    }
    return string.substring(index);
  }
}
