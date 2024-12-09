/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.resources.ResourceType
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl.Companion.wellKnownOrCreate
import com.android.tools.idea.gradle.project.sync.snapshots.PreparedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.SyncedProjectTestDef
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.projectsystem.AndroidProjectRootUtil
import com.android.tools.idea.projectsystem.gradle.GradleSourceSetProjectPath
import com.android.tools.idea.projectsystem.gradle.resolveIn
import com.android.tools.idea.res.TransitiveAarRClass
import com.android.tools.idea.res.resourceNamespace
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.PathUtil
import org.jetbrains.android.augment.ResourceLightField
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import java.io.File

data class HighlightProjectTestDef(
  override val testProject: TestProject,
  val checkHighlighting: (JavaCodeInsightTestFixture) -> Unit = { fixture -> fixture.checkHighlighting() },
  override val agpVersion: AgpVersionSoftwareEnvironmentDescriptor = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
  val modulesAndFiles: Map<GradleSourceSetProjectPath, List<String>>
) : SyncedProjectTestDef {
  override val name: String = testProject.projectName

  override fun toString(): String = testProject.projectName

  override fun withAgpVersion(agpVersion: AgpVersionSoftwareEnvironmentDescriptor): SyncedProjectTestDef {
    return copy(agpVersion = agpVersion)
  }

  override fun isCompatible(): Boolean {
    return agpVersion == AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
  }

  override fun PreparedTestProject.Context.runTest(root: File, project: Project, expect: Expect) {
    modulesAndFiles.forEach { (gradleProjectPath, filePaths) ->
      val module = (gradleProjectPath.copy(buildRoot = PathUtil.toSystemIndependentName(root.resolve(root).path)).resolveIn(project)
        ?: error("Module $gradleProjectPath not found"))
      selectModule(module)
      val moduleRoot =
        AndroidProjectRootUtil.getModuleDirPath(module) ?: error("Cannot find the Gradle root of the selected module: $gradleProjectPath")
      fixture.allowTreeAccessForAllFiles()
      filePaths.map { File(moduleRoot).resolve(it).path }.forEach { path ->
        fixture.configureFromExistingVirtualFile(VfsUtil.findFileByIoFile(File(path), false)!!)
        val h = fixture.doHighlighting()
        checkHighlighting.invoke(fixture)
      }
    }
  }

  companion object {
    val tests: List<HighlightProjectTestDef> = listOf(
      HighlightProjectTestDef(
        TestProject.SIMPLE_APPLICATION,
        modulesAndFiles = mapOf(
          GradleSourceSetProjectPath(
            "/",
            ":app",
            IdeModuleWellKnownSourceSet.MAIN
          ) to listOf("src/main/java/google/simpleapplication/MyActivity.java")
        )
      ),
      HighlightProjectTestDef(
        TestProject.CUSTOM_NAMESPACE,
        modulesAndFiles = mapOf(
          GradleSourceSetProjectPath(
            "/",
            ":app",
            IdeModuleWellKnownSourceSet.MAIN
          ) to listOf("src/main/java/google/simpleapplication/MyActivity.java")
        )
      ),
      HighlightProjectTestDef(
        TestProject.NON_TRANSITIVE_R_CLASS_SYMBOL,
        checkHighlighting = ::validateNonTransitiveRClass,
        modulesAndFiles = mapOf(
          GradleSourceSetProjectPath(
            "/",
            ":app",
            IdeModuleWellKnownSourceSet.ANDROID_TEST
          ) to listOf("src/androidTest/java/com/example/app/ExampleInstrumentedTest.kt")
        )
      ),
      HighlightProjectTestDef(
        TestProject.NON_TRANSITIVE_R_CLASS_SYMBOL_TRUE,
        checkHighlighting = ::validateNonTransitiveRClassTrue,
        modulesAndFiles = mapOf(
          GradleSourceSetProjectPath(
            "/",
            ":app",
            IdeModuleWellKnownSourceSet.ANDROID_TEST
          ) to listOf("src/androidTest/java/com/example/app/ExampleInstrumentedTest.kt")
        )
      ),
      HighlightProjectTestDef(
        TestProject.ANDROID_KOTLIN_MULTIPLATFORM,
        modulesAndFiles = mapOf(
          GradleSourceSetProjectPath(
            "/",
            ":kmpFirstLib",
            wellKnownOrCreate("androidInstrumentedTest")
          ) to listOf("src/androidInstrumentedTest/kotlin/com/example/kmpfirstlib/test/KmpAndroidFirstLibActivityTest.kt")
        )
      ),
      HighlightProjectTestDef(
        TestProject.ANDROID_KOTLIN_MULTIPLATFORM,
        checkHighlighting = ::validateRClassResolutionInKmpAndroid,
        modulesAndFiles = mapOf(
          GradleSourceSetProjectPath(
            "/",
            ":kmpFirstLib",
            wellKnownOrCreate("androidInstrumentedTest")
          ) to listOf("src/androidInstrumentedTest/kotlin/com/example/kmpfirstlib/test/KmpAndroidFirstLibActivityTest.kt")
        )
      ),
    )

    private fun validateNonTransitiveRClass(fixture: JavaCodeInsightTestFixture) {
      val unresolvedReferenceWarnings =
        fixture.doHighlighting(HighlightSeverity.WARNING).map { it.description }.filter { it.startsWith("[UNRESOLVED_REFERENCE]") }
      val expectedWarnings = if (KotlinPluginModeProvider.isK2Mode()) {
        "[UNRESOLVED_REFERENCE] Unresolved reference 'R'."
      } else {
        "[UNRESOLVED_REFERENCE] Unresolved reference: R"
      }
      assertThat(unresolvedReferenceWarnings).containsExactly(expectedWarnings)
    }

    private fun validateNonTransitiveRClassTrue(fixture: JavaCodeInsightTestFixture) {
      val unresolvedReferenceWarnings =
        fixture.doHighlighting(HighlightSeverity.WARNING).map { it.description }.filter { it.startsWith("[UNRESOLVED_REFERENCE]") }
      val expectedWarnings = if (KotlinPluginModeProvider.isK2Mode()) {
        arrayOf(
          "[UNRESOLVED_REFERENCE] Unresolved reference 'R'.",
          "[UNRESOLVED_REFERENCE] Unresolved reference 'view_in_lib'."
        )
      } else {
        arrayOf(
          "[UNRESOLVED_REFERENCE] Unresolved reference: R",
          "[UNRESOLVED_REFERENCE] Unresolved reference: view_in_lib"
        )
      }
      assertThat(unresolvedReferenceWarnings).containsExactly(*expectedWarnings)
    }

    private fun validateRClassResolutionInKmpAndroid(fixture: JavaCodeInsightTestFixture) {
      fixture.moveCaret("fun testJavaResources() {|")
      fixture.type("\n    androidx.appcompat.R.")
      fixture.completeBasic()
      assertThat(fixture.lookupElementStrings).containsAllOf(
        "drawable", "layout"
      )
      fixture.editor.executeAndSave { insertText("drawable.tooltip_frame_light") }
      fixture.moveCaret("tooltip_|frame_light")
      val elementAtCaret = fixture.elementAtCaret
      assertThat(elementAtCaret).isInstanceOf(ResourceLightField::class.java)
      assertThat((elementAtCaret as ResourceLightField).resourceType).isEqualTo(ResourceType.DRAWABLE)
      assertThat(elementAtCaret.parent.parent).isInstanceOf(TransitiveAarRClass::class.java)
      assertThat(
        (elementAtCaret.parent.parent as TransitiveAarRClass).packageName
      ).isEqualTo("androidx.appcompat")
    }
  }
}