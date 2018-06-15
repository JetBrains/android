@file:JvmName("AndroidRefactoringUtil")

package org.jetbrains.android.refactoring

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMigration
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.migration.MigrationUtil
import org.jetbrains.android.dom.resources.Style
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.android.util.ErrorReporter


internal fun getParentStyle(style: Style): StyleRefData? {
  val parentStyleRefValue = style.parentStyle.value

  return if (parentStyleRefValue != null) {
    parentStyleRefValue.resourceName?.let { StyleRefData(it, parentStyleRefValue.`package`) }
  } else {
    style.name.stringValue
      ?.takeIf { it.indexOf('.') > 0 }
      ?.let { StyleRefData(it.substringBeforeLast('.'), null) }
  }
}

internal fun computeAttributeMap(
  style: Style,
  errorReporter: ErrorReporter,
  errorReportTitle: String
): Map<AndroidAttributeInfo, String>? {
  val attributeValues = mutableMapOf<AndroidAttributeInfo, String>()

  for (item in style.items) {
    val attributeName = item.name.stringValue
    val attributeValue = item.stringValue

    if (attributeName == null || attributeName.isEmpty() || attributeValue == null) {
      continue
    }
    val localName = attributeName.substringAfterLast(':')
    val nsPrefix = attributeName.substringBefore(':', missingDelimiterValue = "")

    if (nsPrefix.isNotEmpty()) {
      if (AndroidUtils.SYSTEM_RESOURCE_PACKAGE != nsPrefix) {
        errorReporter.report(
          RefactoringBundle.getCannotRefactorMessage("Unknown XML attribute prefix '$nsPrefix:'"),
          errorReportTitle
        )
        return null
      }
    }
    else {
      errorReporter.report(
        RefactoringBundle.getCannotRefactorMessage("The style contains attribute without 'android' prefix."),
        errorReportTitle
      )
      return null
    }
    attributeValues[AndroidAttributeInfo(localName, nsPrefix)] = attributeValue
  }
  return attributeValues
}

/**
 * Public version of the same method from [MigrationUtil].
 */
fun findOrCreateClass(project: Project, migration: PsiMigration, qName: String): PsiClass {
  return JavaPsiFacade.getInstance(project).findClass(qName, GlobalSearchScope.allScope(project))
         ?: runWriteAction { migration.createClass(qName) }
}

/**
 * Public version of the same method from [MigrationUtil].
 */
fun findOrCreatePackage(project: Project, migration: PsiMigration, qName: String): PsiPackage {
  return JavaPsiFacade.getInstance(project).findPackage(qName) ?: runWriteAction { migration.createPackage(qName) }
}

