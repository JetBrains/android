/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger

@Service(Service.Level.PROJECT)
@State(name = "AndroidProjectSystem", storages = [Storage("AndroidProjectSystem.xml")], reloadable = false)
class ProjectSystemService(val project: Project): PersistentStateComponent<ProjectSystemService.State> {
  /**
   * A state for the mini state machine around updating the view of the project system:
   *
   *    NORMAL_STATE: nothing to report;
   *   UPDATE_NEEDED: detectProjectSystem has detected a non-default project system where previously
   *                  we had initialized and cached a default project system;
   *     UPDATE_SENT: we have sent a rootsChanged event in response to having been in state 1.
   */
  private val rootsChangedState = AtomicInteger(NORMAL_STATE)

  /**
   * Uses [LazyThreadSafetyMode.PUBLICATION] intentionally: we cannot use [LazyThreadSafetyMode.SYNCHRONIZED] because of the
   * very concrete risk of introducing cycles amongst locks.  The initialized-or-not state of this delegate tells us whether
   * we need to update the world: under usual circumstances (no unusual contention when computing the project system) this
   * remains uninitialized.
   */
  private val cachedDefaultProjectSystemDelegate = lazy(LazyThreadSafetyMode.PUBLICATION) { defaultProjectSystem(project) }

  /**
   * Uses [LazyThreadSafetyMode.PUBLICATION] intentionally: we cannot use [LazyThreadSafetyMode.SYNCHRONIZED] because of the
   * very concrete risk of introducing cycles amongst locks.
   */
  private val cachedProjectSystemDelegate = lazy(LazyThreadSafetyMode.PUBLICATION) {
    detectProjectSystem(project).also {
      if (cachedDefaultProjectSystemDelegate.isInitialized()) rootsChangedState.compareAndSet(NORMAL_STATE, UPDATE_NEEDED)
    }
  }
  private val cachedDefaultProjectSystem by cachedDefaultProjectSystemDelegate
  private val cachedProjectSystem by cachedProjectSystemDelegate
  private var projectSystemForTests: AndroidProjectSystem? = null

  fun setProviderId(id: String) {
    state = State(id)
  }
  private var state: State? = null

  companion object {
    private const val NORMAL_STATE = 0
    private const val UPDATE_NEEDED = 1
    private const val UPDATE_SENT = 2
    @JvmStatic
    fun getInstance(project: Project): ProjectSystemService {
      return project.getService(ProjectSystemService::class.java)!!
    }
  }

  private class ReadLockUnavailable : Exception()

  val projectSystem: AndroidProjectSystem
    get() = projectSystemForTests ?: try {
      cachedProjectSystem.also {
        if (rootsChangedState.compareAndSet(UPDATE_NEEDED, UPDATE_SENT)) {
          ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            // We get here if at a previous time in this session we returned a DefaultProjectSystem because of being unable to
            // acquire the read lock.  This happens rarely in tests, and almost never in production.
            //
            // Under those circumstances, some previously-computed answers involving the Project System will be wrong.  In general
            // it is probably not the case that sending a roots changed event is enough to force recomputation of all dependents,
            // but this at least allows other IDE systems to listen for these events and respond appropriately.
            runWriteAction { sendRootsChangedEvents(project) }
          }
        }
      }
    }
    catch (e: ReadLockUnavailable) {
      cachedDefaultProjectSystem
    }

  private fun detectProjectSystem(project: Project): AndroidProjectSystem {
    val extensions = EP_NAME.extensionList
    getState()?.providerId?.let { providerId ->
      extensions.find { it.id == providerId }?.also { return it.projectSystemFactory(project) }
    }
    val application = ApplicationManagerEx.getApplicationEx()
    var result: AndroidProjectSystem? = null
    // In principle:
    // - we could be detecting the Project System from an arbitrary IDE subsystem, holding arbitrary resources from that
    //   subsystem or the platform;
    // - our detection routines might want to acquire the read lock;
    // - other parts of the platform might be in a read action, waiting on resources that we hold;
    // - a write action might be pending
    // Under those circumstances, trying to take a read action would deadlock (see b/375355918 for one example)
    //
    // Wrapping the attempt to compute the Project System inside a readAction which only starts if there is no pending
    // write means that we can avoid the deadlock, at the cost of sometimes returning a DefaultProjectSystem when that
    // is wrong.  We maintain two separate caches in order to detect and correct for that.
    application.tryRunReadAction {
      val provider = extensions.find { it.isApplicable(project) }
      if (provider != null) {
        result = provider.projectSystemFactory(project)
        state = State(provider.id)
      }
      else {
        result = defaultProjectSystem(project, extensions)
      }
    }
    return result ?: throw ReadLockUnavailable()
  }

  private fun defaultProjectSystem(
    project: Project,
    extensions: List<AndroidProjectSystemProvider> = EP_NAME.extensionList
  ): AndroidProjectSystem {
    val provider = extensions.find { it.id == "" }
                   ?: throw IllegalStateException("Default AndroidProjectSystem not found for project " + project.name)
    return provider.projectSystemFactory(project)
  }

  private fun sendRootsChangedEvents(project: Project) {
    ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.INSTANCE, RootsChangeRescanningInfo.TOTAL_RESCAN)
  }

  /**
   * Replaces the project system of the current [project] and re-initializes it by sending [ModuleRootListener.TOPIC] notifications.
   */
  @TestOnly
  fun replaceProjectSystemForTests(projectSystem: AndroidProjectSystem) {
    projectSystemForTests = projectSystem
    if (cachedProjectSystemDelegate.isInitialized()) {
      runWriteAction {
        sendRootsChangedEvents(project)
      }
    }
  }

  class State(id: String? = null) {
    var providerId: String? = id
  }

  // Do not serialize default project system provider if it somehow ends up getting to be our state: if we have fallen back
  // to the default provider, give detection another chance when re-opening the project (for example, opening a Bazel-based
  // project in Android Studio with Bazel support after once opening it in vanilla Android Studio).
  override fun getState(): State? = state?.takeIf { it.providerId.orEmpty() != "" }

  override fun loadState(state: State) {
    this.state = state
  }
}
