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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.javaproject

import com.android.java.model.SourceSet
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.javaproject.JavaProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.javaproject.JavaSourceSet
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.OldJavaProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.toNew
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.JavaProjectProto

const val MAIN_SOURCE_SET = "main"
const val TEST_SOURCE_SET = "test"

data class NewJavaProject(
  override val name: String,
  override val mainSourceSet: JavaSourceSet,
  override val testSourceSet: JavaSourceSet?,
  override val extraSourceSets: Collection<JavaSourceSet>,
  override val javaLanguageLevel: String
) : JavaProject {
  constructor(oldJavaProject: OldJavaProject) : this(
    oldJavaProject.name,
    NewJavaSourceSet(oldJavaProject.sourceSets.first { it.name == MAIN_SOURCE_SET }),
    (oldJavaProject.sourceSets.firstOrNull { it.name == TEST_SOURCE_SET })?.toNew(),
    buildExtraSourceSets(oldJavaProject.sourceSets),
    oldJavaProject.javaLanguageLevel
  )

  constructor(proto: JavaProjectProto.JavaProject, converter: PathConverter) : this(
    proto.name,
    NewJavaSourceSet(proto.mainSourceSet, converter),
    NewJavaSourceSet(proto.testSourceSet, converter),
    proto.extraSourceSetsList.map { NewJavaSourceSet(it, converter) },
    proto.javaLanguageLevel
  )
}

private fun buildExtraSourceSets(sourceSets: Collection<SourceSet>): Collection<JavaSourceSet> {
  return sourceSets
    .filter { it.name !in listOf(MAIN_SOURCE_SET, TEST_SOURCE_SET) }
    .map { NewJavaSourceSet(it) }
}
