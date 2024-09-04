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
import com.google.idea.blaze.base.scope.output.StatusOutput;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;

/**
 * Progress indicator scope.
 *
 * <p>
 *
 * <p>Channels status outputs to the progress indicator text.
 *
 * <p>Cancels the scope if the user presses cancel on the progress indicator.
 */
public class ProgressIndicatorScope extends AbstractProgressIndicatorExBase
    implements BlazeScope, OutputSink<StatusOutput> {

  private final ProgressIndicator progressIndicator;
  private BlazeContext context;

  public ProgressIndicatorScope(ProgressIndicator progressIndicator) {
    this.progressIndicator = progressIndicator;

    if (progressIndicator instanceof ProgressIndicatorEx) {
      ((ProgressIndicatorEx) progressIndicator).addStateDelegate(this);
    }
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    this.context = context;
    context.addOutputSink(StatusOutput.class, this);
  }

  @Override
  public void cancel() {
    context.setCancelled();
  }

  @Override
  public Propagation onOutput(StatusOutput output) {
    progressIndicator.setText(output.getStatus());
    return Propagation.Continue;
  }
}
