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
package com.google.idea.blaze.base.qsync

import com.google.common.io.ByteSource
import com.google.idea.blaze.base.bazel.BuildSystem
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.deps.OutputGroup
import java.io.IOException
import java.nio.file.Path
import org.jetbrains.annotations.VisibleForTesting

/**
 * A workaround to expose these methods to tests that do not instantiate the builder directly.
 *
 * Our test framework wraps instances and the interface allows it to delegate these methods to the original implementation.
 */
interface BazelDependencyBuilderPublicForTests {
  @VisibleForTesting
  fun getInvocationInfo(
    context: BlazeContext,
    buildTargets: Set<Label>,
    buildInvokerCapabilities: Set<BuildSystem.BuildInvoker.Capability>,
    outputGroups: Collection<OutputGroup>
  ): BuildDependenciesBazelInvocationInfo

  @VisibleForTesting
  fun getBundledAspectPath(filename: String): Path

  @VisibleForTesting
  @Throws(IOException::class, BuildException::class)
  fun prepareInvocationFiles(
    context: BlazeContext, invocationFiles: Map<Path, ByteSource>
  )
}
