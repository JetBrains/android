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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.Java8DefaultProcessorSettings
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle
import java.util.function.Supplier

private val LOG = Logger.getInstance(LOG_CATEGORY)

class Java8DefaultRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  var noLanguageLevelAction = INSERT_OLD_DEFAULT
    set(value) {
      LOG.info("setting noLanguageLevelAction to ${value.name}")
      field = value
    }

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = AgpUpgradeComponentNecessity.standardPointNecessity(current, new, ACTIVATED_VERSION)

  override fun findComponentUsages(): Array<out UsageInfo> {
    fun usageType(model: LanguageLevelPropertyModel): UsageType? = when {
      model.psiElement != null -> EXISTING_DIRECTIVE_USAGE_TYPE
      noLanguageLevelAction == INSERT_OLD_DEFAULT -> INSERT_OLD_USAGE_TYPE
      noLanguageLevelAction == ACCEPT_NEW_DEFAULT -> ACCEPT_NEW_USAGE_TYPE
      else -> null
    }

    val usages = mutableListOf<UsageInfo>()
    projectBuildModel.allIncludedBuildModels.forEach model@{ model ->
      // TODO(xof): we should consolidate the various ways of guessing what a module is from its plugins (see also
      //  PsModuleType.kt)
      val pluginNames = model.appliedPlugins().map { it.name().forceString() }
      pluginNames
        .firstOrNull { it.startsWith("java") || it.startsWith("org.gradle.java") || it == "application" || it == "org.gradle.application" }
        ?.let { _ ->
          model.java().sourceCompatibility().let {
            val psiElement = listOf(it, model.java(), model).firstNotNullOfOrNull { model -> model.psiElement }!!
            val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
            val existing = it.psiElement != null
            usages.add(JavaLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "sourceCompatibility"))
          }
          model.java().targetCompatibility().let {
            val psiElement = listOf(it, model.java(), model).firstNotNullOfOrNull { model -> model.psiElement }!!
            val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
            val existing = it.psiElement != null
            usages.add(JavaLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "targetCompatibility"))
          }
        }

      pluginNames.firstOrNull { it.startsWith("com.android") }?.let { _ ->
        model.android().compileOptions().sourceCompatibility().let {
          val psiElement = listOf(it, model.android().compileOptions(), model.android(), model)
            .firstNotNullOfOrNull { model -> model.psiElement }!!
          val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
          val existing = it.psiElement != null
          usages.add(JavaLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "sourceCompatibility"))
        }
        model.android().compileOptions().targetCompatibility().let {
          val psiElement = listOf(it, model.android().compileOptions(), model.android(), model)
            .firstNotNullOfOrNull { model -> model.psiElement }!!
          val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
          val existing = it.psiElement != null
          usages.add(JavaLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "targetCompatibility"))
        }
        pluginNames.firstOrNull { it.startsWith("org.jetbrains.kotlin") || it.startsWith("kotlin") }?.let { _ ->
          model.android().kotlinOptions().jvmTarget().let {
            val psiElement = listOf(it, model.android().kotlinOptions(), model.android(), model)
              .firstNotNullOfOrNull { model -> model.psiElement }!!
            val wrappedElement = WrappedPsiElement(psiElement, this, usageType(it))
            val existing = it.psiElement != null
            usages.add(KotlinLanguageLevelUsageInfo(wrappedElement, it, existing, noLanguageLevelAction, "jvmTarget"))
          }
        }
      }
    }
    return usages.toTypedArray()
  }

  override fun completeComponentInfo(builder: UpgradeAssistantComponentInfo.Builder): UpgradeAssistantComponentInfo.Builder {
    val protoNoLanguageLevelAction = when (noLanguageLevelAction) {
      INSERT_OLD_DEFAULT -> Java8DefaultProcessorSettings.NoLanguageLevelAction.INSERT_OLD_DEFAULT
      ACCEPT_NEW_DEFAULT -> Java8DefaultProcessorSettings.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
    }
    val java8Settings = Java8DefaultProcessorSettings.newBuilder().setNoLanguageLevelAction(protoNoLanguageLevelAction).build()
    return builder.setKind(UpgradeAssistantComponentKind.JAVA8_DEFAULT).setJava8DefaultSettings(java8Settings)
  }

  override fun getCommandName(): String = AndroidBundle.message("project.upgrade.java8DefaultRefactoringProcessor.commandName")

  override val groupingName: String
    get() = AndroidBundle.message("project.upgrade.java8DefaultRefactoringProcessor.groupingName")

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.Java8Default"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = AndroidBundle.message("project.upgrade.java8DefaultRefactoringProcessor.usageView.header")
    }
  }

  override fun computeIsAlwaysNoOpForProject(): Boolean =
    findComponentUsages().all {
      when (it) {
        is JavaLanguageLevelUsageInfo -> it.existing
        is KotlinLanguageLevelUsageInfo -> it.existing
        else -> false // shouldn't happen; if it does, return conservative value
      }
    }

  override fun getReadMoreUrl(): String? = "https://developer.android.com/r/tools/upgrade-assistant/java8-default"

  override fun getShortDescription(): String? =
    """
      The default Java Language Level is now Java 8, rather than the previous
      Java 7.  If your project requires building with Java 7, the project's
      build files need explicit Language Level directives.
    """.trimIndent()

  companion object {
    val ACTIVATED_VERSION = GradleVersion.parse("4.2.0-alpha05")

    val EXISTING_DIRECTIVE_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.java8DefaultRefactoringProcessor.existingDirectiveUsageType"))
    val ACCEPT_NEW_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.java8DefaultRefactoringProcessor.acceptNewUsageType"))
    val INSERT_OLD_USAGE_TYPE = UsageType(AndroidBundle.messagePointer("project.upgrade.java8DefaultRefactoringProcessor.insertOldUsageType"))
  }

  enum class NoLanguageLevelAction(val supplier: Supplier<String>) {
    ACCEPT_NEW_DEFAULT(AndroidBundle.messagePointer("project.upgrade.noLanguageLevelAction.acceptNewDefault")),
    INSERT_OLD_DEFAULT(AndroidBundle.messagePointer("project.upgrade.noLanguageLevelAction.insertOldDefault")),
    ;

    override fun toString() = supplier.get()
  }
}

class JavaLanguageLevelUsageInfo(
  element: WrappedPsiElement,
  private val model: LanguageLevelPropertyModel,
  internal val existing: Boolean,
  private val noLanguageLevelAction: NoLanguageLevelAction,
  private val propertyName: String
): GradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String {
    return when (existing) {
      false -> when (noLanguageLevelAction) {
        INSERT_OLD_DEFAULT -> AndroidBundle.message("project.upgrade.javaLanguageLevelUsageInfo.tooltipText.insertOldDefault", propertyName)
        ACCEPT_NEW_DEFAULT -> AndroidBundle.message("project.upgrade.javaLanguageLevelUsageInfo.tooltipText.acceptNewDefault")
      }
      true -> AndroidBundle.message("project.upgrade.javaLanguageLevelUsageInfo.tooltipText.existing", propertyName)
    }
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    when {
      !existing && noLanguageLevelAction == INSERT_OLD_DEFAULT -> model.setLanguageLevel(LanguageLevel.JDK_1_7)
    }
  }

  override fun getDiscriminatingValues(): List<Any> = listOf(propertyName)
}

class KotlinLanguageLevelUsageInfo(
  element: WrappedPsiElement,
  private val model: LanguageLevelPropertyModel,
  internal val existing: Boolean,
  private val noLanguageLevelAction: NoLanguageLevelAction,
  private val propertyName: String
): SpoilingGradleBuildModelUsageInfo(element) {
  override fun getTooltipText(): String {
    return when (existing) {
      false -> when (noLanguageLevelAction) {
        INSERT_OLD_DEFAULT -> AndroidBundle.message("project.upgrade.kotlinLanguageLevelUsageInfo.tooltipText.insertOldDefault", propertyName)
        ACCEPT_NEW_DEFAULT -> AndroidBundle.message("project.upgrade.kotlinLanguageLevelUsageInfo.tooltipText.acceptNewDefault")

      }
      true -> AndroidBundle.message("project.upgrade.kotlinLanguageLevelUsageInfo.tooltipText.existing", propertyName)
    }
  }

  private val gradleFile = model.gradleFile

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    when {
      !existing && noLanguageLevelAction == INSERT_OLD_DEFAULT -> {
        model.setLanguageLevel(LanguageLevel.JDK_1_6)
        super.performBuildModelRefactoring(processor)
      }
    }
  }

  override fun performPsiSpoilingBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    val element = processor.projectBuildModel.getModuleBuildModel(gradleFile).android().kotlinOptions().jvmTarget().psiElement ?: return
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(element.containingFile) ?: return
    document.insertString(element.textRange.endOffset, " // Java 7 not supported by kotlinOptions jvmTarget")
    FileDocumentManager.getInstance().saveDocument(document)
    documentManager.commitDocument(document)
  }

  override fun getDiscriminatingValues(): List<Any> = listOf(propertyName)
}
