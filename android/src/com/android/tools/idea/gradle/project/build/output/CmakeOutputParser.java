/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output;

import static com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.MESSAGE_GROUP_ERROR_SUFFIX;
import static com.android.tools.idea.gradle.project.build.output.BuildOutputParserUtils.MESSAGE_GROUP_WARNING_SUFFIX;

import com.android.annotations.NonNull;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FileMessageEventImpl;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputParser;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses output from cmake.
 */
public class CmakeOutputParser implements BuildOutputParser {
  @NonNull static final String CMAKE = "CMake";
  @NonNull private static final String ERROR = "Error";
  @NonNull private static final String CMAKE_ERROR = CMAKE + " " + ERROR;

  private final Pattern cmakeErrorOrWarning = Pattern.compile("^\\s*CMake (Error|Warning).+");
  static final Pattern fileAndLineNumber = Pattern.compile("^(([A-Za-z]:)?.*):([0-9]+)? *:([0-9]+)?(.+)?");
  static final Pattern errorFileAndLineNumber =
    Pattern.compile("CMake (Error|Warning).*at (([A-Za-z]:)?[^:]+):([0-9]+)?.*(\\([^:]*\\))?:([0-9]+)?(.+)?");

  private static final int SOURCE_POSITION_OFFSET = -1;

  @Override
  public boolean parse(String line, BuildOutputInstantReader reader, Consumer<? super BuildEvent> messageConsumer) {
    if (cmakeErrorOrWarning.matcher(line).matches()) {
      List<String> messages = new ArrayList<>();
      messages.add(line.trim());
      String nextLine;
      // stop when nextLine is blank or matches the CMake prefix
      while ((nextLine = reader.readLine()) != null) {
        if (nextLine.isEmpty() || cmakeErrorOrWarning.matcher(nextLine).matches()) {
          reader.pushBack();
          break;
        }
        else {
          nextLine = nextLine.trim();
          if (!nextLine.isEmpty()) {
            messages.add(nextLine);
          }
        }
      }

      if (matchesErrorFileAndLineNumberError(messages, reader.getBuildId(), messageConsumer) ||
          matchesFileAndLineNumberError(messages, reader.getBuildId(), messageConsumer)) {
        return true;
      }

      if (messages.get(0).startsWith(CMAKE_ERROR)) {
        messageConsumer.accept(
          new MessageEventImpl(reader.getBuildId(), MessageEvent.Kind.ERROR, CMAKE + MESSAGE_GROUP_ERROR_SUFFIX, String.join(" ", messages),
                               String.join("\n", messages)));
      }
      else {
        messageConsumer.accept(
          new MessageEventImpl(reader.getBuildId(), MessageEvent.Kind.WARNING, CMAKE + MESSAGE_GROUP_WARNING_SUFFIX,
                               String.join(" ", messages),
                               String.join("\n", messages)));
      }
      return true;
    }

    return false;
  }

  /**
   * Matches the following error or warning parsing CMakeLists.txt: <code>
   * CMake Error: ... at
   * /path/to/file:1234:1234
   * [Description of the error.]
   * </code> Or the same error on a single line. If the line number and/or column number are
   * missing, it defaults to -1, and won't affect the code link. If the description is missing, it
   * will use the full line as the description.
   *
   * <p>This also matches a "CMake Warning:" with the same structure.
   */
  private static boolean matchesFileAndLineNumberError(@NonNull List<String> messages,
                                                       @NonNull Object buildId,
                                                       @NonNull Consumer<? super BuildEvent> messageConsumer) {
    String fullMessage = String.join(" ", messages);
    Matcher matcher = fileAndLineNumber.matcher(fullMessage);
    if (matcher.matches()) {
      File file = new File(matcher.group(1));

      ErrorFields fields = matchFileAndLineNumberErrorParts(matcher, fullMessage);

      if (fullMessage.contains(CMAKE_ERROR)) {
        fields.kind = MessageEvent.Kind.ERROR;
      }
      else {
        fields.kind = MessageEvent.Kind.WARNING;
      }

      FilePosition position =
        new FilePosition(file, fields.lineNumber + SOURCE_POSITION_OFFSET, fields.columnNumber + SOURCE_POSITION_OFFSET);
      messageConsumer.accept(
        new FileMessageEventImpl(buildId, fields.kind, CMAKE +
                                                       (fields.kind == MessageEvent.Kind.ERROR
                                                        ? MESSAGE_GROUP_ERROR_SUFFIX
                                                        : MESSAGE_GROUP_WARNING_SUFFIX), fields.errorMessage, String.join("\n", messages),
                                 position));
      return true;
    }

    return false;
  }

  @VisibleForTesting
  static ErrorFields matchFileAndLineNumberErrorParts(
    @NonNull Matcher matcher, @NonNull String line) {
    ErrorFields fields = new ErrorFields();
    fields.lineNumber = -1;
    if (matcher.group(3) != null) {
      fields.lineNumber = Integer.valueOf(matcher.group(3));
    }

    fields.columnNumber = -1;
    if (matcher.group(4) != null) {
      fields.columnNumber = Integer.valueOf(matcher.group(4));
    }

    fields.errorMessage = line;
    if (matcher.group(5) != null) {
      fields.errorMessage = matcher.group(5);
    }

    return fields;
  }

  /**
   * Matches the following error or warning parsing CMakeLists.txt: <code>
   * CMake Error ... at
   * /path/to/file:1234 (message):1234
   * [Description of the error.]
   * </code> Or the same error on a single line. If the line number and/or column number are
   * missing, it defaults to -1, and won't affect the code link. If the description is missing, it
   * will use the full line as the description.
   *
   * <p>This also matches a warning with the same format.
   */
  private static boolean matchesErrorFileAndLineNumberError(@NonNull List<String> messages,
                                                            @NonNull Object buildId,
                                                            @NonNull Consumer<? super BuildEvent> messageConsumer) {
    String fullMessage = String.join(" ", messages);
    Matcher matcher = errorFileAndLineNumber.matcher(fullMessage);
    if (matcher.matches()) {
      File file = new File(matcher.group(2));

      ErrorFields fields = matchErrorFileAndLineNumberErrorParts(matcher, fullMessage);
      FilePosition position =
        new FilePosition(file, fields.lineNumber + SOURCE_POSITION_OFFSET, fields.columnNumber + SOURCE_POSITION_OFFSET);
      messageConsumer.accept(
        new FileMessageEventImpl(buildId, fields.kind, CMAKE +
                                                       (fields.kind == MessageEvent.Kind.ERROR
                                                        ? MESSAGE_GROUP_ERROR_SUFFIX
                                                        : MESSAGE_GROUP_WARNING_SUFFIX), fields.errorMessage, String.join("\n", messages),
                                 position));
      return true;
    }

    return false;
  }

  @VisibleForTesting
  static ErrorFields matchErrorFileAndLineNumberErrorParts(
    @NonNull Matcher matcher, @NonNull String line) {
    ErrorFields
      fields = new ErrorFields();
    fields.kind = MessageEvent.Kind.WARNING;
    if (matcher.group(1).equals(ERROR)) {
      fields.kind = MessageEvent.Kind.ERROR;
    }

    fields.lineNumber = 0;
    if (matcher.group(4) != null) {
      fields.lineNumber = Integer.valueOf(matcher.group(4));
    }

    fields.columnNumber = 0;
    if (matcher.group(6) != null) {
      fields.columnNumber = Integer.valueOf(matcher.group(6));
    }

    fields.errorMessage = line;
    if (matcher.group(7) != null) {
      fields.errorMessage = matcher.group(7).trim();
    }

    return fields;
  }

  static class ErrorFields {
    MessageEvent.Kind kind;
    int lineNumber;
    int columnNumber;
    String errorMessage;
  }
}
