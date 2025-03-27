/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.test;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.testing.junit.junit4.runner.RunNotifierWrapper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import junit.framework.AssertionFailedError;
import junit.framework.ComparisonCompactor;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

/**
 * A {@link RunNotifierWrapper} that supports expected failures
 * <p>
 * It reads from a file a list of tests that are expected to fail.
 * <p>
 * If a test that is expected to fail fails, it is treated as a pass. It is passes, it is treated as a fail.
 */
public class ExpectedFailuresRunNotifier extends RunNotifierWrapper {
  private static final Set<String> expectedFailures = new HashSet<>();
  private final Set<String> failures = new HashSet<>();

  static {
    String expectedFailuresFile = System.getenv("EXPECTED_FAILURES_FILE");
    if (expectedFailuresFile != null && !expectedFailuresFile.isEmpty()) {
      Path path = Path.of(System.getenv("RUNFILES_DIR")).resolve("_main").resolve(expectedFailuresFile);
      try {
        expectedFailures.addAll(Files.readAllLines(path));
      }
      catch (IOException e) {
        throw new RuntimeException("Expected failures file not found");
      }
    }
  }

  public ExpectedFailuresRunNotifier(RunNotifier delegate) {
    super(delegate);
  }

  @Override
  public void fireTestFailure(Failure failure) {
    String name = getTestName(failure.getDescription());
    failures.add(name);
    if (!expectedFailures.contains(name)) {
      Throwable exception = failure.getException();
      if (exception instanceof org.opentest4j.AssertionFailedError) {
        String message = getDetailedErrorMessage((org.opentest4j.AssertionFailedError)exception);
        super.fireTestFailure(new Failure(failure.getDescription(), new AssertionFailedError(message)));
      }
      super.fireTestFailure(failure);
    }
  }

  @Override
  public void fireTestFinished(Description description) {
    String name = getTestName(description);
    if (expectedFailures.contains(name) && !failures.contains(name)) {
      super.fireTestFailure(new Failure(description, new AssertionFailedError("Expected to fail")));
    }
    super.fireTestFinished(description);
  }

  public String getTestName(Description description) {
    int classNameStart = description.getTestClass().getPackageName().length() + 1;
    return description.getClassName().substring(classNameStart) + "." + description.getMethodName();
  }

  private String getDetailedErrorMessage(org.opentest4j.AssertionFailedError error) {
    String expected = error.isExpectedDefined() ? error.getExpected().getStringRepresentation() : "";
    String actual = error.isActualDefined() ? error.getActual().getStringRepresentation() : "";
    if (expected.equals(actual)) {
      return error.getMessage();
    }
    StringBuilder sb = new StringBuilder();
    sb.append(error.getMessage());
    sb.append("\n");
    sb.append("---- expected -------------------------------\n");
    sb.append(expected);
    sb.append("---- actual ---------------------------------\n");
    sb.append(actual);

    sb.append("---- diff -----------------------------------\n");
    try {
      File expectedFile = writeToTempFile(expected, "expected");
      File actualFile = writeToTempFile(actual, "actual");
      try {
        Process process = Runtime.getRuntime().exec("diff " + expectedFile.getPath() + " " + actualFile.getPath());
        process.waitFor();
        String diff = new String(process.getInputStream().readAllBytes());
        sb.append(diff);
      }
      finally {
        //noinspection ResultOfMethodCallIgnored
        expectedFile.delete();
        //noinspection ResultOfMethodCallIgnored
        actualFile.delete();
      }
    }
    catch (IOException | InterruptedException e) {
      ComparisonCompactor compactor = new ComparisonCompactor(30, expected, actual);
      sb.append(compactor.compact(""));
    }
    sb.append("---------------------------------------------\n");
    return sb.toString();
  }

  private static File writeToTempFile(String content, String prefix) throws IOException {
    File file = File.createTempFile(prefix, ".txt");
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(content.getBytes(UTF_8));
    }
    return file;
  }
}
