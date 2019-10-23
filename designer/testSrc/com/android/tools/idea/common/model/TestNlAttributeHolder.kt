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
package com.android.tools.idea.common.model

class TestNlAttributeHolder : NlAttributesHolder {

  val attributes = mutableMapOf<Pair<String?, String>, String>()

  override fun setAttribute(namespace: String?, attribute: String, value: String?) {
    val key = Pair(namespace, attribute)
    if (value == null) {
      attributes.remove(key)
    }
    else {
      attributes[key] = value
    }
  }

  override fun getAttribute(namespace: String?, attribute: String): String? {
    val key = Pair(namespace, attribute)
    return attributes[key]
  }
}
