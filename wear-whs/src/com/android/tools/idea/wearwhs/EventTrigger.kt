/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.wearwhs

import com.android.tools.idea.wearwhs.WearWhsBundle.message

private val METADATA_SHOT_SWING_TYPE_KEY = "golf_shot_swing_type"
private val METADATA_SHOT_SWING_TYPE_VALUE_PUTT = "putt"
private val METADATA_SHOT_SWING_TYPE_VALUE_PARTIAL = "partial"
private val METADATA_SHOT_SWING_TYPE_VALUE_FULL = "full"

/**
 * Data class representing WHS event triggers such as User Asleep or exercise paused. [eventKey] is the
 * key for this data type in WHS, [eventLabel] is the user displayed label of the trigger.
 */
data class EventTrigger(
  val eventKey: String,
  val eventLabel: String,
  val eventMetadata: Map<String, String> = emptyMap()
)

/**
 * Data class representing a group of WHS event triggers, as displayed in the UI.
 */
data class EventTriggerGroup(
  val eventGroupLabel: String,
  val eventTriggers: List<EventTrigger>
)

/**
 * List of default event trigger groups, displayed in the UI.
 */
val EVENT_TRIGGER_GROUPS = listOf(
  EventTriggerGroup(
    eventGroupLabel = message("wear.whs.event.trigger.auto.pause.resume.group"),
    eventTriggers = listOf(
      EventTrigger(
        eventKey = "whs.AUTO_PAUSE_DETECTED",
        eventLabel = message("wear.whs.event.trigger.auto.pause")
      ),
      EventTrigger(
        eventKey = "whs.AUTO_RESUME_DETECTED",
        eventLabel = message("wear.whs.event.trigger.auto.resume")
      )
    )
  ),
  EventTriggerGroup(
    eventGroupLabel = message("wear.whs.event.trigger.sleep.group"),
    eventTriggers = listOf(
      EventTrigger(
        eventKey = "whs.START_SLEEPING",
        eventLabel = message("wear.whs.event.trigger.asleep")
      ),
      EventTrigger(
        eventKey = "whs.STOP_SLEEPING",
        eventLabel = message("wear.whs.event.trigger.awake")
      ),
    )
  ),
  EventTriggerGroup(
    eventGroupLabel = message("wear.whs.event.trigger.golf.shots.group"),
    eventTriggers = listOf(
      EventTrigger(
        eventKey = "whs.GOLF_SHOT",
        eventLabel = message("wear.whs.event.trigger.golf.shot.putt"),
        eventMetadata = mapOf(
          METADATA_SHOT_SWING_TYPE_KEY to METADATA_SHOT_SWING_TYPE_VALUE_PUTT
        )
      ),
      EventTrigger(
        eventKey = "whs.GOLF_SHOT",
        eventLabel = message("wear.whs.event.trigger.golf.shot.partial"),
        eventMetadata = mapOf(
          METADATA_SHOT_SWING_TYPE_KEY to METADATA_SHOT_SWING_TYPE_VALUE_PARTIAL
        )
      ),
      EventTrigger(
        eventKey = "whs.GOLF_SHOT",
        eventLabel = message("wear.whs.event.trigger.golf.shot.full"),
        eventMetadata = mapOf(
          METADATA_SHOT_SWING_TYPE_KEY to METADATA_SHOT_SWING_TYPE_VALUE_FULL
        )
      ),
    )
  )
)