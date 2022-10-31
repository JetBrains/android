/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.npw.actions.NewAndroidComponentAction
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.fromFacet
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.project.getPackageForPath
import com.android.tools.idea.templates.AdditionalTemplateActionsProvider
import com.android.tools.idea.util.androidFacet
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.idea.wizard.template.WizardUiContext
import com.android.tools.idea.wizard.ui.SimpleStudioWizardLayout
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.google.common.collect.Table
import com.google.common.collect.TreeBasedTable
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.MENU_GALLERY
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import icons.StudioIcons
import org.jetbrains.android.util.AndroidBundle.message
import org.jetbrains.annotations.PropertyKey

/**
 * Handles locating templates and providing template metadata
 */
class TemplateManager private constructor() {
  /** Template info needed by the menus, so that we don't need to keep a full instance of [Template] in memory */
  private data class TemplateInfo(val minSdk: Int, val constraints: Collection<TemplateConstraint>)

  /** Lock protecting access to [_categoryTable]  */
  private val CATEGORY_TABLE_LOCK = Any()

  /** Table mapping (Category, Template Name) -> Template File  */
  @GuardedBy("CATEGORY_TABLE_LOCK")
  private var _categoryTable: Table<Category, String, TemplateInfo>? = null

  @get:GuardedBy("CATEGORY_TABLE_LOCK")
  private val categoryTable: Table<Category, String, TemplateInfo>?
    get() {
      if (_categoryTable == null) {
        reloadCategoryTable()
      }
      return _categoryTable
    }

  private val topGroup = DefaultActionGroup("AndroidTemplateGroup", false)

  @Slow
  fun getTemplateCreationMenu(): ActionGroup {
    refreshDynamicTemplateMenu()
    return topGroup
  }

  @Slow
  fun refreshDynamicTemplateMenu() = synchronized(CATEGORY_TABLE_LOCK) {
    topGroup.apply {
      removeAll()
      addSeparator()
    }

    val am = ActionManager.getInstance()
    reloadCategoryTable() // Force reload
    for (category in categoryTable!!.rowKeySet()) {
      // Create the menu group item
      val categoryGroup: NonEmptyActionGroup = object : NonEmptyActionGroup() {
        override fun update(e: AnActionEvent) {
          updateAction(e, category.name, childrenCount > 0, false)
        }
      }
      categoryGroup.isPopup = true
      fillCategory(categoryGroup, category, am)
      topGroup.add(categoryGroup)
      setPresentation(category, categoryGroup)
    }
  }

  @GuardedBy("CATEGORY_TABLE_LOCK")
  private fun fillCategory(categoryGroup: NonEmptyActionGroup, category: Category, am: ActionManager) {
    val categoryRow = _categoryTable!!.row(category)

    fun addCategoryGroup(category: Category, name: String, @PropertyKey(resourceBundle = "messages.AndroidBundle") messageKey: String) {
      val galleryAction: AnAction = object : AnAction() {
        override fun update(e: AnActionEvent) {
          updateAction(e, "Gallery...", true, true)
        }

        override fun actionPerformed(e: AnActionEvent) {
          showWizardDialog(e, category.name, message(messageKey, FormFactor.MOBILE.id), "New $name")
        }
      }
      categoryGroup.add(galleryAction)
      categoryGroup.addSeparator()
      setPresentation(category, galleryAction)
    }

    if (category == Category.Activity) {
      addCategoryGroup(category, "Android Activity", "android.wizard.activity.add")
    }

    if (StudioFlags.NPW_SHOW_FRAGMENT_GALLERY.get() && category == Category.Fragment) {
      addCategoryGroup(category, "Android Fragment", "android.wizard.fragment.add")
    }

    for (templateName in categoryRow.keys) {
      val template = _categoryTable!![category, templateName]!!
      val templateAction = NewAndroidComponentAction(category, templateName, template.minSdk, template.constraints)
      val actionId = ACTION_ID_PREFIX + category + templateName
      am.replaceAction(actionId, templateAction)
      categoryGroup.add(templateAction)
    }

    val providers = AdditionalTemplateActionsProvider.EP_NAME.extensionList
    for (provider in providers) {
      for (anAction in provider.getAdditionalActions(category)) {
        categoryGroup.add(anAction)
      }
    }
  }

  @Slow
  @GuardedBy("CATEGORY_TABLE_LOCK")
  private fun reloadCategoryTable() {
    _categoryTable = TreeBasedTable.create()

    TemplateResolver.getAllTemplates()
      .filter { WizardUiContext.MenuEntry in it.uiContexts }
      .forEach { addTemplateToTable(it) }
  }

  @GuardedBy("CATEGORY_TABLE_LOCK")
  private fun addTemplateToTable(template: Template) = with(template) {
    val existingTemplate = _categoryTable!![category, name]
    if (existingTemplate == null) {
      _categoryTable!!.put(category, name, TemplateInfo(template.minSdk, template.constraints))
    }
  }

  companion object {
    /**
     * A directory relative to application home folder where we can find an extra template folder. This lets us ship more up-to-date
     * templates with the application instead of waiting for SDK updates.
     */
    private const val CATEGORY_ACTIVITY = "Activity"
    private const val CATEGORY_FRAGMENT = "Fragment"
    private const val ACTION_ID_PREFIX = "template.create."

    @JvmStatic
    val instance = TemplateManager()

    private fun updateAction(event: AnActionEvent, actionText: String?, visible: Boolean, disableIfNotReady: Boolean) {
      val module = event.getData(PlatformCoreDataKeys.MODULE)
      val facet = module?.androidFacet
      val isProjectReady = facet != null && AndroidModel.get(facet) != null
      event.presentation.apply {
        text = actionText + (" (Project not ready)".takeUnless { isProjectReady } ?: "")
        isVisible = visible && facet != null && AndroidModel.isRequired(facet)
        isEnabled = !disableIfNotReady || isProjectReady
      }
    }

    private fun showWizardDialog(e: AnActionEvent, category: String, commandName: String, dialogTitle: String) {
      val projectSyncInvoker: ProjectSyncInvoker = DefaultProjectSyncInvoker()
      val module = PlatformCoreDataKeys.MODULE.getData(e.dataContext)!!
      val targetFile = CommonDataKeys.VIRTUAL_FILE.getData(e.dataContext)!!
      var targetDirectory = targetFile
      if (!targetDirectory.isDirectory) {
        targetDirectory = targetFile.parent
        assert(targetDirectory != null)
      }
      val facet = module.androidFacet
      assert(facet != null && AndroidModel.get(facet) != null)
      val moduleTemplates = facet!!.getModuleTemplates(targetDirectory)
      assert(moduleTemplates.isNotEmpty())
      val initialPackageSuggestion = facet.getPackageForPath(moduleTemplates, targetDirectory)
      val renderModel = fromFacet(
        facet, initialPackageSuggestion, moduleTemplates[0],
        commandName, projectSyncInvoker, true, MENU_GALLERY
      )
      val chooseTypeStep = when (category) {
        CATEGORY_ACTIVITY -> ChooseActivityTypeStep.forActivityGallery(renderModel, targetDirectory)
        CATEGORY_FRAGMENT -> ChooseFragmentTypeStep(renderModel, FormFactor.MOBILE, targetDirectory)
        else -> throw RuntimeException("Invalid category name: $category")
      }
      val wizard = ModelWizard.Builder().addStep(chooseTypeStep).build()
      val wizardLayout = SimpleStudioWizardLayout()
      StudioWizardDialogBuilder(wizard, dialogTitle).build(wizardLayout).show()
    }

    private fun setPresentation(category: Category, categoryGroup: AnAction) {
      categoryGroup.templatePresentation.apply {
        icon = StudioIcons.Common.ANDROID_HEAD
        text = category.name
      }
    }
  }
}