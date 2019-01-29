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
package com.android.tools.profilers.performance

import com.android.tools.profiler.proto.Common

import java.util.Random

class GeneratorProperties private constructor(private val myBuilder: Builder) {
  val session: Common.Session
    get() = myBuilder.session

  val pid: Int
    get() = myBuilder.pid

  class Builder(internal var session: Common.Session) {
    internal var pid: Int = 0

    fun setPid(pid: Int): Builder {
      this.pid = pid
      return this
    }

    fun build(): GeneratorProperties {
      return GeneratorProperties(this)
    }

  }
}
