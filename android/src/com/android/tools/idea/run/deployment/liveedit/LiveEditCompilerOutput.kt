/*
 * Copyright (C) 2022 The Android Open Source Project
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

data class LiveEditCompilerOutput private constructor (val classes: Map<String, ByteArray>,
                                                       val supportClasses: Map<String, ByteArray>,
                                                       val groupIds: List<Int>,
                                                       val resetState: Boolean) {
  fun hasGroupIds() = groupIds.isNotEmpty()

  class Builder(
    var classes: HashMap<String, ByteArray> = HashMap(),
    var supportClasses: HashMap<String, ByteArray> = HashMap(),
    var groupIds: ArrayList<Int> = ArrayList(),
    var resetState: Boolean = false) {

    fun addClass(name: String, code: ByteArray) : Builder {
      classes[name] = code
      return this
    }

    fun addSupportClass(name: String, code: ByteArray): Builder {
      supportClasses[name] = code
      return this
    }

    fun addSupportClasses(classes: Map<String, ByteArray>): Builder {
      supportClasses.putAll(classes)
      return this
    }

    fun addGroupId(id: Int) : Builder{
      groupIds.add(id)
      return this
    }

    fun build() = LiveEditCompilerOutput(classes, supportClasses, groupIds, resetState)
  }
}