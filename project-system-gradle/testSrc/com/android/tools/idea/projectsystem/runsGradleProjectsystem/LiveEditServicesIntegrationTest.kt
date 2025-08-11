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
package com.android.tools.idea.projectsystem.runsGradleProjectsystem

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider.Companion.getApplicationProjectContext
import com.android.tools.idea.projectsystem.ApplicationProjectContextProvider.RunningApplicationIdentity
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.deployment.liveedit.tokens.BuildSystemBytecodeTransformation
import com.android.tools.idea.run.deployment.liveedit.tokens.BuildSystemLiveEditServices.Companion.getBuildSystemLiveEditServices
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.Rule
import org.junit.Test


class LiveEditServicesIntegrationTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testByteCodeTransformationModules() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    val appBuildGradle = preparedProject.root.resolve("app/build.gradle")

    appBuildGradle.appendText("""

    import com.android.build.api.artifact.ScopedArtifact
    import com.android.build.api.artifact.SingleArtifact
    import com.android.build.api.variant.ScopedArtifacts.Scope

    abstract class ModifyClassesTask extends DefaultTask {
        @OutputFile
        abstract RegularFileProperty getOutputClasses()
        @InputFiles
        abstract ListProperty<RegularFile> getInputJars()
        @InputFiles
        abstract ListProperty<Directory> getInputDirectories()

        @TaskAction
        void taskAction() {
           // do nothing as this tas will not be executed
        }
    }
    androidComponents {
        onVariants(selector().all(), { variant ->
            TaskProvider<?> taskProvider = project.tasks.register(variant.getName() + "ModifyClasses", ModifyClassesTask.class)
            variant.artifacts.forScope(Scope.PROJECT).use(taskProvider)
                .toTransform(
                    ScopedArtifact.CLASSES.INSTANCE,
                    ModifyClassesTask::getInputJars,
                    ModifyClassesTask::getInputDirectories,
                    ModifyClassesTask::getOutputClasses
                )
        })
    }
    """.trimIndent())
    preparedProject.open { project ->
      val bytecodeTransformation = project.getBuildSystemTransformation()
      assertThat(bytecodeTransformation).isNotNull()
      assertThat(bytecodeTransformation!!.buildHasTransformation).isTrue()
      assertThat(bytecodeTransformation.transformationPoints).containsExactly(
        "Modified class files in Gradle project"
      ).inOrder()
    }
  }

  @Test
  fun testNoByteCodeTransformationModules() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    preparedProject.open { project ->
      val bytecodeTransformation = project.getBuildSystemTransformation()
      assertThat(bytecodeTransformation).isNotNull()
      assertThat(bytecodeTransformation.buildHasTransformation).isFalse()
      assertThat(bytecodeTransformation.transformationPoints).isEmpty()
    }
  }

  private fun Project.getBuildSystemTransformation(): BuildSystemBytecodeTransformation {
    val context = getProjectSystem()
      .getApplicationProjectContext(RunningApplicationIdentity(applicationId = "google.simpleapplication", processName = null))
    val services = context!!.getBuildSystemLiveEditServices()
    return services!!.disqualifyingBytecodeTransformation(context)!!
  }

}