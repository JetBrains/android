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
import com.android.tools.idea.util.ReformatUtil
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.Template
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplateRenderer
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.TemplateType
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateModule.BytecodeLevel
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateModule.ModuleType
import com.google.wireless.android.sdk.stats.KotlinSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.WriteCommandAction.writeCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import org.jetbrains.android.util.AndroidBundle.message
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import com.android.tools.idea.wizard.template.BytecodeLevel as TemplateBytecodeLevel

private val log: Logger get() = logger<Template>()

fun Template.render(c: RenderingContext, e: RecipeExecutor, metrics: TemplateMetrics? = null) =
  recipe.render(c, e, titleToTemplateRenderer(name, formFactor), metrics)

fun Recipe.findReferences(c: RenderingContext) =
  render(c, FindReferencesRecipeExecutor(c))

fun Recipe.actuallyRender(c: RenderingContext) =
  render(c, DefaultRecipeExecutor(c))

fun Recipe.render(c: RenderingContext, e: RecipeExecutor): Boolean {
  val success = if (c.project.isInitialized)
    doRender(c, e)
  else
    PostprocessReformattingAspect.getInstance(c.project).disablePostprocessFormattingInside<Boolean> {
      doRender(c, e)
    }

  if (!c.dryRun) {
    ApplicationManager.getApplication().invokeAndWait { PsiDocumentManager.getInstance(c.project).commitAllDocuments() }
    ReformatUtil.reformatRearrangeAndSave(c.project, c.targetFiles)
  }

  return success
}

fun Recipe.render(c: RenderingContext, e: RecipeExecutor, loggingEvent: TemplateRenderer, metrics: TemplateMetrics? = null): Boolean {
  return render(c, e).also {
    if (!c.dryRun) {
      logRendering(c.projectTemplateData, c.project, loggingEvent)
      if (metrics != null) {
        logRendering(c.projectTemplateData, c.project, metrics)
      }
    }
  }
}

private fun Recipe.doRender(c: RenderingContext, e: RecipeExecutor): Boolean {
  try {
    writeCommandAction(c.project)
      .withName(c.commandName)
      .withGlobalUndo()
      .withUndoConfirmationPolicy(UndoConfirmationPolicy.REQUEST_CONFIRMATION)
      .run<IOException> {
        this(e, c.templateData)
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
@Suppress("DEPRECATION")
@VisibleForTesting
fun titleToTemplateRenderer(title: String, formFactor: FormFactor): TemplateRenderer = when (title) {
  "" -> TemplateRenderer.UNKNOWN_TEMPLATE_RENDERER

  "Android Project" -> TemplateRenderer.ANDROID_PROJECT

  message("android.wizard.module.new.benchmark.module.app") -> TemplateRenderer.BENCHMARK_LIBRARY_MODULE
  message("android.wizard.module.new.mobile") -> TemplateRenderer.ANDROID_MODULE
  message("android.wizard.module.new.java.or.kotlin.library") -> TemplateRenderer.JAVA_LIBRARY
  message("android.wizard.module.new.tv") -> TemplateRenderer.ANDROID_TV_MODULE
  message("android.wizard.module.new.dynamic.module") -> TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE
  message("android.wizard.module.new.wear") -> TemplateRenderer.ANDROID_WEAR_MODULE

  "Basic Activity" -> TemplateRenderer.BASIC_ACTIVITIY
  "Basic Activity (Material3)" -> TemplateRenderer.BASIC_ACTIVITIY
  "Empty Activity" -> TemplateRenderer.EMPTY_ACTIVITY
  "Blank Activity" -> if (formFactor == FormFactor.Wear) TemplateRenderer.BLANK_WEAR_ACTIVITY else TemplateRenderer.BLANK_ACTIVITY
  "Login Activity" -> TemplateRenderer.LOGIN_ACTIVITY
  "Tabbed Activity" -> TemplateRenderer.TABBED_ACTIVITY
  "Scrolling Activity" -> TemplateRenderer.SCROLLING_ACTIVITY
  "Google AdMob Ads Activity" -> TemplateRenderer.GOOGLE_ADMOBS_ADS_ACTIVITY
  "Always On Wear Activity" -> TemplateRenderer.ALWAYS_ON_WEAR_ACTIVITY
  "Android TV Blank Activity" -> TemplateRenderer.ANDROID_TV_ACTIVITY
  "Fullscreen Activity" -> TemplateRenderer.FULLSCREEN_ACTIVITY
  "Empty Compose Activity" -> TemplateRenderer.COMPOSE_EMPTY_ACTIVITY
  "Empty Compose Activity (Material3)" -> TemplateRenderer.COMPOSE_EMPTY_ACTIVITY
  "Google Maps Activity" -> TemplateRenderer.GOOGLE_MAPS_ACTIVITY
  "Navigation Drawer Activity" -> TemplateRenderer.NAVIGATION_DRAWER_ACTIVITY
  "Settings Activity" -> TemplateRenderer.SETTINGS_ACTIVITY
  "Responsive Activity" ->  TemplateRenderer.RESPONSIVE_ACTIVITY
  "Primary/Detail Flow" -> TemplateRenderer.MASTER_DETAIL_FLOW
  "Android Things E6mpty Activity" -> TemplateRenderer.THINGS_ACTIVITY
  "Messaging Service" -> TemplateRenderer.AUTOMOTIVE_MESSAGING_SERVICE
  "Media Service" -> TemplateRenderer.AUTOMOTIVE_MEDIA_SERVICE
  "Google Pay Activity" -> TemplateRenderer.GOOGLE_PAY_ACTIVITY
  "Google Wallet Activity" -> TemplateRenderer.GOOGLE_WALLET_ACTIVITY
  "Basic Wear App",
  "Basic Wear App Without Associated Tile And Complication" -> TemplateRenderer.BLANK_WEAR_ACTIVITY

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

@Suppress("DEPRECATION")
fun titleToTemplateType(title: String, formFactor: FormFactor): TemplateType {
  return when (titleToTemplateRenderer(title, formFactor)) {
    TemplateRenderer.UNKNOWN_TEMPLATE_RENDERER -> TemplateType.UNKNOWN_TEMPLATE
    // Note: These values where never added/missing from the old TemplateRenderer
    TemplateRenderer.CUSTOM_TEMPLATE_RENDERER -> when (title) {
      "Slice Provider" -> TemplateType.SLICE_PROVIDER
      "Fragment + ViewModel" -> TemplateType.VIEW_MODEL_ACTIVITY
      "Bottom Navigation Activity" -> TemplateType.BOTTOM_NAVIGATION_ACTIVITY
      "Native C++" -> TemplateType.CPP_EMPTY_ACTIVITY
      "Game Activity (C++)" -> TemplateType.CPP_GAME_ACTIVITY
      "AIDL Folder" -> TemplateType.AIDL_FOLDER
      "Font Folder" -> TemplateType.FONT_FOLDER
      "Raw Resources Folder" -> TemplateType.RAW_RESOURCES_FOLDER
      "Java Resources Folder" -> TemplateType.JAVA_RESOURCES_FOLDER
      "XML Resources Folder" -> TemplateType.XML_RESOURCES_FOLDER
      "RenderScript Folder" -> TemplateType.RENDER_SCRIPT_FOLDER
      "Content Provider" -> TemplateType.CONTENT_PROVIDER
      "Android Manifest File" -> TemplateType.ANDROID_MANIFEST_FILE
      "App Actions XML File (deprecated)" -> TemplateType.APP_ACTIONS_XML_FILE
      "Shortcuts XML File" -> TemplateType.SHORTCUTS_XML_FILE
      else -> TemplateType.CUSTOM_TEMPLATE
    }

    TemplateRenderer.EMPTY_ACTIVITY -> TemplateType.EMPTY_ACTIVITY
    TemplateRenderer.LAYOUT_XML_FILE -> TemplateType.LAYOUT_XML_FILE
    TemplateRenderer.FRAGMENT_BLANK -> TemplateType.FRAGMENT_BLANK
    TemplateRenderer.NAVIGATION_DRAWER_ACTIVITY -> TemplateType.NAVIGATION_DRAWER_ACTIVITY
    TemplateRenderer.VALUES_XML_FILE -> TemplateType.VALUES_XML_FILE
    TemplateRenderer.GOOGLE_MAPS_ACTIVITY -> TemplateType.GOOGLE_MAPS_ACTIVITY
    TemplateRenderer.LOGIN_ACTIVITY -> TemplateType.LOGIN_ACTIVITY
    TemplateRenderer.ASSETS_FOLDER -> TemplateType.ASSETS_FOLDER
    TemplateRenderer.TABBED_ACTIVITY -> TemplateType.TABBED_ACTIVITY
    TemplateRenderer.SCROLLING_ACTIVITY -> TemplateType.SCROLLING_ACTIVITY
    TemplateRenderer.FULLSCREEN_ACTIVITY -> TemplateType.FULLSCREEN_ACTIVITY
    TemplateRenderer.SERVICE -> TemplateType.SERVICE
    TemplateRenderer.SETTINGS_ACTIVITY -> TemplateType.SETTINGS_ACTIVITY
    TemplateRenderer.FRAGMENT_LIST -> TemplateType.FRAGMENT_LIST
    TemplateRenderer.MASTER_DETAIL_FLOW -> TemplateType.PRIMARY_DETAIL_FLOW_ACTIVITY
    TemplateRenderer.BROADCAST_RECEIVER -> TemplateType.BROADCAST_RECEIVER
    TemplateRenderer.AIDL_FILE -> TemplateType.AIDL_FILE
    TemplateRenderer.INTENT_SERVICE -> TemplateType.INTENT_SERVICE
    TemplateRenderer.JNI_FOLDER -> TemplateType.JNI_FOLDER
    TemplateRenderer.JAVA_FOLDER -> TemplateType.JAVA_FOLDER
    TemplateRenderer.CUSTOM_VIEW -> TemplateType.CUSTOM_VIEW
    TemplateRenderer.GOOGLE_ADMOBS_ADS_ACTIVITY -> TemplateType.GOOGLE_ADMOBS_ADS_ACTIVITY
    TemplateRenderer.RES_FOLDER -> TemplateType.RES_FOLDER
    TemplateRenderer.ANDROID_TV_ACTIVITY -> TemplateType.ANDROID_TV_EMPTY_ACTIVITY
    TemplateRenderer.BLANK_WEAR_ACTIVITY -> TemplateType.BLANK_WEAR_ACTIVITY
    TemplateRenderer.BASIC_ACTIVITIY -> TemplateType.BASIC_ACTIVITY
    TemplateRenderer.APP_WIDGET -> TemplateType.APP_WIDGET
    TemplateRenderer.FRAGMENT_FULLSCREEN -> TemplateType.FRAGMENT_FULLSCREEN
    TemplateRenderer.FRAGMENT_GOOGLE_ADMOB_ADS -> TemplateType.FRAGMENT_GOOGLE_ADMOB_ADS
    TemplateRenderer.FRAGMENT_GOOGLE_MAPS -> TemplateType.FRAGMENT_GOOGLE_MAPS
    TemplateRenderer.FRAGMENT_LOGIN -> TemplateType.FRAGMENT_LOGIN
    TemplateRenderer.FRAGMENT_MODAL_BOTTOM_SHEET -> TemplateType.FRAGMENT_MODAL_BOTTOM_SHEET
    TemplateRenderer.FRAGMENT_SCROLL -> TemplateType.FRAGMENT_SCROLL
    TemplateRenderer.FRAGMENT_SETTINGS -> TemplateType.FRAGMENT_SETTINGS
    TemplateRenderer.FRAGMENT_VIEWMODEL -> TemplateType.FRAGMENT_VIEW_MODEL
    TemplateRenderer.COMPOSE_EMPTY_ACTIVITY -> TemplateType.COMPOSE_EMPTY_ACTIVITY
    TemplateRenderer.AUTOMOTIVE_MEDIA_SERVICE -> TemplateType.AUTOMOTIVE_MEDIA_SERVICE
    TemplateRenderer.AUTOMOTIVE_MESSAGING_SERVICE -> TemplateType.AUTOMOTIVE_MESSAGING_SERVICE
    TemplateRenderer.THINGS_ACTIVITY -> TemplateType.THINGS_EMPTY_ACTIVITY
    TemplateRenderer.WATCH_GOOGLE_MAPS_ACTIVITY -> TemplateType.WEAR_GOOGLE_MAPS_ACTIVITY
    TemplateRenderer.WATCH_FACE -> TemplateType.WEAR_FACE_ACTIVITY
    TemplateRenderer.WEAR_OS_COMPOSE_ACTIVITY -> TemplateType.EMPTY_ACTIVITY
    TemplateRenderer.RESPONSIVE_ACTIVITY -> TemplateType.RESPONSIVE_ACTIVITY
    TemplateRenderer.GOOGLE_PAY_ACTIVITY -> TemplateType.GOOGLE_PAY_ACTIVITY
    TemplateRenderer.GOOGLE_WALLET_ACTIVITY -> TemplateType.GOOGLE_WALLET_ACTIVITY

    TemplateRenderer.BLANK_ACTIVITY,
    TemplateRenderer.ANDROID_MODULE,
    TemplateRenderer.ANDROID_PROJECT,
    TemplateRenderer.JAVA_LIBRARY,
    TemplateRenderer.ANDROID_WEAR_MODULE,
    TemplateRenderer.ANDROID_TV_MODULE,
    TemplateRenderer.ALWAYS_ON_WEAR_ACTIVITY,
    TemplateRenderer.ANDROID_INSTANT_APP_PROJECT,
    TemplateRenderer.ANDROID_INSTANT_APP_MODULE,
    TemplateRenderer.ANDROID_INSTANT_APP_BUNDLE_PROJECT,
    TemplateRenderer.ANDROID_INSTANT_APP_DYNAMIC_MODULE,
    TemplateRenderer.BENCHMARK_LIBRARY_MODULE,
    TemplateRenderer.MACROBENCHMARK_LIBRARY_MODULE,
    TemplateRenderer.ANDROID_LIBRARY,
    TemplateRenderer.DYNAMIC_FEATURE_MODULE,
    TemplateRenderer.INSTANT_DYNAMIC_FEATURE_MODULE,
    TemplateRenderer.AUTOMOTIVE_MODULE,
    TemplateRenderer.THINGS_MODULE,
    TemplateRenderer.ML_MODEL_BINDING_IMPORT_WIZARD,
    TemplateRenderer.ML_MODEL_BINDING_FEATURE_OFF_NOTIFICATION,
    TemplateRenderer.ANDROID_NATIVE_MODULE -> throw RuntimeException("Invalid Template Title")
  }
}

fun moduleTemplateRendererToModuleType(moduleTemplateRenderer: TemplateRenderer): ModuleType {
  return when (moduleTemplateRenderer) {

    TemplateRenderer.UNKNOWN_TEMPLATE_RENDERER -> ModuleType.NOT_APPLICABLE // Existing module, can't find what type is
    TemplateRenderer.ANDROID_MODULE -> ModuleType.PHONE_TABLET
    TemplateRenderer.ANDROID_LIBRARY -> ModuleType.ANDROID_LIBRARY
    TemplateRenderer.DYNAMIC_FEATURE_MODULE -> ModuleType.DYNAMIC_FEATURE
    TemplateRenderer.INSTANT_DYNAMIC_FEATURE_MODULE -> ModuleType.INSTANT_DYNAMIC_FEATURE
    TemplateRenderer.AUTOMOTIVE_MODULE -> ModuleType.AUTOMOTIVE
    TemplateRenderer.ANDROID_WEAR_MODULE ->  ModuleType.WEAR_OS
    TemplateRenderer.ANDROID_TV_MODULE -> ModuleType.ANDROID_TV
    TemplateRenderer.THINGS_MODULE -> ModuleType.ANDROID_THINGS
    TemplateRenderer.JAVA_LIBRARY -> ModuleType.JAVA_OR_KOTLIN_LIBRARY
    TemplateRenderer.BENCHMARK_LIBRARY_MODULE -> ModuleType.BENCHMARK_LIBRARY
    else -> ModuleType.UNKNOWN

    // TODO: b/161230278 Some new modules don't render a template. Need to send the event from their Model render.
    // IMPORT_GRADLE = 10; IMPORT_ECLIPSE = 11; IMPORT_JAR_AAR = 12;
  }
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
}

fun logRendering(projectData: ProjectTemplateData, project: Project, metrics: TemplateMetrics) {
  val templateComponentBuilder = TemplatesUsage.TemplateComponent.newBuilder().apply {
    templateType = metrics.templateType
    wizardUiContext = metrics.wizardContext
  }

  val templateModuleBuilder = TemplatesUsage.TemplateModule.newBuilder().apply {
    moduleType = metrics.moduleType
    minSdk = metrics.minSdk
    if (metrics.bytecodeLevel != null) {
      bytecodeLevel = when (metrics.bytecodeLevel) {
        TemplateBytecodeLevel.L6 -> BytecodeLevel.LEVEL_6
        TemplateBytecodeLevel.L7 -> BytecodeLevel.LEVEL_7
        TemplateBytecodeLevel.L8 -> BytecodeLevel.LEVEL_8
      }
    }
  }

  val templateProjectBuilder = TemplatesUsage.TemplateProject.newBuilder().apply {
    usesLegacySupport = metrics.useAppCompat
    usesBuildGradleKts = metrics.useGradleKts
  }

  val kotlinSupport = KotlinSupport.newBuilder().apply {
    includeKotlinSupport = projectData.language == Language.Kotlin
    kotlinSupportVersion = projectData.kotlinVersion
  }

  val aseBuilder = AndroidStudioEvent.newBuilder()
    .setCategory(AndroidStudioEvent.EventCategory.TEMPLATE)
    .setKind(AndroidStudioEvent.EventKind.WIZARD_TEMPLATES_USAGE)
    .setTemplateUsage(
      TemplatesUsage.newBuilder()
        .setTemplateComponent(templateComponentBuilder)
        .setTemplateModule(templateModuleBuilder)
        .setTemplateProject(templateProjectBuilder)
        .setKotlinSupport(kotlinSupport)
    )
  UsageTracker.log(aseBuilder.withProjectId(project))
}