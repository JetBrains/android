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
package com.android.tools.idea.insights

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DynamicEventGalleryTest {

  @Test
  fun `reconcile additional event page`() {
    var gallery = DynamicEventGallery(listOf(Event("event1")), 0, "abc")
    assertThat(gallery.hasNext()).isFalse()
    assertThat(gallery.canRequestMoreEvents()).isTrue()
    assertThat(gallery.selected).isEqualTo(Event("event1"))

    gallery = gallery.appendEventPage(EventPage(listOf(Event("event2"), Event("event3")), ""))
    assertThat(gallery.hasNext()).isTrue()
    assertThat(gallery.canRequestMoreEvents()).isFalse()

    gallery = gallery.next()
    assertThat(gallery.selected).isEqualTo(Event("event2"))
    assertThat(gallery.hasNext()).isTrue()

    gallery = gallery.next()
    assertThat(gallery.selected).isEqualTo(Event("event3"))
    assertThat(gallery.hasNext()).isFalse()
  }

  @Test
  fun `empty gallery returns null for selected`() {
    val gallery = DynamicEventGallery(emptyList(), 0, "")
    assertThat(gallery.selected).isNull()
  }

  @Test
  fun `retrieving events outside of bounds returns null`() {
    var gallery = DynamicEventGallery(listOf(Event("event1"), Event("event2")), 0, "abc")
    assertThat(gallery.previous()).isSameAs(gallery)

    gallery = gallery.next()
    assertThat(gallery.selected).isEqualTo(Event("event2"))
    assertThat(gallery.next()).isSameAs(gallery)
  }
}
