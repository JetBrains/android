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
import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor2
import com.android.tools.idea.templates.recipe.RenderingContext2
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.Template
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.KotlinSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

private val log: Logger get() = logger<Template>()

fun Template.render(c: RenderingContext2, e: RecipeExecutor): Boolean {
  val success = if (c.project.isInitialized)
    doRender(c, e)
  else
    PostprocessReformattingAspect.getInstance(c.project).disablePostprocessFormattingInside<Boolean> {
      doRender(c, e)
    }

  if (!c.dryRun && this != Template.NoActivity) {
    logRendering(c.templateData, c.project)
  }

  if (!c.dryRun) {
    StartupManager.getInstance(c.project)
      .runWhenProjectIsInitialized { TemplateUtils.reformatAndRearrange(c.project, c.targetFiles) }
  }

  ApplicationManager.getApplication().invokeAndWait { PsiDocumentManager.getInstance(c.project).commitAllDocuments() }

  return success
}

fun Template.doRender(c: RenderingContext2, e: RecipeExecutor): Boolean {
  try {
    writeCommandAction(c.project).withName(c.commandName).run<IOException> {
      recipe(e, c.templateData)
      if (e is DefaultRecipeExecutor2 && !c.dependencies.isEmpty) {
        e.mergeDependenciesIntoGradle()
      }
    }
  }
  catch (e: IOException) {
    if (c.showErrors) {
      invokeAndWaitIfNeeded {
        Messages.showErrorDialog(
          c.project,
          formatErrorMessage(c.commandName, !c.dryRun, e),
          "${c.commandName} Failed")
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


fun formatWarningMessage(context: RenderingContext2): String {
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

@VisibleForTesting
internal fun titleToTemplateRenderer(title: String): AndroidStudioEvent.TemplateRenderer = when (title) {
  "" -> AndroidStudioEvent.TemplateRenderer.UNKNOWN_TEMPLATE_RENDERER
  "Android Module" -> AndroidStudioEvent.TemplateRenderer.ANDROID_MODULE
  "Android Project" -> AndroidStudioEvent.TemplateRenderer.ANDROID_PROJECT
  "Empty Activity" -> AndroidStudioEvent.TemplateRenderer.EMPTY_ACTIVITY
  "Blank Activity" -> AndroidStudioEvent.TemplateRenderer.BLANK_ACTIVITY
  "Layout XML File" -> AndroidStudioEvent.TemplateRenderer.LAYOUT_XML_FILE
  "Fragment (Blank)" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_BLANK
  "Navigation Drawer Activity" -> AndroidStudioEvent.TemplateRenderer.NAVIGATION_DRAWER_ACTIVITY
  "Values XML File" -> AndroidStudioEvent.TemplateRenderer.VALUES_XML_FILE
  "Google Maps Activity" -> AndroidStudioEvent.TemplateRenderer.GOOGLE_MAPS_ACTIVITY
  "Login Activity" -> AndroidStudioEvent.TemplateRenderer.LOGIN_ACTIVITY
  "Assets Folder" -> AndroidStudioEvent.TemplateRenderer.ASSETS_FOLDER
  "Tabbed Activity" -> AndroidStudioEvent.TemplateRenderer.TABBED_ACTIVITY
  "Scrolling Activity" -> AndroidStudioEvent.TemplateRenderer.SCROLLING_ACTIVITY
  "Fullscreen Activity" -> AndroidStudioEvent.TemplateRenderer.FULLSCREEN_ACTIVITY
  "Service" -> AndroidStudioEvent.TemplateRenderer.SERVICE
  "Java Library" -> AndroidStudioEvent.TemplateRenderer.JAVA_LIBRARY
  "Settings Activity" -> AndroidStudioEvent.TemplateRenderer.SETTINGS_ACTIVITY
  "Fragment (List)" -> AndroidStudioEvent.TemplateRenderer.FRAGMENT_LIST
  "Master/Detail Flow" -> AndroidStudioEvent.TemplateRenderer.MASTER_DETAIL_FLOW
  "Wear OS Module" -> AndroidStudioEvent.TemplateRenderer.ANDROID_WEAR_MODULE
  "Broadcast Receiver" -> AndroidStudioEvent.TemplateRenderer.BROADCAST_RECEIVER
  "AIDL File" -> AndroidStudioEvent.TemplateRenderer.AIDL_FILE
  "Service (IntentService)" -> AndroidStudioEvent.TemplateRenderer.INTENT_SERVICE
  "JNI Folder" -> AndroidStudioEvent.TemplateRenderer.JNI_FOLDER
  "Java Folder" -> AndroidStudioEvent.TemplateRenderer.JAVA_FOLDER
  "Custom View" -> AndroidStudioEvent.TemplateRenderer.CUSTOM_VIEW
  "Android TV Module" -> AndroidStudioEvent.TemplateRenderer.ANDROID_TV_MODULE
  "Google AdMob Ads Activity" -> AndroidStudioEvent.TemplateRenderer.GOOGLE_ADMOBS_ADS_ACTIVITY
  "Always On Wear Activity" -> AndroidStudioEvent.TemplateRenderer.ALWAYS_ON_WEAR_ACTIVITY
  "Res Folder" -> AndroidStudioEvent.TemplateRenderer.RES_FOLDER
  "Android TV Activity" -> AndroidStudioEvent.TemplateRenderer.ANDROID_TV_ACTIVITY
  "Blank Wear Activity" -> AndroidStudioEvent.TemplateRenderer.BLANK_WEAR_ACTIVITY
  "Basic Activity" -> AndroidStudioEvent.TemplateRenderer.BASIC_ACTIVITIY
  "App Widget" -> AndroidStudioEvent.TemplateRenderer.APP_WIDGET
  "Instant App Project" -> AndroidStudioEvent.TemplateRenderer.ANDROID_INSTANT_APP_PROJECT
  "Instant App" -> AndroidStudioEvent.TemplateRenderer.ANDROID_INSTANT_APP_MODULE
  "Dynamic Feature (Instant App)" -> AndroidStudioEvent.TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE
  "Benchmark Module" -> AndroidStudioEvent.TemplateRenderer.BENCHMARK_LIBRARY_MODULE
  "Compose Activity" -> AndroidStudioEvent.TemplateRenderer.COMPOSE_EMPTY_ACTIVITY
  else -> AndroidStudioEvent.TemplateRenderer.CUSTOM_TEMPLATE_RENDERER
}

fun Template.logRendering(moduleTemplateData: ModuleTemplateData, project: Project) {
  val aseBuilder = AndroidStudioEvent.newBuilder()
    .setCategory(AndroidStudioEvent.EventCategory.TEMPLATE)
    .setKind(AndroidStudioEvent.EventKind.TEMPLATE_RENDER)
    .setTemplateRenderer(titleToTemplateRenderer(this.name))
    .setKotlinSupport(
      KotlinSupport.newBuilder()
        .setIncludeKotlinSupport(moduleTemplateData.projectTemplateData.language == Language.Kotlin)
        .setKotlinSupportVersion(moduleTemplateData.projectTemplateData.kotlinVersion))
  UsageTracker.log(aseBuilder.withProjectId(project))

  /*TODO(qumeric)
  if (ATTR_DYNAMIC_IS_INSTANT_MODULE) {
    aseBuilder.templateRenderer = AndroidStudioEvent.TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE
    UsageTracker.log(aseBuilder.withProjectId(project))
  }*/
}

