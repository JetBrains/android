/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.runner

import com.android.tools.idea.run.classes.BuildOutcome
import com.google.idea.blaze.base.bazel.BuildSystem
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs

/**
 * An entity that knows how to instrument Bazel build and how to collect data required for Live Edit from the build results.
 */
interface LiveEditDataExtractor {
  fun prepareInvocation(
    context: BlazeContext,
    buildInvoker: BuildSystem.BuildInvoker,
    commandBuilder: BlazeCommand.Builder,
  )

  fun blockingExtract(context: BlazeContext, buildOutputs: BlazeBuildOutputs)

  fun getBuildOutcomeBlocking(): BuildOutcome
}