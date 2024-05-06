/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass

interface IrClassCache {
  operator fun get(name: String): IrClass?
  operator fun contains(name: String): Boolean
}

class MutableIrClassCache : IrClassCache {
  private val cache = mutableMapOf<String, IrClass>()

  override operator fun get(name: String): IrClass? = cache[name]
  override fun contains(name: String) = name in cache

  fun update(clazz: IrClass) {
    cache[clazz.name] = clazz
  }

  fun update(classes: List<IrClass>) {
    classes.forEach { update(it) }
  }

  fun clear() {
    cache.clear()
  }
}