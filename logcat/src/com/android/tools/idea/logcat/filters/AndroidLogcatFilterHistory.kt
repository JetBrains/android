/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.filters

import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Transient

private const val MAX_HISTORY_SIZE = 20

/**
 * A list of filters used as history for [FilterTextField].
 *
 * This is actually maintained as 2 separate lists. A favorites list followed by a non-favorite list.
 */
@State(name = "AndroidLogcatFilterHistory", storages = [Storage("androidLogcatFilterHistory.xml")])
internal class AndroidLogcatFilterHistory(
  var favorites: MutableList<String> = mutableListOf(),
  var named: MutableList<String> = mutableListOf(),
  var nonFavorites: MutableList<String> = mutableListOf(),
  var mostRecentlyUsed: String = AndroidLogcatSettings.getInstance().defaultFilter,
  @Transient
  private val maxNonFavoriteItems: Int = MAX_HISTORY_SIZE,
) : PersistentStateComponent<AndroidLogcatFilterHistory> {

  val items get() = favorites + named + nonFavorites

  fun add(filterParser: LogcatFilterParser, filter: String, isFavorite: Boolean) {
    remove(filter)

    fun isNamed() = filterParser.parse(filter)?.filterName != null
    when {
      isFavorite -> favorites.add(0, filter)
      isNamed() -> named.add(0, filter)
      else -> {
        nonFavorites.add(0, filter)
        if (nonFavorites.size > maxNonFavoriteItems) {
          nonFavorites.removeLast()
        }
      }
    }
  }

  fun remove(filter: String) {
    nonFavorites.remove(filter)
    named.remove(filter)
    favorites.remove(filter)
  }

  override fun getState(): AndroidLogcatFilterHistory = this

  override fun loadState(state: AndroidLogcatFilterHistory) {
    XmlSerializerUtil.copyBean(state, this)
  }

  companion object {
    fun getInstance(): AndroidLogcatFilterHistory =
      ApplicationManager.getApplication().getService(AndroidLogcatFilterHistory::class.java)
  }
}
