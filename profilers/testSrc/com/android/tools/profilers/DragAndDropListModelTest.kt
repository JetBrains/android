/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers

import org.junit.Test

import com.google.common.truth.Truth.assertThat
import org.jetbrains.annotations.NotNull

class DragAndDropListModelTest {
  private val FIRST = SimpleListModelElement("First")
  private val SECOND = SimpleListModelElement("Second")
  private val THIRD = SimpleListModelElement("Third")

  @Test
  fun testElementsAddedAlsoAddedToModelAtEnd() {
    val initialArray = arrayOf(FIRST, SECOND)
    val finalArray = arrayOf(FIRST, SECOND, THIRD)
    val model = DragAndDropListModel<SimpleListModelElement>()
    addElements(model, initialArray)
    validateOrder(model, initialArray)
    model.insertOrderedElement(finalArray[2])
    validateOrder(model, finalArray)
  }

  @Test
  fun testElementsMovedAreOrderedInModel() {
    val initialArray = arrayOf(FIRST, SECOND, THIRD)
    val finalArray = arrayOf(THIRD, FIRST, SECOND)
    val model = DragAndDropListModel<SimpleListModelElement>()
    addElements(model, initialArray)
    model.moveElementTo(THIRD, 0);
    validateOrder(model, finalArray)
  }

  @Test
  fun testElementsRemovedAreRemovedFromModel() {
    val initialArray = arrayOf(FIRST, SECOND, THIRD)
    val finalArray = arrayOf(FIRST, SECOND)
    val model = DragAndDropListModel<SimpleListModelElement>()
    addElements(model, initialArray)
    model.removeOrderedElement(THIRD)
    validateOrder(model, finalArray)
  }

  @Test
  fun testElementsOrderedRemovedAndAddedMaintainOrder() {
    val initialArray = arrayOf(FIRST, SECOND, THIRD)
    val finalArray = arrayOf(FIRST, THIRD, SECOND)
    val model = DragAndDropListModel<SimpleListModelElement>()
    addElements(model, initialArray)
    // Move Third between First and Second -> First, Third, Second
    model.moveElementTo(THIRD, 1)
    validateOrder(model, finalArray)
    // Remove First -> Third, Second
    model.removeOrderedElement(FIRST)
    // Add First back -> First, Third, Second, order should be the same.
    model.insertOrderedElement(FIRST)
    validateOrder(model, finalArray)
  }

  @Test
  fun testMovingElementsToEndOfListDoesNotThrowException() {
    val initialArray = arrayOf(FIRST, SECOND, THIRD)
    val finalArray = arrayOf(SECOND, THIRD, FIRST)
    val model = DragAndDropListModel<SimpleListModelElement>()
    addElements(model, initialArray)
    // Move Third between First and Second -> First, Third, Second
    model.moveElementTo(FIRST, 3)
    validateOrder(model, finalArray)
  }

  @Test(expected = UnsupportedOperationException::class)
  fun testAddElementThrowsException() {
    val model = DragAndDropListModel<SimpleListModelElement>()
    model.addElement(SimpleListModelElement("Unsupported"))
  }

  @Test(expected = UnsupportedOperationException::class)
  fun testAddElementException() {
    val model = DragAndDropListModel<SimpleListModelElement>()
    model.add(0, SimpleListModelElement("Unsupported"))
  }

  @Test(expected = UnsupportedOperationException::class)
  fun testRemoveThrowsException() {
    val model = DragAndDropListModel<SimpleListModelElement>()
    model.remove(0)
  }

  @Test(expected = UnsupportedOperationException::class)
  fun testRemoveElementThrowsException() {
    val model = DragAndDropListModel<SimpleListModelElement>()
    model.removeElement("Unsupported")
  }

  private fun addElements(model: DragAndDropListModel<SimpleListModelElement>, values: Array<SimpleListModelElement>) {
    for (i in 0 until values.size) {
      model.insertOrderedElement(values[i])
    }
  }

  private fun validateOrder(model: DragAndDropListModel<SimpleListModelElement>, values: Array<SimpleListModelElement>) {
    assertThat(model.size).isEqualTo(values.size)
    for (i in 0 until model.size) {
      assertThat(model[i]).isEqualTo(values[i])
    }
  }

  private inner class SimpleListModelElement(@NotNull private val value: String) : DragAndDropModelListElement {
    override fun getId(): Int {
      return value.hashCode()
    }
  }
}
