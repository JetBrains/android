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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant.InstantRun
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldInstantRun
import java.io.File

open class LegacyInstantRun(private val instantRun: InstantRun) : OldInstantRun {
  override fun getInfoFile(): File = instantRun.infoFile
  override fun isSupportedByArtifact(): Boolean = instantRun.isSupportedByArtifact
  override fun getSupportStatus(): Int = instantRun.supportStatus.oldValue

  override fun toString(): String = "LegacyBaseArtifact{" +
                                    "infoFile=$infoFile," +
                                    "isSupportedByArtifact=$isSupportedByArtifact," +
                                    "supportStatus=$supportStatus" +
                                    "}"
}
