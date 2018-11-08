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
package com.android.tools.idea.common.property2.impl.model.util

import com.android.tools.idea.common.property2.api.ActionIconButton

/**
 * A variant of [TestPropertyItem] where the value is not immediately set.
 *
 * The set is delayed for some reason, e.g. a delay in a transaction etc.
 */
class TestAsyncPropertyItem(
  namespace: String,
  name: String,
  initialValue: String? = null,
  browseButton: ActionIconButton? = null,
  colorButton: ActionIconButton? = null
) : TestPropertyItem(namespace, name, initialValue, browseButton, colorButton) {

  override var value: String? = initialValue
    set(value) {
      lastUpdatedValue = value
      updateCount++
    }

  var lastUpdatedValue: String? = null
    private set
}
