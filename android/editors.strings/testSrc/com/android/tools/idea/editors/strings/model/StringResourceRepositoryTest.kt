/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.model

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.Locale
import com.android.projectmodel.DynamicResourceValue
import com.android.resources.ResourceType
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.res.DynamicValueResourceRepository
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.createTestModuleRepository
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.EdtExecutorService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

/**
 * Tests methods in [StringResourceRepository].
 *
 * Test data for this test is located in `tools/adt/idea/android/testData/stringsEditor/base/res/`
 */
@RunWith(JUnit4::class)
class StringResourceRepositoryTest {
  @get:Rule val androidProjectRule = AndroidProjectRule.withAndroidModel()

  private val emptyRepository = StringResourceRepository.empty()
  private lateinit var facet: AndroidFacet
  private lateinit var dynamicResourceRepository: DynamicValueResourceRepository
  private lateinit var resourceDirectory: VirtualFile
  private lateinit var key1: StringResourceKey
  private lateinit var invalidKey: StringResourceKey
  private lateinit var localResourceRepository: LocalResourceRepository
  private lateinit var stringResourceRepository: StringResourceRepository

  @Before
  fun setUp() {
    androidProjectRule.fixture.testDataPath =
        resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    facet = AndroidFacet.getInstance(androidProjectRule.module)!!
    dynamicResourceRepository =
        DynamicValueResourceRepository.createForTest(
            facet,
            ResourceNamespace.RES_AUTO,
            mutableMapOf(
                DYNAMIC_KEY_1 to DynamicResourceValue(ResourceType.STRING, DYNAMIC_VALUE_1),
                DYNAMIC_KEY_2 to DynamicResourceValue(ResourceType.STRING, DYNAMIC_VALUE_2),
            ))

    resourceDirectory =
        androidProjectRule.fixture.copyDirectoryToProject("stringsEditor/base/res", "res")
    key1 = StringResourceKey("key1", resourceDirectory)
    invalidKey = StringResourceKey("key1", resourceDirectory.parent)
    localResourceRepository =
        createTestModuleRepository(
            facet, listOf(resourceDirectory), ResourceNamespace.RES_AUTO, dynamicResourceRepository)
    stringResourceRepository = StringResourceRepository.create(localResourceRepository)

    runBlocking {
      withTimeout(2.seconds) {
        suspendCoroutine<Unit> {
          localResourceRepository.invokeAfterPendingUpdatesFinish(
              EdtExecutorService.getInstance()) { it.resume(Unit) }
        }
      }
    }
  }

  @Test
  fun getKeys_empty() {
    assertThat(emptyRepository.getKeys()).isEmpty()
  }

  @Test
  fun getKeys() {
    assertThat(stringResourceRepository.getKeys())
        .containsExactly(
            StringResourceKey("key1", resourceDirectory),
            StringResourceKey("key2", resourceDirectory),
            StringResourceKey("key3", resourceDirectory),
            StringResourceKey("key5", resourceDirectory),
            StringResourceKey("key6", resourceDirectory),
            StringResourceKey("key7", resourceDirectory),
            StringResourceKey("key8", resourceDirectory),
            StringResourceKey("key4", resourceDirectory),
            StringResourceKey("key9", resourceDirectory),
            StringResourceKey("key10", resourceDirectory),
            StringResourceKey(DYNAMIC_KEY_1),
            StringResourceKey(DYNAMIC_KEY_2),
        )
        .inOrder()
  }

  @Test
  fun getItems_empty() {
    assertThat(emptyRepository.getItems(StringResourceKey(DYNAMIC_KEY_1))).isEmpty()
  }

  @Test
  fun getItems_dynamicResources() {
    val value1Items = stringResourceRepository.getItems(StringResourceKey(DYNAMIC_KEY_1))
    assertThat(value1Items).hasSize(1)
    assertThat(value1Items.first().resourceValue.resourceType).isEqualTo(ResourceType.STRING)
    assertThat(value1Items.first().resourceValue.value).isEqualTo(DYNAMIC_VALUE_1)
    val value2Items = stringResourceRepository.getItems(StringResourceKey(DYNAMIC_KEY_2))
    assertThat(value2Items).hasSize(1)
    assertThat(value2Items.first().resourceValue.resourceType).isEqualTo(ResourceType.STRING)
    assertThat(value2Items.first().resourceValue.value).isEqualTo(DYNAMIC_VALUE_2)

    assertThat(stringResourceRepository.getItems(StringResourceKey(MISSING_KEY))).isEmpty()
  }

  @Test
  fun getItems_directoryResources() {
    val key1Items = stringResourceRepository.getItems(key1)
    assertThat(key1Items).hasSize(4)
    key1Items.forEach {
      assertThat(it.name).isEqualTo("key1")
      assertThat(it.type).isEqualTo(ResourceType.STRING)
    }

    val key1Values = evaluateInEdtExecutor { key1Items.map { it.resourceValue.value } }

    assertThat(key1Values)
        .containsExactly(
            "Key 1 default",
            "Key 1 en",
            "Key 1 en-rGB",
            """<b>Google I/O 2014</b><br> Version %s<br><br> <a href=http://www.google.com/policies/privacy/>Privacy Policy</a>""")
  }

  @Test
  fun getItems_invalidKey() {
    assertFailsWith<IllegalArgumentException> { stringResourceRepository.getItems(invalidKey) }
  }

  @Test
  fun getDefaultValue() {
    assertThat(stringResourceRepository.getDefaultValue(StringResourceKey(DYNAMIC_KEY_1)))
        .isNotNull()
    assertThat(
            stringResourceRepository
                .getDefaultValue(StringResourceKey(DYNAMIC_KEY_1))
                ?.resourceValue
                ?.value)
        .isEqualTo(DYNAMIC_VALUE_1)
    assertThat(stringResourceRepository.getDefaultValue(StringResourceKey(DYNAMIC_KEY_2)))
        .isNotNull()
    assertThat(
            stringResourceRepository
                .getDefaultValue(StringResourceKey(DYNAMIC_KEY_2))
                ?.resourceValue
                ?.value)
        .isEqualTo(DYNAMIC_VALUE_2)

    val key1DefaultItem = stringResourceRepository.getDefaultValue(key1)
    assertThat(key1DefaultItem).isNotNull()
    val key1Value = evaluateInEdtExecutor { key1DefaultItem?.resourceValue?.value }
    assertThat(key1Value).isEqualTo("Key 1 default")

    // Key 4 does not have a default
    val key4DefaultItem =
        stringResourceRepository.getDefaultValue(StringResourceKey("key4", resourceDirectory))
    assertThat(key4DefaultItem).isNull()
  }

  @Test
  fun getDefaultValue_invalidKey() {
    assertFailsWith<IllegalArgumentException> {
      stringResourceRepository.getDefaultValue(invalidKey)
    }
  }

  @Test
  fun getTranslation() {
    assertThat(
            stringResourceRepository.getTranslation(
                StringResourceKey(DYNAMIC_KEY_1), Locale.create("en-rUS")))
        .isNull()
    assertThat(
            stringResourceRepository.getTranslation(
                StringResourceKey(DYNAMIC_KEY_2), Locale.create("pt-rBR")))
        .isNull()

    val key1TranslatedItem = stringResourceRepository.getTranslation(key1, Locale.create("en-rGB"))
    assertThat(key1TranslatedItem).isNotNull()
    val key1Value = evaluateInEdtExecutor { key1TranslatedItem?.resourceValue?.value }
    assertThat(key1Value).isEqualTo("Key 1 en-rGB")

    // Key 1 does not have a French translation.
    assertThat(stringResourceRepository.getTranslation(key1, Locale.create("fr"))).isNull()
  }

  @Test
  fun getTranslation_invalidKey() {
    assertFailsWith<IllegalArgumentException> {
      stringResourceRepository.getTranslation(invalidKey, Locale.create("en"))
    }
  }

  @Test
  fun invokeAfterPendingUpdatesFinish() {
    val key1Item = stringResourceRepository.getDefaultValue(key1)
    val key1Value: String? = runBlocking {
      withTimeout(2.seconds) {
        suspendCoroutine {
          stringResourceRepository.invokeAfterPendingUpdatesFinish(key1) {
            // This must run on the EdtExecutorService or an exception will be thrown.
            it.resume(key1Item?.resourceValue?.value)
          }
        }
      }
    }
    assertThat(key1Value).isNotNull()
  }

  @Test
  fun invokeAfterPendingUpdatesFinish_invalidKey() {
    assertFailsWith<IllegalArgumentException> {
      stringResourceRepository.invokeAfterPendingUpdatesFinish(invalidKey) {}
    }
  }

  /** Runs code inside the [EdtExecutorService] and waits for it to return a value. */
  private fun <T> evaluateInEdtExecutor(block: () -> T): T {
    return runBlocking {
      withTimeout(2.seconds) {
        suspendCoroutine { EdtExecutorService.getInstance().execute { it.resume(block.invoke()) } }
      }
    }
  }

  companion object {
    private const val DYNAMIC_KEY_1 = "foo"
    private const val DYNAMIC_KEY_2 = "bar"
    private const val DYNAMIC_VALUE_1 = "baz"
    private const val DYNAMIC_VALUE_2 = "quux"
    private const val MISSING_KEY = "missing!"
  }
}
