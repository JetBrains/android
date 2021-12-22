/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.actions.annotations

import com.android.SdkConstants
import com.android.tools.idea.actions.annotations.InferSupportAnnotations.Companion.apply
import com.android.tools.idea.actions.annotations.InferSupportAnnotations.Companion.generateReport
import com.android.tools.idea.actions.annotations.InferSupportAnnotations.Companion.nothingFoundMessage
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager.Companion.get
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.model.AndroidModuleInfo
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.analysis.BaseAnalysisActionDialog
import com.intellij.codeInsight.FileModificationService
import com.intellij.facet.ProjectFacetManager
import com.intellij.history.LocalHistory
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.calcRelativeToProjectPath
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Factory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.refactoring.RefactoringBundle
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoSearcherAdapter
import com.intellij.usages.UsageSearcher
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewPresentation
import com.intellij.util.Processor
import com.intellij.util.SequentialModalProgressTask
import com.intellij.util.SequentialTask
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.annotations.NonNls
import java.util.Locale
import javax.swing.JComponent

/** Analyze support annotations */
class InferSupportAnnotationsAction : BaseAnalysisAction("Infer Support Annotations", INFER_SUPPORT_ANNOTATIONS) {
  override fun update(event: AnActionEvent) {
    if (!ENABLED) {
      event.presentation.isEnabledAndVisible = false
      return
    }
    val project = event.project
    if (project == null || !ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      // don't show this action in IDEA in non-android projects
      event.presentation.isEnabledAndVisible = false
      return
    }
    super.update(event)
    if (!GradleProjectInfo.getInstance(project).isBuildWithGradle) {
      val presentation = event.presentation
      presentation.isEnabled = false
    }
  }

  override fun analyze(project: Project, scope: AnalysisScope) {
    if (!GradleProjectInfo.getInstance(project).isBuildWithGradle) {
      return
    }
    val fileCount = intArrayOf(0)
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    val usageInfos = findUsages(project, scope, fileCount[0]) ?: return
    val modules = findModulesFromUsage(usageInfos)
    if (!checkModules(project, scope, modules)) {
      return
    }
    if (usageInfos.size < MAX_ANNOTATIONS_WITHOUT_PREVIEW) {
      ApplicationManager.getApplication().invokeLater(applyRunnable(project) { usageInfos })
    } else {
      showUsageView(project, usageInfos, scope)
    }
  }

  // For Android we need to check SDK version and possibly update the gradle project file
  private fun checkModules(
    project: Project,
    scope: AnalysisScope,
    modules: Map<Module, PsiFile>
  ): Boolean {
    val modulesWithoutAnnotations: MutableSet<Module> = HashSet()
    val modulesWithLowVersion: MutableSet<Module> = HashSet()
    for (module in modules.keys) {
      val info = AndroidModuleInfo.getInstance(module)
      if (info != null && info.buildSdkVersion != null && info.buildSdkVersion!!.featureLevel < MIN_SDK_WITH_NULLABLE) {
        modulesWithLowVersion.add(module)
      }
      val buildModel = GradleBuildModel.get(module)
      if (buildModel == null) {
        Logger.getInstance(InferSupportAnnotationsAction::class.java)
          .warn("Unable to find Gradle build model for module " + AndroidRootUtil.getModuleDirPath(module))
        continue
      }
      var dependencyFound = false
      val dependenciesModel = buildModel.dependencies()
      val configurationName =
        GradleUtil.mapConfigurationName(CommonConfigurationNames.COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false)
      for (dependency in dependenciesModel.artifacts(configurationName)) {
        val notation = dependency.compactNotation()
        if (notation.startsWith(SdkConstants.APPCOMPAT_LIB_ARTIFACT) ||
          notation.startsWith(SdkConstants.ANDROIDX_APPCOMPAT_LIB_ARTIFACT) ||
          notation.startsWith(SdkConstants.SUPPORT_LIB_ARTIFACT) ||
          notation.startsWith(SdkConstants.ANDROIDX_SUPPORT_LIB_ARTIFACT) ||
          notation.startsWith(SdkConstants.ANDROIDX_ANNOTATIONS_ARTIFACT) ||
          notation.startsWith(SdkConstants.ANNOTATIONS_LIB_ARTIFACT)
        ) {
          dependencyFound = true
          break
        }
      }
      if (!dependencyFound) {
        modulesWithoutAnnotations.add(module)
      }
    }
    if (modulesWithLowVersion.isNotEmpty()) {
      Messages.showErrorDialog(
        project,
        String.format(
          Locale.US,
          "Infer Support Annotations requires the project sdk level be set to %1\$d or greater.",
          MIN_SDK_WITH_NULLABLE
        ),
        "Infer Support Annotations"
      )
      return false
    }
    if (modulesWithoutAnnotations.isEmpty()) {
      return true
    }
    val moduleNames = StringUtil.join(modulesWithoutAnnotations, { obj: Module -> obj.name }, ", ")
    val count = modulesWithoutAnnotations.size
    val message = String.format(
      """
      The %1${"$"}s %2${"$"}s %3${"$"}sn't refer to the existing '%4${"$"}s' library with Android nullity annotations.

      Would you like to add the %5${"$"}s now?
      """.trimIndent(),
      StringUtil.pluralize("module", count),
      moduleNames,
      if (count > 1) "do" else "does",
      GoogleMavenArtifactId.SUPPORT_ANNOTATIONS.mavenArtifactId,
      StringUtil.pluralize("dependency", count)
    )
    if (Messages.showOkCancelDialog(
        project,
        message,
        "Infer Nullity Annotations",
        "OK",
        "Cancel",
        Messages.getErrorIcon()
      ) == Messages.OK
    ) {
      val action = LocalHistory.getInstance().startAction(ADD_DEPENDENCY)
      try {
        WriteCommandAction.writeCommandAction(project).withName(ADD_DEPENDENCY).run<RuntimeException> {
          val manager = get()
          val annotation =
            if (project.isAndroidx()) GoogleMavenArtifactId.ANDROIDX_SUPPORT_ANNOTATIONS else GoogleMavenArtifactId.SUPPORT_ANNOTATIONS
          val annotationsLibraryCoordinate = manager.getArtifactStringCoordinate(annotation, true)
          if (annotationsLibraryCoordinate != null) {
            for (module in modulesWithoutAnnotations) {
              addDependency(module, annotationsLibraryCoordinate)
            }
          }
          syncAndRestartAnalysis(project, scope)
        }
      } finally {
        action.finish()
      }
    }
    return false
  }

  private fun syncAndRestartAnalysis(project: Project, scope: AnalysisScope) {
    assert(ApplicationManager.getApplication().isDispatchThread)
    val syncResult = project.getProjectSystem()
      .getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED)
    Futures.addCallback(
      syncResult,
      object : FutureCallback<ProjectSystemSyncManager.SyncResult> {
        override fun onSuccess(syncResult: ProjectSystemSyncManager.SyncResult?) {
          if (syncResult != null && syncResult.isSuccessful) {
            restartAnalysis(project, scope)
          }
        }

        override fun onFailure(t: Throwable?) {
          throw RuntimeException(t)
        }
      },
      MoreExecutors.directExecutor()
    )
  }

  private fun restartAnalysis(project: Project, scope: AnalysisScope) {
    ApplicationManager.getApplication().invokeLater { analyze(project, scope) }
  }

  /* Android nullable annotations do not support annotations on local variables. */
  override fun getAdditionalActionSettings(project: Project, dialog: BaseAnalysisActionDialog): JComponent? {
    return if (!GradleProjectInfo.getInstance(project).isBuildWithGradle) {
      super.getAdditionalActionSettings(project, dialog)
    } else null
  }

  private class AnnotateTask(
    private val myProject: Project,
    private val myTask: SequentialModalProgressTask,
    private val myInfos: Array<UsageInfo>
  ) : SequentialTask {
    private var myCount = 0
    private val myTotal: Int = myInfos.size

    override fun isDone(): Boolean {
      return myCount > myTotal - 1
    }

    override fun iteration(): Boolean {
      val indicator = myTask.indicator
      if (indicator != null) {
        indicator.fraction = myCount.toDouble() / myTotal
      }
      apply(myProject, myInfos[myCount++])
      val done = isDone
      if (isDone) {
        try {
          showReport()
        } catch (ignore: Throwable) {
        }
      }
      return done
    }

    fun showReport() {
      if (InferSupportAnnotations.CREATE_INFERENCE_REPORT) {
        val report = generateReport(myInfos)
        val fileName = "Annotation Inference Report"
        val option = ScratchFileService.Option.create_new_always
        val f = ScratchRootType.getInstance().createScratchFile(myProject, fileName, PlainTextLanguage.INSTANCE, report, option)
        if (f != null) {
          FileEditorManager.getInstance(myProject).openFile(f, true)
        }
      }
    }
  }

  companion object {
    /** Whether this feature is enabled or not during development */
    val ENABLED = java.lang.Boolean.parseBoolean("studio.infer.annotations")

    /** Number of times we pass through the project files */
    const val MAX_PASSES = 3
    private const val INFER_SUPPORT_ANNOTATIONS: @NonNls String = "Infer Support Annotations"
    private const val MAX_ANNOTATIONS_WITHOUT_PREVIEW = 5
    private const val ADD_DEPENDENCY = "Add Support Dependency"
    private const val MIN_SDK_WITH_NULLABLE = 19
    private fun findModulesFromUsage(infos: Array<UsageInfo>): Map<Module, PsiFile> {
      // We need 1 file from each module that requires changes (the file may be overwritten below):
      val modules: MutableMap<Module, PsiFile> = HashMap()
      for (info in infos) {
        val element = info.element ?: continue
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: continue
        val file = element.containingFile
        modules[module] = file
      }
      return modules
    }

    private fun findUsages(
      project: Project,
      scope: AnalysisScope,
      fileCount: Int
    ): Array<UsageInfo>? {
      val inferrer = InferSupportAnnotations(false, project)
      val psiManager = PsiManager.getInstance(project)
      val searchForUsages = Runnable {
        scope.accept(object : PsiElementVisitor() {
          var myFileCount = 0
          override fun visitFile(file: PsiFile) {
            myFileCount++
            val virtualFile = file.virtualFile
            val viewProvider = psiManager.findViewProvider(virtualFile)
            val document = viewProvider?.document
            if (document == null || virtualFile.fileType.isBinary) return // do not inspect binary files
            val progressIndicator = ProgressManager.getInstance().progressIndicator
            if (progressIndicator != null) {
              progressIndicator.text2 = calcRelativeToProjectPath(virtualFile, project)
              progressIndicator.fraction = myFileCount.toDouble() / (MAX_PASSES * fileCount)
            }
            if (file is PsiJavaFile) {
              inferrer.collect(file)
            }
          }
        })
      }

      /*
      Collect these files and visit repeatedly. Consider this
      scenario, where I visit files A, B, C in alphabetical order.
      Let's say a method in A unconditionally calls a method in B
      calls a method in C. In file C I discover that the method
      requires permission P. At this point it's too late for me to
      therefore conclude that the method in B also requires it. If I
      make a whole separate pass again, I could now add that
      constraint. But only after that second pass can I infer that
      the method in A also requires it. In short, I need to keep
      passing through all files until I make no more progress. It
      would be much more efficient to handle this with a global call
      graph such that as soon as I make an inference I can flow it
      backwards.
     */
      val multipass = Runnable {
        for (i in 0 until MAX_PASSES) {
          searchForUsages.run()
        }
      }
      if (ApplicationManager.getApplication().isDispatchThread) {
        if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(multipass, INFER_SUPPORT_ANNOTATIONS, true, project)) {
          return null
        }
      } else {
        multipass.run()
      }
      val usages = ArrayList<UsageInfo>()
      inferrer.collect(usages, scope)
      return usages.toTypedArray()
    }

    private fun applyRunnable(project: Project, computable: Computable<Array<UsageInfo>>): Runnable {
      return Runnable {
        val action = LocalHistory.getInstance().startAction(INFER_SUPPORT_ANNOTATIONS)
        try {
          WriteCommandAction.writeCommandAction(project).withName(INFER_SUPPORT_ANNOTATIONS).run<RuntimeException> {
            val infos = computable.compute()
            if (infos.isNotEmpty()) {
              val elements: MutableSet<PsiElement> = LinkedHashSet()
              for (info in infos) {
                val element = info.element
                if (element != null) {
                  val containingFile = element.containingFile ?: continue
                  // Skip results in .class files; these are typically from extracted AAR files
                  val virtualFile = containingFile.virtualFile
                  if (virtualFile.fileType.isBinary) {
                    continue
                  }
                  elements.add(containingFile)
                }
              }
              if (!FileModificationService.getInstance().preparePsiElementsForWrite(elements)) return@run
              val progressTask = SequentialModalProgressTask(project, INFER_SUPPORT_ANNOTATIONS, false)
              progressTask.setMinIterationTime(200)
              progressTask.setTask(AnnotateTask(project, progressTask, infos))
              ProgressManager.getInstance().run(progressTask)
            } else {
              nothingFoundMessage(project)
            }
          }
        } finally {
          action.finish()
        }
      }
    }

    private fun showUsageView(project: Project, usageInfos: Array<UsageInfo>, scope: AnalysisScope) {
      val targets = UsageTarget.EMPTY_ARRAY
      val convertUsagesRef = Ref<Array<UsageInfo2UsageAdapter>>()
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(
          { ApplicationManager.getApplication().runReadAction { convertUsagesRef.set(UsageInfo2UsageAdapter.convert(usageInfos)) } },
          "Preprocess usages", true, project
        )
      ) {
        return
      }
      if (convertUsagesRef.isNull) return
      val usages = convertUsagesRef.get()
      val presentation = UsageViewPresentation()
      presentation.tabText = "Infer Nullity Preview"
      presentation.isShowReadOnlyStatusAsRed = true
      presentation.isShowCancelButton = true
      presentation.searchString = RefactoringBundle.message("usageView.usagesText")
      val usageView = UsageViewManager.getInstance(project).showUsages(targets, usages, presentation, rerunFactory(project, scope))
      val refactoringRunnable = applyRunnable(project) {
        val infos = UsageViewUtil.getNotExcludedUsageInfos(usageView)
        infos.toTypedArray()
      }
      val canNotMakeString =
        "Cannot perform operation.\nThere were changes in code after usages have been found.\nPlease perform operation search again."
      usageView.addPerformOperationAction(
        refactoringRunnable,
        INFER_SUPPORT_ANNOTATIONS,
        canNotMakeString,
        INFER_SUPPORT_ANNOTATIONS,
        false
      )
    }

    private fun rerunFactory(project: Project, scope: AnalysisScope): Factory<UsageSearcher> {
      return Factory {
        object : UsageInfoSearcherAdapter() {
          override fun findUsages(): Array<UsageInfo> {
            return findUsages(project, scope, scope.fileCount) ?: UsageInfo.EMPTY_ARRAY
          }

          override fun generate(processor: Processor<in Usage>) {
            processUsages(processor, project)
          }
        }
      }
    }

    private fun addDependency(module: Module, libraryCoordinate: String) {
      if (StringUtil.isNotEmpty(libraryCoordinate)) {
        ModuleRootModificationUtil.updateModel(module) {
          val buildModel = GradleBuildModel.get(module)
          if (buildModel != null) {
            val name =
              GradleUtil.mapConfigurationName(CommonConfigurationNames.COMPILE, GradleUtil.getAndroidGradleModelVersionInUse(module), false)
            buildModel.dependencies().addArtifact(name, libraryCoordinate)
            buildModel.applyChanges()
          }
        }
      }
    }
  }
}