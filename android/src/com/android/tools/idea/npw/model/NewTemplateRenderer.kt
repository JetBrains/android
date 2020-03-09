/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.model

import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.stats.withProjectId
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.FindReferencesRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.util.EditorUtil
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.Template
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer
import com.google.wireless.android.sdk.stats.KotlinSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

private val log: Logger get() = logger<Template>()

fun Template.render(c: RenderingContext, e: RecipeExecutor) =
  recipe.render(c, e, titleToTemplateRenderer(name, formFactor).takeUnless { this == Template.NoActivity })

fun Recipe.findReferences(c: RenderingContext) =
  render(c, FindReferencesRecipeExecutor(c), null)

fun Recipe.actuallyRender(c: RenderingContext) =
  render(c, DefaultRecipeExecutor(c), null)

fun Recipe.render(c: RenderingContext, e: RecipeExecutor, loggingEvent: TemplateRenderer?): Boolean {
  val success = if (c.project.isInitialized)
    doRender(c, e)
  else
    PostprocessReformattingAspect.getInstance(c.project).disablePostprocessFormattingInside<Boolean> {
      doRender(c, e)
    }

  if (!c.dryRun && loggingEvent != null) {
    logRendering(c.projectTemplateData, c.project, loggingEvent)
  }

  if (!c.dryRun) {
    EditorUtil.reformatAndRearrange(c.project, c.targetFiles)
  }

  ApplicationManager.getApplication().invokeAndWait { PsiDocumentManager.getInstance(c.project).commitAllDocuments() }

  return success
}

private fun Recipe.doRender(c: RenderingContext, e: RecipeExecutor): Boolean {
  try {
    writeCommandAction(c.project).withName(c.commandName).run<IOException> {
      this(e, c.templateData)
      // TODO(qumeric): remove casting when the old executor will be deleted
      if (e is DefaultRecipeExecutor) {
        e.applyChanges()
      }
    }
  }
  catch (e: IOException) {
    if (c.showErrors) {
      invokeAndWaitIfNeeded {
        Messages.showErrorDialog(c.project, formatErrorMessage(c.commandName, !c.dryRun, e), "${c.commandName} Failed")
      }
    }
    else {
      throw RuntimeException(e)
    }
    return false
  }

  if (c.warnings.isEmpty()) {
    return true
  }

  if (!c.showWarnings) {
    log.warn("WARNING: " + c.warnings)
    return true
  }

  val result = AtomicBoolean()
  ApplicationManager.getApplication().invokeAndWait {
    val userReply = Messages.showOkCancelDialog(
      c.project,
      formatWarningMessage(c),
      "${c.commandName}, Warnings",
      "Proceed Anyway", "Cancel", Messages.getWarningIcon())
    result.set(userReply == Messages.OK)
  }
  return result.get()
}

/**
 * If this is not a dry run, we may have created/changed some files and the project
 * may no longer compile. Let the user know about undo.
 */
fun formatErrorMessage(commandName: String, canCausePartialRendering: Boolean, ex: IOException): String =
  if (!canCausePartialRendering)
    ex.message ?: "Unknown IOException occurred"
  else """${ex.message}

$commandName was only partially completed.
Your project may not compile.
You may want to Undo to get back to the original state.
"""

fun formatWarningMessage(context: RenderingContext): String {
  val maxWarnings = 10
  val warningCount = context.warnings.size
  var messages: MutableList<String> = context.warnings.toMutableList()
  if (warningCount > maxWarnings + 1) {  // +1 such that the message can say "warnings" in plural...
    // Guard against too many warnings (the dialog may become larger than the screen size)
    messages = messages.subList(0, maxWarnings)
    val strippedWarningsCount = warningCount - maxWarnings
    messages.add("And $strippedWarningsCount more warnings...")
  }
  messages.add("\nIf you proceed the resulting project may not compile or not work as intended.")
  return messages.joinToString("\n\n")
}

// TODO(qumeric): update TemplateRenderer and this method
@VisibleForTesting
internal fun titleToTemplateRenderer(title: String, formFactor: FormFactor): TemplateRenderer = when (title) {
  "" -> TemplateRenderer.UNKNOWN_TEMPLATE_RENDERER

  "Android Project" -> TemplateRenderer.ANDROID_PROJECT

  "Android Module" -> TemplateRenderer.ANDROID_MODULE
  "Java Library" -> TemplateRenderer.JAVA_LIBRARY
  "Android TV Module" -> TemplateRenderer.ANDROID_TV_MODULE
  "Dynamic Feature (Instant App)" -> TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE
  "Wear OS Module" -> TemplateRenderer.ANDROID_WEAR_MODULE

  "Basic Activity" -> TemplateRenderer.BASIC_ACTIVITIY
  "Empty Activity" -> TemplateRenderer.EMPTY_ACTIVITY
  "Blank Activity" -> if (formFactor == FormFactor.Wear) TemplateRenderer.BLANK_WEAR_ACTIVITY else TemplateRenderer.BLANK_ACTIVITY
  "Login Activity" -> TemplateRenderer.LOGIN_ACTIVITY
  "Tabbed Activity" -> TemplateRenderer.TABBED_ACTIVITY
  "Scrolling Activity" -> TemplateRenderer.SCROLLING_ACTIVITY
  "Google AdMob Ads Activity" -> TemplateRenderer.GOOGLE_ADMOBS_ADS_ACTIVITY
  "Always On Wear Activity" -> TemplateRenderer.ALWAYS_ON_WEAR_ACTIVITY
  "Android TV Activity" -> TemplateRenderer.ANDROID_TV_ACTIVITY
  "Fullscreen Activity" -> TemplateRenderer.FULLSCREEN_ACTIVITY
  "Empty Compose Activity" -> TemplateRenderer.COMPOSE_EMPTY_ACTIVITY
  "Google Maps Activity" -> TemplateRenderer.GOOGLE_MAPS_ACTIVITY
  "Navigation Drawer Activity" -> TemplateRenderer.NAVIGATION_DRAWER_ACTIVITY
  "Settings Activity" -> TemplateRenderer.SETTINGS_ACTIVITY
  "Master/Detail Flow" -> TemplateRenderer.MASTER_DETAIL_FLOW

  "Fullscreen Fragment" -> TemplateRenderer.FRAGMENT_FULLSCREEN
  "Google AdMob Ads Fragment" -> TemplateRenderer.FRAGMENT_GOOGLE_ADMOB_ADS
  "Google Maps Fragment" -> TemplateRenderer.FRAGMENT_GOOGLE_MAPS
  "Login Fragment" -> TemplateRenderer.FRAGMENT_LOGIN
  "Modal Bottom Sheet" -> TemplateRenderer.FRAGMENT_MODAL_BOTTOM_SHEET
  "Scrolling Fragment" -> TemplateRenderer.FRAGMENT_SCROLL
  "Settings Fragment" -> TemplateRenderer.FRAGMENT_SETTINGS
  "Fragment (with ViewModel)" -> TemplateRenderer.FRAGMENT_VIEWMODEL
  "Fragment (Blank)" -> TemplateRenderer.FRAGMENT_BLANK
  "Fragment (List)" -> TemplateRenderer.FRAGMENT_LIST

  "Assets Folder" -> TemplateRenderer.ASSETS_FOLDER
  "AIDL File" -> TemplateRenderer.AIDL_FILE
  "JNI Folder" -> TemplateRenderer.JNI_FOLDER
  "Java Folder" -> TemplateRenderer.JAVA_FOLDER

  "Service" -> TemplateRenderer.SERVICE
  "Broadcast Receiver" -> TemplateRenderer.BROADCAST_RECEIVER
  "Service (IntentService)" -> TemplateRenderer.INTENT_SERVICE
  "Custom View" -> TemplateRenderer.CUSTOM_VIEW
  "Res Folder" -> TemplateRenderer.RES_FOLDER
  "App Widget" -> TemplateRenderer.APP_WIDGET
  "Layout XML File" -> TemplateRenderer.LAYOUT_XML_FILE
  "Values XML File" -> TemplateRenderer.VALUES_XML_FILE

  else -> TemplateRenderer.CUSTOM_TEMPLATE_RENDERER
}

fun logRendering(projectData: ProjectTemplateData, project: Project, templateRenderer: TemplateRenderer) {
  val aseBuilder = AndroidStudioEvent.newBuilder()
    .setCategory(AndroidStudioEvent.EventCategory.TEMPLATE)
    .setKind(AndroidStudioEvent.EventKind.TEMPLATE_RENDER)
    .setTemplateRenderer(templateRenderer)
    .setKotlinSupport(
      KotlinSupport.newBuilder()
        .setIncludeKotlinSupport(projectData.language == Language.Kotlin)
        .setKotlinSupportVersion(projectData.kotlinVersion))
  UsageTracker.log(aseBuilder.withProjectId(project))


  /*TODO(qumeric)
  if (ATTR_DYNAMIC_IS_INSTANT_MODULE) {
    aseBuilder.templateRenderer = AndroidStudioEvent.TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE
    UsageTracker.log(aseBuilder.withProjectId(project))
  }*/
}
