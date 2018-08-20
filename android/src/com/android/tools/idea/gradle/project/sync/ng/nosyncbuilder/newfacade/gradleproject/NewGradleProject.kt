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

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.PathConverter
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.GradleProjectProto
import org.gradle.tooling.model.*
import org.gradle.tooling.model.gradle.GradleScript
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import java.io.File

data class NewGradleProject(
  private val buildScript: GradleScript,
  private val buildDirectory: File,
  private val projectDirectory: File,
  private val tasks: Collection<NewGradleTask>,
  private val name: String,
  private val projectIdentifier: ProjectIdentifier,
  private val description: String?
) : GradleProject {

  // This is not in the main constructor because it breaks toString() etc. with infinite recursion
  private var parent: GradleProject? = null
  private var children: MutableSet<NewGradleProject> = mutableSetOf()

  lateinit var childrenNames: List<String>

  constructor(proto: GradleProjectProto.GradleProject, converter: PathConverter): this(
    NewGradleScript(proto.buildScript, converter),
    converter.fileFromProto(proto.buildDirectory),
    converter.knownDirs[PathConverter.DirType.MODULE]!!.toFile(),
    proto.tasksList.map { NewGradleTask(it) },
    proto.name,
    NewProjectIdentifier(proto.projectPath, converter.knownDirs[PathConverter.DirType.MODULE]!!.toFile()),
    if (proto.hasDescription()) proto.description else null
  ) {
    tasks.forEach { it.project = this}
    childrenNames = proto.childrenList
  }

  constructor(
    proto: GradleProjectProto.GradleProject,
    converter: PathConverter,
    oldProjectPath: String,
    newProjectPath: String,
    newName: String
  ): this(
    updateGradleProjectProto(proto, oldProjectPath, newProjectPath, newName),
    converter
  )

  override fun getBuildScript(): GradleScript = buildScript
  override fun getBuildDirectory(): File = buildDirectory
  override fun getProjectDirectory(): File = projectDirectory
  override fun getTasks(): DomainObjectSet<out GradleTask> = ImmutableDomainObjectSet(tasks)
  override fun getName(): String = name
  override fun getProjectIdentifier(): ProjectIdentifier = projectIdentifier
  override fun getDescription(): String? = description
  override fun getPath(): String = projectIdentifier.projectPath
  override fun getParent(): GradleProject? = parent
  override fun getChildren(): DomainObjectSet<out GradleProject> = ImmutableDomainObjectSet(children)

  override fun findByPath(p0: String): GradleProject? {
    if (path == p0) {
      return this
    }

    for (child in children) {
      val findByPathInChildResult = child.findByPath(p0)
      findByPathInChildResult?.let { return it }
    }

    return null
  }

  fun addChild(child: NewGradleProject) {
    if (child.parent != null && child.parent != this) {
      throw IllegalArgumentException("This child already has a parent")
    }
    child.parent = this
    children.add(child)
  }
}

// This is used for projects from the New Project Wizard
private fun updateGradleProjectProto(
  proto: GradleProjectProto.GradleProject,
  oldProjectPath: String,
  newProjectPath: String,
  newName: String
): GradleProjectProto.GradleProject {
  return proto.toBuilder().apply {
    name = newName
    projectPath = newProjectPath
    val unchangedTasks = tasksList
    clearTasks()
    addAllTasks(unchangedTasks.map { updateGradleTaskProto(it, oldProjectPath, newProjectPath) })
  }.build()!!
}

// This is used for projects from the New Project Wizard
private fun updateGradleTaskProto(
  proto: GradleProjectProto.GradleTask,
  oldProjectPath: String,
  newProjectPath: String
): GradleProjectProto.GradleTask {
  return proto.toBuilder().apply {
    path = proto.path.replace(oldProjectPath, newProjectPath)
    displayName = proto.displayName.replace(oldProjectPath, newProjectPath)
  }.build()!!
}
