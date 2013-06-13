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
package com.android.tools.idea.jps.output.parser.aapt;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.jps.AndroidGradleJps;
import com.android.tools.idea.jps.output.parser.CompilerOutputParser;
import com.android.tools.idea.jps.output.parser.OutputLineReader;
import com.android.tools.idea.jps.output.parser.ParsingFailedException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.WeakValueHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@VisibleForTesting
public abstract class AbstractAaptOutputParser implements CompilerOutputParser {
  private static final Logger LOG = Logger.getInstance(AbstractAaptOutputParser.class);

  @VisibleForTesting
  public static File ourRootDir;

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

  private static final String START_MARKER = "<!-- From: "; // Keep in sync with MergedResourceWriter#FILENAME_PREFIX
  private static final String END_MARKER = " -->";

  @NotNull private static final WeakValueHashMap<String, ReadOnlyDocument> ourDocumentsByPathCache =
    new WeakValueHashMap<String, ReadOnlyDocument>();

  @Nullable
  final Matcher getNextLineMatcher(@NotNull OutputLineReader reader, @NotNull Pattern pattern) {
    // unless we can't, because we reached the last line
    String line = reader.readLine();
    if (line == null) {
      // we expected a 2nd line, so we flag as error and we bail
      return null;
    }
    Matcher m = pattern.matcher(line);
    return m.matches() ? m : null;
  }

  @NotNull
  CompilerMessage createErrorMessage(@NotNull String text, @Nullable String sourcePath, @Nullable String lineNumberAsText)
    throws ParsingFailedException {
    return createMessage(BuildMessage.Kind.ERROR, text, sourcePath, lineNumberAsText);
  }

  @NotNull
  CompilerMessage createWarningMessage(@NotNull String text, @Nullable String sourcePath, @Nullable String lineNumberAsText)
    throws ParsingFailedException {
    return createMessage(BuildMessage.Kind.WARNING, text, sourcePath, lineNumberAsText);
  }

  @NotNull
  private CompilerMessage createMessage(@NotNull BuildMessage.Kind kind,
                                        @NotNull String text,
                                        @Nullable String sourcePath,
                                        @Nullable String lineNumberAsText) throws ParsingFailedException {
    File file = null;
    if (sourcePath != null) {
      file = new File(sourcePath);
      if (!file.isFile()) {
        throw new ParsingFailedException();
      }
    }
    int lineNumber = -1;
    if (lineNumberAsText != null) {
      try {
        lineNumber = Integer.parseInt(lineNumberAsText);
      }
      catch (NumberFormatException e) {
        throw new ParsingFailedException();
      }
    }
    int column = -1;

    if (sourcePath != null) {
      Pair<File,Integer> source = findSourcePosition(file, lineNumber, text);
      if (source != null) {
        file = source.getFirst();
        sourcePath = file.getPath();
        if (source.getSecond() != null) {
          lineNumber = source.getSecond();
        }
      }
    }

    // Attempt to determine the exact range of characters affected by this error.
    // This will look up the actual text of the file, go to the particular error line and findText for the specific string mentioned in the
    // error.
    if (file != null && lineNumber != -1) {
      Position errorPosition = findMessagePositionInFile(file, text, lineNumber);
      if (errorPosition != null) {
        lineNumber = errorPosition.myLineNumber;
        column = errorPosition.myColumn;
      }
    }
    return AndroidGradleJps.createCompilerMessage(kind, text, sourcePath, lineNumber, column);
  }

  @Nullable
  private static Position findMessagePositionInFile(@NotNull File file, @NotNull String msgText, int locationLine) {
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
      else if (position1.myOffset < position2.myOffset) {
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
  private static Position findText(@NotNull File file, @NotNull String first, @Nullable String second, int locationLine) {
    ReadOnlyDocument document = getDocument(file);
    if (document == null) {
      return null;
    }
    int offset = document.lineOffset(locationLine);
    if (offset == -1L) {
      return null;
    }
    int resultOffset = document.findText(first, offset);
    if (resultOffset == -1L) {
      return null;
    }
    if (second != null) {
      resultOffset = document.findText(second, resultOffset + first.length());
      if (resultOffset == -1L) {
        return null;
      }
    }
    int lineNumber = document.lineNumber(resultOffset);
    int lineOffset = document.lineOffset(lineNumber);
    return new Position(lineNumber, resultOffset - lineOffset + 1, resultOffset);
  }

  @Nullable
  private static Position findLineStart(@NotNull File file, int locationLine) {
    ReadOnlyDocument document = getDocument(file);
    if (document == null) {
      return null;
    }
    int lineOffset = document.lineOffset(locationLine);
    if (lineOffset == -1L) {
      return null;
    }
    int nextLineOffset = document.lineOffset(locationLine + 1);
    if (nextLineOffset == -1) {
      nextLineOffset = document.length();
    }
    int resultOffset = -1;
    for (int i = lineOffset; i < nextLineOffset; i++) {
      char c = document.charAt(i);
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
  private static ReadOnlyDocument getDocument(@NotNull File file) {
    String filePath = file.getAbsolutePath();
    ReadOnlyDocument document = ourDocumentsByPathCache.get(filePath);
    if (document == null) {
      try {
        if (!file.exists()) {
          if (ourRootDir != null && ourRootDir.isAbsolute() && !file.isAbsolute()) {
            file = new File(ourRootDir, file.getPath());
            return getDocument(file);
          }
          return null;
        }
        document = new ReadOnlyDocument(file);
        ourDocumentsByPathCache.put(filePath, document);
      }
      catch (IOException e) {
        String format = "Unexpected error occurred while reading file '%s'";
        LOG.warn(String.format(format, file.getAbsolutePath()), e);
        return null;
      }
    }
    return document;
  }

  @Nullable
  protected Pair<File,Integer> findSourcePosition(@NotNull File file, int locationLine, String message) {
    if (!file.getPath().endsWith(".xml")) {
      return null;
    }

    ReadOnlyDocument document = getDocument(file);
    if (document == null) {
      return null;
    }
    // All value files get merged together into a single values file; in that case, we need to
    // search for comment markers backwards which indicates the source file for the current file

    int searchStart;
    String fileName = file.getName();
    boolean isValueFile = fileName.equals("values.xml"); // Keep in sync with MergedResourceWriter.FN_VALUES_XML
    if (isValueFile) {
      searchStart = document.lineOffset(locationLine);
    } else {
      searchStart = document.length();
    }
    if (searchStart == -1L) {
      return null;
    }

    int start = document.findTextBackwards(START_MARKER, searchStart);
    if (start == -1) {
      return null;
    }
    start += START_MARKER.length();
    int end = document.findText(END_MARKER, start);
    if (end == -1) {
      return null;
    }
    String sourcePath = document.subsequence(start, end).toString();
    File sourceFile = new File(sourcePath);

    if (isValueFile) {
      // Look up the line number
      locationLine = -1;

      Position position = findMessagePositionInFile(sourceFile, message, 1); // Search from the beginning
      if (position != null) {
        locationLine = position.myLineNumber;
      }
    }

    return Pair.create(sourceFile, locationLine);
  }

  private static class Position {
    final int myLineNumber;
    final int myColumn;
    final int myOffset;

    Position(int lineNumber, int column, int offset) {
      myLineNumber = lineNumber;
      myColumn = column;
      myOffset = offset;
    }
  }
}
