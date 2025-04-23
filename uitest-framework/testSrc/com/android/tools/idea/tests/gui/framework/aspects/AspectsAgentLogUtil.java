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

import com.android.test.testutils.TestUtils;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Manages the logs used by the aspects agent:
 * <ol>
 *   <li>Agent log: logs the non-ignored stack traces hit when running the UI tests.</li>
 *   <li>Active stack traces: logs the stack traces hit when running the UI tests. Useful to identify stack traces traces that can be
 *       removed from the baseline.
 *   </li>
 * </ol>
 *   1)
 *   2)
 */
public class AspectsAgentLogUtil {

  private static final Logger LOGGER = Logger.getInstance(AspectsAgentLogUtil.class);

  @Nullable
  private static File ourAspectsAgentLog;

  @Nullable
  private static File ourAspectsActiveStackTracesLog;

  @Nullable
  public static File getAspectsAgentLog() {
    if (ourAspectsAgentLog == null) {
      try {
        ourAspectsAgentLog = createOrGetAspectsLog("aspects_agent_log.txt");
      }
      catch (IOException e) {
        LOGGER.warn("Error while creating the aspects agent output log", e);
      }
    }
    return ourAspectsAgentLog;
  }

  @Nullable
  public static File getAspectsActiveStackTracesLog() {
    if (ourAspectsActiveStackTracesLog == null) {
      try {
        ourAspectsActiveStackTracesLog = createOrGetAspectsLog("aspects_active_stack_traces_log.txt");
      }
      catch (IOException e) {
        LOGGER.warn("Error while creating the aspects log of the active stack traces", e);
      }
    }
    return ourAspectsActiveStackTracesLog;
  }

  @Nullable
  private static File createOrGetAspectsLog(@NotNull String fileName) throws IOException {
    if (TestUtils.runningFromBazel()) {
      // When running from bazel, we don't generate the aspects agent log.
      return null;
    }
    String logPath = Paths.get(GuiTests.getGuiTestRootDirPath().getAbsolutePath(), "system", "log", fileName).toString();
    File aspectsLog = new File(logPath);
    FileUtil.ensureExists(aspectsLog.getParentFile());
    boolean created = aspectsLog.createNewFile();
    if (!created) {
      LOGGER.warn("Aspects agent log already exists. Reusing the log file.");
    }
    return aspectsLog;
  }
}
