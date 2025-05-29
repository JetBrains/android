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

  @Test
  fun testChunked() {
    val originalCollection = listOf(1, 2, 3, 4)
    val flowableCollection = FlowableCollection.Present(originalCollection)

    assertEquals(FlowableCollection.Present(listOf(listOf(1), listOf(2), listOf(3), listOf(4))), flowableCollection.chunked(1))
    assertEquals(FlowableCollection.Present(listOf(listOf(1,2), listOf(3,4))), flowableCollection.chunked(2))
    assertEquals(FlowableCollection.Present(listOf(listOf(1,2,3), listOf(4))), flowableCollection.chunked(3))
    assertEquals(FlowableCollection.Present(listOf(listOf(1,2,3,4))), flowableCollection.chunked(4))
    assertEquals(FlowableCollection.Present(listOf(listOf(1,2,3,4))), flowableCollection.chunked(5))
  }

  @Test
  fun testGetOrNull() {
    val originalCollection = listOf(1, 2, 3, 4)
    val flowableCollection = FlowableCollection.Present(originalCollection)

    assertEquals(1, flowableCollection.getOrNull(0))
    assertEquals(2, flowableCollection.getOrNull(1))
    assertEquals(3, flowableCollection.getOrNull(2))
    assertEquals(4, flowableCollection.getOrNull(3))
    assertEquals(null, flowableCollection.getOrNull(4))
  }

  @Test
  fun testSizeOrNull() {
    var flowableCollection: FlowableCollection<Int> = FlowableCollection.Uninitialized
    assertEquals(null, flowableCollection.sizeOrNull())

    var collection = listOf(1, 2, 3, 4)
    flowableCollection = FlowableCollection.Present(collection)

    assertEquals(4, flowableCollection.sizeOrNull())

    collection = emptyList()
    flowableCollection = FlowableCollection.Present(collection)

    assertEquals(0, flowableCollection.sizeOrNull())
  }
}