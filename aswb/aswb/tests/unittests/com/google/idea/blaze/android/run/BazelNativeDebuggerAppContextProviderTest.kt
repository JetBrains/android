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
package com.google.idea.blaze.android.run

import com.android.sdklib.devices.Abi
import com.android.tools.idea.projectsystem.ApplicationProjectContext
import com.android.tools.ndk.run.SymbolDir
import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.BlazeTestCase
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.settings.BazelImportSettingsManager
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager
import com.google.idea.blaze.base.settings.BuildSystemName
import java.io.File
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Unit tests for [BazelNativeDebuggerAppContextProvider]. */
@RunWith(JUnit4::class)
class BazelNativeDebuggerAppContextProviderTest : BlazeTestCase() {

  override fun initTest(applicationServices: Container, projectServices: Container) {
    super.initTest(applicationServices, projectServices)
    projectServices.register(BazelImportSettingsManager::class.java, BlazeImportSettingsManager(project))
    BlazeImportSettingsManager.getInstanceForTestingOnly(project)
      .setImportSettingsForTests(Path.of("/path/to/workspace"), BuildSystemName.Bazel)
  }

  @Test
  fun testGetNativeDebuggerAppContext_withBazelContext() {
    val provider = BazelNativeDebuggerAppContextProvider()
    val workspaceRoot = WorkspaceRoot(File("/path/to/workspace"))
    val symDir = SymbolDir.WithoutSubdirectories(File("/path/to/symbols"))
    val context =
      BazelApplicationProjectContext.forDeployedApplication(
        project = project,
        applicationId = "com.example.app",
        liveEditDataExtractor = null,
        symbolDirs = listOf(symDir),
      )

    val nativeContext = provider.getNativeDebuggerAppContext(context)
    assertThat(nativeContext).isNotNull()
    assertThat(nativeContext!!.project).isSameInstanceAs(project)
    assertThat(nativeContext.applicationId).isEqualTo("com.example.app")
    assertThat(nativeContext.getSymDirs(listOf(Abi.ARM64_V8A))).containsExactly(symDir)
    assertThat(nativeContext.sourceMap).containsExactly("/proc/self/cwd", workspaceRoot.directory().path)
    assertThat(nativeContext.getExplicitModuleSymbolMap(Abi.ARM64_V8A)).isEmpty()
    assertThat(nativeContext.modulesToVerify).isEmpty()
  }

  @Test
  fun testGetNativeDebuggerAppContext_withMultipleSymbolDirs() {
    val provider = BazelNativeDebuggerAppContextProvider()
    val symDir1 = SymbolDir.WithoutSubdirectories(File("/path/to/symbols"))
    val symDir2 = SymbolDir.WithoutSubdirectories(File("/other/path/symbols"))
    val context =
      BazelApplicationProjectContext.forDeployedApplication(
        project = project,
        applicationId = "com.example.app",
        liveEditDataExtractor = null,
        symbolDirs = listOf(symDir1, symDir2),
      )

    val nativeContext = provider.getNativeDebuggerAppContext(context)
    assertThat(nativeContext).isNotNull()
    assertThat(nativeContext!!.getSymDirs(listOf(Abi.ARM64_V8A))).containsExactly(symDir1, symDir2)
  }

  @Test
  fun testGetNativeDebuggerAppContext_withNonBazelContext_returnsNull() {
    val provider = BazelNativeDebuggerAppContextProvider()
    val nonBazelContext =
      object : ApplicationProjectContext {
        override val applicationId: String = "com.example.app"
      }

    val nativeContext = provider.getNativeDebuggerAppContext(nonBazelContext)
    assertThat(nativeContext).isNull()
  }
}
