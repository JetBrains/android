/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import java.util.concurrent.TimeUnit.DAYS

/**
 * Provides settings for a basic reminder:
 * 1 - Monitors the passing of a specific [timePeriod]
 * 2 - Tracks whether the reminder has been ignored for this project
 * 3 - Tracks whether the reminder has been ignored for every project (for the Application)
 *
 * The settings are persisted across instances of this object, as long as two instances were created with the same
 * [settingsPropertyRoot] they will reuse the same information. This reminder is backed by intellijs [PropertiesComponent].
 */
open class TimeBasedReminder(
  protected val project: Project,
  protected val settingsPropertyRoot: String,
  private val timePeriod: Long = DAYS.toMillis(1)
) {
  val doNotAskForProjectPropertyString = "$settingsPropertyRoot.do.not.ask.for.project"
  var doNotAskForApplication: Boolean
    get() =  PropertiesComponent.getInstance().getBoolean("$settingsPropertyRoot.do.not.show.again", false)
    set(value) = PropertiesComponent.getInstance().setValue("$settingsPropertyRoot.do.not.show.again", value)
  var doNotAskForProject: Boolean
    get() =  PropertiesComponent.getInstance(project).getBoolean(doNotAskForProjectPropertyString, false)
    set(value) = PropertiesComponent.getInstance(project).setValue(doNotAskForProjectPropertyString, value)
  var lastTimeStamp: Long
    get() =  PropertiesComponent.getInstance(project).getLong("$settingsPropertyRoot.last.time.stamp", 0L)
    set(value) = PropertiesComponent.getInstance(project).setValue("$settingsPropertyRoot.last.time.stamp", value.toString())

  /**
   * Returns true iff "Do not ask for project" is false and "Do not ask for application" is false and
   * the given [timePeriod] has passed.
   */
  @JvmOverloads
  open fun shouldAsk(currentTime: Long = System.currentTimeMillis()) : Boolean {
    if (doNotAskForApplication || doNotAskForProject) return false
    return (currentTime - lastTimeStamp) >= timePeriod
  }

  fun updateLastTimestamp() {
    lastTimeStamp = System.currentTimeMillis()
  }
}
