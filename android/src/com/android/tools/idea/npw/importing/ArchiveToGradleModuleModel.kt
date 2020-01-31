/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.importing

import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.project.getContainingModule
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.core.StringValueProperty
import com.android.tools.idea.observable.expressions.bool.BooleanExpression
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Model that represents the import of an existing library (.jar or .aar) into a Gradle project as a new Module
 */
class ArchiveToGradleModuleModel(val project: Project, private val projectSyncInvoker: ProjectSyncInvoker) : WizardModel() {
  @JvmField val archive: StringProperty = StringValueProperty()
  @JvmField val gradlePath: StringProperty = StringValueProperty()
  @JvmField val moveArchive: BoolProperty = BoolValueProperty()

  init {
    archive.addConstraint(String::trim)
    gradlePath.addConstraint(String::trim)
    archive.set(project.basePath!!)
  }

  fun inModule(): BooleanExpression = object : BooleanExpression(archive) {
    override fun get(): Boolean = getContainingModule(File(archive.get()), project) != null
  }

  public override fun handleFinished() {
    createModuleFromArchive(
      project,
      GRADLE_PATH_SEPARATOR + gradlePath.get().removePrefix(GRADLE_PATH_SEPARATOR),
      File(archive.get()),
      moveArchive.get(),
      getContainingModule(File(archive.get()), project))

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      assert(ApplicationManager.getApplication().isDispatchThread)
      projectSyncInvoker.syncProject(project)
    }
  }
}