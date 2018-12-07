/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.testing

import com.android.tools.adtui.workbench.ComponentStack
import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.TestRunnerUtil
import com.intellij.testFramework.runInEdtAndWait
import org.junit.rules.ExternalResource

/**
 * An Application rule that sets up a test [com.intellij.openapi.application.Application].
 *
 * Setup a component stack for overriding certain components.
 */
class EdtApplicationRule : ExternalResource() {

  private var applicationComponentStack: ComponentStack? = null
  private var testApplication: IdeaTestApplication? = null

  fun <T> registerApplicationComponentImplementation(key: Class<T>, instance: T) {
    applicationComponentStack?.registerComponentImplementation(key, instance)
  }

  companion object {
    init {
      Logger.setFactory(TestLoggerFactory::class.java)
    }
  }

  /**
   * Setup a test Application instance.
   *
   * Some of the components initialized require initialization on the event
   * dispatch thread.
   */
  override fun before() {
    runInEdtAndWait {
      if (ApplicationManager.getApplication() == null) {
        // For running tests in the IDE:
        testApplication = IdeaTestApplication.getInstance()
        TestRunnerUtil.replaceIdeEventQueueSafely()
        (PersistentFS.getInstance() as PersistentFSImpl).cleanPersistedContents()
      }
      applicationComponentStack = ComponentStack(ApplicationManager.getApplication())
    }
  }

  override fun after() {
    applicationComponentStack?.restoreComponents()
    applicationComponentStack = null
    if (testApplication != null) {
      runInEdtAndWait {
        ApplicationManager.getApplication().runWriteAction { Disposer.dispose(testApplication!!) }
      }
      testApplication = null
    }
  }
}
