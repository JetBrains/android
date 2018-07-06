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
package com.android.tools.idea.gradle.project.sync.setup.module.android

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.ModifiableRootModel
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class SdkModuleSetupStepTest : AndroidGradleTestCase() {
  private var androidSdks : AndroidSdks? = null
  private var setupStep : SdkModuleSetupStep? = null

  override fun setUp() {
    super.setUp()
    androidSdks = mock(AndroidSdks::class.java)
    setupStep = SdkModuleSetupStep(androidSdks!!)
  }

  private fun <T> any(): T {
    return Mockito.any<T>()
  }

  @Test
  fun testCreateSdkUnderWriteAction() {
    loadSimpleApplication()

    val appModule = myModules.appModule
    val androidModel = AndroidModuleModel.get(appModule)!!

    val sdk = mock (Sdk::class.java)
    `when`(sdk.name).thenReturn("SdkName")

    `when`(androidSdks!!.findSuitableAndroidSdk(any())).thenReturn(null)
    `when`(androidSdks!!.tryToCreate(any(), any())).thenAnswer {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      sdk
    }

    val moduleContext = mock(ModuleSetupContext::class.java)
    val modifiableRootModel = mock(ModifiableRootModel::class.java)
    val llmei = mock(LanguageLevelModuleExtensionImpl::class.java)
    `when`(moduleContext.modifiableRootModel).thenReturn(modifiableRootModel)
    `when`(moduleContext.module).thenReturn(appModule)
    `when`(modifiableRootModel.getModuleExtension(LanguageLevelModuleExtensionImpl::class.java)).thenReturn(llmei)

    setupStep!!.doSetUpModule(moduleContext, androidModel)
  }
}