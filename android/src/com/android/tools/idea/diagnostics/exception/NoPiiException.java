/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.exception;

import com.android.tools.idea.diagnostics.crash.CrashReport;
import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;

/**
 * This class should be used with caution. Any exceptions reported by this class will return the full exception message back
 * to studio crash analytics. This is useful for diagnosing issues however care should be taken to not report any PII back
 * in the exception text.
 */
public class NoPiiException extends Exception {

  @NotNull
  private final Throwable myRootException;

  public NoPiiException(@NotNull Throwable t) {
    super(t);
    myRootException = CrashReport.getRootCause(t);
  }

  @Override
  public Throwable getCause() {
    // The exception reporter works based on getting the root cause error.
    // If we return the root cause in this case we end up not reporting the exception properly.
    // The message will get elided because the description is based off the root message.
    return null;
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    myRootException.printStackTrace(s);
  }

  @Override
  public String toString() {
    return myRootException.toString();
  }

  @Override
  public StackTraceElement[] getStackTrace() {
    return myRootException.getStackTrace();
  }
}
