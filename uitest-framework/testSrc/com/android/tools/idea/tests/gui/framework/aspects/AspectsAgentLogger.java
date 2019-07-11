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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testGuiFramework.framework.RestartUtilsKt;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Tries to create a log in GuiTests.getGuiTestRootDirPath()/system/log, and writes the test full name to it when the test starts and when
 * it finishes.
 */
public class AspectsAgentLogger extends TestWatcher {

  private static final Logger LOGGER = Logger.getInstance(AspectsAgentLogger.class);


  @Override
  protected void starting(@NotNull Description description) {
    File aspectsAgentLog = AspectsAgentLogUtil.getAspectsAgentLog();
    if (aspectsAgentLog == null) {
      return;
    }
    if (RestartUtilsKt.isFirstRun()) {
      String testName = String.format("%s.%s", description.getClassName(), description.getMethodName());
      try {
        FileUtil.writeToFile(aspectsAgentLog, String.format("STARTED %s\n", testName), true);
      }
      catch (IOException e) {
        LOGGER.warn(String.format("Error while writing STARTED tag of test %s to aspects agent log.", testName));
      }
    }
  }

  @Override
  protected void finished(@NotNull Description description) {
    File aspectsAgentLog = AspectsAgentLogUtil.getAspectsAgentLog();
    if (aspectsAgentLog == null) {
      return;
    }
    if (RestartUtilsKt.isLastRun()) {
      String testName = String.format("%s.%s", description.getClassName(), description.getMethodName());
      try {
        FileUtil.writeToFile(aspectsAgentLog, String.format("FINISHED %s\n", testName), true);
      }
      catch (IOException e) {
        LOGGER.warn(String.format("Error while writing FINISHED tag of test %s to aspects agent log.", testName));
      }
    }
  }
}
