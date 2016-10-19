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

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.ParsingFailedException;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.utils.ILogger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Pattern;

public class AndroidPluginOutputParser implements PatternAwareOutputParser {
  private static final int SEGMENT_COUNT = 3;

  // Sample: 128            android:configChanges="orientation|keyboardHidden|keyboard|screenSize"
  private static final Pattern IGNORED_MESSAGE_PATTERN = Pattern.compile("[\\d]+[\\s]+[\\w]+:[\\w]+=[\"|'].*[\"|']");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<Message> messages, @NotNull ILogger logger)
    throws ParsingFailedException {
    if (line.contains("[DEBUG] ") || line.contains("[INFO] ")) {
      // Ignore 'debug' and 'info' messages.
      return false;
    }
    if (IGNORED_MESSAGE_PATTERN.matcher(line).matches()) {
      return false;
    }

    // pattern is type|path|message
    String[] segments = line.split("\\|", SEGMENT_COUNT);
    if (segments.length == SEGMENT_COUNT) {
      Message.Kind kind = Message.Kind.findIgnoringCase(segments[0], Message.Kind.ERROR);
      String path = segments[1];
      if (StringUtil.isEmpty(path)) {
        return false;
      }
      String msg = StringUtil.notNullize(segments[2]);
      // The SourceFile description is the Gradle path of the project.
      messages.add(new Message(kind, msg.trim(), new SourceFilePosition(new SourceFile(path.trim()), SourcePosition.UNKNOWN)));

      return true;
    }
    return false;
  }
}
