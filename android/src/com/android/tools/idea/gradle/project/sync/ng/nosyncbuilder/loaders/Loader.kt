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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.loaders

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.Level2GlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject.NewGradleProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.repackage.com.google.protobuf.InvalidProtocolBufferException
import java.nio.file.Path

typealias LoaderConstructor = (Path, PathConverter) -> Loader

interface Loader {
  @Throws(InvalidProtocolBufferException::class)
  fun loadAndroidProject(variant: String): OldAndroidProject
  @Throws(InvalidProtocolBufferException::class)
  fun loadGlobalLibraryMap(): Level2GlobalLibraryMap
  @Throws(InvalidProtocolBufferException::class)
  fun loadGradleProject(): NewGradleProject
}