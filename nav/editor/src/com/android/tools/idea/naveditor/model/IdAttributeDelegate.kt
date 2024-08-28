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
package com.android.tools.idea.naveditor.model

import com.android.SdkConstants
import com.android.ide.common.resources.stripPrefixFromId
import com.android.tools.idea.common.model.NlComponent
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class IdAttributeDelegate(private val namespace: String?, private val propertyName: String) :
  ReadWriteProperty<NlComponent, String?> {
  override operator fun getValue(thisRef: NlComponent, property: KProperty<*>): String? {
    return thisRef.resolveAttribute(namespace, propertyName)?.let(::stripPrefixFromId)
  }

  override operator fun setValue(thisRef: NlComponent, property: KProperty<*>, value: String?) {
    thisRef.setAttribute(namespace, propertyName, value?.let { SdkConstants.ID_PREFIX + it })
  }
}

class IdAutoAttributeDelegate(propertyName: String) :
  IdAttributeDelegate(SdkConstants.AUTO_URI, propertyName)
