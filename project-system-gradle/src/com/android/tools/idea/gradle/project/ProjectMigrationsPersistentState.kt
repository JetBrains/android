/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import org.jetbrains.annotations.VisibleForTesting

/**
 * A [PersistentStateComponent] that stores the different project migrations state under .idea/migrations.xml with the following format:
 * <component name="ProjectMigrations">
 *   <option name="MigrateToGradleLocalJavaHome">
 *     <set>
 *       <option value="$PROJECT_DIR$/project_root1" />
 *       <option value="$PROJECT_DIR$/project_root2" />
 *     </set>
 *   </option>
 *   <option name="MigrateToX" value="false" />
 * </component>
 */
@State(name = "ProjectMigrations", storages = [Storage("migrations.xml")])
class ProjectMigrationsPersistentState @VisibleForTesting constructor() : PersistentStateComponent<ProjectMigrationsPersistentState> {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectMigrationsPersistentState {
      return project.getService(ProjectMigrationsPersistentState::class.java)
    }
  }

  @OptionTag("MigrateToGradleLocalJavaHome")
  val migratedGradleRootsToGradleLocalJavaHome: MutableSet<String> = mutableSetOf()

  override fun getState() = this
  override fun loadState(state: ProjectMigrationsPersistentState) = XmlSerializerUtil.copyBean(state, this)
}