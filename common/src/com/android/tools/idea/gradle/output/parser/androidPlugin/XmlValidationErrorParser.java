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

import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.blame.parser.ParsingFailedException;
import com.android.ide.common.blame.parser.PatternAwareOutputParser;
import com.android.ide.common.blame.parser.util.OutputLineReader;
import com.android.ide.common.blame.parser.util.ParserUtil;
import com.android.utils.ILogger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for errors that occur during XML validation of various resource source files. They are of the form:
 * <p/>
 * <pre>
 * [Fatal Error] :LINE:COL: MESSAGE
 * Failed to parse PATHNAME
 * </pre>
 * <p/>
 * The second line with the pathname may not appear (which means we can't tell the user what file the error occurred in. Bummer.)
 */
public class XmlValidationErrorParser implements PatternAwareOutputParser {
  private static final Pattern FATAL_ERROR = Pattern.compile("\\[Fatal Error\\] :(\\d+):(\\d+): (.+)");
  private static final Pattern FILE_REFERENCE = Pattern.compile("Failed to parse (.+)");

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<Message> messages, @NotNull ILogger logger)
    throws ParsingFailedException {
    Matcher m1 = FATAL_ERROR.matcher(line);
    if (!m1.matches()) {
      // Sometimes the parse failure message appears by itself (for example with duplicate resources);
      // in this case also recognize the line by itself even though it's separated from the next message
      Matcher m2 = FILE_REFERENCE.matcher(line);
      if (m2.matches()) {
        File sourceFile = new File(m2.group(1));
        if (sourceFile.exists()) {
          String message = line;
          // Eat the entire stacktrace
          String exceptionMessage = ParserUtil.digestStackTrace(reader);
          if (exceptionMessage != null) {
            message = exceptionMessage + ": " + message;
          }
          messages.add(new Message(Message.Kind.ERROR, message, new SourceFilePosition(sourceFile, SourcePosition.UNKNOWN)));
          return true;
        }
      }
      return false;
    }
    String message = m1.group(3);
    int lineNumber = Integer.parseInt(m1.group(1));
    int column = Integer.parseInt(m1.group(2));
    SourceFile sourceFile = SourceFile.UNKNOWN;
    String nextLine = reader.peek(0);
    if (nextLine == null) {
      return false;
    }
    Matcher m2 = FILE_REFERENCE.matcher(nextLine);
    if (m2.matches()) {
      reader.readLine(); // digest peeked line
      File possibleSourceFile = new File(m2.group(1));
      if (possibleSourceFile.exists()) {
        sourceFile = new SourceFile(possibleSourceFile);
      }
    }
    messages.add(new Message(Message.Kind.ERROR, message, new SourceFilePosition(sourceFile, new SourcePosition(lineNumber - 1, column - 1, -1))));
    return true;
  }

}
