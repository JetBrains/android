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

import com.google.testing.junit.junit4.runner.RunNotifierWrapper;
import junit.framework.AssertionFailedError;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

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
      Path path = Path.of(System.getenv("RUNFILES_DIR")).resolve("__main__").resolve(expectedFailuresFile);
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
    String name = getTestFullName(failure.getDescription());
    failures.add(name);
    if (!expectedFailures.contains(name)) {
      super.fireTestFailure(failure);
    }
  }

  @Override
  public void fireTestFinished(Description description) {
    String name = getTestFullName(description);
    if (expectedFailures.contains(name) && !failures.contains(name)) {
      super.fireTestFailure(new Failure(description, new AssertionFailedError("Expected to fail")));
    }
    super.fireTestFinished(description);
  }

  public String getTestFullName(Description description) {
    return description.getClassName() + "." + description.getMethodName();
  }
}
