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
package com.google.idea.blaze.base.scope.scopes;

import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.google.idea.blaze.common.PrintOutput;
import com.intellij.openapi.diagnostic.Logger;

/** Scope that captures relevant output to the IntelliJ log file. */
public class IdeaLogScope implements BlazeScope {

  private static final Logger logger = Logger.getInstance(IdeaLogScope.class);

  private static final OutputSink<IssueOutput> issueSink =
      (output) -> {
        logger.warn(output.toString());
        return OutputSink.Propagation.Continue;
      };

  private static final OutputSink<PrintOutput> printSink =
      (output) -> {
        switch (output.getOutputType()) {
          case NORMAL:
            break;
          case LOGGED:
            logger.info(output.getText());
            break;
          case ERROR:
            logger.warn(output.getText());
            break;
        }
        return OutputSink.Propagation.Continue;
      };

  private static final OutputSink<StatusOutput> statusSink =
      (output) -> {
        logger.info(output.getStatus());
        return OutputSink.Propagation.Continue;
      };

  @Override
  public void onScopeBegin(BlazeContext context) {
    context.addOutputSink(IssueOutput.class, issueSink);
    context.addOutputSink(PrintOutput.class, printSink);
    context.addOutputSink(StatusOutput.class, statusSink);
  }

  public static void info(String line) {
    logger.info(line);
  }
}
