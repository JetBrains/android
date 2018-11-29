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
package com.android.tools.idea.naveditor.property

import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.CHANGE_PROPERTY
import com.google.wireless.android.sdk.stats.NavEditorEvent.Source.PROPERTY_INSPECTOR

class NavPropertyWrapper(private val toWrap: NlProperty) : NlProperty by toWrap {
  override fun setValue(value: Any?) {
    val orig = getValue()
    if (orig != value) {
      toWrap.setValue(value)
      NavUsageTracker.getInstance(model)
        .createEvent(CHANGE_PROPERTY)
        .withPropertyInfo(this, orig == null)
        .withSource(PROPERTY_INSPECTOR)
        .log()
    }
  }
}