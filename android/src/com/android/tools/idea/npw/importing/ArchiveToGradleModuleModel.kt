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
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils.getContainingModule
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
class ArchiveToGradleModuleModel(val project: Project,
                                 private val myProjectSyncInvoker: ProjectSyncInvoker) : WizardModel() {
  private val myArchive: StringProperty = StringValueProperty()
  private val myGradlePath: StringProperty = StringValueProperty()
  private val myMoveArchive: BoolProperty = BoolValueProperty()

  init {
    myArchive.addConstraint { it.trim() }
    myGradlePath.addConstraint { it.trim() }
    myArchive.set(project.basePath!!)
  }

  fun archive(): StringProperty {
    return myArchive
  }

  fun gradlePath(): StringProperty {
    return myGradlePath
  }

  fun moveArchive(): BoolProperty {
    return myMoveArchive
  }

  fun inModule(): BooleanExpression = object : BooleanExpression(myArchive) {
    override fun get(): Boolean = getContainingModule(File(myArchive.get()), project) != null
  }

  public override fun handleFinished() {
    val path = myGradlePath.get()
    CreateModuleFromArchiveAction(
      project,
      if (path.startsWith(GRADLE_PATH_SEPARATOR)) path else GRADLE_PATH_SEPARATOR + path,
      myArchive.get(),
      myMoveArchive.get(),
      getContainingModule(File(myArchive.get()), project)).execute()
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      assert(ApplicationManager.getApplication().isDispatchThread)
      myProjectSyncInvoker.syncProject(project)
    }
  }
}