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
import com.android.tools.idea.npw.module.ChooseModuleTypeWizard
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep
import com.android.tools.idea.npw.project.ConfigureAndroidSdkStep
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.wizard.model.ModelWizard
import com.google.common.base.Preconditions
import com.intellij.ide.projectWizard.NewProjectWizardConstants.Generators
import com.intellij.ide.projectWizard.ProjectTypeStep
import com.intellij.ide.util.newProjectWizard.WizardDelegate
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractWizard
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle
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
  private var wizardAdapter: IdeaWizardDelegate? = null // null if no adapter has been instantiated

  override fun getBuilderId(): String = Generators.ANDROID
  override fun getPresentableName(): String = AndroidBundle.message("android.wizard.module.presentable.name")
  override fun getDescription(): String = AndroidBundle.message("android.wizard.module.description")

  override fun getNodeIcon(): Icon = StudioIcons.Common.ANDROID_HEAD
  override fun getParentGroup(): String = JavaModuleType.JAVA_GROUP
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
  override fun getCustomOptionsStep(ctx: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
    if (wizardAdapter == null) {
      createWizardAdaptor(ctx.getUserData(AbstractWizard.KEY)!!, if (ctx.isCreatingNewProject) WizardType.PROJECT else WizardType.MODULE, ctx.project)
    }

    return wizardAdapter!!.getCustomOptionsStep()
  }

  override fun doNextAction() = wizardAdapter!!.doNextAction()

  override fun doPreviousAction() = wizardAdapter!!.doPreviousAction()

  override fun doFinishAction() = wizardAdapter!!.doFinishAction()

  override fun canProceed(): Boolean {
    ApplicationManager.getApplication().invokeLater {
      // Unfortunately the [WizardDelegate] does not provide a call for "canGoBack", so we trigger an async full refresh of the buttons.
      wizardAdapter?.updateButtons()
    }
    return wizardAdapter!!.canProceed()
  }

  private fun createWizardAdaptor(hostWizard: AbstractWizard<*>, type: WizardType, project: Project?) {
    Preconditions.checkState(wizardAdapter == null, "Attempting to create a Wizard Adaptor when one already exists.")

    when (type) {
      WizardType.MODULE -> {
        val moduleDescriptions = ModuleDescriptionProvider.EP_NAME.extensions.flatMap { it.getDescriptions(project!!) }
        val chooseModuleWizard =
          ChooseModuleTypeWizard(project!!, ":", moduleDescriptions, ProjectSyncInvoker.DefaultProjectSyncInvoker())

        wizardAdapter = IdeaMultiWizardAdapter(hostWizard, chooseModuleWizard.mainPanel).apply {
          chooseModuleWizard.setWizardModelListenerAndFire { modelWizard ->
            setModelWizard(modelWizard)
          }
        }
      }
      WizardType.PROJECT -> {
        val modelWizard = ModelWizard.Builder().apply {
          if (IdeSdks.getInstance().androidSdkPath == null) {
            addStep(ConfigureAndroidSdkStep())
          }
          addStep(ChooseAndroidProjectStep(NewProjectModel()))
        }.build()
        wizardAdapter = IdeaWizardAdapter(hostWizard, modelWizard)
      }
    }
  }

  private enum class WizardType {
    PROJECT,
    MODULE
  }
}
