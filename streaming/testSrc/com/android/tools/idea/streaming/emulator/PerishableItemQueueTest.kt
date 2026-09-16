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
package com.android.tools.idea.streaming.emulator

import com.android.testutils.waitForCondition
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.util.containers.ContainerUtil
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test

/** Tests for [PerishableItemQueue]. */
class PerishableItemQueueTest {

  @get:Rule val disposableRule = DisposableRule()

  private val queue = PerishableItemQueue<TestItem>().also { Disposer.register(disposableRule.disposable, it) }

  private data class TestItem(val id: Int, override val expirationTime: Instant) : Perishable

  @Test
  fun testOrdering() {
    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

    val item1 = TestItem(1, now + 10.seconds)
    val item2 = TestItem(2, now + 20.seconds)
    val item3 = TestItem(3, now + 5.seconds)
    val item4 = TestItem(4, now + 20.seconds)

    queue.add(item1)
    queue.add(item2)
    queue.add(item3)
    queue.add(item4)

    assertThat(queue.toList()).containsExactly(item3, item1, item2, item4).inOrder()

    assertThat(queue.poll()).isEqualTo(item3)
    assertThat(queue.poll()).isEqualTo(item1)
    assertThat(queue.poll()).isEqualTo(item2)
    assertThat(queue.poll()).isEqualTo(item4)
    assertThat(queue.poll()).isNull()
  }

  @Test
  fun testIterator() {
    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())

    queue.add(TestItem(1, now + 10.seconds))
    queue.add(TestItem(2, now + 20.seconds))

    val iterator = queue.iterator()
    assertThat(iterator.hasNext()).isTrue()
    val first = iterator.next()
    assertThat(first.id).isEqualTo(1)

    // Add another item while iterating.
    queue.add(TestItem(3, now + 5.seconds))
    queue.add(TestItem(4, now + 30.seconds))

    // Iterator should not see new items.
    assertThat(iterator.hasNext()).isTrue()
    val second = iterator.next()
    assertThat(second.id).isEqualTo(2)

    assertThat(iterator.hasNext()).isFalse()

    // Test remove through iterator.
    val iteratorToRemove = queue.iterator()
    assertThat(iteratorToRemove.next().id).isEqualTo(3) // The newly added item 3 is now first
    iteratorToRemove.remove()

    assertThat(queue.map { it.id }).containsExactly(1, 2, 4).inOrder()
  }

  @Test
  fun testExpiration() {
    val expiredItems = ContainerUtil.createLockFreeCopyOnWriteList<TestItem>()

    queue.addExpirationListener(
      object : PerishableItemQueue.ExpirationListener<TestItem> {
        override fun itemExpired(item: TestItem) {
          expiredItems.add(item)
        }
      }
    )

    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    val item1 = TestItem(1, now + 100.milliseconds)
    val item2 = TestItem(2, now + 300.milliseconds)
    val item3 = TestItem(3, now + 10.seconds)

    queue.add(item1)
    queue.add(item2)
    queue.add(item3)

    // Wait for the first two items to expire.
    waitForCondition(2.seconds) { expiredItems.size >= 2 }

    assertThat(expiredItems).containsExactly(item1, item2).inOrder()
    assertThat(queue.toList()).containsExactly(item3)
  }

  @Test
  fun testAddAlreadyExpired() {
    val expiredItems = mutableListOf<TestItem>()

    queue.addExpirationListener(
      object : PerishableItemQueue.ExpirationListener<TestItem> {
        override fun itemExpired(item: TestItem) {
          expiredItems.add(item)
        }
      }
    )

    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    val item1 = TestItem(1, now - 1.seconds)

    val added = queue.add(item1)

    assertThat(added).isFalse()
    assertThat(queue).isEmpty()
    assertThat(expiredItems).containsExactly(item1)
  }
}
