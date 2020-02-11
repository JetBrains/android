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
package com.android.tools.idea.templates

import com.android.SdkConstants
import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.repository.io.FileOpUtils
import com.android.tools.idea.actions.NewAndroidComponentAction
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.model.ProjectSyncInvoker.DefaultProjectSyncInvoker
import com.android.tools.idea.npw.model.RenderTemplateModel
import com.android.tools.idea.npw.model.RenderTemplateModel.Companion.fromFacet
import com.android.tools.idea.npw.project.getModuleTemplates
import com.android.tools.idea.npw.project.getPackageForPath
import com.android.tools.idea.npw.template.ChooseActivityTypeStep
import com.android.tools.idea.npw.template.ChooseFragmentTypeStep
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.utils.XmlUtils
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import com.google.common.collect.Table
import com.google.common.collect.TreeBasedTable
import com.intellij.ide.actions.NonEmptyActionGroup
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import icons.AndroidIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle
import java.io.File
import java.util.EnumSet
import java.util.function.Function
import java.util.stream.Collectors

/**
 * Handles locating templates and providing template metadata
 */
class TemplateManager private constructor() {
  /**
   * Cache for [.getTemplateMetadata]
   */
  private var templateMap: MutableMap<File, TemplateMetadata>? = null

  /** Lock protecting access to [.myCategoryTable]  */
  private val CATEGORY_TABLE_LOCK = Any()

  /** Table mapping (Category, Template Name) -> Template File  */
  @GuardedBy("CATEGORY_TABLE_LOCK")
  private var _categoryTable: Table<String?, String, File>? = null

  @get:GuardedBy("CATEGORY_TABLE_LOCK")
  private val categoryTable: Table<String?, String, File>?
    private get() {
      if (_categoryTable == null) {
        reloadCategoryTable(null)
      }
      return _categoryTable
    }

  private var topGroup: DefaultActionGroup? = null

  @Slow
  fun getTemplateCreationMenu(project: Project?): ActionGroup? {
    refreshDynamicTemplateMenu(project)
    return topGroup
  }

  @Slow
  fun refreshDynamicTemplateMenu(project: Project?) = synchronized(CATEGORY_TABLE_LOCK) {
    if (topGroup == null) {
      topGroup = DefaultActionGroup("AndroidTemplateGroup", false)
    }
    else {
      topGroup!!.removeAll()
    }
    topGroup!!.addSeparator()
    val am = ActionManager.getInstance()
    reloadCategoryTable(project) // Force reload
    for (category in categoryTable!!.rowKeySet()) {
      if (EXCLUDED_CATEGORIES.contains(category)) {
        continue
      }
      // Create the menu group item
      val categoryGroup: NonEmptyActionGroup = object : NonEmptyActionGroup() {
        override fun update(e: AnActionEvent) {
          updateAction(e, category, childrenCount > 0, false)
        }
      }
      categoryGroup.isPopup = true
      fillCategory(categoryGroup, category, am)
      topGroup!!.add(categoryGroup)
      setPresentation(category, categoryGroup)
    }
  }

  @GuardedBy("CATEGORY_TABLE_LOCK")
  private fun fillCategory(categoryGroup: NonEmptyActionGroup, category: String?, am: ActionManager) {
    val categoryRow = _categoryTable!!.row(category)
    if (CATEGORY_ACTIVITY == category) {
      val galleryAction: AnAction = object : AnAction() {
        override fun update(e: AnActionEvent) {
          updateAction(e, "Gallery...", true, true)
        }

        override fun actionPerformed(e: AnActionEvent) {
          showWizardDialog(e,
                           CATEGORY_ACTIVITY,
                           AndroidBundle.message(
                             "android.wizard.activity.add",
                             FormFactor.MOBILE.id),
                           "New Android Activity")
        }
      }
      categoryGroup.add(galleryAction)
      categoryGroup.addSeparator()
      setPresentation(category, galleryAction)
    }
    if (StudioFlags.NPW_SHOW_FRAGMENT_GALLERY.get() && category == CATEGORY_FRAGMENT) {
      val fragmentGalleryAction: AnAction = object : AnAction() {
        override fun update(e: AnActionEvent) {
          updateAction(e, "Gallery...", true, true)
        }

        override fun actionPerformed(e: AnActionEvent) {
          showWizardDialog(e,
                           CATEGORY_FRAGMENT,
                           AndroidBundle.message(
                             "android.wizard.fragment.add",
                             FormFactor.MOBILE.id),
                           "New Android Fragment")
        }
      }
      categoryGroup.add(fragmentGalleryAction)
      categoryGroup.addSeparator()
      setPresentation(category, fragmentGalleryAction)
    }

    // Automotive category includes Car category templates. If a template is in both categories, use the automotive one.
    val templateCategoryMap = categoryRow.keys.stream().collect(
      Collectors.toMap(
        Function { it: String -> it },
        Function { it: String? -> category }))
    for ((templateName, templateCategory) in templateCategoryMap) {
      if (EXCLUDED_TEMPLATES.contains(templateName)) {
        continue
      }
      val metadata = getTemplateMetadata(_categoryTable!![templateCategory, templateName])
      val minSdkVersion = metadata?.minSdk ?: 0
      val minBuildSdkApi = metadata?.minBuildApi ?: 0
      val templateConstraints = metadata?.constraints ?: EnumSet.noneOf(TemplateMetadata.TemplateConstraint::class.java)
      val templateAction = NewAndroidComponentAction(category!!, templateName, minSdkVersion, minBuildSdkApi, templateConstraints)
      val actionId = ACTION_ID_PREFIX + templateCategory + templateName
      am.replaceAction(actionId, templateAction)
      categoryGroup.add(templateAction)
    }
  }

  @Slow
  @GuardedBy("CATEGORY_TABLE_LOCK")
  private fun reloadCategoryTable(project: Project?) {
    if (templateMap != null) {
      templateMap!!.clear()
    }
    _categoryTable = TreeBasedTable.create()
    val templateRootFolder = templateRootFolder
    if (templateRootFolder != null) {
      for (categoryDirectory in listFiles(templateRootFolder)) {
        for (newTemplate in listFiles(categoryDirectory)) {
          addTemplateToTable(newTemplate, false)
        }
      }
    }
  }

  @GuardedBy("CATEGORY_TABLE_LOCK")
  private fun addTemplateToTable(newTemplate: File, userDefinedTemplate: Boolean) {
    val newMetadata = getTemplateMetadata(newTemplate, userDefinedTemplate)
    if (newMetadata != null) {
      val title = newMetadata.title
      if (title == null || newMetadata.category == null &&
          _categoryTable!!.columnKeySet().contains(title) && _categoryTable!![CATEGORY_OTHER, title] == null) {
        // If this template is uncategorized, and we already have a template of this name that has a category,
        // that is NOT "Other," then ignore this new template since it's undoubtedly older.
        return
      }
      val category = if (newMetadata.category != null) newMetadata.category else CATEGORY_OTHER
      if (CATEGORY_COMPOSE == category && !StudioFlags.COMPOSE_WIZARD_TEMPLATES.get()) {
        return
      }
      val existingTemplate = _categoryTable!![category, title]
      if (existingTemplate == null || compareTemplates(existingTemplate, newTemplate) > 0) {
        _categoryTable!!.put(category, title, newTemplate)
      }
    }
  }

  /**
   * Compare two files, and return the one with the HIGHEST revision, and if
   * the same, most recently modified
   */
  private fun compareTemplates(file1: File, file2: File): Int {
    val template1 = getTemplateMetadata(file1)
    val template2 = getTemplateMetadata(file2)
    return when {
      template1 == null -> 1
      template2 == null -> -1
      else -> {
        var delta = template2.revision - template1.revision
        if (delta == 0) {
          delta = (file2.lastModified() - file1.lastModified()).toInt()
        }
        delta
      }
    }
  }

  /**
   * Given a root path, parse the target template.xml file found there and return the Android data
   * contained within. This data will be cached and reused on subsequent requests.
   *
   * @return The Android metadata contained in the template.xml file, or `null` if there was
   * any problem collecting it, such as a parse failure or invalid path, etc.
   */
  @JvmOverloads
  fun getTemplateMetadata(templateRoot: File, userDefinedTemplate: Boolean = false): TemplateMetadata? {
    if (templateMap != null) {
      val metadata = templateMap!![templateRoot]
      if (metadata != null) {
        return metadata
      }
    }
    else {
      templateMap = Maps.newHashMap()
    }
    try {
      val templateFile = File(templateRoot, Template.TEMPLATE_XML_NAME)
      if (templateFile.isFile) {
        val doc = XmlUtils.parseUtfXmlFile(templateFile, true)
        if (doc.documentElement != null) {
          val metadata = TemplateMetadata(doc)
          templateMap!![templateRoot] = metadata
          return metadata
        }
      }
    }
    catch (e: Exception) {
      if (userDefinedTemplate) {
        LOG.warn(e)
      }
    }
    return null
  }

  companion object {
    private val LOG = Logger.getInstance(TemplateManager::class.java)

    /**
     * A directory relative to application home folder where we can find an extra template folder. This lets us ship more up-to-date
     * templates with the application instead of waiting for SDK updates.
     */
    const val CATEGORY_OTHER = "Other"
    const val CATEGORY_ACTIVITY = "Activity"
    const val CATEGORY_FRAGMENT = "Fragment"
    const val CATEGORY_COMPOSE = "Compose"
    private const val ACTION_ID_PREFIX = "template.create."
    private val EXCLUDED_CATEGORIES: Set<String?> = ImmutableSet.of("Application", "Applications")
    val EXCLUDED_TEMPLATES: Set<String> = ImmutableSet.of()

    @JvmStatic
    val instance = TemplateManager()

    /**
     * @return the root folder containing templates
     */
    @JvmStatic
    val templateRootFolder: File?
      get() {
        val homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath())
        // Release build?
        val root = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName("$homePath/plugins/android/lib/templates"))
        if (root != null) {
          val rootFile = VfsUtilCore.virtualToIoFile(root)
          if (templateRootIsValid(rootFile)) {
            return rootFile
          }
        }
        return null
      }

    private fun updateAction(event: AnActionEvent, text: String?, visible: Boolean, disableIfNotReady: Boolean) {
      val view = event.getData(LangDataKeys.IDE_VIEW)
      val module = event.getData(LangDataKeys.MODULE)
      val facet = if (module != null) AndroidFacet.getInstance(module) else null
      val presentation = event.presentation
      val isProjectReady = facet != null && AndroidModel.get(facet) != null
      presentation.text = text + if (isProjectReady) "" else " (Project not ready)"
      presentation.isVisible = visible && view != null && facet != null && AndroidModel.isRequired(facet)
      presentation.isEnabled = !disableIfNotReady || isProjectReady
    }

    private fun showWizardDialog(e: AnActionEvent, category: String, commandName: String, dialogTitle: String) {
      val projectSyncInvoker: ProjectSyncInvoker = DefaultProjectSyncInvoker()
      val dataContext = e.dataContext
      val module = LangDataKeys.MODULE.getData(dataContext)!!
      val targetFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)!!
      var targetDirectory = targetFile
      if (!targetDirectory.isDirectory) {
        targetDirectory = targetFile.parent
        assert(targetDirectory != null)
      }
      val facet = AndroidFacet.getInstance(module)
      assert(facet != null && AndroidModel.get(facet) != null)
      val moduleTemplates = facet!!.getModuleTemplates(targetDirectory)
      assert(moduleTemplates.isNotEmpty())
      val initialPackageSuggestion = facet.getPackageForPath(moduleTemplates, targetDirectory)
      val renderModel = fromFacet(
        facet, initialPackageSuggestion, moduleTemplates[0],
        commandName, projectSyncInvoker, true)
      val chooseTypeStep: SkippableWizardStep<RenderTemplateModel>
      chooseTypeStep = when (category) {
        CATEGORY_ACTIVITY -> ChooseActivityTypeStep(renderModel, FormFactor.MOBILE, targetDirectory)
        CATEGORY_FRAGMENT -> ChooseFragmentTypeStep(renderModel, FormFactor.MOBILE, targetDirectory)
        else -> throw RuntimeException("Invalid category name: $category")
      }
      val wizard = ModelWizard.Builder().addStep(chooseTypeStep).build()
      StudioWizardDialogBuilder(wizard, dialogTitle).build().show()
    }

    private fun setPresentation(category: String?, categoryGroup: AnAction) {
      val presentation = categoryGroup.templatePresentation
      presentation.icon = AndroidIcons.Android
      presentation.text = category
    }

    @JvmStatic
    fun getWrapperLocation(templateRootFolder: File) = File(templateRootFolder, SdkConstants.FD_GRADLE_WRAPPER)

    private fun templateRootIsValid(templateRootFolder: File) =
      File(getWrapperLocation(templateRootFolder), SdkConstants.FN_GRADLE_WRAPPER_UNIX).exists()

    private fun listFiles(root: File): Array<File> {
      return FileOpUtils.create().listFiles(root)
    }
  }
}