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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.javaproject

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.JavaProjectProto

/** Entry point for the model of the Java Projects. Models a single module. */
interface JavaProject {
  val name: String
  val mainSourceSet: JavaSourceSet
  val testSourceSet: JavaSourceSet?
  val extraSourceSets: Collection<JavaSourceSet>
  val javaLanguageLevel: String

  fun toProto(converter: PathConverter): JavaProjectProto.JavaProject = JavaProjectProto.JavaProject.newBuilder()
    .setName(name)
    .setMainSourceSet(mainSourceSet.toProto(converter))
    .addAllExtraSourceSets(extraSourceSets.map { it.toProto(converter) })
    .setJavaLanguageLevel(javaLanguageLevel)
    .also {
      testSourceSet?.run { it.testSourceSet = this.toProto(converter)}
    }.build()!!

}