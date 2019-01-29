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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.gradleproject

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.GradleProjectProto
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import org.gradle.tooling.model.ProjectIdentifier

data class NewGradleTask(
  private val name: String,
  private val displayName: String,
  private val path: String,
  private val isPublic: Boolean,
  private val group: String?,
  private val description: String?
) : GradleTask {

  /**
   * [GradleProject] which contains the task.
   *
   * This is required but unfortunately cannot be initialized in constructor because [GradleProject] has a reference to [GradleTask] too.
   * Thereby "natural" building will cause infinite constructor calls. Also it will break default data class equality, toString etc.
  */
  private lateinit var project: GradleProject

  constructor(proto: GradleProjectProto.GradleTask): this(
    proto.name,
    proto.displayName,
    proto.path,
    proto.isPublic,
    if (proto.hasGroup()) proto.group else null,
    if (proto.hasDescription()) proto.description else null
  )

  override fun getName(): String = name
  override fun getDisplayName(): String = displayName
  override fun getPath(): String = path
  override fun isPublic(): Boolean = isPublic
  override fun getGroup(): String? = group
  override fun getDescription(): String? = description
  override fun getProject(): GradleProject = project
  override fun getProjectIdentifier(): ProjectIdentifier = project.projectIdentifier

  fun setProject(project: GradleProject) {
    if (this::project.isInitialized) {
      throw IllegalStateException("GradleProject for the GradleTask can be set up only once")
    }
    this.project = project
  }
}
