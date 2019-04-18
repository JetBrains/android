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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.gradleproject

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.GradleProjectProto
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask

/** There is no new [GradleProject] interface, so we just add an extension [toProto] function for consistency */
fun GradleProject.toProto(converter: PathConverter): GradleProjectProto.GradleProject {
  val buildScriptProto = GradleProjectProto.GradleScript.newBuilder().apply {
    buildScript.sourceFile?.let { setSourceFile(converter.fileToProto(it)) }
  }.build()!!

  return GradleProjectProto.GradleProject.newBuilder()
    .setBuildScript(buildScriptProto)
    .setBuildDirectory(converter.fileToProto(buildDirectory))
    .addAllTasks(tasks.map(GradleTask::toProto))
    .setName(name)
    .setProjectPath(path)
    .addAllChildren(children.map { it.name })
    .also {
      description?.run { it.description = this }
    }.build()
}