/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class PsSigningConfigTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testDescriptor() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {

      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule, notNullValue())

      val signingConfig = appModule.findSigningConfig("myConfig")
      assertThat(appModule, notNullValue()); signingConfig!!

      assertThat(
        signingConfig.descriptor.testEnumerateProperties(),
        equalTo(PsSigningConfig.SigningConfigDescriptors.testEnumerateProperties())
      )
    }
  }

  @Test
  fun testProperties() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule, notNullValue())

      val signingConfig = appModule.findSigningConfig("myConfig")
      assertThat(appModule, notNullValue()); signingConfig!!

      val keyAlias = PsSigningConfig.SigningConfigDescriptors.keyAlias.bind(signingConfig).getValue()
      val keyPassword = PsSigningConfig.SigningConfigDescriptors.keyPassword.bind(signingConfig).getValue()
      val storeFile = PsSigningConfig.SigningConfigDescriptors.storeFile.bind(signingConfig).getValue()
      val storePassword = PsSigningConfig.SigningConfigDescriptors.storePassword.bind(signingConfig).getValue()
      // TODO(b/70501607): Decide on val storeType = PsSigningConfig.SigningConfigDescriptors.storeType.getValue(signingConfig)

      assertThat(keyAlias.resolved.asTestValue(), equalTo("androiddebugkey"))
      assertThat(keyAlias.parsedValue.asTestValue(), equalTo("androiddebugkey"))

      // TODO(b/70501607): assertThat(keyPassword.resolved.asTestValue(), equalTo("android"))
      assertThat(keyPassword.parsedValue.asTestValue(), equalTo("android"))

      assertThat(storeFile.resolved.asTestValue(), equalTo(File(File(project.ideProject.basePath, "app"), "debug.keystore")))
      assertThat(storeFile.parsedValue.asTestValue(), equalTo(File("debug.keystore")))

      assertThat(storePassword.resolved.asTestValue(), equalTo("android"))
      assertThat(storePassword.parsedValue.asTestValue(), equalTo("android"))
    }
  }

  @Test
  fun testSetProperties() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      var appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule, notNullValue())

      val signingConfig = appModule.findSigningConfig("myConfig")
      assertThat(appModule, notNullValue()); signingConfig!!

      signingConfig.keyAlias = "ka".asParsed()
      signingConfig.keyPassword = "kp".asParsed()
      signingConfig.storeFile = File("sf").asParsed()
      signingConfig.storePassword = "sp".asParsed()

      fun verifyValues(signingConfig: PsSigningConfig, afterSync: Boolean = false) {

        val keyAlias = PsSigningConfig.SigningConfigDescriptors.keyAlias.bind(signingConfig).getValue()
        val keyPassword = PsSigningConfig.SigningConfigDescriptors.keyPassword.bind(signingConfig).getValue()
        val storeFile = PsSigningConfig.SigningConfigDescriptors.storeFile.bind(signingConfig).getValue()
        val storePassword = PsSigningConfig.SigningConfigDescriptors.storePassword.bind(signingConfig).getValue()
        // TODO(b/70501607): Decide on val storeType = PsSigningConfig.SigningConfigDescriptors.storeType.getValue(signingConfig)

        assertThat(keyAlias.parsedValue.asTestValue(), equalTo("ka"))
        // TODO(b/70501607): assertThat(keyPassword.resolved.asTestValue(), equalTo("android"))
        assertThat(keyPassword.parsedValue.asTestValue(), equalTo("kp"))
        assertThat(storeFile.parsedValue.asTestValue(), equalTo(File("sf")))
        assertThat(storePassword.parsedValue.asTestValue(), equalTo("sp"))

        if (afterSync) {
          assertThat(keyAlias.parsedValue.asTestValue(), equalTo(keyAlias.resolved.asTestValue()))
          // TODO(b/70501607): assertThat(keyPassword.parsedValue.asTestValue(), equalTo(keyPassword.resolved.asTestValue()))
          // TODO(b/73716779): assertThat(storeFile.parsedValue.asTestValue(), equalTo(storeFile.resolved.asTestValue()))
          assertThat(storePassword.parsedValue.asTestValue(), equalTo(storePassword.resolved.asTestValue()))
        }
      }

      verifyValues(signingConfig)

      appModule.applyChanges()
      requestSyncAndWait()
      project = PsProjectImpl(resolvedProject).also { it.testResolve() }
      appModule = project.findModuleByName("app") as PsAndroidModule
      // Verify nothing bad happened to the values after the re-parsing.
      verifyValues(appModule.findSigningConfig("myConfig")!!, afterSync = true)
    }
  }

  @Test
  fun testSetProperties_undeclaredDebug() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    projectRule.psTestWithProject(preparedProject) {
      var project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      var appModule = project.findModuleByName("app") as PsAndroidModule
      assertThat(appModule, notNullValue())

      val signingConfig = appModule.findSigningConfig("debug")
      assertThat(appModule, notNullValue()); signingConfig!!

      signingConfig.keyAlias = "ka".asParsed()

      fun verifyValues(signingConfig: PsSigningConfig, afterSync: Boolean = false) {

        val keyAlias = PsSigningConfig.SigningConfigDescriptors.keyAlias.bind(signingConfig).getValue()

        assertThat(keyAlias.parsedValue.asTestValue(), equalTo("ka"))

        if (afterSync) {
          assertThat(keyAlias.parsedValue.asTestValue(), equalTo(keyAlias.resolved.asTestValue()))
        }
      }

      verifyValues(signingConfig)

      appModule.applyChanges()
      requestSyncAndWait()
      project = PsProjectImpl(resolvedProject).also { it.testResolve() }
      appModule = project.findModuleByName("app") as PsAndroidModule
      // Verify nothing bad happened to the values after the re-parsing.
      verifyValues(appModule.findSigningConfig("debug")!!, afterSync = true)
    }
  }
}
