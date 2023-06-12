/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.stubs.gradle

import org.gradle.tooling.model.BuildIdentifier
import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.GradleTask
import org.gradle.tooling.model.ProjectIdentifier
import org.gradle.tooling.model.gradle.GradleScript
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import java.io.File

class GradleProjectStub(
  private val name: String,
  private val path: String,
  rootDir: File,
  projectFile: File,
  vararg taskNames: String
) : GradleProject {
  private val projectIdentifier: ProjectIdentifier
  private val script: GradleScript
  private val tasks: MutableList<GradleTask>

  init {
    script = GradleScript { projectFile }
    val buildIdentifier = BuildIdentifier { rootDir }
    projectIdentifier = object : ProjectIdentifier {
      override fun getProjectPath() = path
      override fun getBuildIdentifier() = buildIdentifier
    }
    tasks = ArrayList()
    for (taskName in taskNames) {
      val task: GradleTask = object : GradleTask {
        override fun getProject() = this@GradleProjectStub
        override fun getPath(): String {
          throw UnsupportedOperationException()
        }
        override fun getName() = taskName
        override fun getDescription() = null
        override fun getGroup() = null
        override fun getProjectIdentifier(): ProjectIdentifier {
          // task.getProjectIdentifier() is only called serialization: if we need a value we could use `projectIdentifier`
          // but otherwise this is fine.
          throw UnsupportedOperationException()
        }
        override fun getDisplayName() = taskName

        override fun isPublic() = true
      }
      tasks.add(task)
    }
  }

  override fun getTasks(): DomainObjectSet<GradleTask> = ImmutableDomainObjectSet.of(tasks)
  override fun getParent() = null
  override fun getChildren(): DomainObjectSet<out GradleProject> {
    throw UnsupportedOperationException()
  }
  override fun getPath() = path
  override fun findByPath(path: String): GradleProject? {
    throw UnsupportedOperationException()
  }
  override fun getName() = name
  override fun getDescription(): String? {
    throw UnsupportedOperationException()
  }
  override fun getBuildScript() = script
  override fun getBuildDirectory() = null
  override fun getProjectDirectory() = null
  override fun getProjectIdentifier() = projectIdentifier
}