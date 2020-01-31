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
package com.android.tools.idea.tests.gui.framework;

import com.android.utils.FileUtils;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.PathManager;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.junit.rules.Timeout;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestTimedOutException;

/** A {@link Timeout} that is disabled when the idea.debug.mode property is true. */
public class DebugFriendlyTimeout extends Timeout {
  private boolean dumpThreadsOnTimeout = false;

  public DebugFriendlyTimeout(long timeout, TimeUnit timeUnit) {
    super(timeout, timeUnit);
  }

  public DebugFriendlyTimeout withThreadDumpOnTimeout() {
    dumpThreadsOnTimeout = true;
    return this;
  }

  @Override
  public Statement apply(Statement base, Description description) {
    if (Boolean.getBoolean("idea.debug.mode")) {
      return base;
    } else if (dumpThreadsOnTimeout) {
      Statement dumpThreads = new Statement() {
        @Override
        public void evaluate() throws Throwable {
          try {
            base.evaluate();
          } catch (TestTimedOutException e) {
            String fileName = description.getTestClass().getSimpleName() + "." + description.getMethodName() + "-TimeoutThreadDump";
            FileUtils.writeToFile(new File(PathManager.getLogPath(), fileName), ThreadDumper.dumpThreadsToString());
            throw e;
          }
        }
      };
      return super.apply(dumpThreads, description);
    } else {
      return super.apply(base, description);
    }
  }
}
