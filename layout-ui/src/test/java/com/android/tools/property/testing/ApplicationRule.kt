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
package com.android.tools.property.testing

import com.intellij.mock.MockApplication
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestLoggerFactory
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement
import javax.swing.SwingUtilities

/**
 * An Application rule that sets up a test [com.intellij.openapi.application.Application].
 *
 * The application instance is based on [MockApplication] which is much lighter and faster
 * than using [com.intellij.idea.IdeaTestApplication].
 */
open class ApplicationRule : ExternalResource() {
  private lateinit var testName: String
  private var rootDisposable: Disposable? = null
  private var application: MockApplication? = null

  val testRootDisposable: Disposable
    get() = rootDisposable!!

  val testApplication: MockApplication
    get() = application!!

  companion object {
    init {
      Logger.setFactory(TestLoggerFactory::class.java)
    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    testName = description.displayName
    return super.apply(base, description)
  }

  /**
   * Setup a test Application instance with a few common services needed for property tests.
   */
  override fun before() {
    rootDisposable = Disposer.newDisposable("ApplicationRule::rootDisposable")
    application = TestApplication(rootDisposable!!, testName)
    ApplicationManager.setApplication(application!!, rootDisposable!!)
  }

  override fun after() {
    Disposer.dispose(rootDisposable!!) // This will recover previous instance of Application (see ApplicationManager::setApplication)
    rootDisposable = null
    application = null
  }

  private class TestApplication(disposable: Disposable, val name: String): MockApplication(disposable) {
    override fun invokeLater(runnable: Runnable) {
      SwingUtilities.invokeLater(runnable)
    }

    override fun toString(): String {
      return "TestApplication@${name}"
    }
  }
}
