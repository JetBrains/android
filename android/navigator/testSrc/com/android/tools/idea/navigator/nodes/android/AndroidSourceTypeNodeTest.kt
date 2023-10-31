/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.android

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.navigator.AndroidViewNodes
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.ide.projectView.ViewSettings
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidSourceType
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path

@RunsInEdt
class AndroidSourceTypeNodeTest {
  @get:Rule
  val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.SIMPLE_APPLICATION).onEdt()

  @Test
  fun testNodeFoldersOrder() {
    val sourceType = AndroidSourceType.KOTLIN_AND_JAVA
    val androidFacet = AndroidFacet.getInstance(projectRule.fixture.module)!!
    val providers = SourceProviders.getInstance(androidFacet)
    val sources = AndroidViewNodes.getSourceProviders(providers).flatMap { sourceType.getSources(it) }.toSet()

    val sourcesInExpectedOrder = linkedSetOf(
      sources.single { it.toNioPath().endsWith(Path.of("main", "java")) },
      sources.single { it.toNioPath().endsWith(Path.of("androidTest", "java")) },
      sources.single { it.toNioPath().endsWith(Path.of("test", "java")) }
    )
    val sourcesInShuffledOrder = linkedSetOf(
      sources.single { it.toNioPath().endsWith(Path.of("test", "java")) },
      sources.single { it.toNioPath().endsWith(Path.of("main", "java")) },
      sources.single { it.toNioPath().endsWith(Path.of("androidTest", "java")) }
    )

    // Test node returns folders in expected order when provided in expected order
    AndroidSourceTypeNode(projectRule.project, androidFacet, ViewSettings.DEFAULT, sourceType, sourcesInExpectedOrder).let { node ->
      val sourcesFromNode = node.folders.map { it.virtualFile }
      Truth.assertThat(sourcesFromNode).containsExactlyElementsIn(sourcesInExpectedOrder.asIterable()).inOrder()
    }

    // Test node returns folders in expected order when provided in different order
    AndroidSourceTypeNode(projectRule.project, androidFacet, ViewSettings.DEFAULT, sourceType, sourcesInShuffledOrder).let { node ->
      val sourcesFromNode = node.folders.map { it.virtualFile }
      Truth.assertThat(sourcesFromNode).containsExactlyElementsIn(sourcesInExpectedOrder.asIterable()).inOrder()
    }
  }

}