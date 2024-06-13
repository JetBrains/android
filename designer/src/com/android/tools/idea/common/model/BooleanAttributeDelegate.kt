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
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Delegate for boolean attributes. [defaultForSet], if specified and non-null, indicates what value
 * should be represented by an absence of the property when <b>setting</b> only. (null will still be
 * returned when getting if the attribute is unset).
 */
open class BooleanAttributeDelegate(
  private val namespace: String?,
  private val propertyName: String,
  private val defaultForSet: Boolean? = false,
) : ReadWriteProperty<NlComponent, Boolean?> {

  // This has to be separate rather than just using default arguments due to KT-8834
  constructor(namespace: String?, propertyName: String) : this(namespace, propertyName, false)

  override operator fun getValue(thisRef: NlComponent, property: KProperty<*>): Boolean? {
    return thisRef.resolveAttribute(namespace, propertyName)?.toBoolean()
  }

  override operator fun setValue(thisRef: NlComponent, property: KProperty<*>, value: Boolean?) {
    thisRef.setAttribute(
      namespace,
      propertyName,
      if (value == defaultForSet) null else value?.toString(),
    )
  }
}

class BooleanAutoAttributeDelegate(propertyName: String, default: Boolean?) :
  BooleanAttributeDelegate(SdkConstants.AUTO_URI, propertyName, default) {
  // This has to be separate rather than just using default arguments due to KT-8834
  constructor(propertyName: String) : this(propertyName, false)
}
