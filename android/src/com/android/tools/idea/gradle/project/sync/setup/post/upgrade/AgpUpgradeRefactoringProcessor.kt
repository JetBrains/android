/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade

import com.android.SdkConstants.GRADLE_DISTRIBUTION_URL_PROPERTY
import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.SdkConstants.GRADLE_MINIMUM_VERSION
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.CLASSPATH
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.isUpdatablePluginVersion
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.AgpUpgradeComponentNecessity.*
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
import com.android.tools.idea.gradle.util.BuildFileProcessor
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.utils.FileUtils
import com.android.utils.appendCapitalized
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.Property
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.ThreeState.NO
import com.intellij.util.ThreeState.YES
import java.io.File

abstract class GradleBuildModelRefactoringProcessor : BaseRefactoringProcessor {
  constructor(project: Project) : super(project) {
    this.project = project
    this.buildModel = ProjectBuildModel.get(project)
  }
  constructor(processor: GradleBuildModelRefactoringProcessor): super(processor.project) {
    this.project = processor.project
    this.buildModel = processor.buildModel
  }

  val project: Project
  val buildModel: ProjectBuildModel

  val psiSpoilingUsageInfos = mutableListOf<UsageInfo>()

  var foundUsages: Boolean = false

  override fun performRefactoring(usages: Array<out UsageInfo>?) {
    usages?.forEach {
      if (it is GradleBuildModelUsageInfo) {
        it.performBuildModelRefactoring(this)
      }
    }
  }

  override fun performPsiSpoilingRefactoring() {
    buildModel.applyChanges()

    // this is (at present) somewhat speculative generality: it was originally motivated by the refactoring
    // doing non-undoable things to the Psi, and so preventing Undo for the entire refactoring operation.  However,
    // the GradleWrapper.foo() manipulations of the properties file was so low-level that even running it in the
    // context of performPsiSpoilingRefactoring() destroyed Undo, so I rewrote the properties file manipulation,
    // at which point the manipulation could be done in performRefactoring().
    psiSpoilingUsageInfos.forEach {
      if (it is GradleBuildModelUsageInfo)
        it.performPsiSpoilingBuildModelRefactoring(this)
    }

    super.performPsiSpoilingRefactoring()
  }
}

/**
 * Instances of [GradleBuildModelUsageInfo] should perform their refactor through the buildModel, and must not
 * invalidate either the BuildModel or the underlying Psi in their [performBuildModelRefactoring] method.  Any spoiling
 * should be done in the [performPsiSpoilingBuildModelRefactoring] method, which will run after the changes in the
 * buildModel have been applied.
 */
abstract class GradleBuildModelUsageInfo(element: PsiElement, val current: GradleVersion, val new: GradleVersion): UsageInfo(element) {
  open fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    processor.psiSpoilingUsageInfos.add(this)
  }
  open fun performPsiSpoilingBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) = Unit
}

class AgpUpgradeRefactoringProcessor(
  project: Project,
  val current: GradleVersion,
  val new: GradleVersion
) : GradleBuildModelRefactoringProcessor(project) {

  val classpathRefactoringProcessor = AgpClasspathDependencyRefactoringProcessor(this)
  val componentRefactoringProcessors = listOf(
    GMavenRepositoryRefactoringProcessor(this),
    AgpGradleVersionRefactoringProcessor(this),
    Java8DefaultRefactoringProcessor(this),
    CompileRuntimeConfigurationRefactoringProcessor(this)
  )

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>?): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Upgrade AGP from $current to $new"
    }
  }

  override fun findUsages(): Array<UsageInfo> {
    // TODO(xof): *something* needs to ensure that the buildModel has a fresh view of the Dsl files before
    //  looking for things (particularly since findUsages can be re-run by user action) but it's not clear that
    //  this is the right thing: it is a bit expensive, and sub-processors will have to also reparse() in case
    //  they are run in isolation.
    buildModel.reparse()
    val usages = ArrayList<UsageInfo>()

    usages.addAll(classpathRefactoringProcessor.findUsages())

    componentRefactoringProcessors.forEach { processor ->
      usages.addAll(processor.findUsages())
    }

    foundUsages = usages.size > 0
    return usages.toTypedArray()
  }

  override fun performPsiSpoilingRefactoring() {
    super.performPsiSpoilingRefactoring()
    // in AndroidRefactoringUtil this happens between performRefactoring() and performPsiSpoilingRefactoring().  Not
    // sure why.
    //
    // FIXME(xof): having this here works (in that a sync is triggered at the end of the refactor) but no sync is triggered
    //  if the refactoring action is undone.
    GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncInvoker.Request(TRIGGER_AGP_VERSION_UPDATED))
  }

  override fun getCommandName() = "Upgrade AGP version from ${current} to ${new}"

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade"
}

/**
One common way to characterise a compatibility change is that some old feature f_o is deprecated in favour of some new feature f_n from
version v_n (when the new feature f_n is available); the old feature f_o is finally removed in version v_o.  That is, feature f_n is
available in the versions [v_n, +∞), and f_o is available in versions (-∞, v_o) -- note the exclusion of v_o, which is the first version
in which f_o is *not* available.  For the rest of this analysis to hold, we also assume that v_n <= v_o -- that is, there is a set
of versions where the features overlap, or else a feature is replaced wholesale in a single version, but that there is no period where
neither of the features is present.

If we can characterise the upgrade from a (cur, new > cur) pair of AGP versions, a compatibility change (implemented by a single
component refactoring) can be put into one of six categories:

| 1 | 2 | 3 | 4 | Necessity
|---|---|---|---|----------
|v_n|v_o|cur|new| [IRRELEVANT_PAST]
|cur|new|v_n|v_o| [IRRELEVANT_FUTURE]
|cur|v_n|v_o|new| [MANDATORY_CODEPENDENT] (must do the refactoring in the same action as the AGP version upgrade)
|v_n|cur|v_o|new| [MANDATORY_INDEPENDENT] (must do the refactoring, but can do it before the AGP version upgrade)
|cur|v_n|new|v_o| [OPTIONAL_CODEPENDENT] (need not do the refactoring, but if done must be with or after the AGP version upgrade)
|v_n|cur|new|v_o| [OPTIONAL_INDEPENDENT] (need not do the refactoring, but if done can be at any point in the process)

with the conventions for v_n and v_o as described above, equality in version numbers (e.g. if we are upgrading to the first version
where a feature appears or disappears) is handled by v_n/v_o sorting before cur/new -- so that when comparing a feature version against
an version associated with an AGP dependency, we must use the < or >= operators depending on whether the feature version is on the left
or right of the operator respectively.

For the possibly-simpler case where we have a discontinuity in behaviour, v_o = v_n = vvv, and the three possible cases are:

| 1 | 2 | 3 | Necessity
+---+---+---+----------
|vvv|cur|new| [IRRELEVANT_PAST]
|cur|vvv|new| [MANDATORY_CODEPENDENT]
|cur|new|vvv| [IRRELEVANT_FUTURE]

(again in case of equality, vvv sorts before cur and new)

If other refactorings come along which are more complicated than can be supported by this model of a single feature replaced by another,
we might need more necessity values.
*/
enum class AgpUpgradeComponentNecessity {
  IRRELEVANT_PAST,
  IRRELEVANT_FUTURE,
  MANDATORY_CODEPENDENT,
  MANDATORY_INDEPENDENT,
  OPTIONAL_CODEPENDENT,
  OPTIONAL_INDEPENDENT,
}

// Each individual refactoring involved in an AGP Upgrade is implemented as its own refactoring processor.  For a "batch" upgrade, most
// of the functionality of a refactoring processor is handled by an outer (master) RefactoringProcessor, which delegates to sub-processors
// for findUsages (and implicitly for performing the refactoring, implemented as methods on the UsageInfos).  However, there may be
// a need for chained upgrades in the future, where each individual refactoring processor would run independently.
abstract class AgpUpgradeComponentRefactoringProcessor: GradleBuildModelRefactoringProcessor {
  val current: GradleVersion
  val new: GradleVersion
  private var _isEnabled: Boolean? = null
  var isEnabled: Boolean
    set(value) { _isEnabled = value }
    get() {
      if (_isEnabled == null) _isEnabled = when (necessity()) {
        IRRELEVANT_FUTURE, IRRELEVANT_PAST -> false
        MANDATORY_CODEPENDENT, MANDATORY_INDEPENDENT, OPTIONAL_CODEPENDENT, OPTIONAL_INDEPENDENT -> true
      }
      return _isEnabled!!
    }

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project) {
    this.current = current
    this.new = new
  }

  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor) {
    this.current = processor.current
    this.new = processor.new
  }

  abstract fun necessity(): AgpUpgradeComponentNecessity

  public final override fun findUsages(): Array<out UsageInfo> {
    if (!isEnabled) return UsageInfo.EMPTY_ARRAY
    return findComponentUsages()
  }

  protected abstract fun findComponentUsages(): Array<out UsageInfo>

  public abstract override fun getCommandName(): String

  open fun getReadMoreUrl(): String? = null
}

class AgpClasspathDependencyRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = MANDATORY_CODEPENDENT

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    // using the buildModel, look for classpath dependencies on AGP, and if we find one, record it as a usage, and additionally
    buildModel.allIncludedBuildModels.forEach model@{ model ->
      model.buildscript().dependencies().artifacts(CLASSPATH).forEach dep@{ dep ->
        when (val shouldUpdate = isUpdatablePluginVersion(new, dep)) {
          YES -> {
            val resultModel = dep.version().resultModel
            val psiElement = when (val element = resultModel.rawElement) {
              null -> return@dep
              // TODO(xof): most likely we need a range in PsiElement, if the dependency is expressed in compactNotation
              is FakeArtifactElement -> element.realExpression.psiElement
              else -> element.psiElement
            }
            psiElement?.let {
              usages.add(AgpVersionUsageInfo(it, current, new, resultModel))
            }
          }
          NO -> return@model
          else -> Unit
        }
      }
    }
    foundUsages = usages.size > 0
    return usages.toTypedArray()
  }

  override fun getCommandName(): String = "Upgrade AGP dependency from $current to $new"

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.classpathDependency"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>?): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Upgrade AGP classpath dependency version from $current to $new"
    }
  }
}

class AgpVersionUsageInfo(
  element: PsiElement,
  current: GradleVersion,
  new: GradleVersion,
  private val resultModel: GradlePropertyModel
) : GradleBuildModelUsageInfo(element, current, new) {
  override fun getTooltipText(): String {
    return "Upgrade AGP version from ${current} to ${new}"
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    resultModel.setValue(new.toString())
  }
}

class GMavenRepositoryRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: GradleVersion, new: GradleVersion, gradleVersion: GradleVersion): super(project, current, new) {
    this.gradleVersion = gradleVersion
  }
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor) {
    // FIXME(xof): this is (theoretically) wrong; the version in question is the version of Gradle that the project
    //  will use, after refactoring, not necessarily the minimum-supported version of Gradle.
    //  This means this refactoring is intertwingled with the refactoring which upgrades the Gradle version in the wrapper properties,
    //  though in practice it is not currently a problem (the behaviour changed in Gradle 4.0).
    //  Further: we have the opportunity to make this correct if we can rely on the order of processing UsageInfos
    //  because if we assure ourselves that the Gradle upgrade happens before this one, we can (in principle)
    //  inspect the buildModel or the project to determine the appropriate version of Gradle.
    //  However: at least if we have gone through a preview, the UsageInfo ordering is randomized as
    //  BaseRefactoringProcessor#customizeUsagesView / UsageViewUtil#getNotExcludedUsageInfos makes a Set of
    //  them.
    this.gradleVersion = GradleVersion.tryParse(GRADLE_MINIMUM_VERSION)!!
  }

  var gradleVersion: GradleVersion
    @VisibleForTesting set

  override fun necessity() = when {
    current < GradleVersion(3, 0, 0) && new >= GradleVersion(3, 0, 0) -> MANDATORY_CODEPENDENT
    new < GradleVersion(3, 0, 0) -> IRRELEVANT_FUTURE
    else -> IRRELEVANT_PAST
  }

  override fun findComponentUsages(): Array<UsageInfo> {
    val usages = ArrayList<UsageInfo>()
    // using the buildModel, look for classpath dependencies on AGP, and if we find one,
    // check the buildscript/repositories block for a google() gmaven entry, recording an additional usage if we don't find one
    buildModel.allIncludedBuildModels.forEach model@{ model ->
      model.buildscript().dependencies().artifacts(CLASSPATH).forEach dep@{ dep ->
        when (isUpdatablePluginVersion(new, dep)) {
          // consider returning a usage even if the dependency has the current version (in a chained upgrade, the dependency
          // might have been updated before this RefactoringProcessor gets a chance to run).  The applicability of the processor
          // will prevent this from being a problem.
          YES, NO -> {
            val repositories = model.buildscript().repositories()
            if (!repositories.hasGoogleMavenRepository()) {
              // TODO(xof) if we don't have a psiElement, we should add a suitable parent (and explain what
              //  we're going to do in terms of that parent.  (But a buildscript block without a repositories block is unusual)
              repositories.psiElement?.let {
                element -> usages.add(RepositoriesNoGMavenUsageInfo(element, current, new, repositories, gradleVersion))
              }
            }
          }
          else -> Unit
        }
      }
    }
    foundUsages = usages.size > 0
    return usages.toTypedArray()
  }

  override fun getCommandName(): String = "Add google() GMaven to buildscript repositories"

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.gmaven"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>?): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Add google() GMaven to buildscript repositories"
    }
  }
}

class RepositoriesNoGMavenUsageInfo(
  element: PsiElement,
  current: GradleVersion,
  new: GradleVersion,
  private val repositoriesModel: RepositoriesModel,
  private val gradleVersion: GradleVersion
) : GradleBuildModelUsageInfo(element, current, new) {
  override fun getTooltipText(): String {
    return "Add google() to buildscript repositories"
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    repositoriesModel.addGoogleMavenRepository(gradleVersion)
  }
}

class AgpGradleVersionRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  constructor(project: Project, current: GradleVersion, new: GradleVersion, gradleVersion: GradleVersion): super(project, current, new) {
    this.gradleVersion = gradleVersion
  }
  constructor(processor: AgpUpgradeRefactoringProcessor) : super(processor) {
    gradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION)
  }

  val gradleVersion: GradleVersion

  override fun necessity() = MANDATORY_CODEPENDENT

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    // check the project's wrapper(s) for references to no-longer-supported Gradle versions
    project.basePath?.let {
      val projectRootFolders = listOf(File(FileUtils.toSystemDependentPath(it))) + BuildFileProcessor.getCompositeBuildFolderPaths(project)
      projectRootFolders.filterNotNull().forEach { ioRoot ->
        val ioFile = GradleWrapper.getDefaultPropertiesFilePath(ioRoot)
        val gradleWrapper = GradleWrapper.get(ioFile, project)
        val currentGradleVersion = gradleWrapper.gradleVersion ?: return@forEach
        val parsedCurrentGradleVersion = GradleVersion.tryParse(currentGradleVersion) ?: return@forEach
        if (!GradleUtil.isSupportedGradleVersion(parsedCurrentGradleVersion)) {
          val virtualFile = VfsUtil.findFileByIoFile(ioFile, true) ?: return@forEach
          val propertiesFile = PsiManager.getInstance(project).findFile(virtualFile) as? PropertiesFile ?: return@forEach
          val property = propertiesFile.findPropertyByKey(GRADLE_DISTRIBUTION_URL_PROPERTY) ?: return@forEach
          usages.add(GradleVersionUsageInfo(property.psiElement, current, new, gradleVersion))
        }
      }
    }
    foundUsages = usages.size > 0
    return usages.toTypedArray()
  }

  override fun getCommandName(): String = "Upgrade Gradle version to $gradleVersion"

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.gradleVersion"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>?): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Upgrade Gradle version to $gradleVersion"
    }
  }
}

class GradleVersionUsageInfo(
  element: PsiElement,
  current: GradleVersion,
  new: GradleVersion,
  private val gradleVersion: GradleVersion
) : GradleBuildModelUsageInfo(element, current, new) {
  override fun getTooltipText(): String {
    return "Upgrade Gradle version to $gradleVersion"
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    (element as? Property)?.setValue(GradleWrapper.getDistributionUrl(gradleVersion.toString(), true))
    // TODO(xof): if we brought properties files into the build model, this would not be necessary here, but the buildModel applyChanges()
    //  does all that is necessary to save files, so we do that here to mimic that.
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(element!!.containingFile) ?: return
    if (documentManager.isDocumentBlockedByPsi(document)) {
      documentManager.doPostponedOperationsAndUnblockDocument(document)
    }
    FileDocumentManager.getInstance().saveDocument(document)
    if (!documentManager.isCommitted(document)) {
      documentManager.commitDocument(document)
    }
  }
}

class Java8DefaultRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {

  var noLanguageLevelAction = INSERT_OLD_DEFAULT

  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = when {
    current < ACTIVATED_VERSION && new >= ACTIVATED_VERSION -> MANDATORY_CODEPENDENT
    new < ACTIVATED_VERSION -> IRRELEVANT_FUTURE
    else -> IRRELEVANT_PAST
  }

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()
    buildModel.allIncludedBuildModels.forEach model@{ model ->
      // TODO(xof): we should consolidate the various ways of guessing what a module is from its plugins (see also
      //  PsModuleType.kt)
      val pluginNames = model.plugins().map { it.name().forceString() }
      pluginNames.firstOrNull { it.startsWith("java") || it == "application" }?.let { _ ->
        model.java().sourceCompatibility().let {
          val psiElement = it.psiElement ?: model.java().psiElement ?: model.psiElement!!
          usages.add(JavaLanguageLevelUsageInfo(psiElement, current, new, it, it.psiElement != null, noLanguageLevelAction, "sourceCompatibility"))
        }
        model.java().targetCompatibility().let {
          val psiElement = it.psiElement ?: model.java().psiElement ?: model.psiElement!!
          usages.add(JavaLanguageLevelUsageInfo(psiElement, current, new, it, it.psiElement != null, noLanguageLevelAction, "targetCompatibility"))
        }
      }

      pluginNames.firstOrNull { it.startsWith("com.android") }?.let { _ ->
        model.android().compileOptions().sourceCompatibility().let {
          val psiElement = it.psiElement ?: model.android().compileOptions().psiElement ?: model.android().psiElement ?: model.psiElement!!
          usages.add(JavaLanguageLevelUsageInfo(psiElement, current, new, it, it.psiElement != null, noLanguageLevelAction, "sourceCompatibility"))
        }
        model.android().compileOptions().targetCompatibility().let {
          val psiElement = it.psiElement ?: model.android().compileOptions().psiElement ?: model.android().psiElement ?: model.psiElement!!
          usages.add(JavaLanguageLevelUsageInfo(psiElement, current, new, it, it.psiElement != null, noLanguageLevelAction, "targetCompatibility"))
        }
        pluginNames.firstOrNull { it.startsWith("org.jetbrains.kotlin") || it.startsWith("kotlin") }?.let { _ ->
          model.android().kotlinOptions().jvmTarget().let {
            val psiElement = it.psiElement ?: model.android().kotlinOptions().psiElement ?: model.android().psiElement ?: model.psiElement!!
            usages.add(KotlinLanguageLevelUsageInfo(psiElement, current, new, it, it.psiElement != null, noLanguageLevelAction, "jvmOptions"))
          }
        }
      }
    }
    foundUsages = usages.size > 0
    return usages.toTypedArray()
  }

  override fun getCommandName(): String = "Update implicit LanguageLevel properties"

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.Java8Default"

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>?): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> {
        return PsiElement.EMPTY_ARRAY
      }

      override fun getProcessedElementsHeader() = "Update implicit LanguageLevel properties"
    }
  }

  // TODO(xof): move this target to _redirects.yaml (or find some other way to be future-proof)
  override fun getReadMoreUrl(): String? = "https://developer.android.com/studio/write/java8-support#supported_features"

  companion object {
    val ACTIVATED_VERSION = GradleVersion.parse("4.2.0-alpha05")
  }

  enum class NoLanguageLevelAction(val text: String) {
    ACCEPT_NEW_DEFAULT("Accept the new default of Java 8"),
    INSERT_OLD_DEFAULT("Insert directives to continue using Java 7"),
    ;

    override fun toString() = text
  }
}

class JavaLanguageLevelUsageInfo(
  element: PsiElement,
  current: GradleVersion,
  new: GradleVersion,
  private val model: LanguageLevelPropertyModel,
  private val existing: Boolean,
  private val noLanguageLevelAction: NoLanguageLevelAction,
  private val propertyName: String
): GradleBuildModelUsageInfo(element, current, new) {
  override fun getTooltipText(): String {
    return when (existing) {
      false -> when (noLanguageLevelAction) {
        INSERT_OLD_DEFAULT -> "insert explicit $propertyName to preserve previous behaviour"
        ACCEPT_NEW_DEFAULT -> "accept new default"
      }
      true -> "preserve existing explicit $propertyName"
    }
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    when {
      !existing && noLanguageLevelAction == INSERT_OLD_DEFAULT -> model.setLanguageLevel(LanguageLevel.JDK_1_7)
    }
  }

  // Don't need hashCode for correctness because this is stricter than the superclass's equals().
  override fun equals(other: Any?): Boolean {
    return super.equals(other) && other is JavaLanguageLevelUsageInfo && propertyName == other.propertyName
  }
}

class KotlinLanguageLevelUsageInfo(
    element: PsiElement,
    current: GradleVersion,
    new: GradleVersion,
    private val model: LanguageLevelPropertyModel,
    private val existing: Boolean,
    private val noLanguageLevelAction: NoLanguageLevelAction,
    private val propertyName: String
  ): GradleBuildModelUsageInfo(element, current, new) {
  override fun getTooltipText(): String {
    return when (existing) {
      false -> when (noLanguageLevelAction) {
        INSERT_OLD_DEFAULT -> "insert explicit $propertyName to preserve previous behaviour"
        ACCEPT_NEW_DEFAULT -> "accept new default"

      }
      true -> "preserve existing explicit $propertyName"
    }
  }

  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    when {
      !existing && noLanguageLevelAction == INSERT_OLD_DEFAULT -> model.setLanguageLevel(LanguageLevel.JDK_1_6)
    }
  }

  // Don't need hashCode for correctness because this is stricter than the superclass's equals().
  override fun equals(other: Any?): Boolean {
    return super.equals(other) && other is KotlinLanguageLevelUsageInfo && propertyName == other.propertyName
  }
}

class CompileRuntimeConfigurationRefactoringProcessor : AgpUpgradeComponentRefactoringProcessor {
  constructor(project: Project, current: GradleVersion, new: GradleVersion): super(project, current, new)
  constructor(processor: AgpUpgradeRefactoringProcessor): super(processor)

  override fun necessity() = when {
    current < IMPLEMENTATION_API_INTRODUCED && new >= COMPILE_REMOVED -> MANDATORY_CODEPENDENT
    current < COMPILE_REMOVED && new >= COMPILE_REMOVED -> MANDATORY_INDEPENDENT
    new < IMPLEMENTATION_API_INTRODUCED -> IRRELEVANT_FUTURE
    current >= COMPILE_REMOVED -> IRRELEVANT_PAST
    current < IMPLEMENTATION_API_INTRODUCED -> OPTIONAL_CODEPENDENT
    else -> OPTIONAL_INDEPENDENT
  }

  override fun findComponentUsages(): Array<out UsageInfo> {
    val usages = mutableListOf<UsageInfo>()

    fun computeReplacementName(name: String, compileReplacement: String): String? {
      return when {
        name == "compile" -> compileReplacement
        name.endsWith("Compile") -> name.removeSuffix("Compile").appendCapitalized(compileReplacement)
        name == "runtime" -> "runtimeOnly"
        name.endsWith("Runtime") -> "${name}Only"
        else -> null
      }
    }

    fun maybeAddUsageForDependency(dependency: DependencyModel, compileReplacement: String, psiElement: PsiElement) {
      val configuration = dependency.configurationName()
      computeReplacementName(configuration, compileReplacement)?.let {
        usages.add(ObsoleteConfigurationDependencyUsageInfo(psiElement, current, new, dependency, it))
      }
    }

    fun maybeAddUsageForConfiguration(configuration: ConfigurationModel, compileReplacement: String, psiElement: PsiElement) {
      val name = configuration.name()
      computeReplacementName(name, compileReplacement)?.let {
        usages.add(ObsoleteConfigurationConfigurationUsageInfo(psiElement, current, new, configuration, it))
      }
    }

    buildModel.allIncludedBuildModels.forEach model@{ model ->
      // if we don't have a PsiElement for the model, we don't have a file at all, and attempting to perform a refactoring is not
      // going to work
      val modelPsiElement = model.psiElement ?: return@model
      val pluginSet = model.plugins().map { it.name().forceString() }.toSet()
      // TODO(xof): as with the Java8Default refactoring above, we should define and use some kind of API
      //  to determine what kind of a module we have.
      val applicationSet = setOf(
        "application",
        "com.android.application", "com.android.test", "com.android.instant-app")
      val librarySet = setOf(
        "java", "java-library",
        "com.android.library", "com.android.dynamic-feature", "com.android.feature")
      val compileReplacement = when {
        !model.android().dynamicFeatures().toList().isNullOrEmpty() -> "api"
        pluginSet.intersect(applicationSet).isNotEmpty() -> "implementation"
        pluginSet.intersect(librarySet).isNotEmpty() -> "api"
        else -> return@model
      }

      model.dependencies().all()
        .forEach { dependency ->
          val psiElement = dependency.psiElement ?: model.dependencies().psiElement ?: modelPsiElement
          maybeAddUsageForDependency(dependency, compileReplacement, psiElement)
        }
      // Although there might be a buildscript with dependencies, those dependencies cannot be added to a compile/runtime configuration
      // out of the box -- and if somehow a user manages to configure things to have compile/runtime configurations there, there's no
      // guarantee that they mean the same as the deprecated gradle ones.
      model.configurations().all()
        .forEach { configuration ->
          // this PsiElement is used for displaying in the refactoring preview window, rather than for performing the refactoring; it is
          // therefore appropriate and safe to pass a notional parent Psi if the element is not (yet) on file.
          // TODO(b/159597456): here and elsewhere, encode this hierarchical logic more declaratively.
          val psiElement = configuration.psiElement ?: model.configurations().psiElement ?: modelPsiElement
          maybeAddUsageForConfiguration(configuration, compileReplacement, psiElement)
        }
    }

    foundUsages = usages.size > 0
    return usages.toTypedArray()
  }

  override fun createUsageViewDescriptor(usages: Array<out UsageInfo>?): UsageViewDescriptor {
    return object : UsageViewDescriptorAdapter() {
      override fun getElements(): Array<PsiElement> = PsiElement.EMPTY_ARRAY

      override fun getProcessedElementsHeader(): String = "Replace deprecated configurations"
    }
  }

  override fun getRefactoringId(): String = "com.android.tools.agp.upgrade.CompileRuntimeConfiguration"

  override fun getCommandName(): String = "Replace deprecated configurations"

  companion object {
    val IMPLEMENTATION_API_INTRODUCED = GradleVersion(3, 5, 0)
    val COMPILE_REMOVED = GradleVersion(5, 0, 0)
  }
}

class ObsoleteConfigurationDependencyUsageInfo(
  element: PsiElement,
  current: GradleVersion,
  new: GradleVersion,
  private val dependency: DependencyModel,
  private val newConfigurationName: String
) : GradleBuildModelUsageInfo(element, current, new) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    dependency.setConfigurationName(newConfigurationName)
  }

  override fun getTooltipText() = "Update configuration to $newConfigurationName"

  // Don't need hashCode() because this is stricter than the superclass method.
  override fun equals(other: Any?): Boolean {
    return super.equals(other) && other is ObsoleteConfigurationDependencyUsageInfo &&
           dependency == other.dependency && newConfigurationName == other.newConfigurationName
  }
}

class ObsoleteConfigurationConfigurationUsageInfo(
  element: PsiElement,
  current: GradleVersion,
  new: GradleVersion,
  private val configuration: ConfigurationModel,
  private val newConfigurationName: String
) : GradleBuildModelUsageInfo(element, current, new) {
  override fun performBuildModelRefactoring(processor: GradleBuildModelRefactoringProcessor) {
    configuration.rename(newConfigurationName)
  }

  override fun getTooltipText() = "Rename configuration to $newConfigurationName"

  // Don't need hashCode() because this is stricter than the superclass method.
  override fun equals(other: Any?): Boolean {
    return super.equals(other) && other is ObsoleteConfigurationConfigurationUsageInfo &&
           configuration == other.configuration && newConfigurationName == other.newConfigurationName
  }
}