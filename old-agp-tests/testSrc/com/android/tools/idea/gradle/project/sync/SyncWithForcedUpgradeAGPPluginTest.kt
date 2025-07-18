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
package com.android.tools.idea.gradle.project.sync

import com.android.SdkConstants
import com.android.ide.common.repository.AgpVersion
import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.RefactoringProcessorInstantiator
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.applicableAgpVersions
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.replaceService
import org.jetbrains.annotations.Contract
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OldAgpTest
@RunWith(Parameterized::class)
class SyncWithForcedUpgradeAGPPluginTest(private val environmentDescriptor: AgpVersionSoftwareEnvironmentDescriptor) {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  companion object {
    @Suppress("unused")
    @Contract(pure = true)
    @JvmStatic
    @Parameterized.Parameters(name="{0}")
    fun testParameters(): Collection<*> {
      fun AgpVersionSoftwareEnvironmentDescriptor.isExpectForcedUpgradeVersion() = when (val v = this.agpVersion) {
        null -> false
        else -> AgpVersion.parse(v).let { it >= AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_FORCED_UPGRADE_VERSION) &&
                                          it < AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION) }
      }
      return applicableAgpVersions().filter { it.isExpectForcedUpgradeVersion() }.reversed().map { arrayOf(it) }
    }
  }

  @Test
  fun testGradleSyncSucceeds() {
    val instantiator = spy(RefactoringProcessorInstantiator())
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, agpVersion = environmentDescriptor)
    preparedProject.open(
      updateOptions = {
        it.copy(
          onProjectCreated = {
            this.replaceService(RefactoringProcessorInstantiator::class.java, instantiator, projectRule.testRootDisposable)
            doReturn(false).whenever(instantiator).showAndGetAgpUpgradeDialog(any(), any(), any())          }
        )
      }
    ) { p ->
      val processorCaptor = argumentCaptor<AgpUpgradeRefactoringProcessor>()
      verify(instantiator).showAndGetAgpUpgradeDialog(processorCaptor.capture())
      assertThat(processorCaptor.firstValue.project).isEqualTo(p)
      assertThat(processorCaptor.firstValue.current).isEqualTo(AgpVersion.parse(environmentDescriptor.agpVersion!!))
      // if this fails, make sure that GRADLE_PLUGIN_MINIMUM_VERSION is represented in the offline data file for
      // IdeGoogleMavenRepository in tools/base/sdk-common/src/main/resources/versions-offline/com/android/tools/build/group-index.xml
      AgpVersion.parse(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION).let { min ->
        assertThat(processorCaptor.firstValue.new.major).named(">%s<.%s.%s", min.major, min.minor, min.micro).isEqualTo(min.major)
        assertThat(processorCaptor.firstValue.new.minor).named("%s.>%s<.%s", min.major, min.minor, min.micro).isEqualTo(min.minor)
        assertThat(processorCaptor.firstValue.new.micro).named("%s.%s.>%s<", min.major, min.minor, min.micro).isAtLeast(min.micro)
      }
    }
  }
}