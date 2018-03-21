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
package com.android.tools.idea.gradle.structure.configurables.android

import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.ActionCallback
import com.intellij.ui.navigation.History
import com.intellij.ui.navigation.Place

/**
 * A base class for configurables for [PsChildModel] entities.
 */
abstract class ChildModelConfigurable<T : PsChildModel>(val model: T) : NamedConfigurable<T>(), Place.Navigator {
  var myHistory: History? = null

  override fun isModified() = model.isModified
  override fun apply() {
    // Changes are applied at the Project/<All modules> level.
  }

  override fun getDisplayName() = model.name
  override fun setDisplayName(name: String?) = throw UnsupportedOperationException()
  override fun getEditableObject() = model

  override fun setHistory(history: History?) {
    this.myHistory = history
  }

  override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback = ActionCallback.DONE
  override fun queryPlace(place: Place) = Unit
}