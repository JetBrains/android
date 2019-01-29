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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.legacyfacade.library

import com.android.builder.model.level2.Library
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.GlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.Level2GlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.toLegacy

open class LegacyGlobalLibraryMap(private val glm: GlobalLibraryMap) : Level2GlobalLibraryMap {
  override fun getLibraries(): Map<String, Library> = glm.libraries.mapValues { it.value.toLegacy() }
}
