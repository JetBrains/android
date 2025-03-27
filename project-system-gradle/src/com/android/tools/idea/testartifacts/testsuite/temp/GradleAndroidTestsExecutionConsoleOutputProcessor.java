/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.testsuite.temp;

import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.execution.test.runner.events.TestEventXPPXmlView;

/**
 * This class is copied and modified from
 * {@link org.jetbrains.plugins.gradle.execution.test.runner.events.GradleTestsExecutionConsoleOutputProcessor}
 * to make it work with AndroidTestSuiteView instead of
 * {@link org.jetbrains.plugins.gradle.execution.test.runner.GradleTestsExecutionConsole}.
 * <a href="https://youtrack.jetbrains.com/issue/IDEA-368796">IDEA-368796</a>
 */
public final class GradleAndroidTestsExecutionConsoleOutputProcessor {
  private static final Logger LOG = Logger.getInstance(GradleAndroidTestsExecutionConsoleOutputProcessor.class);
  private static final String LOG_EOL = "<ijLogEol/>";
  private static final String LOG_START = "<ijLog>";
  private static final String LOG_END = "</ijLog>";

  private static final Key<StringBuilder> STRING_BUFFER_KEY = new Key<>("com.android.tools.idea.testartifacts.testsuite.jetbrains.STRING_BUFFER_KEY");
  private static final Key<AndroidTestSuiteViewAdaptor> ADAPTOR_KEY = new Key<>("com.android.tools.idea.testartifacts.testsuite.jetbrains.ADAPTOR_KEY");

  public static void onOutput(@NotNull AndroidTestSuiteView executionConsole,
                              @NotNull String text,
                              @NotNull Key<?> processOutputType) {
    var eventMessage = getEventMessage(executionConsole, text, processOutputType);
    if (eventMessage == null) return;

    try {
      var adaptor = executionConsole.getUserData(ADAPTOR_KEY);
      if (adaptor == null) {
        adaptor = executionConsole.putUserDataIfAbsent(ADAPTOR_KEY, new AndroidTestSuiteViewAdaptor());
      }
      var xml = new TestEventXPPXmlView(eventMessage);
      adaptor.processEvent(xml, executionConsole);
    }
    catch (NumberFormatException e) {
      LOG.error("Gradle test events parser error", e);
    }
  }

  private static StringBuilder getBuffer(@NotNull AndroidTestSuiteView executionConsole) {
    var buffer = executionConsole.getUserData(STRING_BUFFER_KEY);
    if (buffer != null) {
      return buffer;
    }
    return executionConsole.putUserDataIfAbsent(STRING_BUFFER_KEY, new StringBuilder());
  }

  private static @Nullable String getEventMessage(@NotNull AndroidTestSuiteView executionConsole,
                                                  @NotNull String text,
                                                  @NotNull Key<?> processOutputType) {
    String eventMessage = null;
    final StringBuilder consoleBuffer = getBuffer(executionConsole);
    String trimmedText = text.trim();
    if (StringUtil.endsWith(trimmedText, LOG_EOL)) {
      consoleBuffer.append(StringUtil.trimEnd(trimmedText, LOG_EOL));
      return null;
    }
    else {
      if (consoleBuffer.isEmpty()) {
        if (StringUtil.startsWith(trimmedText, LOG_START) && StringUtil.endsWith(trimmedText, LOG_END)) {
          eventMessage = text;
        }
        else {
          executionConsole.print(text, ConsoleViewContentType.getConsoleViewType(processOutputType));
          return null;
        }
      }
      else {
        consoleBuffer.append(text);
        if (trimmedText.isEmpty()) return null;
      }
    }

    if (eventMessage == null) {
      String bufferText = consoleBuffer.toString().trim();
      consoleBuffer.setLength(0);
      if (!StringUtil.startsWith(bufferText, LOG_START) || !StringUtil.endsWith(bufferText, LOG_END)) {
        executionConsole.print(bufferText, ConsoleViewContentType.getConsoleViewType(processOutputType));
        return null;
      }
      eventMessage = bufferText;
    }
    assert consoleBuffer.isEmpty();
    return eventMessage;
  }
}