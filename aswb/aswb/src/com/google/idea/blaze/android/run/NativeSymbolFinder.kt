/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run

import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.base.sync.aspects.BlazeBuildOutputs
import com.google.idea.blaze.common.Label
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import java.io.File

/** Configures Blaze build to output native symbols and obtains symbol file paths.  */
interface NativeSymbolFinder {
  /** Returns additional build flags required to output native symbols.  */
  val additionalBuildFlags: String

  /** Returns native symbol files present in build output and store under [reference] label.*/
  fun getNativeSymbolsForBuild(
    project: Project, context: BlazeContext, reference: Label, outputs: BlazeBuildOutputs
  ): List<File>

  companion object {
    @JvmStatic
    fun getInstances(): List<NativeSymbolFinder> = EP_NAME.extensionList

    @JvmField
    val EP_NAME: ExtensionPointName<NativeSymbolFinder> =
      ExtensionPointName.create("com.google.idea.blaze.NativeSymbolFinder")

    @JvmStatic
    fun fetchNativeSymbols(project: Project, context: BlazeContext, reference: Label, buildOutputs: BlazeBuildOutputs): List<File> {
      return getInstances().flatMap { it.getNativeSymbolsForBuild(project, context, reference, buildOutputs) }
    }
  }
}
