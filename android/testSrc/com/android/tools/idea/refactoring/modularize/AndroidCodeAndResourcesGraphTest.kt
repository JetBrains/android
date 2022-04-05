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
package com.android.tools.idea.refactoring.modularize

import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import org.junit.Test

class AndroidCodeAndResourcesGraphTest {

  @Test
  fun `only specified roots are included`() {
    val roots = listOf<PsiElement>(mock(), mock(), mock())

    val builder = AndroidCodeAndResourcesGraph.Builder()
    for (r in roots) {
      builder.addRoot(r)
    }
    val graph = builder.build()

    assertThat(graph.getRoots()).containsExactlyElementsIn(roots)
  }

  @Test
  fun `only sources are vertices`() {
    val sources = listOf<PsiElement>(mock(), mock(), mock())
    val targets = listOf<PsiElement>(mock(), mock(), mock())

    val builder = AndroidCodeAndResourcesGraph.Builder()
    for (s in sources) {
      for (t in targets) {
        builder.markReference(s, t)
      }
    }
    val graph = builder.build()

    assertThat(graph.vertices).containsExactlyElementsIn(sources)
  }

  @Test
  fun `direct references are counted precisely`() {
    val source = mock<PsiElement>()
    val target = mock<PsiElement>()

    val builder = AndroidCodeAndResourcesGraph.Builder()
    builder.markReference(source, target)
    builder.markReference(source, target)
    builder.markReference(source, target)
    val graph = builder.build()

    assertThat(graph.getFrequency(source, target)).isEqualTo(3)
  }

  @Test
  fun `indirect references are not counted`() {
    val source = mock<PsiElement>()
    val middle = mock<PsiElement>()
    val target = mock<PsiElement>()

    val builder = AndroidCodeAndResourcesGraph.Builder()
    builder.markReference(source, middle)
    builder.markReference(middle, target)
    val graph = builder.build()

    assertThat(graph.getFrequency(source, target)).isEqualTo(0)
  }

  @Test
  fun `frequency defaults to 0 when there is no link between source and target`() {
    val graph = AndroidCodeAndResourcesGraph.Builder().build()

    assertThat(graph.getFrequency(mock(), mock())).isEqualTo(0)
  }

  @Test
  fun `frequency defaults to 0 when target is null`() {
    val graph = AndroidCodeAndResourcesGraph.Builder().build()

    assertThat(graph.getFrequency(mock(), null)).isEqualTo(0)
  }

  @Test
  fun `targets of a nonexistent node is the empty set`() {
    val graph = AndroidCodeAndResourcesGraph.Builder().build()

    assertThat(graph.getTargets(mock())).isEmpty()
  }

  @Test
  fun `targets of a sink node is the empty set`() {
    val sink = mock<PsiElement>()

    val builder = AndroidCodeAndResourcesGraph.Builder()
    builder.markReference(mock(), sink)
    val graph = builder.build()

    assertThat(graph.getTargets(sink)).isEmpty()
  }

  @Test
  fun `targets of a source node is all its direct targets`() {
    val source = mock<PsiElement>()
    val direct1 = mock<PsiElement>()
    val direct2 = mock<PsiElement>()
    val indirect = mock<PsiElement>()

    val builder = AndroidCodeAndResourcesGraph.Builder()
    builder.markReference(source, direct1)
    builder.markReference(source, direct2)
    builder.markReference(direct1, indirect)
    builder.markReference(direct2, indirect)
    val graph = builder.build()

    assertThat(graph.getTargets(source)).containsExactly(direct1, direct2)
  }

  @Test
  fun `root referenced outside scope don't matter`() {
    val root = mock<PsiElement>()

    val builder = AndroidCodeAndResourcesGraph.Builder()
    builder.addRoot(root)
    builder.markReferencedOutsideScope(root)
    val graph = builder.build()

    assertThat(graph.getReferencedOutsideScope()).isEmpty()
  }

  @Test
  fun `non-root referenced outside scope do matter`() {
    val nonroot = mock<PsiElement>()

    val builder = AndroidCodeAndResourcesGraph.Builder()
    builder.markReferencedOutsideScope(nonroot)
    val graph = builder.build()

    assertThat(graph.getReferencedOutsideScope()).containsExactly(nonroot)
  }

  @Test
  fun `transitive referenced outside scope is detected`() {
    val source = mock<PsiElement>()
    val target = mock<PsiElement>()

    val builder = AndroidCodeAndResourcesGraph.Builder()
    builder.markReferencedOutsideScope(source)
    builder.markReference(source, target)
    val graph = builder.build()

    assertThat(graph.getReferencedOutsideScope()).containsExactly(source, target)
  }
}