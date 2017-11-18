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
package org.jetbrains.android.refactoring

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.flags.StudioFlags
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import org.jetbrains.android.refactoring.AppCompatMigrationEntry.*

class MigrateToAndroidxAction : BaseRefactoringAction() {

  override fun isAvailableInEditorOnly() = false

  override fun isEnabledOnDataContext(dataContext: DataContext) = true

  override fun isEnabledOnElements(elements: Array<out PsiElement>) = true

  override fun getHandler(dataContext: DataContext): RefactoringActionHandler? = MigrateToAndroidxHandler()

  override fun update(anActionEvent: AnActionEvent) {
    val project = anActionEvent.getData(CommonDataKeys.PROJECT)
    anActionEvent.presentation.isEnabledAndVisible = project != null && StudioFlags.MIGRATE_TO_ANDROID_X_REFACTORING_ENABLED.get()
  }

  override fun isAvailableForLanguage(language: Language) = true
}

private const val ROOT_ELEMENT = "migration-map"
// migrate package/class element and attribute names
private const val MIGRATE_ENTRY_NAME = "migrate"
private const val ATTR_OLD_NAME = "old-name"
private const val ATTR_NEW_NAME = "new-name"
private const val ATTR_TYPE = "type"
private const val TYPE_CLASS = "CLASS"
private const val TYPE_PACKAGE = "PACKAGE"
// migrate-dependency element and attribute names
private const val MIGRATE_DEPENDENCY_NAME = "migrate-dependency"
private const val ATTR_OLD_GROUP_NAME = "old-group-name"
private const val ATTR_OLD_ARTIFACT_NAME = "old-artifact-name"
private const val ATTR_NEW_GROUP_NAME = "new-group-name"
private const val ATTR_NEW_ARTIFACT_NAME = "new-artifact-name"
private const val ATTR_NEW_BASE_VERSION_NAME = "base-version"

class MigrateToAndroidxHandler : RefactoringActionHandler {

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
    invoke(project, arrayOf<PsiElement>(file!!), dataContext)
  }

  override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
    val processor = MigrateToAndroidxProcessor(project, parseMigrationMap())

    with(processor) {
      setPreviewUsages(true)
      run()
    }
  }

  @VisibleForTesting
  private fun parseMigrationMap(): List<AppCompatMigrationEntry> {
    val stream = javaClass.getResourceAsStream("/migrateToAndroidx/migration.xml") ?:
        throw InvalidDataException("Missing required migration entry: /migrateToAndroidx/migration.xml")
    val result = mutableListOf<AppCompatMigrationEntry>()
    stream.use {
      val root = JDOMUtil.load(it)
      if (ROOT_ELEMENT != root.name) {
        throw InvalidDataException()
      }
      for (node in root.children) {
        if (node.name == MIGRATE_ENTRY_NAME) {
          val oldName = node.getAttributeValue(ATTR_OLD_NAME)
          val newName = node.getAttributeValue(ATTR_NEW_NAME)
          val type = node.getAttributeValue(ATTR_TYPE)
          result.add(when (type) {
            TYPE_PACKAGE -> PackageMigrationEntry(oldName, newName)
            TYPE_CLASS -> ClassMigrationEntry(oldName, newName)
            else -> throw InvalidDataException()
          })
        }
        else if (node.name == MIGRATE_DEPENDENCY_NAME) {
          val oldGroupName = node.getAttributeValue(ATTR_OLD_GROUP_NAME)
          val oldArtifactName = node.getAttributeValue(ATTR_OLD_ARTIFACT_NAME)
          val newGroupName = node.getAttributeValue(ATTR_NEW_GROUP_NAME)
          val newArtifactName = node.getAttributeValue(ATTR_NEW_ARTIFACT_NAME)
          val newBaseVersion = node.getAttributeValue(ATTR_NEW_BASE_VERSION_NAME)
          result.add(GradleDependencyMigrationEntry(oldGroupName, oldArtifactName, newGroupName, newArtifactName, newBaseVersion))
        }
      }
    }
    return result
  }
}
