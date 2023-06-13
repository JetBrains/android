/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceRepository
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.res.ResourceRepositoryManager
import com.android.tools.res.ids.ResourceIdManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.withSettings
import java.util.HexFormat
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.days

private const val PKG_1 = "come.on.fhqwhgads"
private const val PKG_2 = "everybody.to.the.limit"

private val String.namespace : ResourceNamespace
  get() = ResourceNamespace.fromPackageName(this)

@RunWith(JUnit4::class)
class ResourceClassRegistryTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private val registry = ResourceClassRegistry(1.days) // Not testing the TimeoutCachedValue
  private val fixture by lazy { projectRule.fixture }
  private val idManager by lazy { ResourceIdManager.get(fixture.module) }
  private val manager: ResourceRepositoryManager = mock()
  private val repository: ResourceRepository = mock()
  private val disposableRepository: ResourceRepository = mock(withSettings().extraInterfaces(Disposable::class.java))

  @Before
  fun setUp() {
    Disposer.register(projectRule.testRootDisposable, disposableRepository as Disposable)
    whenever(manager.getAppResourcesForNamespace(eq(PKG_1.namespace))).thenReturn(listOf(repository, disposableRepository))
    whenever(manager.getAppResourcesForNamespace(eq(PKG_2.namespace))).thenReturn(listOf(repository, disposableRepository))
  }

  @Test
  fun addLibrary_alreadyDisposedRepo() {
    Disposer.dispose(disposableRepository as Disposable)
    registry.addLibrary(repository, idManager, PKG_1, PKG_1.namespace)
    registry.addLibrary(disposableRepository, idManager, PKG_2, PKG_2.namespace)

    assertThat(registry.findClassDefinition("$PKG_1.R", manager)).isNotNull()
    assertThat(registry.findClassDefinition("$PKG_2.R", manager)).isNull()
  }

  @Test
  fun addLibrary_disposableRepo() {
    registry.addLibrary(disposableRepository, idManager, PKG_1, PKG_1.namespace)

    assertThat(registry.findClassDefinition("$PKG_1.R", manager)).isNotNull()

    Disposer.dispose(disposableRepository as Disposable);

    assertThat(registry.findClassDefinition("$PKG_1.R", manager)).isNull()
  }

  @Test
  fun findClassDefinition_notRClass() {
    assertThat(registry.findClassDefinition("$PKG_1.StrongBad", manager)).isNull()
    verifyNoInteractions(manager)
  }

  @Test
  fun findClassDefinition_notInPackages() {
    assertThat(registry.findClassDefinition("$PKG_1.R", manager)).isNull()
    verifyNoInteractions(manager)
  }

  @Test
  fun findClassDefinition_packageNameCollision() {
    registry.addLibrary(repository, idManager, PKG_1, PKG_1.namespace)
    registry.addLibrary(disposableRepository, idManager, PKG_1, PKG_1.namespace)

    assertFailsWith<NoClassDefFoundError> {
      registry.findClassDefinition("$PKG_1.R\$string", manager)
    }
  }

  @Test
  fun findClassDefinition_noPackageNameCollisionIfOneReturnedFromManager() {
    registry.addLibrary(repository, idManager, PKG_1, PKG_1.namespace)
    registry.addLibrary(disposableRepository, idManager, PKG_1, PKG_1.namespace)
    whenever(manager.getAppResourcesForNamespace(eq(PKG_1.namespace))).thenReturn(listOf(repository))

    assertThat(registry.findClassDefinition("$PKG_1.R\$string", manager)).isNotNull()
  }

  @Test
  fun findClassDefinition_noRepository() {
    registry.addLibrary(repository, idManager, PKG_1, PKG_1.namespace)
    whenever(manager.getAppResourcesForNamespace(eq(PKG_1.namespace))).thenReturn(listOf())

    assertThat(registry.findClassDefinition("$PKG_1.R", manager)).isNull()
  }

  @Test
  fun findClassDefinition() {
    registry.addLibrary(repository, idManager, PKG_1, PKG_1.namespace)

    var bytes = registry.findClassDefinition("$PKG_1.R", manager)
    assertThat(HexFormat.of().formatHex(bytes)).startsWith("cafebabe")
  }

  @Test
  fun clearCache() {
    registry.addLibrary(repository, idManager, PKG_1, PKG_1.namespace)

    registry.clearCache()

    assertThat(registry.findClassDefinition("$PKG_1.R\$values", manager)).isNull()
  }
}
