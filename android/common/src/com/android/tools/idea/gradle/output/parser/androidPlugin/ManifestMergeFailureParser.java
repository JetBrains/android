/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.output.parser.androidPlugin;

import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.output.parser.OutputLineReader;
import com.android.tools.idea.gradle.output.parser.ParsingFailedException;
import com.android.tools.idea.gradle.output.parser.PatternAwareOutputParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for errors that happen during manifest merging
 * <p>
 * The error will be in one of these formats:
 * <pre>
 * [path:line] message
 * </pre>
 */
public class ManifestMergeFailureParser implements PatternAwareOutputParser {
  // Only allow : in the second position (Windows drive letter)
  // Format emitted by the old manifest merger (it will go away in the not too distant future.)
  private static final Pattern ERROR1 = Pattern.compile("\\[([^:].[^:]+):(\\d+)\\] (.+)");

  // path:line:column messageType:
  private static final Pattern ERROR2 = Pattern.compile("([^:].[^:]+):(\\d+):(\\d+) (.+):");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages)
    throws ParsingFailedException {
    Matcher m = ERROR1.matcher(line);
    if (m.matches()) {
      String sourcePath = m.group(1);
      int lineNumber;
      try {
        lineNumber = Integer.parseInt(m.group(2));
      }
      catch (NumberFormatException e) {
        throw new ParsingFailedException(e);
      }
      String message = m.group(3);
      messages.add(new GradleMessage(GradleMessage.Kind.ERROR, message, sourcePath, lineNumber, -1));
      return true;
    }
    m = ERROR2.matcher(line);
    if (m.matches()) {
      String sourcePath = removeLeadingTab(m.group(1)).trim();
      int lineNumber;
      try {
        lineNumber = Integer.parseInt(m.group(2));
      }
      catch (NumberFormatException e) {
        throw new ParsingFailedException(e);
      }
      int column;
      try {
        column = Integer.parseInt(m.group(3));
      }
      catch (NumberFormatException e) {
        throw new ParsingFailedException(e);
      }
      if (lineNumber == 0 && column == 0) {
        // When both line number and column is zero, it is just a message saying "Validation failed, exiting". No need to display it.
        String next = reader.peek(0);
        if (next != null && next.contains("Validation failed, exiting")) {
          reader.readLine();
          return true;
        }
      }
      else {
        String msg = reader.readLine();
        if (msg != null) {
          msg = removeLeadingTab(msg).trim();

          GradleMessage.Kind kind = GradleMessage.Kind.findIgnoringCase(m.group(4));
          if (kind == null) {
            kind = GradleMessage.Kind.ERROR;
          }

          messages.add(new GradleMessage(kind, msg.trim(), sourcePath, lineNumber, column));
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  private static String removeLeadingTab(@NotNull String s) {
    if (s.startsWith("\t") && s.length() > 1) {
      return s.substring(1);
    }
    return s;
  }
}