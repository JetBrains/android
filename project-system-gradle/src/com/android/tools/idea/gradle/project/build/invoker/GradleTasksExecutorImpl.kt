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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.builder.model.PROPERTY_ATTRIBUTION_FILE_LOCATION
import com.android.builder.model.PROPERTY_INVOKED_FROM_IDE
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.attribution.BasicBuildAttributionInfo
import com.android.tools.idea.gradle.project.build.attribution.BuildAttributionManager
import com.android.tools.idea.gradle.project.build.attribution.getAgpAttributionFileDir
import com.android.tools.idea.gradle.project.build.attribution.isBuildAttributionEnabledForProject
import com.android.tools.idea.gradle.project.build.compiler.AndroidGradleBuildConfiguration
import com.android.tools.idea.gradle.project.common.GradleInitScripts
import com.android.tools.idea.gradle.project.common.addAndroidSupportVersionArg
import com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink
import com.android.tools.idea.gradle.util.AndroidGradleSettings
import com.android.tools.idea.gradle.util.GradleBuilds
import com.android.tools.idea.gradle.util.GradleUtil
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.SelectSdkDialog
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.tracer.Trace
import com.google.common.base.Stopwatch
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerManagerImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.VetoableProjectManagerListener
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.AppIcon
import com.intellij.ui.AppUIUtil
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ArrayUtil
import com.intellij.util.ExceptionUtil
import com.intellij.util.Function
import com.intellij.util.ui.UIUtil
import org.gradle.tooling.BuildAction
import org.gradle.tooling.BuildActionExecuter
import org.gradle.tooling.BuildCancelledException
import org.gradle.tooling.BuildException
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.LongRunningOperation
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionHelper
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.event.HyperlinkEvent

internal class GradleTasksExecutorImpl : GradleTasksExecutor {
  override fun execute(
    request: GradleBuildInvoker.Request,
    buildAction: BuildAction<*>?,
    buildStopper: BuildStopper,
    listener: ExternalSystemTaskNotificationListener
  ): ListenableFuture<GradleInvocationResult> {
    val resultFuture = SettableFuture.create<GradleInvocationResult>()
    TaskImpl(request, buildAction, buildStopper, listener, resultFuture).queue()
    return resultFuture
  }

  override fun internalIsBuildRunning(project: Project): Boolean {
    val frame = (WindowManager.getInstance() as WindowManagerEx).findFrameFor(project)
    val statusBar = (if (frame == null) null else frame.statusBar as StatusBarEx?) ?: return false
    for (backgroundProcess in statusBar.backgroundProcesses) {
      val task = backgroundProcess.getFirst()
      if (task is TaskImpl) {
        val second = backgroundProcess.getSecond()
        if (second.isRunning) {
          return true
        }
      }
    }
    return false
  }

  private class TaskImpl constructor(
    private val myRequest: GradleBuildInvoker.Request,
    private val myBuildAction: BuildAction<*>?,
    private val myBuildStopper: BuildStopper,
    private val myListener: ExternalSystemTaskNotificationListener,
    private val myResultFuture: SettableFuture<GradleInvocationResult>
  ) : Task.Backgroundable(myRequest.project, "Gradle Build Running", true) {
    private val myHelper = GradleExecutionHelper()

    @Volatile
    private var myErrorCount = 0

    @Volatile
    private var myProgressIndicator: ProgressIndicator = EmptyProgressIndicator()
    override fun getNotificationInfo(): NotificationInfo? {
      return NotificationInfo(
        if (myErrorCount > 0) "Gradle Invocation (errors)" else "Gradle Invocation (success)",
        "Gradle Invocation Finished", "$myErrorCount Errors", true
      )
    }

    override fun run(indicator: ProgressIndicator) {
      try {
        myProgressIndicator = indicator
        indicator.text = this.title
        val projectManager = ProjectManager.getInstance()
        val project = myRequest.project
        val closeListener: CloseListener = CloseListener()
        projectManager.addProjectManagerListener(project, closeListener)
        val semaphore = (CompilerManager.getInstance(project) as CompilerManagerImpl).compilationSemaphore
        var acquired = false
        try {
          try {
            while (!acquired) {
              acquired = semaphore.tryAcquire(300, TimeUnit.MILLISECONDS)
              if (myProgressIndicator.isCanceled) {
                // Give up obtaining the semaphore, let compile work begin in order to stop gracefully on cancel event.
                break
              }
            }
          } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
          }
          addIndicatorDelegate()
          myResultFuture.set(invokeGradleTasks(myBuildAction))
        } finally {
          try {
            myProgressIndicator.stop()
            projectManager.removeProjectManagerListener(project, closeListener)
          } finally {
            if (acquired) {
              semaphore.release()
            }
          }
        }
      } catch (t: Throwable) {
        myResultFuture.setException(t)
        throw t
      }
    }

    private fun addIndicatorDelegate() {
      if (myProgressIndicator is ProgressIndicatorEx) {
        val indicator = myProgressIndicator as ProgressIndicatorEx
        indicator.addStateDelegate(ProgressIndicatorStateDelegate(myRequest.taskId, myBuildStopper))
      }
    }

    private fun setUpBuildAttributionManager(
      operation: LongRunningOperation,
      buildAttributionManager: BuildAttributionManager?,
      skipIfNull: Boolean
    ) {
      if (skipIfNull && buildAttributionManager == null) {
        return
      }
      operation.addProgressListener(
        buildAttributionManager,
        OperationType.PROJECT_CONFIGURATION,
        OperationType.TASK,
        OperationType.TEST,
        OperationType.FILE_DOWNLOAD
      )
      buildAttributionManager!!.onBuildStart(myRequest)
    }

    private fun invokeGradleTasks(buildAction: BuildAction<*>?): GradleInvocationResult {
      val project = myRequest.project
      val executionSettings = GradleUtil.getOrCreateGradleExecutionSettings(project)
      val model = AtomicReference<Any?>(null)
      val gradleRootProjectPath = myRequest.rootProjectPath.path
      val executeTasksFunction = Function { connection: ProjectConnection ->
        val stopwatch = Stopwatch.createStarted()
        val isRunBuildAction = buildAction != null
        val gradleTasks = myRequest.gradleTasks
        val executingTasksText = "Executing tasks: $gradleTasks in project $gradleRootProjectPath"
        addToEventLog(executingTasksText, MessageType.INFO)
        var buildError: Throwable? = null
        val id = myRequest.taskId
        val taskListener = myListener
        val cancellationTokenSource = GradleConnector.newCancellationTokenSource()
        myBuildStopper.register(id, cancellationTokenSource)
        taskListener.onStart(id, gradleRootProjectPath)
        taskListener.onTaskOutput(id, executingTasksText + System.lineSeparator() + System.lineSeparator(), true)
        val buildState = GradleBuildState.getInstance(myProject!!)
        buildState.buildStarted(BuildContext(myRequest))
        var buildAttributionManager: BuildAttributionManager? = null
        val enableBuildAttribution = isBuildAttributionEnabledForProject(myProject)
        try {
          val buildConfiguration = AndroidGradleBuildConfiguration.getInstance(project)
          val commandLineArguments: MutableList<String?> = Lists.newArrayList(*buildConfiguration.commandLineOptions)
          if (!commandLineArguments.contains(GradleBuilds.PARALLEL_BUILD_OPTION) &&
            CompilerConfiguration.getInstance(project).isParallelCompilationEnabled
          ) {
            commandLineArguments.add(GradleBuilds.PARALLEL_BUILD_OPTION)
          }
          commandLineArguments.add(AndroidGradleSettings.createProjectProperty(PROPERTY_INVOKED_FROM_IDE, true))
          addAndroidSupportVersionArg(commandLineArguments)
          if (enableBuildAttribution) {
            val attributionFileDir = getAgpAttributionFileDir(myRequest.data)
            commandLineArguments.add(
              AndroidGradleSettings.createProjectProperty(
                PROPERTY_ATTRIBUTION_FILE_LOCATION,
                attributionFileDir.absolutePath
              )
            )
          }
          commandLineArguments.addAll(myRequest.commandLineArguments)

          // Inject embedded repository if it's enabled by user.
          if (StudioFlags.USE_DEVELOPMENT_OFFLINE_REPOS.get() && !GuiTestingService.isInTestingMode()) {
            GradleInitScripts.getInstance().addLocalMavenRepoInitScriptCommandLineArg(commandLineArguments)
            GradleUtil.attemptToUseEmbeddedGradle(project)
          }

          // Don't include passwords in the log
          var logMessage = "Build command line options: $commandLineArguments"
          if (logMessage.contains(PASSWORD_KEY_SUFFIX)) {
            val replaced: MutableList<String?> = ArrayList(commandLineArguments.size)
            for (option in commandLineArguments) {
              // -Pandroid.injected.signing.store.password=, -Pandroid.injected.signing.key.password=
              val index = option!!.indexOf(".password=")
              if (index == -1) {
                replaced.add(option)
              } else {
                replaced.add(option.substring(0, index + PASSWORD_KEY_SUFFIX.length) + "*********")
              }
            }
            logMessage = replaced.toString()
          }
          logger.info(logMessage)
          val jvmArguments: List<String> = ArrayList(myRequest.jvmArguments)
          // Add trace arguments to jvmArguments.
          Trace.addVmArgs(jvmArguments)
          executionSettings
            .withVmOptions(jvmArguments)
            .withArguments(commandLineArguments)
            .withEnvironmentVariables(myRequest.env)
            .passParentEnvs(myRequest.isPassParentEnvs)
          val operation: LongRunningOperation = if (isRunBuildAction) connection.action(buildAction) else connection.newBuild()
          GradleExecutionHelper.prepare(operation, id, executionSettings, object : ExternalSystemTaskNotificationListenerAdapter() {
            override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
              if (myBuildStopper.contains(id)) {
                taskListener.onStatusChange(event)
              }
            }

            override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
              // For test use only: save the logs to a file. Note that if there are multiple tasks at once
              // the output will be interleaved.
              if (StudioFlags.GRADLE_SAVE_LOG_TO_FILE.get()) {
                try {
                  val path = Paths.get(PathManager.getLogPath(), "gradle.log")
                  Files.writeString(path, text, StandardOpenOption.APPEND, StandardOpenOption.CREATE)
                } catch (e: IOException) {
                  // Ignore
                }
              }
              if (myBuildStopper.contains(id)) {
                taskListener.onTaskOutput(id, text, stdOut)
              }
            }
          }, connection)
          if (enableBuildAttribution) {
            buildAttributionManager = myProject.getService(BuildAttributionManager::class.java)
            setUpBuildAttributionManager(
              operation, buildAttributionManager,  // In some tests we don't care about build attribution being setup
              ApplicationManager.getApplication().isUnitTestMode
            )
          }
          if (isRunBuildAction) {
            (operation as BuildActionExecuter<*>).forTasks(*ArrayUtil.toStringArray(gradleTasks))
          } else {
            (operation as BuildLauncher).forTasks(*ArrayUtil.toStringArray(gradleTasks))
          }
          operation.withCancellationToken(cancellationTokenSource.token())
          if (isRunBuildAction) {
            model.set((operation as BuildActionExecuter<*>).run())
          } else {
            (operation as BuildLauncher).run()
          }
          buildState.buildFinished(BuildStatus.SUCCESS)
          taskListener.onSuccess(id)
          val buildInfo: BasicBuildAttributionInfo?
          buildInfo = buildAttributionManager?.onBuildSuccess(myRequest)
          if (buildInfo != null && buildInfo.agpVersion != null) {
            reportAgpVersionMismatch(project, buildInfo)
          }
        } catch (e: BuildException) {
          buildError = e
        } catch (e: Throwable) {
          buildError = e
          handleTaskExecutionError(e)
        } finally {
          executeWithoutProcessCanceledException {
            val application = ApplicationManager.getApplication()
            if (buildError != null) {
              buildAttributionManager?.onBuildFailure(myRequest)
              if (wasBuildCanceled(buildError)) {
                buildState.buildFinished(BuildStatus.CANCELED)
                taskListener.onCancel(id)
              } else {
                buildState.buildFinished(BuildStatus.FAILED)
                val projectResolverChain = GradleProjectResolver.createProjectResolverChain()
                val userFriendlyError = projectResolverChain.getUserFriendlyError(null, buildError, gradleRootProjectPath, null)
                taskListener.onFailure(id, userFriendlyError)
              }
            }
            taskListener.onEnd(id)
            myBuildStopper.remove(id)
            if (GuiTestingService.getInstance().isGuiTestingMode) {
              val testOutput = application.getUserData(GuiTestingService.GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY)
              if (StringUtil.isNotEmpty(testOutput)) {
                application.putUserData(GuiTestingService.GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY, null)
              }
            }
            application.invokeLater { notifyGradleInvocationCompleted(buildState, stopwatch.elapsed(TimeUnit.MILLISECONDS)) }
          }
          if (!getProject().isDisposed) {
            return@Function GradleInvocationResult(myRequest.rootProjectPath, myRequest.gradleTasks, buildError, model.get())
          }
        }
        GradleInvocationResult(myRequest.rootProjectPath, myRequest.gradleTasks, null)
      }
      if (GuiTestingService.getInstance().isGuiTestingMode) {
        // We use this task in GUI tests to simulate errors coming from Gradle project sync.
        val application = ApplicationManager.getApplication()
        val task = application.getUserData(GuiTestingService.EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY)
        if (task != null) {
          application.putUserData(GuiTestingService.EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY, null)
          task.run()
        }
      }
      return try {
        myHelper.execute(
          gradleRootProjectPath, executionSettings,
          myRequest.taskId, myListener, null, executeTasksFunction
        )
      } catch (e: ExternalSystemException) {
        if (e.originalReason.startsWith("com.intellij.openapi.progress.ProcessCanceledException")) {
          logger.info("Gradle execution cancelled.", e)
          GradleInvocationResult(myRequest.rootProjectPath, myRequest.gradleTasks, e)
        } else {
          throw e
        }
      }
    }

    private fun reportAgpVersionMismatch(project: Project, buildInfo: BasicBuildAttributionInfo) {
      val syncedAgpVersions = ProjectStructure.getInstance(project).androidPluginVersions.allVersions
      if (!syncedAgpVersions.contains(buildInfo.agpVersion)) {
        val incompatibilityMessage = String.format("Project was built with Android Gradle Plugin (AGP) %s but it is synced with %s.",
                                                   buildInfo.agpVersion,
                                                   syncedAgpVersions.joinToString(", ") { obj: AgpVersion? -> obj.toString() }
        )
        logger.warn(incompatibilityMessage)
        val quickFix = SyncProjectWithExtraCommandLineOptionsHyperlink("Sync project", "")
        NotificationGroupManager.getInstance()
          .getNotificationGroup("Android Gradle Sync Issues")
          .createNotification(
            "Gradle sync needed",
            """
                  $incompatibilityMessage
                  Please sync the project with Gradle Files.

                  ${quickFix.toHtml()}
                  """.trimIndent(),
            NotificationType.ERROR
          )
          .setImportant(true)
          .setListener { notification: Notification, event: HyperlinkEvent? ->
            quickFix.executeIfClicked(project, event!!)
            notification.hideBalloon()
          }
          .notify(project)
        throw ProcessCanceledException()
      }
    }

    private fun handleTaskExecutionError(e: Throwable) {
      if (myProgressIndicator.isCanceled) {
        logger.info("Failed to complete Gradle execution. Project may be closing or already closed.", e)
        return
      }
      val rootCause = ExceptionUtil.getRootCause(e)
      val error = Strings.nullToEmpty(rootCause.message)
      if (error.contains("Build cancelled")) {
        return
      }
      val showErrorTask = Runnable {
        myErrorCount++

        // This is temporary. Once we have support for hyperlinks in "Messages" window, we'll show the error message the with a
        // hyperlink to set the JDK home.
        // For now we show the "Select SDK" dialog, but only giving the option to set the JDK path.
        if (IdeInfo.getInstance().isAndroidStudio && error.startsWith("Supplied javaHome is not a valid folder")) {
          val ideSdks = IdeSdks.getInstance()
          val androidHome = ideSdks.androidSdkPath
          val androidSdkPath = androidHome?.path
          val selectSdkDialog = SelectSdkDialog(null, androidSdkPath)
          selectSdkDialog.isModal = true
          if (selectSdkDialog.showAndGet()) {
            val jdkHome = selectSdkDialog.jdkHome
            UIUtil.invokeLaterIfNeeded {
              ApplicationManager.getApplication()
                .runWriteAction { AndroidStudioGradleInstallationManager.setJdkAsProjectJdk(myRequest.project, jdkHome) }
            }
          }
        }
      }
      AppUIUtil.invokeLaterIfProjectAlive(myRequest.project, showErrorTask)
    }

    private fun notifyGradleInvocationCompleted(buildState: GradleBuildState, durationMillis: Long) {
      val project = myRequest.project
      if (!project.isDisposed) {
        val statusMsg = createStatusMessage(buildState, durationMillis)
        val messageType = if (myErrorCount > 0) MessageType.ERROR else MessageType.INFO
        if (durationMillis > ONE_MINUTE_MS) {
          BALLOON_NOTIFICATION.createNotification(statusMsg, messageType).notify(project)
        } else {
          addToEventLog(statusMsg, messageType)
        }
        logger.info(statusMsg)
      }
    }

    private fun createStatusMessage(buildState: GradleBuildState, durationMillis: Long): String {
      var message = "Gradle build " + formatBuildStatusFromState(buildState)
      if (myErrorCount > 0) {
        message += String.format(Locale.US, " with %d error(s)", myErrorCount)
      }
      message = message + " in " + StringUtil.formatDuration(durationMillis)
      return message
    }

    private fun addToEventLog(message: String, type: MessageType) {
      LOGGING_NOTIFICATION.createNotification(message, type).notify(myProject)
    }

    private fun attemptToStopBuild() {
      myBuildStopper.attemptToStopBuild(myRequest.taskId, myProgressIndicator)
    }

    private inner class CloseListener : ContentManagerListener, VetoableProjectManagerListener {
      private var myIsApplicationExitingOrProjectClosing = false
      private var myUserAcceptedCancel = false
      override fun projectOpened(project: Project) {}
      override fun canClose(project: Project): Boolean {
        if (project != myProject) {
          return true
        }
        if (shouldPromptUser()) {
          myUserAcceptedCancel = askUserToCancelGradleExecution()
          if (!myUserAcceptedCancel) {
            return false // veto closing
          }
          attemptToStopBuild()
          return true
        }
        return !myProgressIndicator.isRunning
      }

      override fun projectClosing(project: Project) {
        if (project == myProject) {
          myIsApplicationExitingOrProjectClosing = true
        }
      }

      private fun shouldPromptUser(): Boolean {
        return !myUserAcceptedCancel && !myIsApplicationExitingOrProjectClosing && myProgressIndicator.isRunning
      }

      private fun askUserToCancelGradleExecution(): Boolean {
        val msg = "Gradle is running. Proceed with Project closing?"
        val result = Messages.showYesNoDialog(myProject, msg, GRADLE_RUNNING_MSG_TITLE, Messages.getQuestionIcon())
        return result == Messages.YES
      }
    }

    private inner class ProgressIndicatorStateDelegate internal constructor(
      taskId: ExternalSystemTaskId,
      buildStopper: BuildStopper
    ) : TaskExecutionProgressIndicator(taskId, buildStopper) {
      public override fun onCancel() {
        stopAppIconProgress()
      }

      override fun stop() {
        super.stop()
        stopAppIconProgress()
      }

      private fun stopAppIconProgress() {
        UIUtil.invokeLaterIfNeeded {
          val appIcon = AppIcon.getInstance()
          val project = myRequest.project
          if (appIcon.hideProgress(project, APP_ICON_ID)) {
            if (myErrorCount > 0) {
              appIcon.setErrorBadge(project, myErrorCount.toString())
              appIcon.requestAttention(project, true)
            } else {
              appIcon.setOkBadge(project, true)
              appIcon.requestAttention(project, false)
            }
          }
        }
      }
    }

    companion object {
      private const val ONE_MINUTE_MS = 60L /*sec*/ * 1000L /*millisec*/
      val LOGGING_NOTIFICATION = NotificationGroupManager.getInstance().getNotificationGroup("Gradle Build (Logging)")!!
      val BALLOON_NOTIFICATION = NotificationGroupManager.getInstance().getNotificationGroup("Gradle Build (Balloon)")!!
      private val APP_ICON_ID: @NonNls String? = "compiler"
      private const val GRADLE_RUNNING_MSG_TITLE = "Gradle Running"
      private const val PASSWORD_KEY_SUFFIX = ".password="
      private fun wasBuildCanceled(buildError: Throwable): Boolean {
        return GradleUtil.hasCause(buildError, BuildCancelledException::class.java) || GradleUtil.hasCause(
          buildError,
          ProcessCanceledException::class.java
        )
      }

      private val logger: Logger
        get() = Logger.getInstance(GradleBuildInvoker::class.java)

      private fun formatBuildStatusFromState(state: GradleBuildState): String {
        val summary = state.lastFinishedBuildSummary
        return if (summary != null) {
          when (summary.status) {
            BuildStatus.SUCCESS -> "finished"
            BuildStatus.FAILED -> "failed"
            BuildStatus.CANCELED -> "cancelled"
          }
        } else "finished"
      }
    }
  }
}

private inline fun <T> executeWithoutProcessCanceledException(crossinline action: () -> T): T {
  var result: T? = null
  ProgressManager.getInstance().executeNonCancelableSection { result = action() }
  @Suppress("UNCHECKED_CAST")
  return result as T
}

