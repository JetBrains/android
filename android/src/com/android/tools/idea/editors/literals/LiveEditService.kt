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

package com.android.tools.idea.editors.literals

import com.android.ddmlib.IDevice
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.ui.EmulatorLiveEditAdapter
import com.android.tools.idea.editors.liveedit.ui.LiveEditAction
import com.android.tools.idea.emulator.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.emulator.SERIAL_NUMBER_KEY
import com.android.tools.idea.run.deployment.liveedit.AndroidLiveEditDeployMonitor
import com.android.tools.idea.run.deployment.liveedit.SourceInlineCandidateCache
import com.android.tools.idea.util.ListenerCollection
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.stream.Collectors


/**
 * @param file: Where the file event originate
 * @param origin: The most narrow PSI Element where the edit event occurred.
 */
data class EditEvent(val file: PsiFile,
                     val origin: KtElement) {

  // A list of all functions that encapsulate the origin of the event in the source code ordered by nesting level
  // from inner-most to outer-most. This will be use to determine which compose groups to invalidate on the given change.
  val parentGroup = ArrayList<KtFunction>()
}

enum class EditState {
  ERROR,            // LiveEdit has encountered an error that is not recoverable.
  RECOMPOSE_ERROR,  // A possibly recoverable error occurred after a recomposition.
  PAUSED,           // No apps are ready to receive live edit updates or a compilation error is preventing push to the device.
  RECOMPOSE_NEEDED, // In manual mode, changes have been pushed to the devices but not recomposed yet.
  OUT_OF_DATE,      // In manual mode, changes have been detected but not pushed to the device yet.
  LOADING,          // App is being deployed.
  IN_PROGRESS,      // Processing...
  UP_TO_DATE,       // The device and the code are in Sync.
  DISABLED          // LiveEdit has been disabled (via UI or custom properties).
}

data class EditStatus(val editState: EditState, val message: String, val actionId: String?)

/**
 * Allows any component to listen to all method body edits of a project.
 */
@Service
class LiveEditService private constructor(val project: Project, var listenerExecutor: Executor) : Disposable {

  val inlineCandidateCache = SourceInlineCandidateCache()

  constructor(project: Project) : this(project,
                                       AppExecutorUtil.createBoundedApplicationPoolExecutor(
                                         "Document changed listeners executor", 1))

  init {
    val adapter = EmulatorLiveEditAdapter(project)
    LiveEditAction.registerProject(project, adapter)
    Disposer.register(this) { LiveEditAction.unregisterProject(project) }
    ApplicationManager.getApplication().invokeLater {
      val contentManager = project.getServiceIfCreated(ToolWindowManager::class.java)
        ?.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID)
        ?.contentManager
      contentManager?.addContentManagerListener( object : ContentManagerListener {
          override fun contentAdded(event: ContentManagerEvent) {
            if (event.content.component !is DataProvider) {
              return
            }
            val serial = (event.content.component as DataProvider).getData(SERIAL_NUMBER_KEY.name) as String?
            serial?.let { adapter.register(it) }
          }

          override fun contentRemoveQuery(event: ContentManagerEvent) {
            if (event.content.component !is DataProvider) {
              return
            }
            val serial = (event.content.component as DataProvider).getData(SERIAL_NUMBER_KEY.name) as String?
            serial?.let { adapter.unregister(it) }
          }
        })
      contentManager?.contents?.forEach {
        if (it.component is DataProvider) {
          val serial = (it.component as DataProvider).getData(SERIAL_NUMBER_KEY.name) as String?
          serial?.let { s -> adapter.register(s) }
        }
      }
    }
  }

  fun resetState() {
    inlineCandidateCache.clear()
    deployMonitor.resetState()
  }

  fun interface EditListener {
    operator fun invoke(method: EditEvent)
  }

  interface EditStatusProvider {
    fun status(device: IDevice): EditStatus

    fun status(): Map<IDevice, EditStatus>

    fun devices(): Set<IDevice>
  }

  private val onEditListeners = ListenerCollection.createWithExecutor<EditListener>(listenerExecutor)

  private val deployMonitor: AndroidLiveEditDeployMonitor

  private val editStatusProviders = mutableListOf<EditStatusProvider>()

  fun addOnEditListener(listener: EditListener) {
    onEditListeners.add(listener)
  }

  fun addEditStatusProvider(provider: EditStatusProvider) {
    editStatusProviders.add(provider)
  }

  init {
    // TODO: Deactivate this when not needed.
    val listener = MyPsiListener(::onMethodBodyUpdated)
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, this)
    deployMonitor = AndroidLiveEditDeployMonitor(this, project)
    // TODO: Delete if it turns our we don't need Hard-refresh trigger.
    //bindKeyMapShortcut(LiveEditApplicationConfiguration.getInstance().leTriggerMode)
  }

  companion object {

    // The action upon which we trigger LiveEdit to do a push in manual mode.
    // Right now this is set to "SaveAll" which is called via Ctrl/Cmd+S.
    @JvmStatic
    val PIGGYBACK_ACTION_ID: String = "SaveAll"

    enum class LiveEditTriggerMode {
      LE_TRIGGER_MANUAL,
      LE_TRIGGER_AUTOMATIC,
    }

    fun isLeTriggerManual(mode : LiveEditTriggerMode) : Boolean {
      return mode == LiveEditTriggerMode.LE_TRIGGER_MANUAL
    }

    @JvmStatic
    fun isLeTriggerManual() = isLeTriggerManual(LiveEditApplicationConfiguration.getInstance().leTriggerMode)

    @JvmStatic
    fun getInstance(project: Project): LiveEditService = project.getService(LiveEditService::class.java)

    @JvmField
    val DISABLED_STATUS = EditStatus(EditState.DISABLED, "", null)
    @JvmField
    val UP_TO_DATE_STATUS = EditStatus(EditState.UP_TO_DATE, "All changes applied.", null)
  }

  fun editStatus(device: IDevice): EditStatus {
    var editStatus = DISABLED_STATUS
    for (provider in editStatusProviders) {
      val nextStatus = provider.status(device)
      // TODO make this state transition more robust/centralized
      if (nextStatus.editState.ordinal < editStatus.editState.ordinal) {
        editStatus = nextStatus
      }
    }
    return editStatus
  }

  fun editStatus(): MutableMap<IDevice, EditStatus> {
    val statuses = HashMap<IDevice, EditStatus>()
    for (provider in editStatusProviders) {
      val nextStatuses = provider.status()
      nextStatuses.forEach { entry ->
        statuses.compute(entry.key) { _, oldStatus ->
          if (oldStatus == null) {
            entry.value
          }
          else {
            if (entry.value.editState.ordinal < oldStatus.editState.ordinal) entry.value else oldStatus
          }
        }
      }
    }
    return statuses
  }

  fun mergeStatuses(statuses: Map<IDevice, EditStatus>): EditStatus {
    return deployMonitor.mergeStatuses(statuses)
  }

  fun devices(): Set<IDevice> {
    return editStatusProviders.stream().map { it.devices() }.flatMap { it.stream() }.collect(Collectors.toSet())
  }

  fun notifyDebug(packageName: String, device: IDevice) {
    deployMonitor.notifyDebug(packageName, device)
  }

  fun getCallback(packageName: String, device: IDevice) : Callable<*>? {
    return deployMonitor.getCallback(packageName, device)
  }

  @com.android.annotations.Trace
  private fun onMethodBodyUpdated(event: EditEvent) {
    // Drop any invalid events.
    // As mention in other parts of the code. The type of PSI event sent are really unpredictable. Intermediate events
    // sometimes contains event origins that is not valid or no longer exist in any file. In automatic mode this might not be a big
    // issue but in automatic mode, a single failing event can get merged into the big edit event which causes the single compiler
    // invocation to crash.
    if (!event.origin.isValid || event.origin.containingFile == null) {
      return
    }

    onEditListeners.forEach {
      it(event)
    }
  }

  private inner class MyPsiListener(private val editListener: EditListener) : PsiTreeChangeListener {
    @com.android.annotations.Trace
    private fun handleChangeEvent(event: PsiTreeChangeEvent) {
      // THIS CODE IS EXTREMELY FRAGILE AT THE MOMENT.
      // According to the PSI listener doc, there is no guarantee what events we get.
      // Changing a single variable name can result with a "replace" of the whole file.
      //
      // While this works "ok" for the most part, we need to figure out a better way to detect
      // the change is actually a function change somehow.

      if (event.file == null || event.file !is KtFile) {
        return
      }

      val file = event.file as KtFile
      var parent = event.parent;

      // The code might not be valid at this point, so we should not be making any
      // assumption based on the Kotlin language structure.

      while (parent != null) {
        when (parent) {
          is KtNamedFunction -> {
            val event = EditEvent(file, parent)
            editListener(event)
            break;
          }
          is KtFunction -> {
            val event = EditEvent(file, parent)

            // Record each unnamed function as part of the event until we reach a named function.
            // This will be used to determine how partial recomposition is done on this edit in a later stage.
            var groupParent = parent.parent
            while (groupParent != null) {
              when (groupParent) {
                is KtNamedFunction -> {
                  event.parentGroup.add(groupParent)
                  break
                }
                is KtNamedFunction -> {
                  event.parentGroup.add(groupParent)
                }
              }
              groupParent = groupParent.parent
            }
            editListener(event)
            break;
          }
          is KtClass -> {
            val event = EditEvent(file, parent)
            editListener(event)
            break;
          }
        }
        parent = parent.parent
      }

      // This is a workaround to experiment with partial recomposition. Right now any simple edit would create multiple
      // edit events and one of them is usually a spurious whole file event that will trigger an unnecessary whole recompose.
      // For now we just ignore that event until Live Edit becomes better at diff'ing changes.
      if (!LiveEditAdvancedConfiguration.getInstance().usePartialRecompose) {
        // If there's no Kotlin construct to use as a parent for this event, use the KtFile itself as the parent.
        val event = EditEvent(file, file)
        editListener(event)
      }
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }
  }

  override fun dispose() {
    //TODO: "Not yet implemented"
  }

  fun triggerLiveEdit() {
    deployMonitor.onManualLETrigger(project)
  }

  fun sendRecomposeRequest() {
    deployMonitor.sendRecomposeRequest();
  }
}