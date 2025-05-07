/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.console.BlazeConsoleLineProcessorProvider;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.run.processhandler.LineProcessingProcessAdapter;
import com.google.idea.blaze.base.run.processhandler.ScopedBlazeProcessHandler;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.IdeaLogScope;
import com.google.idea.blaze.base.scope.scopes.ProblemsViewScope;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.project.Project;

public class LocalInvokerHelper {
  private LocalInvokerHelper() {}

  public static ProcessHandler getScopedProcessHandler(
    Project project, ImmutableList<String> command, WorkspaceRoot workspaceRoot)
    throws ExecutionException {
    return new ScopedBlazeProcessHandler(
      project,
      command,
      workspaceRoot,
      new ScopedBlazeProcessHandler.ScopedProcessHandlerDelegate() {
        @Override
        public void onBlazeContextStart(BlazeContext context) {
          context
            .push(
              new ProblemsViewScope(
                project, BlazeUserSettings.getInstance().getShowProblemsViewOnRun()))
            .push(new IdeaLogScope());
        }

        @Override
        public ImmutableList<ProcessListener> createProcessListeners(BlazeContext context) {
          LineProcessingOutputStream outputStream =
            LineProcessingOutputStream.of(
              BlazeConsoleLineProcessorProvider.getAllStderrLineProcessors(context));
          return ImmutableList.of(new LineProcessingProcessAdapter(outputStream));
        }
      });
  }
}
