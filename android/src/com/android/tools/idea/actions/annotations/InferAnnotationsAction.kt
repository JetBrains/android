/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.actions.annotations.InferAnnotations.Companion.apply
import com.android.tools.idea.actions.annotations.InferAnnotations.Companion.generateReport
import com.android.tools.idea.actions.annotations.InferAnnotations.Companion.nothingFoundMessage
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager.Companion.get
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.util.addDependenciesWithUiConfirmation
import com.android.tools.idea.util.dependsOn
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.analysis.AnalysisScope
import com.intellij.analysis.AnalysisUIOptions
import com.intellij.analysis.BaseAnalysisAction
import com.intellij.analysis.BaseAnalysisActionDialog
import com.intellij.codeInsight.FileModificationService
import com.intellij.facet.ProjectFacetManager
import com.intellij.history.LocalHistory
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.calcRelativeToProjectPath
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Factory
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiCompiledElement
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
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.FileSystems
import javax.swing.JComponent

/** Analyze support annotations */
class InferAnnotationsAction : BaseAnalysisAction("Infer Support Annotations", INFER_SUPPORT_ANNOTATIONS) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    // Don't include test options by default. Unfortunately, there isn't a way to configure this
    // in super.actionPerformed, so we work around it here.
    val options = AnalysisUIOptions.getInstance(project).also { this.options = it }
    includeTestsByDefault = options.ANALYZE_TEST_SOURCES
    options.ANALYZE_TEST_SOURCES = false

    super.actionPerformed(e)
  }

  override fun update(event: AnActionEvent) {
    val project = event.project
    if (project == null || StudioFlags.INFER_ANNOTATIONS_REFACTORING_ENABLED.get().not() ||
      // don't show this action in IDEA in non-android projects
      !ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)
    ) {
      event.presentation.isEnabledAndVisible = false
      return
    }

    super.update(event)
  }

  override fun analyze(project: Project, scope: AnalysisScope) {
    // User hit OK; apply and save settings
    settingsDialog?.apply().also { settingsDialog = null }
    PropertiesComponent.getInstance().setValue(INFER_ANNOTATION_SETTINGS, settings.toString(), "")

    val fileCount = intArrayOf(0)
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    val inferrer = InferAnnotations(settings, project)
    val usageInfos = findUsages(project, scope, fileCount[0], inferrer) ?: return
    if (settings.checkDependencies) {
      val modules = findModulesFromUsage(usageInfos)
      if (!checkModules(project, scope, modules)) {
        return
      }
    }
    // We show the report when presenting the usages, not after the user hits Apply, since
    // they may want to inspect the report when deciding whether to apply the suggestions
    if (settings.generateReport) {
      showReport(inferrer, scope, project)
    }
    if (usageInfos.size < MAX_ANNOTATIONS_WITHOUT_PREVIEW) {
      ApplicationManager.getApplication().invokeLater(applyRunnable(project) { usageInfos })
    } else {
      showUsageView(project, usageInfos, scope)
    }
    restoreTestSetting()
  }

  // See whether we need to add the androidx annotations library to the dependency graph
  private fun checkModules(
    project: Project,
    scope: AnalysisScope,
    modules: Map<Module, PsiFile>
  ): Boolean {
    val artifact = getAnnotationsMavenArtifact(project)
    val modulesWithoutAnnotations = modules.keys.filter { module -> !module.dependsOn(artifact) }.toSet()
    if (modulesWithoutAnnotations.isEmpty()) {
      return true
    }
    val moduleNames = StringUtil.join(modulesWithoutAnnotations, { obj: Module -> obj.name }, ", ")
    val count = modulesWithoutAnnotations.size
    val message = String.format(
      """
      The %1${"$"}s %2${"$"}s %3${"$"}sn't refer to the existing '%4${"$"}s' library with Android annotations.

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
        "Infer Annotations",
        "OK",
        "Cancel",
        Messages.getErrorIcon()
      ) == Messages.OK
    ) {
      val manager = get()
      val revision = manager.getLibraryRevision(artifact.mavenGroupId, artifact.mavenArtifactId, null, false, FileSystems.getDefault())
      if (revision != null) {
        val coordinates = listOf(artifact.getCoordinate(revision))
        for (module in modulesWithoutAnnotations) {
          val added = module.addDependenciesWithUiConfirmation(coordinates, false, requestSync = false)
          if (added.isEmpty()) {
            break // user canceled or some other problem; don't resume with other modules
          }
        }
        syncAndRestartAnalysis(project, scope)
      }
    }
    return false
  }

  // In theory, we should look up project.isAndroidX() here and pick either SUPPORT_ANNOTATIONS or ANDROIDX_SUPPORT_ANNOTATIONS
  // but calling project.isAndroidX is getting caught in the SlowOperations check in recent IntelliJs. And androidx is a reasonable
  // requirement now; support annotations are dying out.
  private fun getAnnotationsMavenArtifact(project: Project) = GoogleMavenArtifactId.ANDROIDX_SUPPORT_ANNOTATIONS

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
    AppUIExecutor.onUiThread().inSmartMode(project).execute { analyze(project, scope) }
  }

  private var settingsDialog: InferAnnotationsSettings.SettingsPanel? = null
  private var includeTestsByDefault: Boolean = false
  private var options: AnalysisUIOptions? = null

  override fun getAdditionalActionSettings(project: Project, dialog: BaseAnalysisActionDialog?): JComponent {
    settings.apply(PropertiesComponent.getInstance().getValue(INFER_ANNOTATION_SETTINGS, ""))
    return settings.SettingsPanel().also { settingsDialog = it }
  }

  override fun canceled() {
    super.canceled()
    restoreTestSetting()
    settingsDialog = null
  }

  private fun restoreTestSetting() {
    options?.ANALYZE_TEST_SOURCES = includeTestsByDefault
    options = null
  }

  private class AnnotateTask constructor(
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
      apply(settings, myProject, myInfos[myCount++])
      return isDone
    }
  }

  companion object {
    val settings = InferAnnotationsSettings()

    @NonNls private val INFER_ANNOTATION_SETTINGS = "infer.annotations.settings"

    /** Number of times we pass through the project files */
    const val MAX_PASSES = 3
    private const val INFER_SUPPORT_ANNOTATIONS: @NonNls String = "Infer Support Annotations"
    private const val MAX_ANNOTATIONS_WITHOUT_PREVIEW = 0

    private fun showReport(
      inferrer: InferAnnotations,
      scope: AnalysisScope,
      project: Project
    ) {

      val usages = ArrayList<UsageInfo>()
      inferrer.collect(usages, scope, settings.includeBinaries)

      var report: String? = null
      try {
        report = generateReport(usages.toTypedArray())
        val fileName = "InferenceReport.txt"
        val option = ScratchFileService.Option.create_new_always
        val f = ScratchRootType.getInstance().createScratchFile(project, fileName, PlainTextLanguage.INSTANCE, report, option)
        if (f != null) {
          FileEditorManager.getInstance(project).openFile(f, true)
        }
      } catch (t: Throwable) {
        report?.let(::println)
        Logger.getInstance(InferAnnotationsAction::class.java).warn(t)
      }
    }

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
      fileCount: Int,
      inferrer: InferAnnotations = InferAnnotations(settings, project)
    ): Array<UsageInfo>? {
      val psiManager = PsiManager.getInstance(project)
      val searchForUsages = Runnable {
        scope.accept(object : PsiElementVisitor() {
          var myFileCount = 0
          override fun visitFile(file: PsiFile) {
            myFileCount++
            if (file is PsiCompiledElement) {
              // Skip binaries (calling viewProvider?.document below would invoke the decompiler etc).
              // This is necessary since our scope seems to include libraries. There could be some
              // value in inferring things about the implementations of libraries, but for now
              // we'll leave this out.
              return
            }
            val virtualFile = file.virtualFile
            val viewProvider = psiManager.findViewProvider(virtualFile)
            val document = viewProvider?.document
            if (document == null || virtualFile.fileType.isBinary) return // do not inspect binary files
            val progressIndicator = ProgressManager.getInstance().progressIndicator
            if (progressIndicator != null && !progressIndicator.isIndeterminate) {
              progressIndicator.text2 = calcRelativeToProjectPath(virtualFile, project)
              progressIndicator.fraction = myFileCount.toDouble() / (MAX_PASSES * fileCount)
            }
            if (file is PsiJavaFile || file is KtFile) {
              try {
                inferrer.collect(file)
              } catch (t: Throwable) {
                Logger.getInstance(InferAnnotationsAction::class.java).warn(t)
              }
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

      val indicator = ProgressIndicatorProvider.getGlobalProgressIndicator()
      if (indicator != null) {
        indicator.isIndeterminate = true
        indicator.text = "Post-processing results..."
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
      presentation.tabText = "Infer Annotations Preview"
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
        "Apply Suggestions",
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
  }
}