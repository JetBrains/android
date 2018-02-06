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

import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ResolvedValue
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import java.io.File

class PsSigningConfigTest : AndroidGradleTestCase() {

  private fun <T> ResolvedValue<T>.asTestValue(): T? = (this as? ResolvedValue.Set<T>)?.resolved
  private fun <T> ParsedValue<T>.asTestValue(): T? = (this as? ParsedValue.Set.Parsed<T>)?.value

  fun testProperties() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val resolvedProject = myFixture.project
    val project = PsProject(resolvedProject)

    val appModule = project.findModuleByName("app") as PsAndroidModule
    assertThat(appModule, notNullValue())

    val signingConfig = appModule.findSigningConfig("myConfig")
    assertThat(appModule, notNullValue()); signingConfig!!

    val keyAlias = PsSigningConfig.SigningConfigDescriptors.keyAlias.getValue(signingConfig)
    // TODO(b/70501607): Decide on val keyPassword = PsSigningConfig.SigningConfigDescriptors.keyPassword.getValue(signingConfig)
    val storeFile = PsSigningConfig.SigningConfigDescriptors.storeFile.getValue(signingConfig)
    val storePassword = PsSigningConfig.SigningConfigDescriptors.storePassword.getValue(signingConfig)
    // TODO(b/70501607): Decide on val storeType = PsSigningConfig.SigningConfigDescriptors.storeType.getValue(signingConfig)

    assertThat(keyAlias.resolved.asTestValue(), equalTo("androiddebugkey"))
    assertThat(keyAlias.parsedValue.asTestValue(), equalTo("androiddebugkey"))

    assertThat(storeFile.resolved.asTestValue(), equalTo(File(File(project.resolvedModel.basePath, "app"), "debug.keystore")))
    assertThat(storeFile.parsedValue.asTestValue(), equalTo(File("debug.keystore")))

    assertThat(storePassword.resolved.asTestValue(), equalTo("android"))
    assertThat(storePassword.parsedValue.asTestValue(), equalTo("android"))
  }
}
