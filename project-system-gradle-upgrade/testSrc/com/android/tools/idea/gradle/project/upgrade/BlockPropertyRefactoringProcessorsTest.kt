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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Test

@RunsInEdt
abstract class AbstractBlockPropertyUnlessNoOpProcessorTestBase: UpgradeGradleFileModelTestCase() {
  override val projectRule = AndroidProjectRule.onDisk()

  abstract val removedVersion: AgpVersion
  abstract val propertyKey: String
  abstract val defaultWhenRemoved: Boolean
  abstract fun createProcessor(currentVersion: AgpVersion, newVersion: AgpVersion): AbstractBlockPropertyUnlessNoOpProcessor

  val olderPreRemoveVersion: AgpVersion
    get() = AgpVersion(removedVersion.major - 1, 2, 0)
  val afterRemoveVersion: AgpVersion
    get() = AgpVersion(removedVersion.major, removedVersion.minor + 1, 0)
  val preRemoveVersion: AgpVersion
    get() = AgpVersion(removedVersion.major - 1, 1000, 0)

  @Test
  fun `Non blocked if property is not present`() {
    val processor = createProcessor(preRemoveVersion, removedVersion)
    assertThat(processor.isBlocked).isFalse()
  }

  @Test
  fun `Non blocked and property removed if it is default`() {
    val processor = createProcessor(preRemoveVersion, removedVersion)
    projectRule.fixture.addFileToProject("gradle.properties", "$propertyKey=$defaultWhenRemoved")
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).contains("$propertyKey=$defaultWhenRemoved")
    assertThat(processor.isBlocked).isFalse()
    processor.run()
    assertThat(VfsUtilCore.loadText(project.findGradleProperties()!!.also { it.refresh(false, false) })).doesNotContain(propertyKey)
  }

  @Test
  fun `Blocked if property present and is not default`() {
    val processor = createProcessor(preRemoveVersion, removedVersion)
    val expectedReasons = listOf("Property $propertyKey has been removed in $removedVersion.")
    projectRule.fixture.addFileToProject("gradle.properties", "$propertyKey=${!defaultWhenRemoved}")
    assertThat(processor.isBlocked).isTrue()
    val blockedReasons = processor.blockProcessorReasons().map { it.shortDescription }
    assertThat(blockedReasons).isEqualTo(expectedReasons)
  }

  @Test
  fun `Refactoring not enabled when current version is newer than removed versions`() {
    val processor = createProcessor(removedVersion, afterRemoveVersion)
    assertThat(processor.isEnabled).isFalse()
  }

  @Test
  fun `Refactoring not enabled when new version lower than removed version`() {
    val processor = createProcessor(olderPreRemoveVersion, preRemoveVersion)
    assertThat(processor.isEnabled).isFalse()
  }

  private fun Project.findGradleProperties(): VirtualFile? = guessProjectDir()?.findChild("gradle.properties")
}

@RunsInEdt
abstract class AbstractBlockPropertyWithPreviousDefaultProcessorTestBase: AbstractBlockPropertyUnlessNoOpProcessorTestBase() {
  abstract val featureName: String
  abstract val changeDefaultVersion: AgpVersion

  val preChangeDefault: AgpVersion
    get() = AgpVersion(changeDefaultVersion.major - 1, 100, 0)
  val olderPreChangeDefault: AgpVersion
    get() = AgpVersion(changeDefaultVersion.major - 1, 2, 0)

  @Test
  fun `Blocked if property is not present but upgrading from pre change in default`() {
    verifyBlockedReasons(preChangeDefault, listOf("There have been changes in how $featureName is configured."))
  }

  @Test
  fun `Refactoring not enabled when new version lower than change in default`() {
    val project = projectRule.project
    val processor = BlockAidlProcessor(project, olderPreChangeDefault, preChangeDefault)
    assertThat(processor.isEnabled).isFalse()
  }

  private fun verifyBlockedReasons(from: AgpVersion, expectedReasons: List<String>) {
    val processor = createProcessor(from, removedVersion)
    assertThat(processor.isBlocked).isTrue()
    val blockedReasons = processor.blockProcessorReasons().map { it.shortDescription }
    assertThat(blockedReasons).isEqualTo(expectedReasons)
  }
}

class BlockAidlProcessorTest: AbstractBlockPropertyWithPreviousDefaultProcessorTestBase() {
  override val removedVersion = AgpVersion.parse("9.0.0-alpha01")
  override val propertyKey = "android.defaults.buildfeatures.aidl"
  override val defaultWhenRemoved = false
  override val featureName = "AIDL"
  override val changeDefaultVersion = AgpVersion.parse("8.0.0-alpha04")

  override fun createProcessor(currentVersion: AgpVersion, newVersion: AgpVersion): AbstractBlockPropertyUnlessNoOpProcessor {
    return BlockAidlProcessor(project, currentVersion, newVersion)
  }
}

class BlockAnalysisPerComponentProcessorTest: AbstractBlockPropertyUnlessNoOpProcessorTestBase() {
  override val removedVersion: AgpVersion = AgpVersion.parse("9.0.0-alpha01")
  override val propertyKey: String = "android.experimental.lint.analysisPerComponent"
  override val defaultWhenRemoved = true

  override fun createProcessor(currentVersion: AgpVersion, newVersion: AgpVersion): AbstractBlockPropertyUnlessNoOpProcessor {
    return BlockAnalysisPerComponentProcessor(project, currentVersion, newVersion)
  }
}

class BlockEmulatorControlProcessorTest: AbstractBlockPropertyUnlessNoOpProcessorTestBase() {
  override val removedVersion: AgpVersion = AgpVersion.parse("9.0.0-alpha01")
  override val propertyKey: String = "android.experimental.androidTest.enableEmulatorControl"
  override val defaultWhenRemoved = true

  override fun createProcessor(currentVersion: AgpVersion, newVersion: AgpVersion): AbstractBlockPropertyUnlessNoOpProcessor {
    return BlockEmulatorControlProcessor(project, currentVersion, newVersion)
  }
}

class BlockMinifyLocalDependenciesLibrariesProcessorTest: AbstractBlockPropertyUnlessNoOpProcessorTestBase() {
  override val removedVersion: AgpVersion = AgpVersion.parse("10.0.0-alpha01")
  override val propertyKey: String = "android.disableMinifyLocalDependenciesForLibraries"
  override val defaultWhenRemoved = true

  override fun createProcessor(currentVersion: AgpVersion, newVersion: AgpVersion): AbstractBlockPropertyUnlessNoOpProcessor {
    return BlockMinifyLocalDependenciesLibrariesProcessor(project, currentVersion, newVersion)
  }
}

class BlockPreciseShrinkingProcessorTest: AbstractBlockPropertyUnlessNoOpProcessorTestBase() {
  override val removedVersion: AgpVersion = AgpVersion.parse("9.1.0-alpha01")
  override val propertyKey: String = "android.enableNewResourceShrinker.preciseShrinking"
  override val defaultWhenRemoved = true

  override fun createProcessor(currentVersion: AgpVersion, newVersion: AgpVersion): AbstractBlockPropertyUnlessNoOpProcessor {
    return BlockPreciseShrinkingProcessor(project, currentVersion, newVersion)
  }
}

class BlockRenderScriptProcessorTest: AbstractBlockPropertyWithPreviousDefaultProcessorTestBase() {
  override val removedVersion = AgpVersion.parse("9.0.0-alpha01")
  override val propertyKey = "android.defaults.buildfeatures.renderscript"
  override val defaultWhenRemoved = false
  override val featureName = "Render Script"
  override val changeDefaultVersion = AgpVersion.parse("8.0.0-alpha02")

  override fun createProcessor(currentVersion: AgpVersion, newVersion: AgpVersion): AbstractBlockPropertyUnlessNoOpProcessor {
    return BlockRenderScriptProcessor(project, currentVersion, newVersion)
  }
}

class BlockResourceOptimizationsProcessorTest: AbstractBlockPropertyUnlessNoOpProcessorTestBase() {
  override val removedVersion: AgpVersion = AgpVersion.parse("9.1.0-alpha01")
  override val propertyKey: String = "android.enableResourceOptimizations"
  override val defaultWhenRemoved = true

  override fun createProcessor(currentVersion: AgpVersion, newVersion: AgpVersion): AbstractBlockPropertyUnlessNoOpProcessor {
    return BlockResourceOptimizationsProcessor(project, currentVersion, newVersion)
  }
}

class BlockUnifiedTestPlatformProcessorTest: AbstractBlockPropertyUnlessNoOpProcessorTestBase() {
  override val removedVersion: AgpVersion = AgpVersion.parse("9.0.0-alpha01")
  override val propertyKey: String = "android.experimental.androidTest.useUnifiedTestPlatform"
  override val defaultWhenRemoved = true

  override fun createProcessor(currentVersion: AgpVersion, newVersion: AgpVersion): AbstractBlockPropertyUnlessNoOpProcessor {
    return BlockUnifiedTestPlatformProcessor(project, currentVersion, newVersion)
  }
}
