package com.android.tools.screensharing;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ThrowableHelper {
  /**
   * Returns the class name and a backtrace of the stack of the given throwable as a string.
   *
   * @param t the throwable to get the description for
   * @return the string containing the description
   */
  public static String describe(Throwable t) {
    StringWriter stringWriter = new StringWriter();
    try (PrintWriter writer = new PrintWriter(stringWriter)) {
      t.printStackTrace(writer);
      return stringWriter.toString();
    } catch (Throwable t2) {
      return t.toString();
    }
  }
}
