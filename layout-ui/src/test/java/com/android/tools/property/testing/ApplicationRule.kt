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

import com.android.testutils.MockitoThreadLocalsCleaner
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.PluginUtil
import com.intellij.ide.plugins.PluginUtilImpl
import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.ide.ui.UISettings
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
  private var mockitoCleaner: MockitoThreadLocalsCleaner? = null

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
    // The global variable StartUpManager.currentState will activate code that requires services
    // that are not supported/needed by tests using this test rule. Reset it here in case other
    // tests have incremented the LoadingState. (See b/204201417).
    @Suppress("UnstableApiUsage")
    StartUpMeasurer.setCurrentState(LoadingState.BOOTSTRAP)

    rootDisposable = Disposer.newDisposable("ApplicationRule::rootDisposable")
    // If there was no previous application,
    // ApplicationManager leaves the MockApplication in place, which can break future tests.
    if (ApplicationManager.getApplication() == null) {
      Disposer.register(testRootDisposable) {
        object : ApplicationManager() {
          init {
            ourApplication = null
          }
        }
      }
    }
    application = TestApplication(testRootDisposable, testName)
    ApplicationManager.setApplication(testApplication, testRootDisposable)
    mockitoCleaner = MockitoThreadLocalsCleaner()
    mockitoCleaner!!.setup()

    // Needed to avoid this kotlin.KotlinNullPointerException:
    //  at com.intellij.ide.ui.UISettings$Companion.getInstance(UISettings.kt:423)
    //  at com.intellij.ide.ui.UISettings$Companion.getInstanceOrNull(UISettings.kt:434)
    //  at com.intellij.ide.ui.AntialiasingType.getAAHintForSwingComponent(AntialiasingType.java:17)
    //  at com.intellij.ide.ui.UISettings$Companion.setupComponentAntialiasing(UISettings.kt:483)
    //  at com.intellij.ide.ui.UISettings.setupComponentAntialiasing(UISettings.kt)
    //  at com.intellij.ui.SimpleColoredComponent.updateUI(SimpleColoredComponent.java:107)
    //  at com.intellij.ui.SimpleColoredComponent.<init>(SimpleColoredComponent.java:102)
    //
    // And from here:
    //  ...
    //  at com.intellij.ide.ui.UISettings.setupComponentAntialiasing(UISettings.kt)
    //  at com.intellij.ui.components.JBLabel.updateUI(JBLabel.java:241)
    //  at javax.swing.JLabel.<init>(JLabel.java:164)
    //
    // Which can happen if the following settings has changed in a different test:
    //  LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred
    application!!.registerService(UISettings::class.java, UISettings(NotRoamableUiSettings()))
    application!!.registerService(PluginUtil::class.java, PluginUtilImpl::class.java)
    application!!.registerService(PerformanceWatcher::class.java, PerformanceWatcher::class.java)
  }

  override fun after() {
    Disposer.dispose(rootDisposable!!) // This will recover previous instance of Application (see ApplicationManager::setApplication)
    rootDisposable = null
    application = null
    mockitoCleaner!!.cleanupAndTearDown()
    mockitoCleaner = null
  }

  private class TestApplication(disposable: Disposable, val name: String): MockApplication(disposable) {
    override fun invokeLater(runnable: Runnable) {
      SwingUtilities.invokeLater(runnable)
    }

    override fun toString(): String {
      return "TestApplication@$name"
    }
  }
}
