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
package com.android.tools.adtui.model

import com.android.tools.adtui.model.MultiSelectionModel.Entry
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MultiSelectionModelTest {
  @Test
  fun `selections indexed by multiple keys have stable order`() = testWithObserver<Int> { model, selections ->
    model.setSelection("Key1", setOf(1, 2, 3))
    model.setSelection("Key2", setOf(2, 3, 4, 5))
    model.setSelection("Key3", setOf(0))
    assertThat(selections()).containsExactly(Entry("Key1", setOf(1, 2, 3)),
                                             Entry("Key2", setOf(2, 3, 4, 5)),
                                             Entry("Key3", setOf(0)))

    model.setSelection("Key2", setOf(42))
    assertThat(selections()).containsExactly(Entry("Key1", setOf(1, 2, 3)),
                                             Entry("Key2", setOf(42)),
                                             Entry("Key3", setOf(0)))

    model.removeSelection("Key2")
    assertThat(selections()).containsExactly(Entry("Key1", setOf(1, 2, 3)),
                                             Entry("Key3", setOf(0)))

    model.clearSelection()
    assertThat(selections()).isEmpty()
  }

  @Test
  fun `most recently modified selection is active`() = testWithObserver<Int> { model, selections ->
    assertThat(model.activeSelectionKey).isNull()

    model.setSelection("Key1", setOf(1, 2, 3))
    assertThat(model.activeSelectionKey).isEqualTo("Key1")

    model.setSelection("Key2", setOf(2, 3, 4, 5))
    assertThat(model.activeSelectionKey).isEqualTo("Key2")

    model.setSelection("Key3", setOf(0))
    assertThat(model.activeSelectionKey).isEqualTo("Key3")

    model.setSelection("Key2", setOf(42))
    assertThat(model.activeSelectionKey).isEqualTo("Key2")
  }

  @Test
  fun `no item for selection is the same as deselection`() = testWithObserver<Int> { model, selections ->
    model.setSelection("Key1", setOf(1, 2, 3))
    model.setSelection("Key2", setOf(2, 3, 4, 5))
    model.setSelection("Key1", setOf())
    assertThat(selections()).containsExactly(Entry("Key2", setOf(2, 3, 4, 5)))
  }

  @Test
  fun `removing active selection leaves no active key`() =
    testWithObserver(MultiSelectionModel<Int>::activeSelectionKey) { model, activeSelection ->
      assertThat(activeSelection()).isNull()
      model.setSelection("Key1", setOf(1, 2, 3))
      assertThat(activeSelection()).isEqualTo("Key1")

      model.setSelection("Key2", setOf(2, 3))
      assertThat(activeSelection()).isEqualTo("Key2")

      model.removeSelection("Key2")
      assertThat(activeSelection()).isNull()
    }

  @Test
  fun `deselection leaves all selections as-is`() = testWithObserver<Int> { model, selections ->
    model.setSelection("Key1", setOf(1, 2, 3))
    model.setSelection("Key2", setOf(2, 3, 4, 5))
    assertThat(selections()).hasSize(2)
    model.deselect()
    assertThat(selections()).hasSize(2)
    model.deselect()
    assertThat(selections()).hasSize(2)
  }

  @Test
  fun `clearing selections leaves no active key`() =
    testWithObserver(MultiSelectionModel<Int>::activeSelectionKey) { model, activeSelection ->
      model.setSelection("Key1", setOf(1, 2, 3))
      model.setSelection("Key2", setOf(2, 3, 4, 5))
      assertThat(activeSelection()).isNotNull()
      model.clearSelection()
      assertThat(activeSelection()).isNull()
    }

  @Test
  fun `changing active selection notifies observer`() =
    testWithObserver(MultiSelectionModel<Int>::activeSelectionKey) { model, activeSelection ->
      model.setSelection("Key1", setOf(1, 2, 3))
      model.setSelection("Key2", setOf(2, 3, 4, 5))
      assertThat(activeSelection()).isEqualTo("Key2")
      model.setActiveSelection("Key1")
      assertThat(activeSelection()).isEqualTo("Key1")
    }

  @Test
  fun `setting non-existent active selection clears it`() =
    testWithObserver(MultiSelectionModel<Int>::activeSelectionKey) { model, activeSelection ->
      model.setSelection("Key1", setOf(1, 2, 3))
      model.setSelection("Key2", setOf(2, 3, 4, 5))
      model.setActiveSelection("Key3")
      assertThat(activeSelection()).isNull()
    }

  @Test
  fun `changing active selection changes its index`() =
    testWithObserver(MultiSelectionModel<Int>::activeSelectionIndex) { model, activeIndex ->
      model.setSelection("Key0", setOf(0, 1, 2))
      model.setSelection("Key1", setOf(1, 2, 3))
      model.setSelection("Key2", setOf(4, 5, 6))

      assertThat(activeIndex()).isEqualTo(2)

      model.setActiveSelection("Key1")
      assertThat(activeIndex()).isEqualTo(1)

      model.setActiveSelection("No")
      assertThat(activeIndex()).isEqualTo(-1)
    }

  private fun<T> testWithObserver(run: (MultiSelectionModel<T>, () -> List<Entry<T>>) -> Unit) =
    testWithObserver(MultiSelectionModel<T>::selections, run)

  private fun<T, O> testWithObserver(observe: (MultiSelectionModel<T>) -> O, run: (MultiSelectionModel<T>, () -> O) -> Unit) {
    val model = MultiSelectionModel<T>()
    val observer = AspectObserver()
    var observation = observe(model)
    model.addDependency(observer)
      .onChange(MultiSelectionModel.Aspect.SELECTIONS_CHANGED) { observation = observe(model) }
      .onChange(MultiSelectionModel.Aspect.ACTIVE_SELECTION_CHANGED) { observation = observe(model) }
    run(model) { observation.also { observer.hashCode() /* keep it live */ } }
  }
}