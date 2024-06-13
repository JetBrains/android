package com.android.tools.idea.concurrency

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowableCollectionTest {
  @Suppress("UNREACHABLE_CODE")
  @Test
  fun testMap() {
    val originalCollection = listOf(
      1,
      2,
      3,
      4
    )

    val flowableCollection = FlowableCollection.Present(originalCollection)
    assertThat(
      flowableCollection
        .map { "A$it" }
        .asCollection())
      .containsExactly("A1", "A2", "A3", "A4")

    assertTrue(
      "FlowableCollection.Uninitialized#map should return FlowableCollection.Uninitialized",
      FlowableCollection.Uninitialized.map { "A$it" } is FlowableCollection.Uninitialized
    )
  }

  @Test
  fun testEmptyIsMappedToEmpty() {
    val emptyFlowableCollection = FlowableCollection.Present<Int>(emptyList())
      .map { it + 1 }
    assertTrue(emptyFlowableCollection is FlowableCollection.Present)
  }

  @Test
  fun testFlatMap() {
    val originalCollection = listOf(
      listOf(1),
      listOf(2,3),
      listOf(4)
    )

    val flowableCollection = FlowableCollection.Present(originalCollection)
    assertThat(
      flowableCollection
        .flatMap { it.asSequence() }
        .map { "A$it" }
        .asCollection())
      .containsExactly("A1", "A2", "A3", "A4")

    assertTrue(
      "FlowableCollection.Uninitialized#flatMap should return FlowableCollection.Uninitialized",
      FlowableCollection.Uninitialized.flatMap { sequenceOf<Nothing>() } is FlowableCollection.Uninitialized
    )
  }

  @Test
  fun testFilter() {
    val originalCollection = listOf(
      1,
      2,
      3,
      4
    )

    val flowableCollection = FlowableCollection.Present(originalCollection)
    assertEquals(
      FlowableCollection.Present(listOf(2, 4)),
      flowableCollection
        .filter { it % 2 == 0}
    )
  }
}