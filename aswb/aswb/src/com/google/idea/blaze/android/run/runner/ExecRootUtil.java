/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner;

import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.output.IssueOutput;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import javax.annotation.Nullable;

/** Utility for fetching execroot. */
public final class ExecRootUtil {
  /**
   * Returns the execroot of the given project.
   *
   * <p>This method tries to obtain the execroot using blaze info.
   */
  @Nullable
  public static String getExecutionRoot(BuildInvoker invoker, BlazeContext context)
      throws GetArtifactsException {
    try {
      return invoker.getBlazeInfo().getExecutionRoot().getAbsolutePath();
    } catch (SyncFailedException e) {
      IssueOutput.error("Could not obtain exec root from blaze info: " + e.getMessage())
          .submit(context);
      context.setHasError();
      return null;
    }
  }

  private ExecRootUtil() {}
}
