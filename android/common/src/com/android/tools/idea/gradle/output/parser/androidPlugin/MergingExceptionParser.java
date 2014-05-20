package com.android.tools.idea.gradle.output.parser.androidPlugin;

import com.android.tools.idea.gradle.output.GradleMessage;
import com.android.tools.idea.gradle.output.parser.PatternAwareOutputParser;
import com.android.tools.idea.gradle.output.parser.OutputLineReader;
import com.android.tools.idea.gradle.output.parser.ParsingFailedException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * A parser for errors that happen during resource merging, usually via
 * a {@link com.android.ide.common.res2.MergingException}
 * <p>
 * The error will be in one of these formats:
 * <pre>
 * path: Error: message
 * path:line: Error: message
 * path:line:column: Error: message
 * </pre>
 */
public class MergingExceptionParser implements PatternAwareOutputParser {
  @Override
  public boolean parse(@NotNull String line, @NotNull OutputLineReader reader, @NotNull List<GradleMessage> messages)
    throws ParsingFailedException {
    int messageIndex = line.indexOf(": Error: ");
    if (messageIndex == -1) {
      return false;
    }

    // TODO: This doesn't handle ambiguous scenarios where the error message itself contains ": " or the path contains " : ".
    // I could disambiguate this by checking file existence on the path component containing a ":" !

    // See if it's preceded by a line number and/or a column
    String path;
    int lineNumber = -1;
    int column = -1;
    int colon = line.lastIndexOf(':', messageIndex - 1);
    if (colon != -1) {
      // Is there a column?
      int colon2 = line.lastIndexOf(':', colon - 1);
      if (colon2 != -1) {
        // Both line number and column
        //lineNumber =
        String columnString = line.substring(colon + 1, messageIndex);
        String lineString = line.substring(colon2 + 1, colon);
        try {
          column = Integer.parseInt(columnString);
          lineNumber = Integer.parseInt(lineString);
        } catch (NumberFormatException e) {
          // Could it be a Windows path with drive letters (and no line number) ?
          if (colon2 == 1) {
            String p = line.substring(0, colon);
            if (new File(p).exists()) {
              colon2 = colon;
            } else {
              return false;
            }
          } else {
            return false;
          }
        }
        path = line.substring(0, colon2);
      } else {
        // Just one number: it's the line
        try {
          lineNumber = Integer.parseInt(line.substring(colon + 1, messageIndex));
        } catch (NumberFormatException e) {
          // Could it be a Windows path with drive letters (and no line number) ?
          if (colon == 1) {
            String p = line.substring(0, messageIndex);
            if (new File(p).exists()) {
              colon = messageIndex;
            } else {
              return false;
            }
          } else {
            return false;
          }
        }
        path = line.substring(0, colon);
      }
    } else {
      path = line.substring(0, messageIndex );
    }

    String message = line.substring(messageIndex + 2);
    messages.add(new GradleMessage(GradleMessage.Kind.ERROR, message, path, lineNumber, column));
    return true;
  }
}
