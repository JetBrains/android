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
package com.android.tools.idea.jps.output.parser.javac;

import com.android.tools.idea.jps.AndroidGradleJps;
import com.android.tools.idea.jps.output.parser.CompilerOutputParser;
import com.android.tools.idea.jps.output.parser.OutputLineReader;
import com.android.tools.idea.jps.output.parser.ParsingFailedException;
import com.google.common.collect.Lists;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * Parses javac's output.
 */
public class JavacOutputParser implements CompilerOutputParser {
  private static final char COLON = ':';

  private static final String WARNING_PREFIX = "warning:"; // default value

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull Collection<CompilerMessage> messages)
    throws ParsingFailedException {
    int colonIndex1 = line.indexOf(COLON);
    if (colonIndex1 == 1) { // drive letter (Windows)
      colonIndex1 = line.indexOf(COLON, colonIndex1 + 1);
    }

    if (colonIndex1 >= 0) { // looks like found something like a file path.
      String part1 = line.substring(0, colonIndex1).trim();
      if (part1.equalsIgnoreCase("error") /* jikes */ || part1.equalsIgnoreCase("Caused by")) {
        messages.add(createErrorMessage(line.substring(colonIndex1)));
        return true;
      }
      if (part1.equalsIgnoreCase("warning")) {
        messages.add(AndroidGradleJps.createCompilerMessage(BuildMessage.Kind.WARNING, line.substring(colonIndex1)));
        return true;
      }
      if (part1.equalsIgnoreCase("javac")) {
        messages.add(createErrorMessage(line));
      }

      int colonIndex2 = line.indexOf(COLON, colonIndex1 + 1);
      if (colonIndex2 >= 0) {
        File file = new File(part1);
        if (!file.isFile()) {
          // the part one is not a file path.
          return false;
        }
        try {
          int lineNumber = Integer.parseInt(line.substring(colonIndex1 + 1, colonIndex2).trim());
          String message = line.substring(colonIndex2 + 1).trim();
          BuildMessage.Kind kind = BuildMessage.Kind.ERROR;
          if (message.startsWith(WARNING_PREFIX)) {
            message = message.substring(WARNING_PREFIX.length()).trim();
            kind = BuildMessage.Kind.WARNING;
          }

          List<String> messageList = Lists.newArrayList();
          messageList.add(message);
          int column;
          String prevLine = null;
          do {
            String nextLine = reader.readLine();
            if (nextLine == null) {
              return false;
            }
            if (nextLine.trim().equals("^")) {
              column = nextLine.indexOf('^');
              String messageEnd = reader.readLine();
              while(isMessageEnd(messageEnd)) {
                messageList.add(messageEnd.trim());
                messageEnd = reader.readLine();
              }
              if (messageEnd != null) {
                reader.pushBack(messageEnd);
              }
              break;
            }
            if (prevLine != null) {
              messageList.add(prevLine);
            }
            prevLine = nextLine;
          } while (true);

          if (column >= 0) {
            messageList = convertMessages(messageList);
            StringBuilder buf = StringBuilderSpinAllocator.alloc();
            try {
              for (String m : messageList) {
                if (buf.length() > 0) {
                  buf.append(SystemProperties.getLineSeparator()) ;
                }
                buf.append(m);
                CompilerMessage compilerMessage =
                  AndroidGradleJps.createCompilerMessage(kind, buf.toString(), file.getAbsolutePath(), lineNumber, column + 1);
                messages.add(compilerMessage);
              }
            } finally {
              StringBuilderSpinAllocator.dispose(buf);
            }
            return true;
          }

        } catch (NumberFormatException ignored) {
        }
      }
    }
    if(line.endsWith("java.lang.OutOfMemoryError")) {
      messages.add(createErrorMessage("Out of memory."));
    }
    return true;
  }

  @NotNull
  private static CompilerMessage createErrorMessage(@NotNull String text) {
    return AndroidGradleJps.createCompilerMessage(BuildMessage.Kind.ERROR, text);
  }

  private static boolean isMessageEnd(@Nullable String line) {
    return line != null && line.length() > 0 && Character.isWhitespace(line.charAt(0));
  }

  @NotNull
  private static List<String> convertMessages(@NotNull List<String> messages) {
    if(messages.size() <= 1) {
      return messages;
    }
    final String line0 = messages.get(0);
    final String line1 = messages.get(1);
    final int colonIndex = line1.indexOf(':');
    if (colonIndex > 0){
      @NonNls String part1 = line1.substring(0, colonIndex).trim();
      // jikes
      if ("symbol".equals(part1)){
        String symbol = line1.substring(colonIndex + 1).trim();
        messages.remove(1);
        if(messages.size() >= 2) {
          messages.remove(1);
        }
        messages.set(0, line0 + " " + symbol);
      }
    }
    return messages;
  }
}
