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

import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testGuiFramework.launcher.GuiTestOptions;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * A test that fails when the aspects-agent log has violations not in the baseline.
 * The log is written during UI tests, so this should run after them.
 * <p>
 * The baseline for grandfathered violations is in tools/adt/idea/android-uitests/aspects_baseline.txt
 */
@RunIn(TestGroup.UNRELIABLE)
public class AspectsAgentLogTest {

  private static final Logger LOGGER = Logger.getInstance(AspectsAgentLogTest.class);

  @Test
  public void checkForViolations() throws IOException {
    File aspectsLog = AspectsAgentLogUtil.getAspectsAgentLog();
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

  @Test
  public void filterBaselineMethods() throws IOException {
    File activeStackTraces = AspectsAgentLogUtil.getAspectsActiveStackTracesLog();
    if (activeStackTraces == null) {
      LOGGER.info("The aspects agent active stacktraces were not checked.");
      return;
    }
    // Create a set with the stacktraces that were hit in the current run of UI tests.
    Set<String> generatedBaseline = new HashSet<>(Files.readAllLines(activeStackTraces.toPath()));

    // Create a set with the stacktraces currently listed in the baseline.
    Set<String> currentBaseline = new HashSet<>(Files.readAllLines(Paths.get(GuiTestOptions.INSTANCE.getAspectsAgentBaseline())));

    // Check whether we can remove some of the stacktraces from the baseline.
    currentBaseline.removeAll(generatedBaseline);
    if (!currentBaseline.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      sb.append("The following stack traces can probably be removed from the aspects agent baseline:\n");
      currentBaseline.forEach((stacktrace -> {
        sb.append(stacktrace);
        sb.append("\n");
      }));
      LOGGER.warn(sb.toString());
    }
    else {
      LOGGER.info("Baseline is up to date.");
    }
  }
}
