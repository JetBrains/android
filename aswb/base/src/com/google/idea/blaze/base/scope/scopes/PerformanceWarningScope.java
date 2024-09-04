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

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import com.google.idea.blaze.base.scope.OutputSink;
import com.google.idea.blaze.base.scope.output.PerformanceWarning;
import com.google.idea.blaze.common.PrintOutput;
import java.util.List;

/** Shows performance warnings. */
public class PerformanceWarningScope implements BlazeScope, OutputSink<PerformanceWarning> {

  private final List<PerformanceWarning> outputs = Lists.newArrayList();

  @Override
  public void onScopeBegin(BlazeContext context) {
    context.addOutputSink(PerformanceWarning.class, this);
  }

  @Override
  public void onScopeEnd(BlazeContext context) {
    if (outputs.isEmpty()) {
      return;
    }
    context.output(new PrintOutput("\n===== PERFORMANCE WARNINGS =====\n"));
    context.output(new PrintOutput("Your IDE isn't as fast as it could be:"));
    context.output(new PrintOutput(""));
    for (PerformanceWarning output : outputs) {
      context.output(new PrintOutput(output.text));
    }
    context.output(new PrintOutput(""));
    context.output(
        new PrintOutput(
            "You can toggle these messages via Blaze > Sync > Show Performance Warnings."));
  }

  @Override
  public Propagation onOutput(PerformanceWarning output) {
    outputs.add(output);
    return Propagation.Continue;
  }
}
