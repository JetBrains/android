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
package com.android.tools.idea.layoutinspector.model

import com.android.tools.idea.layoutinspector.TestScopeRule
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.All
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.None
import com.android.tools.idea.layoutinspector.stateinspection.ObservedNodes.Some
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.RuleChain
import org.junit.Rule
import org.junit.Test

class InspectorRecompositionModelTest {
  private val disposableRule = DisposableRule()

  @get:Rule val rule = RuleChain(TestScopeRule(), disposableRule, ApplicationRule())

  @Test
  fun testObserveNode() {
    val model = createModel()
    val compose2 = model[COMPOSE2] as ComposeViewNode
    val compose8 = model[COMPOSE8] as ComposeViewNode
    assertThat(model.recompositionModel.isNodeObserved(model.node(COMPOSE2))).isFalse()

    model.recompositionModel.observeNode(model.node(COMPOSE2))
    assertThat(model.recompositionModel.observedForRecompositions.value).isEqualTo(Some(setOf(compose2.anchorHash)))
    assertThat(model.recompositionModel.isNodeObserved(model.node(COMPOSE2))).isTrue()
    assertThat(model.recompositionModel.isNodeObserved(model.node(COMPOSE8))).isFalse()

    model.recompositionModel.observeNode(model.node(COMPOSE8))
    assertThat(model.recompositionModel.observedForRecompositions.value).isEqualTo(Some(setOf(compose2.anchorHash, compose8.anchorHash)))
    assertThat(model.recompositionModel.isNodeObserved(model.node(COMPOSE2))).isTrue()
    assertThat(model.recompositionModel.isNodeObserved(model.node(COMPOSE8))).isTrue()
  }

  @Test
  fun testStopObservingNode() {
    val model = createModel()
    val compose2 = model[COMPOSE2] as ComposeViewNode
    val compose3 = model[COMPOSE3] as ComposeViewNode
    val compose4 = model[COMPOSE4] as ComposeViewNode
    val compose5 = model[COMPOSE5] as ComposeViewNode
    model.recompositionModel.observeNode(compose2)
    model.recompositionModel.observeNode(compose3)
    model.recompositionModel.observeNode(compose4)
    model.recompositionModel.observeNode(compose5)
    assertThat(model.recompositionModel.observedForRecompositions.value)
      .isEqualTo(Some(setOf(compose2.anchorHash, compose3.anchorHash, compose4.anchorHash, compose5.anchorHash)))
    assertThat(model.recompositionModel.isNodeObserved(model.node(COMPOSE2))).isTrue()
    assertThat(model.recompositionModel.isNodeObserved(model.node(COMPOSE4))).isTrue()
    assertThat(model.recompositionModel.isNodeObserved(model.node(COMPOSE5))).isTrue()

    model.recompositionModel.stopObservingNode(compose4)
    assertThat(model.recompositionModel.observedForRecompositions.value)
      .isEqualTo(Some(setOf(compose2.anchorHash, compose3.anchorHash, compose5.anchorHash)))
    assertThat(model.recompositionModel.isNodeObserved(compose4)).isFalse()
    model.recompositionModel.stopObservingNode(compose5)
    assertThat(model.recompositionModel.observedForRecompositions.value).isEqualTo(Some(setOf(compose2.anchorHash, compose3.anchorHash)))
    assertThat(model.recompositionModel.isNodeObserved(compose5)).isFalse()
  }

  @Test
  fun testObserveAll() {
    val model = createModel()
    model.recompositionModel.observeAll()
    assertThat(model.recompositionModel.isObservingAll()).isTrue()
    assertThat(model.recompositionModel.observedForRecompositions.value).isEqualTo(All)
    assertThat(model.recompositionModel.isNodeObserved(model.node(COMPOSE3))).isTrue()
  }

  @Test
  fun testObserveNone() {
    val model = createModel()
    model.recompositionModel.observeNode(model.node(COMPOSE2))
    model.recompositionModel.observeNode(model.node(COMPOSE3))
    model.recompositionModel.observeNone()
    assertThat(model.recompositionModel.observedForRecompositions.value).isEqualTo(None)
    assertThat(model.recompositionModel.isObservingAll()).isFalse()
    assertThat(model.recompositionModel.isObservingAny()).isFalse()
  }

  private fun InspectorModel.node(id: Long): ComposeViewNode = get(id) as ComposeViewNode

  private fun createModel(): InspectorModel =
    model(disposableRule.disposable) {
      view(ROOT, 0, 0, 100, 200) {
        compose(COMPOSE1, "Column") {
          compose(COMPOSE2, "Item") { compose(COMPOSE3, "Text") { compose(COMPOSE4, "BasicText") } }
          compose(COMPOSE5, "Item") { compose(COMPOSE6, "Text") }
          compose(COMPOSE7, "Item") { compose(COMPOSE8, "Text") }
        }
      }
    }
}
