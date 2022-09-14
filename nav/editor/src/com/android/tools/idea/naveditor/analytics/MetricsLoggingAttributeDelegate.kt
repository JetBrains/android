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
package com.android.tools.idea.naveditor.analytics

import com.android.tools.idea.common.model.NlComponent
import com.google.wireless.android.sdk.stats.NavEditorEvent
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty1

/**
 * A property delegate that wraps another delegate (specifically an attribute-based delegate, e.g.
 * [com.android.tools.idea.common.model.BooleanAttributeDelegate]) and provides an additional method that sets the value and logs a metrics
 * event.
 *
 * Since we need both the attribute name (required for creating the delegate) and the wrapped delegate itself, the visible constructors
 * take the attribute name and namespace and the delegate constructor rather than the delegate object itself, to avoid redundancy or
 * potential accidental inconsistency between the logged property and the actually modified one.
 *
 * Since we're invoking get and set on the wrapped delegate directly in [set] we need a reference to the associated [KProperty1], in case
 * the wrapped property relies on that being set correctly.
 */
class MetricsLoggingAttributeDelegate<T> private constructor(
  private val attrName: String,
  private val delegate: ReadWriteProperty<NlComponent, T>,
  val property: KProperty1<NlComponent, T>) : ReadWriteProperty<NlComponent, T> by delegate
{
  constructor(delegateConstructor: (String) -> ReadWriteProperty<NlComponent, T>, attrName: String, p: KProperty1<NlComponent, T>)
    : this(attrName, delegateConstructor(attrName), p)

  constructor(delegateConstructor: (String, String) -> ReadWriteProperty<NlComponent, T>,
              namespace: String,
              attrName: String,
              p: KProperty1<NlComponent, T>)
    : this(attrName, delegateConstructor(namespace, attrName), p)

  fun set(component: NlComponent,
          value: T,
          site: NavEditorEvent.Source?) {
    val initial = delegate.getValue(component, property)
    if (initial != value) {
      delegate.setValue(component, property, value)
      NavUsageTracker.getInstance(component.model)
        .createEvent(NavEditorEvent.NavEditorEventType.CHANGE_PROPERTY)
        .withSource(site)
        .withAttributeInfo(attrName, component.tagName, initial == null)
        .log()
    }
    delegate.setValue(component, property, value)
  }
}