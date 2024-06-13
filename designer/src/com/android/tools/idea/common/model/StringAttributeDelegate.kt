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
package com.android.tools.idea.common.model

import com.android.SdkConstants
import com.intellij.util.text.nullize
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class StringAttributeDelegate(
  private val namespace: String?,
  private val propertyName: String,
) : ReadWriteProperty<NlComponent, String?> {
  override operator fun getValue(thisRef: NlComponent, property: KProperty<*>): String? {
    return thisRef.resolveAttribute(namespace, propertyName)
  }

  override operator fun setValue(thisRef: NlComponent, property: KProperty<*>, value: String?) {
    thisRef.setAttribute(namespace, propertyName, value.nullize())
  }
}

class StringAutoAttributeDelegate(propertyName: String) :
  StringAttributeDelegate(SdkConstants.AUTO_URI, propertyName)
