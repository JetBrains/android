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
package com.google.idea.blaze.base.async.process;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

/** Simple adapter between stdout and context print output. */
public class PrintOutputLineProcessor implements LineProcessingOutputStream.LineProcessor {
  private final BlazeContext context;

  public PrintOutputLineProcessor(BlazeContext context) {
    this.context = context;
  }

  @Override
  public boolean processLine(@NotNull String line) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // This is essential output to troubleshoot bazel-in-bazel test.
      System.out.println(line);
    }
    context.output(PrintOutput.output(line));
    return true;
  }
}
