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

import com.android.tools.idea.common.property2.api.FlagPropertyItem
import com.android.tools.idea.common.property2.api.FlagsPropertyItem
import com.google.common.base.Joiner
import com.google.common.base.Splitter

class TestFlagsPropertyItem(
  namespace: String,
  name: String,
  flagNames: List<String>,
  values: List<Int>,
  initialValue: String? = null
) : TestPropertyItem(namespace, name, initialValue, null, null), FlagsPropertyItem<TestFlagPropertyItem> {
  override val children = mutableListOf<TestFlagPropertyItem>()

  val valueAsSet: HashSet<String>
    get() {
      val value = value ?: return HashSet()
      return HashSet(Splitter.on("|").trimResults().splitToList(value))
    }

  override val maskValue: Int
    get() {
      var mask = 0
      children.filter { valueAsSet.contains(it.name) }.forEach { mask = mask or it.maskValue }
      return mask
    }


  init {
    flagNames.forEachIndexed { index, flag -> children.add(TestFlagPropertyItem(namespace, flag, this, values[index])) }
  }

  override fun flag(itemName: String): TestFlagPropertyItem? = children.firstOrNull { it.name == itemName }

}

class TestFlagPropertyItem(
  namespace: String,
  name: String,
  override val flags: TestFlagsPropertyItem,
  override val maskValue: Int
) : TestPropertyItem(namespace, name, null, null, null), FlagPropertyItem {

  override var actualValue: Boolean
    get() = flags.valueAsSet.contains(name)
    set(value) {
      val set = flags.valueAsSet
      if (value) {
        set.add(name)
      }
      else {
        set.remove(name)
      }
      flags.value = Joiner.on("|").join(set)
    }

  override var value: String?
    get() = if (actualValue) "true" else "false"
    set(value) { actualValue = (value == "true") }

  override val effectiveValue: Boolean
    get() = (flags.maskValue and maskValue) == maskValue

  override var resolvedValue: String? = null

  override var isReference = false
}
