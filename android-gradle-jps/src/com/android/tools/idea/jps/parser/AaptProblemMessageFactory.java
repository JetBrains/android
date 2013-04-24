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
package com.android.tools.idea.jps.parser;

import com.android.tools.idea.jps.AndroidGradleJps;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage.Kind;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates a {@link org.jetbrains.jps.incremental.messages.CompilerMessage} from a parsed line of AAPT output.
 */
class AaptProblemMessageFactory extends ProblemMessageFactory {
  private static final Logger LOG = Logger.getInstance(GradleErrorOutputParser.class);

  /**
   * Portion of the error message which states the context in which the error occurred,
   * such as which property was being processed and what the string value was that
   * caused the error.
   * <pre>
   * error: No resource found that matches the given name (at 'text' with value '@string/foo')
   * </pre>
   */
  private static final Pattern PROPERTY_NAME_AND_VALUE = Pattern.compile("\\(at '(.+)' with value '(.*)'\\)");

  /**
   * Portion of error message which points to the second occurrence of a repeated resource
   * definition.
   * <p/>
   * Example:
   * error: Resource entry repeatedStyle1 already has bag item android:gravity.
   */
  private static final Pattern REPEATED_RESOURCE = Pattern.compile("Resource entry (.+) already has bag item (.+)\\.");

  /**
   * Suffix of error message which points to the first occurrence of a repeated resource
   * definition.
   * Example:
   * Originally defined here.
   */
  private static final String ORIGINALLY_DEFINED_HERE = "Originally defined here.";

  private static final Pattern NO_RESOURCE_FOUND = Pattern.compile("No resource found that matches the given name: attr '(.+)'\\.");

  /**
   * Portion of error message which points to a missing required attribute in a
   * resource definition.
   * <p/>
   * Example:
   * error: error: A 'name' attribute is required for <style>
   */
  private static final Pattern REQUIRED_ATTRIBUTE = Pattern.compile("A '(.+)' attribute is required for <(.+)>");

  @NotNull private final Map<String, ReadOnlyDocument> myDocumentsByPathCache = Maps.newHashMap();

  AaptProblemMessageFactory(@NotNull LineReader outputReader) {
    super(outputReader);
  }

  /**
   * Creates a new {@link org.jetbrains.jps.incremental.messages.CompilerMessage} using the given information.
   *
   * @param msgText    the text of the message.
   * @param severity   the severity of the message.
   * @param sourcePath the absolute path of the file owning the message.
   * @param lineNumber the line number where the message will be.
   * @return the created {@code CompilerMessage}. It returns {@code null} if {@code sourcePath}
   *         is specified but does not point to an existing file, or if {@code lineNumber} is not
   *         {@code null} <strong>and</strong> not a parsable integer.
   */
  @Override
  @Nullable
  CompilerMessage createMessage(@NotNull String msgText, @NotNull Kind severity, @Nullable String sourcePath, @Nullable String lineNumber) {
    File file = null;
    if (sourcePath != null) {
      file = new File(sourcePath);
      if (!file.isFile()) {
        return null;
      }
    }
    long locationLine = -1L;
    if (lineNumber != null) {
      try {
        locationLine = Integer.parseInt(lineNumber);
      }
      catch (NumberFormatException e) {
        return null;
      }
    }
    long locationColumn = -1L;
    // Attempt to determine the exact range of characters affected by this error.
    // This will look up the actual text of the file, go to the particular error line and findText for the specific string mentioned in the
    // error.
    if (file != null && locationLine != -1) {
      Position errorPosition = findMessagePositionInFile(file, msgText, locationLine);
      if (errorPosition != null) {
        locationLine = errorPosition.locationLine;
        locationColumn = errorPosition.locationColumn;
      }
    }
    return AndroidGradleJps.createCompilerMessage(sourcePath, msgText, severity, locationLine, locationColumn);
  }

  @Nullable
  private Position findMessagePositionInFile(@NotNull File file, @NotNull String msgText, long locationLine) {
    Matcher matcher = PROPERTY_NAME_AND_VALUE.matcher(msgText);
    if (matcher.find()) {
      String name = matcher.group(1);
      String value = matcher.group(2);
      if (!value.isEmpty()) {
        return findText(file, name, value, locationLine);
      }
      Position position1 = findText(file, name, "\"\"", locationLine);
      Position position2 = findText(file, name, "''", locationLine);
      if (position1 == null) {
        if (position2 == null) {
          // position at the property name instead.
          return findText(file, name, null, locationLine);
        }
        return position2;
      }
      else if (position2 == null) {
        return position1;
      }
      else if (position1.offset < position2.offset) {
        return position1;
      }
      else {
        return position2;
      }
    }

    matcher = REPEATED_RESOURCE.matcher(msgText);
    if (matcher.find()) {
      String property = matcher.group(2);
      return findText(file, property, null, locationLine);
    }

    matcher = NO_RESOURCE_FOUND.matcher(msgText);
    if (matcher.find()) {
      String property = matcher.group(1);
      return findText(file, property, null, locationLine);
    }

    matcher = REQUIRED_ATTRIBUTE.matcher(msgText);
    if (matcher.find()) {
      String elementName = matcher.group(2);
      return findText(file, '<' + elementName, null, locationLine);
    }

    if (msgText.endsWith(ORIGINALLY_DEFINED_HERE)) {
      return findLineStart(file, locationLine);
    }
    return null;
  }

  @Nullable
  private Position findText(@NotNull File file, @NotNull String first, @Nullable String second, long locationLine) {
    ReadOnlyDocument document = getDocument(file);
    if (document == null) {
      return null;
    }
    long offset = document.lineOffset(locationLine);
    if (offset == -1L) {
      return null;
    }
    long resultOffset = document.findText(first, offset);
    if (resultOffset == -1L) {
      return null;
    }
    if (second != null) {
      resultOffset = document.findText(second, resultOffset + first.length());
      if (resultOffset == -1L) {
        return null;
      }
    }
    long lineNumber = document.lineNumber(resultOffset);
    long lineOffset = document.lineOffset(lineNumber);
    return new Position(lineNumber, resultOffset - lineOffset + 1, resultOffset);
  }

  @Nullable
  private Position findLineStart(@NotNull File file, long locationLine) {
    ReadOnlyDocument document = getDocument(file);
    if (document == null) {
      return null;
    }
    long lineOffset = document.lineOffset(locationLine);
    if (lineOffset == -1L) {
      return null;
    }
    long nextLineOffset = document.lineOffset(locationLine + 1);
    if (nextLineOffset == -1) {
      nextLineOffset = document.length();
    }
    long resultOffset = -1;
    for (long i = lineOffset; i < nextLineOffset; i++) {
      char c = document.getCharAt(i);
      if (!Character.isWhitespace(c)) {
        resultOffset = i;
        break;
      }
    }
    if (resultOffset == -1L) {
      return null;
    }
    return new Position(locationLine, resultOffset - lineOffset + 1, resultOffset);
  }

  @Nullable
  private ReadOnlyDocument getDocument(@NotNull File file) {
    String filePath = file.getAbsolutePath();
    ReadOnlyDocument document = myDocumentsByPathCache.get(filePath);
    if (document == null) {
      try {
        document = new ReadOnlyDocument(file);
        myDocumentsByPathCache.put(filePath, document);
      }
      catch (IOException e) {
        String format = "Unexpected error occurred while reading file '%s'";
        LOG.warn(String.format(format, file.getAbsolutePath()), e);
        return null;
      }
    }
    return document;
  }

  private static class Position {
    final long locationLine;
    final long locationColumn;
    final long offset;

    Position(long locationLine, long locationColumn, long offset) {
      this.locationLine = locationLine;
      this.locationColumn = locationColumn;
      this.offset = offset;
    }
  }
}
