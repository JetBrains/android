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
package com.android.tools.idea.npw.ideahost

import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.createWithDefaultGallery
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep
import com.android.tools.idea.npw.project.ConfigureAndroidSdkStep
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.wizard.model.ModelWizard
import com.google.common.base.Preconditions
import com.intellij.ide.projectWizard.ProjectTypeStep
import com.intellij.ide.util.newProjectWizard.WizardDelegate
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import icons.AndroidIcons
import javax.swing.Icon

/**
 * AndroidModuleBuilder integrates the AndroidStudio new project and new module wizards into the IDEA new project and new module wizards.
 *
 * The [ModuleBuilder] base class integrates with the IDEA New Project and New Module UIs. AndroidModuleBuilder extends it to provide
 * an "Android" entry in the list on the left of these UIs (see
 * [the IntelliJ SDK](http://www.jetbrains.org/intellij/sdk/docs/tutorials/project_wizard/module_types.html) for details).
 *
 * If a [ModuleBuilder] also implements [WizardDelegate] then [ProjectTypeStep] will display the [ModuleWizardStep]
 * provided by [ModuleBuilder.getCustomOptionsStep] and then delegate the behaviour of the wizard buttons via [WizardDelegate]'s
 * methods. Doing this bypasses the majority of [ModuleBuilder]'s functionality requiring AndroidModuleBuilder to stub out a few
 * methods. [AndroidModuleBuilder] delegates the implementation of [WizardDelegate] to [IdeaWizardAdapter] which manages the
 * underlying [ModelWizard] instance.
 */
class AndroidModuleBuilder : ModuleBuilder(), WizardDelegate {
  /**
   * This adapter class hosts the Android Studio [ModelWizard] instance
   */
  private var wizardAdapter: IdeaWizardAdapter? = null // null if no adapter has been instantiated

  override fun getBuilderId(): String? = javaClass.name
  override fun getPresentableName(): String = "Android"
  override fun getDescription(): String =
    "Android modules are used for developing apps to run on the <b>Android</b> operating system. An <b>Android</b> module " +
    "consists of one or more <b>Activities</b> and may support a number of form-factors including <b>Phone and Tablet</b>, <b>Wear</b> " +
    "and <b>Android Auto</b>."

  override fun getNodeIcon(): Icon = AndroidIcons.Android
  override fun getParentGroup(): String? = JavaModuleType.JAVA_GROUP
  override fun getModuleType(): ModuleType<*> = JavaModuleType.getModuleType()
  override fun modifyProjectTypeStep(settingsStep: SettingsStep): ModuleWizardStep? = null // Stubbed out. See class header.
  override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
    // Unused. See class header.
  }

  /**
   * Custom UI to be shown on the first wizard page.
   *
   * This is the point where we actually provide the wizard that we are going to show. Return a wrapper around the appropriate AndroidStudio
   * wizard which presents the entire wizard as if it was a single step. This method must be called before any of the methods on the
   * [WizardDelegate] interface can be called.
   *
   * @param ctx Provides information about how the wizard was created (i.e. new project or new module)
   * @param parentDisposable Controls the lifetime of the wizard
   */
  override fun getCustomOptionsStep(ctx: WizardContext, parentDisposable: Disposable): ModuleWizardStep? {
    if (wizardAdapter == null) {
      createWizardAdaptor(ctx.wizard, if (ctx.isCreatingNewProject) WizardType.PROJECT else WizardType.MODULE, ctx.project)
    }

    return wizardAdapter!!.proxyStep
  }

  override fun doNextAction() = wizardAdapter!!.doNextAction()

  override fun doPreviousAction() = wizardAdapter!!.doPreviousAction()

  override fun doFinishAction() = wizardAdapter!!.doFinishAction()

  override fun canProceed(): Boolean = wizardAdapter!!.canProceed()

  private fun createWizardAdaptor(hostWizard: AbstractWizard<*>, type: WizardType, project: Project?) {
    Preconditions.checkState(wizardAdapter == null, "Attempting to create a Wizard Adaptor when one already exists.")

    val builder = ModelWizard.Builder().apply {
      if (IdeSdks.getInstance().androidSdkPath == null) {
        addStep(ConfigureAndroidSdkStep())
      }
      if (type == WizardType.PROJECT) {
        addStep(ChooseAndroidProjectStep(NewProjectModel()))
      }
      else {
        addStep(createWithDefaultGallery(project!!, null, ProjectSyncInvoker.DefaultProjectSyncInvoker()))
      }
    }

    wizardAdapter = IdeaWizardAdapter(hostWizard, builder.build())
  }

  private enum class WizardType {
    PROJECT,
    MODULE
  }
}
