/*
 * Copyright (C) 2025 The Android Open Source Project
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
package org.jetbrains.android.facet

import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.gradle.internal.configuration.inputs.InstrumentedInputs.listener
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test

// language=XML
const val emptyLayout =
  """
    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
    </LinearLayout>
    """

class ResourceFolderManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testTopicNotificationOrder() {
    val resourceFolderManager = ResourceFolderManager.getInstance(projectRule.module.androidFacet!!)
    var earlyListenerCalls = AtomicInteger(0)
    var listenerCalls = AtomicInteger(0)
    val earlyListener =
      ResourceFolderManager.ResourceFolderListener { _, _ -> earlyListenerCalls.incrementAndGet() }
    val listener =
      ResourceFolderManager.ResourceFolderListener { _, _ ->
        val previousCounter = listenerCalls.getAndIncrement()
        assertWithMessage("EARLY_TOPIC is expected to always be called before TOPIC")
          .that(previousCounter)
          .isLessThan(earlyListenerCalls.get())
      }

    projectRule.fixture.addFileToProject("res1/layout/test_layout.xml", emptyLayout)
    val res1Directory = projectRule.fixture.findFileInTempDir("res1")
    var resourceDirectories = mutableListOf(res1Directory.url)
    SourceProviderManager.replaceForTest(
      projectRule.module.androidFacet!!,
      projectRule.testRootDisposable,
      NamedIdeaSourceProviderBuilder.create("main", "AndroidManifest.xml")
        .withResDirectoryUrls(resourceDirectories)
        .build(),
    )

    projectRule.project.messageBus
      .connect(projectRule.fixture.testRootDisposable)
      .subscribe(ResourceFolderManager.EARLY_TOPIC, earlyListener)
    projectRule.project.messageBus
      .connect(projectRule.fixture.testRootDisposable)
      .subscribe(ResourceFolderManager.TOPIC, listener)

    resourceFolderManager.checkForChanges()
    assertThat(resourceFolderManager.folders).containsExactly(res1Directory)

    // Simulate a new resource folder being added
    projectRule.fixture.addFileToProject("res2/layout/test_layout.xml", emptyLayout)
    val res2Directory = projectRule.fixture.findFileInTempDir("res2")
    resourceDirectories.add(res2Directory.url)
    resourceFolderManager.checkForChanges()
    assertThat(resourceFolderManager.folders).containsExactly(res1Directory, res2Directory)
    assertThat(earlyListenerCalls.get()).isEqualTo(1)
    assertThat(listenerCalls.get()).isEqualTo(1)
  }
}
