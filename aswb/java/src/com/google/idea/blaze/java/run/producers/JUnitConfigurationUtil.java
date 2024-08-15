/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.run.producers;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.junit.PatternConfigurationProducer;
import com.intellij.openapi.application.ApplicationManager;

/** Utility methods for junit test run configuration producers. */
public class JUnitConfigurationUtil {

  /** Delegates to {@link PatternConfigurationProducer#isMultipleElementsSelected}. */
  public static boolean isMultipleElementsSelected(ConfigurationContext context) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return context.containsMultipleSelection();
    }
    return RunConfigurationProducer.getInstance(PatternConfigurationProducer.class)
        .isMultipleElementsSelected(context);
  }
}
