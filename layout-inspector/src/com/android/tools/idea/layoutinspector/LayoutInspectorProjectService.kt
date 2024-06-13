/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.ide.AppInspectionDiscoveryService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.metrics.ForegroundProcessDetectionMetrics
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorMetrics
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.TransportErrorListener
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetection
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.ForegroundProcessDetectionInitializer
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.layoutinspector.tree.InspectorTreeSettings
import com.android.tools.idea.transport.manager.TransportStreamManagerService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** Class used to keep track of open projects, for metrics purposes */
object LayoutInspectorOpenProjectsTracker {
  internal var openProjects = 0

  fun areMultipleProjectsOpen(): Boolean = openProjects > 1
}

/**
 * A project service that creates and holds an instance of [LayoutInspector].
 *
 * Methods of this class are meant to be called on the UI thread, so we don't need to worry about
 * concurrency.
 */
@UiThread
class LayoutInspectorProjectService(private val project: Project) : Disposable {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LayoutInspectorProjectService {
      return project.getService(LayoutInspectorProjectService::class.java)
    }
  }

  private var layoutInspector: LayoutInspector? = null

  /**
   * Creates an instance of [LayoutInspector]. [LayoutInspector] will automatically connect to the
   * device, so call this method only when Layout Inspector is meant to be used.
   *
   * TODO(b/289017459): once the standalone Layout Inspector is deprecated, we should refactor LI to
   *   only connect to a device on-demand.
   */
  @UiThread
  fun getLayoutInspector(): LayoutInspector {
    ThreadingAssertions.assertEventDispatchThread()

    if (layoutInspector == null) {
      layoutInspector = createLayoutInspector(project, this)
    }
    return layoutInspector!!
  }

  @UiThread
  private fun createLayoutInspector(project: Project, disposable: Disposable): LayoutInspector {
    ThreadingAssertions.assertEventDispatchThread()

    val layoutInspectorCoroutineScope = AndroidCoroutineScope(disposable)

    LayoutInspectorOpenProjectsTracker.openProjects += 1
    Disposer.register(disposable) { LayoutInspectorOpenProjectsTracker.openProjects -= 1 }

    val inspectorClientSettings = InspectorClientSettings(project)

    val edtExecutor = EdtExecutorService.getInstance()

    val notificationModel = NotificationModel(project)
    TransportErrorListener(project, notificationModel, LayoutInspectorMetrics, disposable)

    val processesModel =
      ProcessesModel(
          executor = edtExecutor,
          processDiscovery = AppInspectionDiscoveryService.instance.apiServices.processDiscovery,
        )
        .also { Disposer.register(disposable, it) }

    val scheduledExecutor = createScheduledExecutor(disposable)
    val model =
      InspectorModel(project, layoutInspectorCoroutineScope, scheduledExecutor, processesModel)

    processesModel.addSelectedProcessListeners {
      // Reset notification bar every time active process changes, since otherwise we might leave up
      // stale notifications from an error
      // encountered during a previous run.
      notificationModel.clear()
    }

    lateinit var launcher: InspectorClientLauncher
    val treeSettings = InspectorTreeSettings { launcher.activeClient }
    val metrics = LayoutInspectorSessionMetrics(project, null)
    launcher =
      InspectorClientLauncher.createDefaultLauncher(
        processesModel,
        model,
        notificationModel,
        metrics,
        treeSettings,
        inspectorClientSettings,
        layoutInspectorCoroutineScope,
        disposable,
      )

    val deviceModel = DeviceModel(disposable, processesModel)

    val foregroundProcessDetection =
      createForegroundProcessDetection(
        disposable,
        project,
        processesModel,
        deviceModel,
        layoutInspectorCoroutineScope,
      )

    return LayoutInspector(
      coroutineScope = layoutInspectorCoroutineScope,
      processModel = processesModel,
      deviceModel = deviceModel,
      foregroundProcessDetection = foregroundProcessDetection,
      inspectorClientSettings = inspectorClientSettings,
      launcher = launcher,
      layoutInspectorModel = model,
      notificationModel = notificationModel,
      treeSettings = treeSettings,
    )
  }

  private fun createScheduledExecutor(disposable: Disposable): ScheduledExecutorService {
    return Executors.newScheduledThreadPool(1).apply {
      Disposer.register(disposable) {
        shutdown()
        awaitTermination(3, TimeUnit.SECONDS)
      }
    }
  }

  private fun createForegroundProcessDetection(
    parentDisposable: Disposable,
    project: Project,
    processesModel: ProcessesModel,
    deviceModel: DeviceModel,
    coroutineScope: CoroutineScope,
  ): ForegroundProcessDetection? {
    return if (LayoutInspectorSettings.getInstance().autoConnectEnabled) {
      ForegroundProcessDetectionInitializer.initialize(
          parentDisposable = parentDisposable,
          project = project,
          processModel = processesModel,
          deviceModel = deviceModel,
          coroutineScope = coroutineScope,
          streamManager = service<TransportStreamManagerService>().streamManager,
          metrics = ForegroundProcessDetectionMetrics,
        )
        .also { it.start() }
    } else {
      null
    }
  }

  override fun dispose() {}
}
