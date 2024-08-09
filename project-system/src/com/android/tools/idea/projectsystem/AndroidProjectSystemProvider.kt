/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.project.Project

/**
 * Interface to be implemented by extensions to the com.android.tools.idea.projectsystem extension
 * point. Implementations of this interface will receive a {@link Project} instance in their constructor,
 * and are responsible for creating [AndroidProjectSystem] instances.
 */
interface AndroidProjectSystemProvider {
  /**
   * Returns true if this instance is applicable to the project.
   * <p>
   * If the correct type of project system is unknown for a given project, it is autodetected using the
   * [isApplicable] method. Every possible [AndroidProjectSystemProvider] is created for the
   * project, and their [isApplicable] methods are invoked in sequence. The first one to return true
   * gets associated with the project and the remaining ones are discarded. For this reason,
   * implementations of this interface should not make any assumptions in their constructor that they
   * will only be instantiated on projects they apply to.
   */
  fun isApplicable(project: Project): Boolean

  /**
   * Unique ID for this type of project system. Each implementation should supply a different
   * id. This will be serialized with the project and should remain stable even if the implementation
   * class name changes. The empty string is reserved for the "default implementation" which will be
   * used if no other project system is applicable. All other implementations must use a non-empty
   * ID string.
   */
  val id: String

  /**
   * The project system factory for this project system.  See the comment for [AndroidProjectSystem] for
   * requirements for implementations of this factory.
   */
  fun projectSystemFactory(project: Project): AndroidProjectSystem
}