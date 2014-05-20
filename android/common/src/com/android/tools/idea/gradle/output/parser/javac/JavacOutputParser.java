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
package com.android.tools.idea.gradle.output.parser.javac;

import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.output.parser.PatternAwareOutputParser;
import com.android.tools.idea.gradle.output.parser.OutputLineReader;
import com.android.tools.idea.gradle.output.parser.ParsingFailedException;
import com.google.common.collect.Lists;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * Parses javac's output.
 */
public class JavacOutputParser implements PatternAwareOutputParser {
  private static final char COLON = ':';

  private static final String WARNING_PREFIX = "warning:"; // default value

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages)
    throws ParsingFailedException {
    int colonIndex1 = line.indexOf(COLON);
    if (colonIndex1 == 1) { // drive letter (Windows)
      colonIndex1 = line.indexOf(COLON, colonIndex1 + 1);
    }

    if (colonIndex1 >= 0) { // looks like found something like a file path.
      String part1 = line.substring(0, colonIndex1).trim();
      if (part1.equalsIgnoreCase("error") /* jikes */ || part1.equalsIgnoreCase("Caused by")) {
        // +1 so we don't include the colon
        String text = line.substring(colonIndex1 + 1).trim();
        addMessage(new GradleMessage(GradleMessage.Kind.ERROR, text), messages);
        return true;
      }
      if (part1.equalsIgnoreCase("warning")) {
        // +1 so we don't include the colon
        String text = line.substring(colonIndex1 + 1).trim();
        addMessage(new GradleMessage(GradleMessage.Kind.WARNING, text), messages);
        return true;
      }
      if (part1.equalsIgnoreCase("javac")) {
        addMessage(new GradleMessage(GradleMessage.Kind.ERROR, line), messages);
        return true;
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
          String text = line.substring(colonIndex2 + 1).trim();
          GradleMessage.Kind kind = GradleMessage.Kind.ERROR;

          if (text.startsWith(WARNING_PREFIX)) {
            text = text.substring(WARNING_PREFIX.length()).trim();
            kind = GradleMessage.Kind.WARNING;
          }

          List<String> messageList = Lists.newArrayList();
          messageList.add(text);
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

              while (isMessageEnd(messageEnd)) {
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
              }
              GradleMessage msg = new GradleMessage(kind, buf.toString(), file.getAbsolutePath(), lineNumber, column + 1);
              addMessage(msg, messages);
            } finally {
              StringBuilderSpinAllocator.dispose(buf);
            }
            return true;
          }

        } catch (NumberFormatException ignored) {
        }
      }
    }

    if (line.endsWith("java.lang.OutOfMemoryError")) {
      addMessage(new GradleMessage(GradleMessage.Kind.ERROR, "Out of memory."), messages);
      return true;
    }

    return false;
  }

  private static void addMessage(@NotNull GradleMessage message, @NotNull List<GradleMessage> messages) {
    boolean duplicatesPrevious = false;
    int messageCount = messages.size();
    if (messageCount > 0) {
      GradleMessage lastMessage = messages.get(messageCount - 1);
      duplicatesPrevious = lastMessage.equals(message);
    }
    if (!duplicatesPrevious) {
      messages.add(message);
    }
  }

  @Contract("null -> false")
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
