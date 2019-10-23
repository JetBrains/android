/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.aspects;

import static org.junit.Assert.fail;

import com.android.tools.idea.tests.gui.framework.AspectsAgentLogger;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * Test to run after all GUI tests of {@link com.android.tools.idea.tests.gui.framework.TestGroup.DEFAULT} runs. While these tests log the
 * aspects agent violations to a file, this test reads the log and fail in case there are any violations. Therefore, it's very important to
 * run after all DEFAULT tests run.
 */
public class AspectsAgentLogTest {

  private static final Logger LOGGER = Logger.getInstance(AspectsAgentLogTest.class);

  @Test
  public void checkForViolations() throws IOException {
    File aspectsLog = AspectsAgentLogger.getAspectsAgentLog();
    if (aspectsLog == null) {
      LOGGER.info("The aspects agent log was not checked.");
      return;
    }

    Map<String, Set<String>> violationsPerTest = new HashMap<>();
    Set<String> currentTestViolations = null;
    String currentTest = null;

    // Read all file lines.
    try (final BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(aspectsLog), StandardCharsets.UTF_8))) {
      for (String line = reader.readLine(); line != null; line = reader.readLine()) {
        if (line.startsWith("STARTED")) {
          // Line is in the format "START <test_name>"
          currentTest = line.split("\\s+")[1].trim();
          currentTestViolations = new HashSet<>();
        }
        else if (line.startsWith("FINISHED")) {
          // Line is in the format "FINISHED <test_name>"
          String testName = line.split("\\s+")[1].trim();
          assert testName.equals(currentTest); // Sanity check we're finishing the test we started.
          if (!currentTestViolations.isEmpty()) {
            violationsPerTest.put(testName, currentTestViolations);
          }
        }
        else {
          // Lines between START and FINISHED tags are violations.
          currentTestViolations.add(line);
        }
      }
    }

    // If we find any violations, fail the test and write them to the output.
    if (!violationsPerTest.isEmpty()) {
      StringBuilder failMessage = new StringBuilder();
      violationsPerTest.forEach((test, violations) -> {
        StringBuilder testFailure = new StringBuilder();
        testFailure.append(String.format("Test %s failed with the following violations:\n", test));
        for (String violation : violations) {
          testFailure.append(violation);
          testFailure.append("\n");
        }
        failMessage.append(testFailure);
        failMessage.append("\n");
      });
      fail(failMessage.toString());
    }
    else {
      LOGGER.info("No aspects agent violations found. Congratulations!");
    }
  }
}
