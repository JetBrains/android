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
package com.android.tools.idea.gradle.output.parser;

import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.output.parser.aapt.AaptOutputParser;
import com.android.tools.idea.gradle.output.parser.aapt.AbstractAaptOutputParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for Gradle's final error message on failed builds. It is of the form:
 * <p/>
 * <pre>
 * FAILURE: Build failed with an exception.
 *
 * * What went wrong:
 * Execution failed for task 'TASK_PATH'.
 *
 * * Where:
 * Build file 'PATHNAME' line: LINE_NUM
 * > ERROR_MESSAGE
 *
 * * Try:
 * Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.
 *
 * BUILD FAILED
 * </pre>
 * <p/>
 * The Where section may not appear (it usually only shows up if there's a problem in the build.gradle file itself). We parse this
 * out to get the failure message and module, and the where output if it appears.
 */
class BuildFailureParser implements PatternAwareOutputParser {
  private static final Pattern[] BEGINNING_PATTERNS =
    {Pattern.compile("^FAILURE: Build failed with an exception."), Pattern.compile("^\\* What went wrong:")};

  private static final Pattern WHERE_LINE_1 = Pattern.compile("^\\* Where:");
  private static final Pattern WHERE_LINE_2 = Pattern.compile("^Build file '(.+)' line: (\\d+)");

  private static final Pattern[] ENDING_PATTERNS = {Pattern.compile("^\\* Try:"),
    Pattern.compile("^Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.")};

  // If there's a failure executing a command-line tool, Gradle will output the complete command line of the tool and will embed the
  // output from that tool. We catch the command line, pull out the tool being invoked, and then take the output and run it through a
  // sub-parser to generate parsed error messages.
  private static final Pattern COMMAND_FAILURE_MESSAGE = Pattern.compile("^> Failed to run command:");
  private static final Pattern COMMAND_LINE_PARSER = Pattern.compile("^\\s+/([^/ ]+/)+([^/ ]+) (.*)");
  private static final Pattern COMMAND_LINE_ERROR_OUTPUT = Pattern.compile("^  Output:$");

  private enum State {
    BEGINNING,
    WHERE,
    MESSAGE,
    COMMAND_FAILURE_COMMAND_LINE,
    COMMAND_FAILURE_OUTPUT,
    ENDING
  }

  private AaptOutputParser myAaptParser = new AaptOutputParser();

  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages)
    throws ParsingFailedException {
    State state = State.BEGINNING;
    int pos = 0;
    String currentLine = line;
    String file = null;
    int lineNum = -1;
    int column = -1;
    String lastQuotedLine = null;
    StringBuilder errorMessage = new StringBuilder();
    Matcher matcher;
    // TODO: If the output isn't quite matching this format (for example, the "Try" statement is missing) this will eat
    // some of the output. We should fall back to emitting all the output in that case.
    while (true) {
      switch (state) {
        case BEGINNING:
          if (WHERE_LINE_1.matcher(currentLine).matches()) {
            state = State.WHERE;
          }
          else if (!BEGINNING_PATTERNS[pos].matcher(currentLine).matches()) {
            return false;
          }
          else if (++pos >= BEGINNING_PATTERNS.length) {
            state = State.MESSAGE;
          }
          break;
        case WHERE:
          matcher = WHERE_LINE_2.matcher(currentLine);
          if (!matcher.matches()) {
            return false;
          }
          file = matcher.group(1);
          lineNum = Integer.parseInt(matcher.group(2));
          column = 0;
          state = State.BEGINNING;
          break;
        case MESSAGE:
          if (ENDING_PATTERNS[0].matcher(currentLine).matches()) {
            state = State.ENDING;
            pos = 1;
          }
          else if (COMMAND_FAILURE_MESSAGE.matcher(currentLine).matches()) {
            state = State.COMMAND_FAILURE_COMMAND_LINE;
          }
          else {
            // Determine whether the string starts with ">" (possibly indented by whitespace), and if so, where
            int quoted = -1;
            for (int i = 0, n = currentLine.length(); i < n; i++) {
              char c = currentLine.charAt(i);
              if (c == '>') {
                quoted = i;
                break;
              }
              else if (!Character.isWhitespace(c)) {
                break;
              }
            }
            if (quoted != -1) {
              if (currentLine.startsWith("> In DataSet ", quoted) && currentLine.contains("no data file for changedFile")) {
                matcher = Pattern.compile("\\s*> In DataSet '.+', no data file for changedFile '(.+)'").matcher(currentLine);
                if (matcher.find()) {
                  file = matcher.group(1);
                }
              }
              else if (currentLine.startsWith("> Duplicate resources: ", quoted)) {
                // For exact format, see com.android.ide.common.res2.DuplicateDataException
                matcher = Pattern.compile("\\s*> Duplicate resources: (.+):(.+), (.+):(.+)\\s*").matcher(currentLine);
                if (matcher.matches()) {
                  file = matcher.group(1);
                  lineNum = AbstractAaptOutputParser.findResourceLine(new File(file), matcher.group(2));
                  messages.add(new GradleMessage(GradleMessage.Kind.ERROR, currentLine, file, lineNum, -1));
                  String other = matcher.group(3);
                  int otherLine = AbstractAaptOutputParser.findResourceLine(new File(other), matcher.group(4));
                  messages.add(new GradleMessage(GradleMessage.Kind.ERROR, "Other duplicate occurrence here", other, otherLine, -1));
                  // Skip appending to the errorMessage buffer; we've already manually added this line and a line pointing to
                  // the second occurrence as separate errors
                  break;
                }
              }
              else if (currentLine.startsWith("> Problems pinging owner of lock ", quoted)) {
                String text = "Possibly unstable network connection: Failed to connect to lock owner. Try to rebuild.";
                messages.add(new GradleMessage(GradleMessage.Kind.ERROR, text, null, -1, -1));
              }
            }
            if (errorMessage.length() > 0) {
              errorMessage.append("\n");
            }
            if (isGradleQuotedLine(currentLine)) {
              lastQuotedLine = currentLine;
            }
            errorMessage.append(currentLine);
          }
          break;
        case COMMAND_FAILURE_COMMAND_LINE:
          // Gradle can put an unescaped "Android Studio" in its command-line output. (It doesn't care because this doesn't have to be
          // a perfectly valid command line; it's just an error message). To keep it from messing up our parsing, let's convert those
          // to "Android_Studio". If there are other spaces in the command-line path, though, it will mess up our parsing. Oh, well.
          currentLine = currentLine.replaceAll("Android Studio", "Android_Studio");
          matcher = COMMAND_LINE_PARSER.matcher(currentLine);
          if (matcher.matches()) {
            String message = String.format("Error while executing %s command", matcher.group(2));
            messages.add(new GradleMessage(GradleMessage.Kind.ERROR, message));
          }
          else if (COMMAND_LINE_ERROR_OUTPUT.matcher(currentLine).matches()) {
            state = State.COMMAND_FAILURE_OUTPUT;
          }
          else if (ENDING_PATTERNS[0].matcher(currentLine).matches()) {
            state = State.ENDING;
            pos = 1;
          }
          break;
        case COMMAND_FAILURE_OUTPUT:
          if (ENDING_PATTERNS[0].matcher(currentLine).matches()) {
            state = State.ENDING;
            pos = 1;
          }
          else {
            currentLine = currentLine.trim();
            if (!myAaptParser.parse(currentLine, reader, messages)) {
              // The AAPT parser punted on it. Just create a message with the unparsed error.
              messages.add(new GradleMessage(GradleMessage.Kind.ERROR, currentLine));
            }
          }
          break;
        case ENDING:
          if (!ENDING_PATTERNS[pos].matcher(currentLine).matches()) {
            return false;
          }
          else if (++pos >= ENDING_PATTERNS.length) {
            if (errorMessage.length() > 0) {
              String text = errorMessage.toString();

              // Sometimes Gradle exits with an error message that doesn't have an associated
              // file. This will show up first in the output, for errors without file associations.
              // However, in some cases we can guess what the error is by looking at the other error
              // messages, for example from the XML Validation parser, where the same error message is
              // provided along with an error message. See for example the parser unit test for
              // duplicate resources.
              if (file == null && lastQuotedLine != null) {
                String msg = unquoteGradleLine(lastQuotedLine);
                GradleMessage rootCause = findRootCause(msg, messages);

                if (rootCause == null) {
                  // For AAPT execution errors, the real cause is the last line (the AAPT output).
                  // Try searching there instead.
                  if (msg.endsWith("Failed to run command:")) {
                    String[] lines = text.split("\n");
                    if (lines.length > 2 && lines[lines.length - 2].contains("Output:")) {
                      String lastLine = lines[lines.length - 1];
                      if (!lastLine.isEmpty()) {
                        rootCause = findRootCause(lastLine.trim(), messages);
                      }
                    }
                  }
                }

                if (rootCause != null) {
                  file = rootCause.getSourcePath();
                  lineNum = rootCause.getLineNumber();
                  column = rootCause.getColumn();
                }
              }
              if (file != null) {
                messages
                  .add(new GradleMessage(GradleMessage.Kind.ERROR, text, file, lineNum, column));
              }
              else {
                messages.add(new GradleMessage(GradleMessage.Kind.ERROR, text));
              }
            }
            return true;
          }
          break;
      }
      while (true) {
        currentLine = reader.readLine();
        if (currentLine == null) {
          return false;
        }
        if (!currentLine.trim().isEmpty()) {
          break;
        }
      }
    }
  }

  /**
   * Looks through the existing errors and attempts to find one that has the same root cause
   */
  @Nullable
  private static GradleMessage findRootCause(@NotNull String text, @NotNull Collection<GradleMessage> messages) {
    for (GradleMessage message : messages) {
      if (message.getKind() != GradleMessage.Kind.INFO && message.getText().contains(text)) {
        String sourcePath = message.getSourcePath();
        if (sourcePath != null) {
          return message;
        }
      }
    }

    // We sometimes strip out the exception name prefix in the error messages;
    // e.g. the gradle output may be "> java.io.IOException: My error message" whereas
    // the XML validation error message was "My error message", so look for these
    // scenarios too
    int index = text.indexOf(':');
    if (index != -1 && index < text.length() - 1) {
      return findRootCause(text.substring(index + 1).trim(), messages);
    }

    return null;
  }

  private static boolean isGradleQuotedLine(@NotNull String line) {
    for (int i = 0, n = line.length() - 1; i < n; i++) {
      char c = line.charAt(i);
      if (c == '>') {
        return line.charAt(i + 1) == ' ';
      }
      else if (c != ' ') {
        break;
      }
    }

    return false;
  }

  private static String unquoteGradleLine(@NotNull String line) {
    assert isGradleQuotedLine(line);
    return line.substring(line.indexOf('>') + 2);
  }
}
