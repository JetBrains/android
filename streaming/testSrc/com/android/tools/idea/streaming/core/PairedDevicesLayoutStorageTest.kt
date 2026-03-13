/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.google.common.truth.Truth.assertThat
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import kotlin.test.fail
import org.junit.Test

class PairedDevicesLayoutStorageTest {

  @Test
  fun testGetAndSetLayout() {
    val storage = PairedDevicesLayoutStorage()
    assertThat(storage.getLayout("key1")).isNull()

    storage.setLayout("key1", PairLayout.LEFT, 0.3f)
    var layout = storage.getLayout("key1") ?: fail("Missing layout")
    assertThat(layout).isNotNull()
    assertThat(layout.side).isEqualTo(PairLayout.LEFT)
    assertThat(layout.splitRatio).isEqualTo(0.3f)

    storage.setLayout("key1", PairLayout.RIGHT, 0.7f)
    layout = storage.getLayout("key1") ?: fail("Missing layout")
    assertThat(layout.side).isEqualTo(PairLayout.RIGHT)
    assertThat(layout.splitRatio).isEqualTo(0.7f)
  }

  @Test
  fun testRemoveLayout() {
    val storage = PairedDevicesLayoutStorage()
    storage.setLayout("key1", PairLayout.LEFT, 0.3f)
    storage.setLayout("key2", PairLayout.TOP, 0.4f)

    storage.removeLayout("key1")
    assertThat(storage.getLayout("key1")).isNull()
    assertThat(storage.getLayout("key2")).isNotNull()
  }

  @Test
  fun testClear() {
    val storage = PairedDevicesLayoutStorage()
    storage.setLayout("key1", PairLayout.LEFT, 0.3f)
    storage.setLayout("key2", PairLayout.TOP, 0.4f)

    storage.clear()
    assertThat(storage.getLayout("key1")).isNull()
    assertThat(storage.getLayout("key2")).isNull()
  }

  @Test
  fun testSerialization() {
    val storage = PairedDevicesLayoutStorage()
    storage.setLayout("key1", PairLayout.LEFT, 0.3f)
    storage.setLayout("key2", PairLayout.TOP, 0.4f)

    val element = serialize(storage, createElementIfEmpty = true)!!
    val deserialized = deserialize<PairedDevicesLayoutStorage>(element)
    assertThat(deserialized).isEqualTo(storage)
  }
}
