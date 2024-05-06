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

/** Response from a single call to ListEvents. */
data class EventPage(val events: List<Event>, val token: String) {
  companion object {
    val EMPTY = EventPage(emptyList(), "")
  }
}

enum class EventMovement {
  NEXT,
  PREVIOUS
}

/**
 * A data structure for managing an ordered list of events. It is backed by an array list and an
 * integer representing the selected index.
 */
data class DynamicEventGallery(val events: List<Event>, val selectedIndex: Int, val token: String) {
  val selected: Event?
    get() = if (events.isEmpty()) null else events[selectedIndex]

  /** Can get the previous event. */
  fun hasPrevious() = selectedIndex > 0

  /**
   * Can get the next downloaded event. This function returning false does not indicate there aren't
   * more events to be requested from the server.
   */
  fun hasNext() = selectedIndex < events.size - 1

  /**
   * Whether there are more events to be requested from the server. Does not indicate the end of the
   * downloaded list of events.
   */
  fun canRequestMoreEvents() = token.isNotEmpty()

  /** Returns an event gallery with the index advanced if possible. */
  fun next(): DynamicEventGallery {
    if (hasNext()) {
      return DynamicEventGallery(events, selectedIndex + 1, token)
    }
    return this
  }

  /** Returns an event gallery with the index put back in the previous position if possible. */
  fun previous(): DynamicEventGallery {
    if (hasPrevious()) {
      return DynamicEventGallery(events, selectedIndex - 1, token)
    }
    return this
  }

  /** Appends items to the end of the list and advances selected index by 1. */
  fun appendEventPage(page: EventPage) =
    DynamicEventGallery(this.events + page.events, selectedIndex, page.token)
}
