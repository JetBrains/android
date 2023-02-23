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
package com.android.tools.idea.gradle.project

import com.android.SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES
import com.android.SdkConstants.FN_SETTINGS_GRADLE
import com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.concurrency.executeOnPooledThread
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector.DetectorResult.EXPLICIT_CALL
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector.DetectorResult.IMPLICIT_LIBS_VERSIONS
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector.DetectorResult.NOT_ENABLED
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector.DetectorResult.NOT_USED
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector.DetectorResult.OLD_GRADLE
import com.android.tools.idea.gradle.util.GradleUtil.findGradleSettingsFile
import com.android.tools.idea.gradle.util.GradleWrapper
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.PROJECT_SYSTEM
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_VERSION_CATALOG_DETECTOR
import com.google.wireless.android.sdk.stats.GradleVersionCatalogDetectorEvent
import com.google.wireless.android.sdk.stats.GradleVersionCatalogDetectorEvent.State.EXPLICIT
import com.google.wireless.android.sdk.stats.GradleVersionCatalogDetectorEvent.State.IMPLICIT
import com.google.wireless.android.sdk.stats.GradleVersionCatalogDetectorEvent.State.NONE
import com.google.wireless.android.sdk.stats.GradleVersionCatalogDetectorEvent.State.UNSUPPORTED
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

/**
 * This class and its methods attempt to answer the question of whether a given (Gradle-based) project uses the Version Catalog
 * feature, introduced as experimental in Gradle version 7.0 and made stable in version 7.4.  (Once Studio support for Version Catalogs
 * is stable and documented, this class can probably go away.
 *
 * A project uses version catalogs if:
 * 1. its Gradle version is greater than 7.4, and
 *    a. it has a versionCatalogs clause in its settings file; or if not
 *    b. it has a gradle/libs.versions.toml file relative to its base directory
 * 2. its Gradle version is between 7.0 and 7.4, and it has an enableFeaturePreview("VERSION_CATALOGS") call in its settings file, and
 *    a. it has a versionCatalogs clause in its settings file; or if not
 *    b. it has a gradle/libs.versions.toml file relative to its base directory
 */
class GradleVersionCatalogDetector(private val project: Project): Disposable {
  private var _gradleVersion: GradleVersion? = null
  private var _settingsVisitorResults: SettingsVisitorResults? = null

  private val fileDocumentManager = FileDocumentManager.getInstance()
  init {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val documentListener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (_gradleVersion == null && _settingsVisitorResults == null) return
        val baseDir = project.baseDir ?: return
        val file = fileDocumentManager.getFile(event.document) ?: return
        if (!VfsUtilCore.isAncestor(baseDir, file, true)) return
        when (file.name) {
          FN_GRADLE_WRAPPER_PROPERTIES -> _gradleVersion = null
          FN_SETTINGS_GRADLE, FN_SETTINGS_GRADLE_KTS -> _settingsVisitorResults = null
        }
      }
    }
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GradleVersionCatalogDetector =
      runReadAction {
        if(project.isDisposed) throw ProcessCanceledException() else project.getService(GradleVersionCatalogDetector::class.java)
      }

    val MISSING_GRADLE_VERSION: GradleVersion = GradleVersion.version("0.0")
    val PREVIEW_GRADLE_VERSION: GradleVersion = GradleVersion.version("7.0")
    val STABLE_GRADLE_VERSION: GradleVersion = GradleVersion.version("7.4")

    val EMPTY_SETTINGS = object : SettingsVisitorResults {
      override val enableFeaturePreview = false
      override val versionCatalogsCall = false
    }
  }

  private val gradleVersion: GradleVersion
    get() {
      _gradleVersion?.let { return it }
      return runReadAction {
        if (project.isDisposed) throw ProcessCanceledException()
        val gradleWrapper = GradleWrapper.find(project)
        ProgressManager.checkCanceled()
        val gradleVersion = gradleWrapper?.gradleVersion
                              ?.let { runCatching { GradleVersion.version(it) }.getOrNull() }
                            ?: MISSING_GRADLE_VERSION
        return@runReadAction gradleVersion.also { _gradleVersion = gradleVersion }
      }
    }

  private val settingsVisitorResults: SettingsVisitorResults
    get() {
      _settingsVisitorResults?.let { return it }
      return runReadAction {
        if (project.isDisposed) throw ProcessCanceledException()
        val baseDir = project.baseDir ?: return@runReadAction EMPTY_SETTINGS.also { _settingsVisitorResults = it }
        val settingsFile = findGradleSettingsFile(baseDir) ?: return@runReadAction EMPTY_SETTINGS.also { _settingsVisitorResults = it }
        val settingsVisitorResults = when (val settingsPsiFile = PsiManager.getInstance(project).findFile(settingsFile)) {
          is GroovyFile -> visitGroovySettings(settingsPsiFile)
          is KtFile -> visitKtSettings(settingsPsiFile)
          else -> EMPTY_SETTINGS
        }
        return@runReadAction settingsVisitorResults.also { _settingsVisitorResults = settingsVisitorResults }
      }
    }

  val versionCatalogDetectorResult: DetectorResult
    get() {
      val gradleVersion = gradleVersion
      if (gradleVersion < PREVIEW_GRADLE_VERSION) return OLD_GRADLE
      val settingsVisitorResults = settingsVisitorResults
      val needEnableFeaturePreview = gradleVersion < STABLE_GRADLE_VERSION
      if (needEnableFeaturePreview && !settingsVisitorResults.enableFeaturePreview) return NOT_ENABLED
      if (settingsVisitorResults.versionCatalogsCall) return EXPLICIT_CALL
      return when(project.baseDir?.findChild("gradle")?.findChild("libs.versions.toml")) {
        null -> NOT_USED
        else -> IMPLICIT_LIBS_VERSIONS
      }
    }

  private var shouldSendTrackerEvent = true

  val isVersionCatalogProject: Boolean
    get() = versionCatalogDetectorResult.run {
      if (shouldSendTrackerEvent) {
        shouldSendTrackerEvent = false
        val thunk = {
          val event = AndroidStudioEvent.newBuilder()
            .setCategory(PROJECT_SYSTEM).setKind(GRADLE_VERSION_CATALOG_DETECTOR)
            .setGradleVersionCatalogDetectorEvent(
              GradleVersionCatalogDetectorEvent.newBuilder().setState(state)
            )
          UsageTracker.log(event)
        }
        when (ApplicationManager.getApplication().isUnitTestMode) {
          true -> thunk()
          false -> executeOnPooledThread(thunk)
        }
      }
      result
    }


  interface SettingsVisitorResults {
    val enableFeaturePreview: Boolean
    val versionCatalogsCall: Boolean
  }

  enum class DetectorResult(val result: Boolean, val state: GradleVersionCatalogDetectorEvent.State) {
    OLD_GRADLE(false, UNSUPPORTED), // Gradle version is too old for Version Catalogs.
    NOT_ENABLED(false, NONE), // Gradle version requires explicit preview feature, not present.
    EXPLICIT_CALL(true, EXPLICIT), // Found an explicit call to versionCatalogs in settings.
    IMPLICIT_LIBS_VERSIONS(true, IMPLICIT), // No explicit call, but implicit use through libs.versions.toml.
    NOT_USED(false, NONE), // No use of Version Catalogs found in this project.
  }

  private fun visitGroovySettings(settingsPsiFile: GroovyFile): SettingsVisitorResults {
    val visitor = object : GroovyRecursiveElementVisitor(), SettingsVisitorResults {
      override var enableFeaturePreview = false
      override var versionCatalogsCall = false
      override fun visitElement(element: GroovyPsiElement) {
        ProgressManager.checkCanceled()
        super.visitElement(element)
      }
      override fun visitMethodCall(call: GrMethodCall) {
        val callee = call.invokedExpression
        val name = callee.text
        if (name == "versionCatalogs") versionCatalogsCall = true
        if (name == "enableFeaturePreview") {
          val arguments = call.argumentList.allArguments
          if (arguments.size == 1) {
            val argument = arguments[0]
            val argumentVisitor = object : GroovyRecursiveElementVisitor() {
              override fun visitLiteralExpression(literal: GrLiteral) {
                if (literal.value == "VERSION_CATALOGS") enableFeaturePreview = true
                super.visitLiteralExpression(literal)
              }
            }
            argument.accept(argumentVisitor)
          }
        }
        super.visitMethodCall(call)
      }
    }
    settingsPsiFile.accept(visitor)
    return visitor
  }

  private fun visitKtSettings(settingsPsiFile: KtFile): SettingsVisitorResults {
    val visitor = object : KtTreeVisitorVoid(), SettingsVisitorResults {
      override var enableFeaturePreview = false
      override var versionCatalogsCall = false
      override fun visitElement(element: PsiElement) {
        ProgressManager.checkCanceled()
        super.visitElement(element)
      }
      override fun visitCallExpression(expression: KtCallExpression) {
        val callee = expression.calleeExpression
        if (callee != null && callee is KtNameReferenceExpression) {
          val name = callee.getReferencedName()
          if (name == "versionCatalogs") versionCatalogsCall = true
          if (name == "enableFeaturePreview") {
            val arguments = expression.valueArguments
            if (arguments.size == 1) {
              val argument = arguments[0]
              val argumentVisitor = object : KtTreeVisitorVoid() {
                override fun visitStringTemplateExpression(expression: KtStringTemplateExpression) {
                  val entries = expression.entries
                  if (entries.size == 1) {
                    val entry = entries[0] as? KtLiteralStringTemplateEntry ?: return
                    if (entry.text == "VERSION_CATALOGS") enableFeaturePreview = true
                  }
                  super.visitStringTemplateExpression(expression)
                }
              }
              argument.accept(argumentVisitor)
            }
          }
        }
        super.visitCallExpression(expression)
      }
    }
    settingsPsiFile.accept(visitor)
    return visitor
  }

  override fun dispose() {}
}
