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
import com.android.tools.tests.KotlinAdtTestProjectDescriptor
import com.google.common.truth.Truth.assertThat
import com.intellij.facet.FacetManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.directRegularDependenciesOfType
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtension
import org.jetbrains.kotlin.idea.base.projectStructure.toKaSourceModuleForProduction
import org.junit.Before
import org.junit.Test

@OptIn(KaExperimentalApi::class)
@RunsInEdt
class SafeArgsResolveExtensionProviderTest : AbstractSafeArgsResolveExtensionTest() {

  private lateinit var module: Module
  private lateinit var sourceModule: KaSourceModule

  @Before
  fun setUp() {
    module = safeArgsRule.module
    sourceModule = module.toKaSourceModuleForProduction()!!
  }

  @Test
  fun sourceModule_kotlinMode_createsExtension_registersListener() {
    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.KOTLIN
    sourceModule.useExtensions {
      assertThat(this).hasSize(1)
      assertThat(single()).isInstanceOf(SafeArgsResolveExtension::class.java)
    }
    assertThat(isChangeListenerRegistered).isTrue()
  }

  @Test
  fun sourceModule_javaMode_doesNotCreateExtension_registersListener() {
    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.JAVA
    sourceModule.useExtensions { assertThat(this).isEmpty() }
    assertThat(isChangeListenerRegistered).isTrue()
  }

  @Test
  fun sourceModule_noSafeArgsMode_doesNotCreateExtension_registersListener() {
    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.NONE
    sourceModule.useExtensions { assertThat(this).isEmpty() }
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

    sourceModule.useExtensions { assertThat(this).isEmpty() }
    assertThat(isChangeListenerRegistered).isTrue()
  }

  @Test
  fun nonSourceModule_doesNotCreateExtension_doesNotRegisterListener() {
    sourceModule
      .directRegularDependenciesOfType<KaLibraryModule>()
      .single { it.libraryName == KotlinAdtTestProjectDescriptor.LIBRARY_NAME }
      .useExtensions { assertThat(this).isEmpty() }

    assertThat(isChangeListenerRegistered).isFalse()
  }

  @Test
  fun shadowedScope_doesNotRegisterForDisposal() {
    safeArgsRule.androidFacet.safeArgsMode = SafeArgsMode.KOTLIN
    val debugMode = Disposer.setDebugMode(true)
    try {
      val extensions = SafeArgsResolveExtensionProvider().provideExtensionsFor(sourceModule)
      val extension = extensions.single()
      extension.getShadowedScope()
      // Ensure that the extension has not been registered as a Disposable yet. This would generally
      // be done if the internal event listener has been registered. Due to b/433681683, we need to
      // ensure that calling getShadowedScope() on the extension does _not_ register it as a
      // Disposable, so that it can be GC'd without needing to be disposed.
      assertThat(Disposer.getRegistrationTrace(extension)).isNull()

      // We also exit here without calling any other functions on the extension. If the extension
      // has been registered for disposal, we will get an exception at the end of the test because
      // it was never actually disposed.
    } finally {
      Disposer.setDebugMode(debugMode)
    }
  }

  private inline fun KaModule.useExtensions(block: List<KaResolveExtension>.() -> Unit) {
    val disposable = Disposer.newDisposable("SafeArgsResolveExtensions")
    try {
      val extensions = SafeArgsResolveExtensionProvider().provideExtensionsFor(this)
      extensions.forEach { Disposer.register(disposable, it) }
      block(extensions)
    } finally {
      Disposer.dispose(disposable)
    }
  }

  private val isChangeListenerRegistered: Boolean
    get() = module.project.getServiceIfCreated(ChangeListenerProjectService::class.java) != null
}
