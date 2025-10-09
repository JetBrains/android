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

class InspectorStateReadModelTest {
  private val disposableRule = DisposableRule()

  @get:Rule val rule = RuleChain(disposableRule, ApplicationRule())

  @Test
  fun testObserveNode() {
    val model = createModel()
    val compose2 = model[COMPOSE2] as ComposeViewNode
    val compose8 = model[COMPOSE8] as ComposeViewNode
    assertThat(model.stateReadsModel.isNodeObserved(model.node(COMPOSE2))).isFalse()

    model.stateReadsModel.observeNode(model.node(COMPOSE2))
    assertThat(model.stateReadsModel.observedForStateReads.value).isEqualTo(Some(setOf(compose2)))
    assertThat(model.stateReadsModel.isNodeObserved(model.node(COMPOSE2))).isTrue()
    assertThat(model.stateReadsModel.isNodeObserved(model.node(COMPOSE8))).isFalse()

    model.stateReadsModel.observeNode(model.node(COMPOSE8))
    assertThat(model.stateReadsModel.observedForStateReads.value)
      .isEqualTo(Some(setOf(compose2, compose8)))
    assertThat(model.stateReadsModel.isNodeObserved(model.node(COMPOSE2))).isTrue()
    assertThat(model.stateReadsModel.isNodeObserved(model.node(COMPOSE8))).isTrue()
  }

  @Test
  fun testStopObservingNode() {
    val model = createModel()
    val compose2 = model[COMPOSE2] as ComposeViewNode
    val compose3 = model[COMPOSE3] as ComposeViewNode
    val compose4 = model[COMPOSE4] as ComposeViewNode
    val compose5 = model[COMPOSE5] as ComposeViewNode
    model.stateReadsModel.observeSubtree(compose2)
    model.stateReadsModel.observeNode(compose5)
    assertThat(model.stateReadsModel.observedForStateReads.value)
      .isEqualTo(Some(setOf(compose2, compose3, compose4, compose5)))
    assertThat(model.stateReadsModel.isNodeObserved(model.node(COMPOSE2))).isTrue()
    assertThat(model.stateReadsModel.isNodeObserved(model.node(COMPOSE4))).isTrue()
    assertThat(model.stateReadsModel.isNodeObserved(model.node(COMPOSE5))).isTrue()

    model.stateReadsModel.stopObservingNode(compose4)
    assertThat(model.stateReadsModel.observedForStateReads.value)
      .isEqualTo(Some(setOf(compose2, compose3, compose5)))
    assertThat(model.stateReadsModel.isNodeObserved(compose4)).isFalse()
    model.stateReadsModel.stopObservingNode(compose5)
    assertThat(model.stateReadsModel.observedForStateReads.value)
      .isEqualTo(Some(setOf(compose2, compose3)))
    assertThat(model.stateReadsModel.isNodeObserved(compose5)).isFalse()
  }

  @Test
  fun testObserveSubTree() {
    val model = createModel()
    val compose2 = model[COMPOSE2] as ComposeViewNode
    val compose3 = model[COMPOSE3] as ComposeViewNode
    val compose4 = model[COMPOSE4] as ComposeViewNode
    val compose7 = model[COMPOSE7] as ComposeViewNode
    val compose8 = model[COMPOSE8] as ComposeViewNode
    model.stateReadsModel.observeSubtree(model.node(COMPOSE2))
    assertThat(model.stateReadsModel.observedForStateReads.value)
      .isEqualTo(Some(setOf(compose2, compose3, compose4)))
    assertThat(model.stateReadsModel.isSubTreeObserved(compose2)).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose3)).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose7)).isFalse()

    model.stateReadsModel.observeSubtree(compose3)
    assertThat(model.stateReadsModel.observedForStateReads.value)
      .isEqualTo(Some(setOf(compose2, compose3, compose4)))
    assertThat(model.stateReadsModel.isSubTreeObserved(compose2)).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose3)).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose7)).isFalse()

    model.stateReadsModel.observeSubtree(compose7)
    assertThat(model.stateReadsModel.observedForStateReads.value)
      .isEqualTo(Some(setOf(compose2, compose3, compose4, compose7, compose8)))
    assertThat(model.stateReadsModel.isSubTreeObserved(model.node(COMPOSE2))).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(model.node(COMPOSE3))).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(model.node(COMPOSE7))).isTrue()
  }

  @Test
  fun testStopObserveSubTree() {
    val model = createModel()
    val compose2 = model[COMPOSE2] as ComposeViewNode
    val compose3 = model[COMPOSE3] as ComposeViewNode
    val compose4 = model[COMPOSE4] as ComposeViewNode
    val compose7 = model[COMPOSE7] as ComposeViewNode
    val compose8 = model[COMPOSE8] as ComposeViewNode
    model.stateReadsModel.observeSubtree(compose2)
    model.stateReadsModel.observeSubtree(compose7)
    assertThat(model.stateReadsModel.observedForStateReads.value)
      .isEqualTo(Some(setOf(compose2, compose3, compose4, compose7, compose8)))
    assertThat(model.stateReadsModel.isSubTreeObserved(compose2)).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose3)).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose7)).isTrue()

    model.stateReadsModel.stopObservingSubtree(compose3)
    assertThat(model.stateReadsModel.observedForStateReads.value)
      .isEqualTo(Some(setOf(compose2, compose7, compose8)))
    assertThat(model.stateReadsModel.isSubTreeObserved(compose2)).isFalse()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose3)).isFalse()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose7)).isTrue()

    model.stateReadsModel.stopObservingSubtree(compose7)
    assertThat(model.stateReadsModel.observedForStateReads.value).isEqualTo(Some(setOf(compose2)))
    assertThat(model.stateReadsModel.isNodeObserved(compose2)).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose2)).isFalse()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose3)).isFalse()
    assertThat(model.stateReadsModel.isSubTreeObserved(compose7)).isFalse()
  }

  @Test
  fun testObserveAll() {
    val model = createModel()
    model.stateReadsModel.observeAll()
    assertThat(model.stateReadsModel.isObservingAll()).isTrue()
    assertThat(model.stateReadsModel.observedForStateReads.value).isEqualTo(All)
    assertThat(model.stateReadsModel.isNodeObserved(model.node(COMPOSE3))).isTrue()
    assertThat(model.stateReadsModel.isSubTreeObserved(model.node(COMPOSE2))).isTrue()
  }

  @Test
  fun testObserveNone() {
    val model = createModel()
    model.stateReadsModel.observeSubtree(model.node(COMPOSE2))
    model.stateReadsModel.observeNone()
    assertThat(model.stateReadsModel.observedForStateReads.value).isEqualTo(None)
    assertThat(model.stateReadsModel.isObservingAll()).isFalse()
    assertThat(model.stateReadsModel.isObservingAny()).isFalse()
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
