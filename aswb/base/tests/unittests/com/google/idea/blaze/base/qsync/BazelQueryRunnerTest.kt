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
package com.google.idea.blaze.base.qsync

import com.google.common.collect.ImmutableSet
import com.google.idea.blaze.base.bazel.BazelExitCodeException
import com.google.idea.blaze.base.bazel.BuildSystem
import com.google.idea.blaze.base.bazel.BuildSystem.BuildEventStreamConsumer
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker.Capability
import com.google.idea.blaze.base.bazel.FakeBuildSystem
import com.google.idea.blaze.base.command.BlazeCommand
import com.google.idea.blaze.base.command.info.BlazeInfo
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.settings.BuildBinaryType
import com.google.idea.blaze.base.settings.BuildSystemName
import com.google.idea.blaze.qsync.query.QuerySpec
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import java.io.InputStream
import java.nio.file.Path
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(JUnit4::class)
class BazelQueryRunnerTest {

  @get:Rule val tempDir = TemporaryFolder()

  @Test
  fun runQuery_invokerThrowsBazelExitCodeException_propagatesException() {
    val project = mock(Project::class.java)
    `when`(project.basePath).thenReturn(tempDir.root.absolutePath)

    lateinit var buildSystem: BuildSystem
    val invoker =
      object : BuildInvoker {
        override val capabilities: Set<Capability> = emptySet()
        override val type: BuildBinaryType = BuildBinaryType.BAZEL
        override val invokeCommand: List<String> = listOf("bazel")
        override val canOverrideBinaryPath: Boolean = false
        override val buildSystem: BuildSystem
          get() = buildSystem

        override fun <T : Any> invoke(
          blazeCommandBuilder: BlazeCommand.Builder,
          blazeContext: BlazeContext,
          consumer: BuildEventStreamConsumer<T>,
        ): T = error("unused")

        override fun invokeAsProcessHandler(
          blazeCommandBuilder: BlazeCommand.Builder,
          blazeContext: BlazeContext,
          consumer: BuildEventStreamConsumer<Unit>,
        ): ProcessHandler = error("unused")

        override fun invokeQuery(
          blazeCommandBuilder: BlazeCommand.Builder,
          blazeContext: BlazeContext,
        ): InputStream {
          throw BazelExitCodeException("query failed", 2)
        }

        override fun invokeInfo(
          blazeCommandBuilder: BlazeCommand.Builder,
          blazeContext: BlazeContext,
        ): InputStream = error("unused")

        override fun getBlazeInfo(blazeContext: BlazeContext): BlazeInfo = error("unused")
      }

    buildSystem = FakeBuildSystem.builder(BuildSystemName.Bazel).setBuildInvoker(invoker).build()

    val queryRunner = BazelQueryRunner(project, buildSystem)
    val querySpec =
      QuerySpec.builder(QuerySpec.QueryStrategy.PLAIN).includePath(Path.of("pkg")).supportedRuleClasses(ImmutableSet.of()).build()
    val context = BlazeContext.create()

    assertThrows(BazelExitCodeException::class.java) {
      queryRunner.runQuery(querySpec, context)
    }
  }
}
