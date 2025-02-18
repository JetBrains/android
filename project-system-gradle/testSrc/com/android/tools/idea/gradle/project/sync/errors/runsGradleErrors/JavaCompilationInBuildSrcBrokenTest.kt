/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.errors.runsGradleErrors

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildErrorMessage
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import org.junit.Test

class JavaCompilationInBuildSrcBrokenTest: AbstractSyncFailureIntegrationTest() {

  @Test
  fun testBrokenCompilation() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.APP_WITH_BUILDSRC)
    preparedProject.root.resolve("buildSrc/src/main/java/org/example/buildsrc/BuildScriptClass.java").let {
      it.appendText("{}{")
    }

    runSyncAndCheckGeneralFailure(
      preparedProject = preparedProject,
      verifySyncViewEvents = { _, buildEvents ->
        // Expect single MessageEvent on Sync Output
        buildEvents.filterIsInstance<MessageEvent>().let { events ->
          expect.that(events).hasSize(1)
          events.firstOrNull()?.let {
            expect.that(it.message).isEqualTo("class, interface, enum, or record expected")
            expect.that(it.group).isEqualTo("Compiler")
          }
        }
        // Make sure no additional error build issue events are generated
        expect.that(buildEvents.filterIsInstance<BuildIssueEvent>()).isEmpty()
        expect.that(buildEvents.finishEventFailures()).isEmpty()
      },
      verifyFailureReported = {
        expect.that(it.gradleSyncFailure).isEqualTo(AndroidStudioEvent.GradleSyncFailure.JAVA_COMPILATION_ERROR)
        expect.that(it.buildOutputWindowStats.buildErrorMessagesList.map { it.errorShownType })
          .containsExactly(BuildErrorMessage.ErrorType.JAVA_COMPILER)

        expect.that(it.gradleSyncStats.printPhases()).isEqualTo("""
          SUCCESS : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD/GRADLE_CONFIGURE_BUILD
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD/GRADLE_RUN_WORK
          FAILURE : SYNC_TOTAL/GRADLE_CONFIGURE_ROOT_BUILD
          FAILURE : SYNC_TOTAL
        """.trimIndent())
        Truth.assertThat(it.gradleFailureDetails.toTestString()).isEqualTo("""
          failure {
            error {
              exception: org.gradle.tooling.BuildActionFailureException
                at: [0]org.gradle.tooling.internal.consumer.connection.PhasedActionAwareConsumerConnection#run
              exception: org.gradle.internal.exceptions.LocationAwareException
                at: [0]org.gradle.initialization.exception.DefaultExceptionAnalyser#transform
              exception: org.gradle.api.tasks.TaskExecutionException
                at: [0]org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter#lambda${'$'}executeIfValid${'$'}1
              exception: org.gradle.api.internal.tasks.compile.CompilationFailedException
                at: [0]org.gradle.api.internal.tasks.compile.JdkJavaCompiler#execute
            }
          }
        """.trimIndent())
      },
    )
  }
}