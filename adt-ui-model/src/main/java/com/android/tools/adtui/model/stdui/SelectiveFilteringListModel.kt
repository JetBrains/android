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
package com.android.tools.adtui.model.stdui

import com.intellij.ui.speedSearch.FilteringListModel
import java.lang.reflect.InvocationTargetException
import javax.swing.ListModel

/**
 * A variation of a [FilteringListModel] that can pull a perfect match to the front of the list.
 */
class SelectiveFilteringListModel<T>(originalModel: ListModel<T>): FilteringListModel<T>(originalModel) {
  private val data: MutableList<T>? = lookupData()
  var perfectMatch: T? = null

  override fun addToFiltered(element: T) {
    if (element == perfectMatch && data != null) {
      data.add(0, element)
    }
    else {
      super.addToFiltered(element)
    }
  }

  private fun lookupData(): MutableList<T>? {
    // TODO: Make myData protected in FilteringListModel
    // Until then: wrap a catch all around the
    try {
      val field = FilteringListModel::class.java.getDeclaredField("myData")
      field.isAccessible = true
      @Suppress("UNCHECKED_CAST")
      return field.get(this) as (MutableList<T>)
    }
    catch (ex: NoSuchFieldException) {}
    catch (ex: IllegalAccessException) {}
    catch (ex: InvocationTargetException) {}
    return null
  }
}
