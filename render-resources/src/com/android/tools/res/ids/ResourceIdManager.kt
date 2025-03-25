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
package com.android.tools.res.ids

import com.android.ide.common.rendering.api.ResourceReference
import java.util.function.Consumer

/**
 * Module service responsible for tracking the numeric resource ids we assign to resources, in an attempt to emulate aapt.
 */
interface ResourceIdManager : ResourceClassGenerator.NumericIdProvider {
  /**
   * Whether R classes with final ids are used for compiling custom views.
   */
  val finalIdsUsed: Boolean

  fun getCompiledId(resource: ResourceReference): Int?

  fun findById(id: Int): ResourceReference?

  /**
   * Resets the currently loaded compiled ids. Accepts a procedure ([Consumer]) that should call the passed [RClassParser] on every class
   * the ids should be extracted from.
   */
  fun resetCompiledIds(rClassProvider: Consumer<RClassParser>)

  fun resetDynamicIds()

  interface RClassParser {
    fun parseUsingReflection(rClass: Class<*>)

    /**
     * Method called when an R class should be parsed from byte code.
     *
     * @param rClass contains the bytecode of the to R class.
     * @param rClassProvider will be called to resolve the different R type classes (e.g. R$string).
     */
    fun parseBytecode(rClass: ByteArray, rClassProvider: (String) -> ByteArray)
  }
}
