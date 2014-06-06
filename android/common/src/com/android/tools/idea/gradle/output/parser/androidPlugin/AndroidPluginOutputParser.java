/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.idea.gradle.output.GradleProjectAwareMessage;
import com.android.tools.idea.gradle.output.parser.OutputLineReader;
import com.android.tools.idea.gradle.output.parser.ParsingFailedException;
import com.android.tools.idea.gradle.output.parser.PatternAwareOutputParser;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AndroidPluginOutputParser implements PatternAwareOutputParser {
  private static final int SEGMENT_COUNT = 3;

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages)
    throws ParsingFailedException {
    // pattern is type|path|message
    String[] segments = line.split("\\|", SEGMENT_COUNT);
    if (segments.length == SEGMENT_COUNT) {
      GradleMessage.Kind kind = GradleMessage.Kind.findIgnoringCase(segments[0]);
      if (kind == null) {
        kind = GradleMessage.Kind.ERROR;
      }
      String path = segments[1];
      if (StringUtil.isEmpty(path)) {
        return false;
      }
      String msg = StringUtil.notNullize(segments[2]);
      messages.add(new GradleProjectAwareMessage(kind, msg.trim(), path.trim()));

      return true;
    }
    return false;
  }
}
