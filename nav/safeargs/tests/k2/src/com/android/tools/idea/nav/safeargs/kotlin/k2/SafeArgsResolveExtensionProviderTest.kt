/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.kotlin.k2

import com.android.tools.idea.nav.safeargs.SafeArgsMode
import com.android.tools.idea.nav.safeargs.safeArgsMode
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtension
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.directRegularDependenciesOfType
import org.jetbrains.kotlin.idea.base.projectStructure.getMainKtSourceModule
import org.junit.Before
import org.junit.Test

@RunsInEdt
class SafeArgsResolveExtensionProviderTest : AbstractSafeArgsResolveExtensionTest() {

  private lateinit var module: Module

  @Before
  fun setUp() {
    module = safeArgsRule.module
  }

  @Test
  fun sourceModule_kotlinMode_createsExtension_registersListener() {
    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.KOTLIN
    assertThat(module.getMainKtSourceModule()!!.provideExtensions())
      .containsExactly(SafeArgsResolveExtensionModuleService.getInstance(safeArgsRule.module))
    assertThat(isChangeListenerRegistered).isTrue()
  }

  @Test
  fun sourceModule_javaMode_doesNotCreateExtension_registersListener() {
    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.JAVA
    assertThat(module.getMainKtSourceModule()!!.provideExtensions()).isEmpty()
    assertThat(isChangeListenerRegistered).isTrue()
  }

  @Test
  fun sourceModule_noSafeArgsMode_doesNotCreateExtension_registersListener() {
    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.NONE
    assertThat(module.getMainKtSourceModule()!!.provideExtensions()).isEmpty()
    assertThat(isChangeListenerRegistered).isTrue()
  }

  @Test
  fun nonAndroidSourceModule_doesNotCreateExtension_registersListener() {
    runWriteAction {
      FacetManager.getInstance(module)
        .createModifiableModel()
        .apply { getFacetsByType(AndroidFacet.ID).forEach { removeFacet(it) } }
        .commit()
    }

    assertThat(module.getMainKtSourceModule()!!.provideExtensions()).isEmpty()
    assertThat(isChangeListenerRegistered).isTrue()
  }

  @Test
  fun nonSourceModule_doesNotCreateExtension_doesNotRegisterListener() {
    val sourceKtModule = module.getMainKtSourceModule()!!
    val sdkKtModule = sourceKtModule.directRegularDependenciesOfType<KtSdkModule>().first()

    assertThat(sdkKtModule.provideExtensions()).isEmpty()
    assertThat(isChangeListenerRegistered).isFalse()
  }

  private fun KtModule.provideExtensions(): List<KaResolveExtension> =
    SafeArgsResolveExtensionProvider().provideExtensionsFor(this)

  private val isChangeListenerRegistered: Boolean
    get() = module.project.getServiceIfCreated(ChangeListenerProjectService::class.java) != null
}
