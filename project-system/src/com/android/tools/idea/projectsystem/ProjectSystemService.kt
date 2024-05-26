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
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.impl.ModuleRootEventImpl
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

class ProjectSystemService(val project: Project) {
  private val cachedProjectSystem = AtomicReference<AndroidProjectSystem?>()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectSystemService {
      return project.getService(ProjectSystemService::class.java)!!
    }
  }

  val projectSystem: AndroidProjectSystem
    get() {
      // We need to guarantee that the project system remains unique until the next time the project
      // is closed. This method may be called by multiple threads in parallel.
      var cache = cachedProjectSystem.get()

      if (cache == null) {
        // The call to detectProjectSystem invokes unknown non-local code loaded from an extension
        // point, so we can't hold any locks or be inside a synchronized block while invoking it or
        // it would be a deadlock risk.
        cache = detectProjectSystem(project)
        if (cachedProjectSystem.compareAndSet(null, cache)) {
          Logger.getInstance(ProjectSystemService::class.java).info("${cache.javaClass.simpleName} project system has been detected")
        }
        // Can't return null since we've set it to a non-null value earlier in the method and there
        // is no code that ever sets it back to null once set to a non-null value. However, it's
        // possible that another thread initialized it to a different non-null value, so we should
        // use the result of get rather than our local cache variable.
        cache = cachedProjectSystem.get()!!
      }
      return cache
    }

  private fun detectProjectSystem(project: Project): AndroidProjectSystem {
    val extensions = EP_NAME.getExtensions(project)
    val provider = extensions.find { it.isApplicable() }
                   ?: extensions.find { it.id == "" }
                   ?: throw IllegalStateException("Default AndroidProjectSystem not found for project " + project.name)
    return provider.projectSystem
  }

  /**
   * Replaces the project system of the current [project] and re-initializes it by sending [ModuleRootListener.TOPIC] notifications.
   */
  @TestOnly
  fun replaceProjectSystemForTests(projectSystem: AndroidProjectSystem) {
    val old = cachedProjectSystem.getAndUpdate { projectSystem }
    if (old != null) {
      // require EDT explicitly, due to issue with TransactionGuard IJPL-150392
      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          val publisher = project.messageBus.syncPublisher(ModuleRootListener.TOPIC)
          val rootChangedEvent = ModuleRootEventImpl(project, false)
          publisher.beforeRootsChange(rootChangedEvent)
          publisher.rootsChanged(rootChangedEvent)
        }
      }
    }
  }
}
