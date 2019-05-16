/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.sdklib.AndroidVersion
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.model.MergedManifestSnapshot
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.utils.concurrency.AsyncSupplier
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.picocontainer.MutablePicoContainer
import kotlin.reflect.KClass

@RunWith(JUnit4::class)
class LaunchCompatibilityCheckerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withSdk()

  private lateinit var facet: AndroidFacet
  private lateinit var mockManifestManager: MergedManifestManager

  @Before
  fun setUp() {
    facet = AndroidFacet.getInstance(projectRule.module)!!
    mockManifestManager = projectRule.module.mockService(MergedManifestManager::class)
  }

  @Test
  fun usesCachedManifestForMinSdkIfAvailable() {
    val api27Manifest = mockManifest(AndroidVersion(27))
    val api28Manifest = mockManifest(AndroidVersion(28))

    `when`(mockManifestManager.mergedManifest).thenReturn(object :  AsyncSupplier<MergedManifestSnapshot> {
      override val now = api27Manifest
      override fun get() = api28Manifest.asFuture()
    })

    val checker = LaunchCompatibilityCheckerImpl.create(facet, null, null) as LaunchCompatibilityCheckerImpl
    assertThat(checker.myMinSdkVersion.apiLevel).isEqualTo(27)
  }

  @Test
  fun usesComputedManifestForMinSdkIfAvailable() {
    val api28Manifest = mockManifest(AndroidVersion(28))

    `when`(mockManifestManager.mergedManifest).thenReturn(object :  AsyncSupplier<MergedManifestSnapshot> {
      override val now : MergedManifestSnapshot? = null
      override fun get() = api28Manifest.asFuture()
    })

    val checker = LaunchCompatibilityCheckerImpl.create(facet, null, null) as LaunchCompatibilityCheckerImpl
    assertThat(checker.myMinSdkVersion.apiLevel).isEqualTo(28)
  }

  @Test
  fun usesDefaultVersionForMinSdkIfManifestUnavailable() {
    `when`(mockManifestManager.mergedManifest).thenReturn(object :  AsyncSupplier<MergedManifestSnapshot> {
      override val now: MergedManifestSnapshot? = null
      override fun get() = SettableFuture.create<MergedManifestSnapshot>()
    })
    val checker = LaunchCompatibilityCheckerImpl.create(facet, null, null) as LaunchCompatibilityCheckerImpl
    assertThat(checker.myMinSdkVersion).isEqualTo(AndroidVersion.DEFAULT)
  }
}

private fun <T> T.asFuture() = Futures.immediateFuture(this)

private fun mockManifest(minSdkVersion: AndroidVersion) : MergedManifestSnapshot {
  return mock(MergedManifestSnapshot::class.java).also {
    `when`(it.minSdkVersion).thenReturn(minSdkVersion)
  }
}

private fun <T: Any> Module.mockService(serviceInterface: KClass<T>): T {
  val mockedImplementation = mock(serviceInterface.java)
  (picoContainer as MutablePicoContainer).apply {
    unregisterComponent(serviceInterface.java.name)
    registerComponentInstance(serviceInterface.java.name, mockedImplementation)
  }
  if (mockedImplementation is Disposable) {
      Disposer.register(this, mockedImplementation)
  }
  return mockedImplementation
}