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

import com.android.repository.api.RepoManager
import com.android.repository.testframework.MockFileOp
import com.android.sdklib.repository.AndroidSdkHandler
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
import org.mockito.Mockito.*

class SdkModuleSetupStepTest : AndroidGradleTestCase() {
  private val androidSdks = mock(AndroidSdks::class.java)
  private val setupStep = SdkModuleSetupStep(androidSdks)
  private val moduleContext = mock(ModuleSetupContext::class.java)
  private val sdk = mock(Sdk::class.java)
  private lateinit var androidModel : AndroidModuleModel

  override fun setUp() {
    super.setUp()
    loadSimpleApplication()

    `when`(sdk.name).thenReturn("SdkName")

    val appModule = myModules.appModule
    androidModel = AndroidModuleModel.get(appModule)!!

    val modifiableRootModel = mock(ModifiableRootModel::class.java)
    val llmei = mock(LanguageLevelModuleExtensionImpl::class.java)
    `when`(moduleContext.modifiableRootModel).thenReturn(modifiableRootModel)
    `when`(moduleContext.module).thenReturn(appModule)
    `when`(modifiableRootModel.getModuleExtension(LanguageLevelModuleExtensionImpl::class.java)).thenReturn(llmei)
  }

  private fun <T> any(): T {
    return Mockito.any<T>()
  }

  @Test
  fun testCreateSdkUnderWriteAction() {
    `when`(androidSdks.findSuitableAndroidSdk(any())).thenReturn(null)
    `when`(androidSdks.tryToChooseSdkHandler()).thenReturn(AndroidSdkHandler.getInstance(null))
    `when`(androidSdks.tryToCreate(any(), any())).thenAnswer {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      sdk
    }

    setupStep.doSetUpModule(moduleContext, androidModel)
  }

  @Test
  fun testSdkIsReloaded() {
    val repoManager = mock(RepoManager::class.java)
    val sdkHandler = AndroidSdkHandler(null, null, MockFileOp(), repoManager)
    `when`(androidSdks.findSuitableAndroidSdk(any())).thenReturn(null)
    `when`(androidSdks.tryToChooseSdkHandler()).thenReturn(sdkHandler)
    `when`(androidSdks.tryToCreate(any(), any())).thenReturn(sdk)

    setupStep.doSetUpModule(moduleContext, androidModel)

    verify(repoManager).reloadLocalIfNeeded(any())
  }
}