/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.observable.expressions.value

import com.android.tools.idea.observable.InvalidationListener
import com.android.tools.idea.observable.ObservableValue
import com.android.tools.idea.observable.core.ObjectProperty
import com.android.tools.idea.observable.core.OptionalProperty

/**
 * Expression for converting a target optional value (that you know will always be present) into a
 * concrete value. This is a useful expression for wrapping Swing properties, which often represent
 * UI elements that technically return `null` but in practice never do.
 *
 * If the optional property you wrap is ever absent, this expression will throw an exception, so
 * be sure this is what you want to do. If you need more robust optional -> concrete handling,
 * consider using [TransformOptionalExpression] instead.
 */
class AsObjectProperty<T>(private val optionalProperty: OptionalProperty<T>) : ObjectProperty<T>(), InvalidationListener {

  init {
    optionalProperty.addWeakListener(this)
  }

  override fun get(): T {
    return optionalProperty.value
  }

  override fun setDirectly(value: T) {
    optionalProperty.setNullableValue(value)
  }

  override fun onInvalidated(sender: ObservableValue<*>) {
    notifyInvalidated()
  }
}
