/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.ActionPlaces
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResults
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getFullTestCaseName
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.getFullTestClassName
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.isRootAggregationResult
import com.android.tools.idea.testartifacts.instrumented.testsuite.logging.AndroidTestSuiteLogger
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteDetailsView.AndroidTestSuiteDetailsViewListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.DetailsViewDeviceSelectorListView.DetailsViewDeviceSelectorListViewListener
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionListener
import javax.swing.BoxLayout
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

private const val MAX_FIRST_COMPONENT_WIDTH: Int = 400
private const val MIN_FIRST_COMPONENT_PROPORTION: Float = 0.1f
private const val MAX_FIRST_COMPONENT_PROPORTION: Float = 0.9f

/**
 * Displays detailed results of an instrumentation test case. The test case may be executed by
 * multiple devices and this view can show them all. It has a device selector list view at left
 * side and the test result of the selected device is displayed at the right side. Use
 * [AndroidTestSuiteDetailsViewListener] to receive events.
 */
class AndroidTestSuiteDetailsView @UiThread constructor(parentDisposable: Disposable,
                                                        controller: AndroidTestSuiteViewController,
                                                        listener: AndroidTestSuiteDetailsViewListener,
                                                        project: Project,
                                                        logger: AndroidTestSuiteLogger) {
  /**
   * An interface to listen events occurred in AndroidTestSuiteDetailsView.
   */
  interface AndroidTestSuiteDetailsViewListener {
    /**
     * Invoked when the close button is clicked.
     */
    fun onAndroidTestSuiteDetailsViewCloseButtonClicked()
  }

  @get:VisibleForTesting val titleTextView: JBLabel = JBLabel().apply {
    border = JBUI.Borders.empty(0, 10)
  }

  private val myChangeOrientationButton: CommonButton = CommonButton(AllIcons.Actions.PreviewDetailsVertically).apply {
    addActionListener {
      when (controller.orientation) {
        AndroidTestSuiteViewController.Orientation.VERTICAL -> {
          icon = AllIcons.Actions.PreviewDetailsVertically
          controller.orientation = AndroidTestSuiteViewController.Orientation.HORIZONTAL
        }
        AndroidTestSuiteViewController.Orientation.HORIZONTAL -> {
          icon = AllIcons.Actions.PreviewDetails
          controller.orientation = AndroidTestSuiteViewController.Orientation.VERTICAL
        }
      }
    }
  }

  @get:VisibleForTesting val closeButton: CommonButton = CommonButton(StudioIcons.Common.CLOSE).apply {
    addActionListener(ActionListener { listener.onAndroidTestSuiteDetailsViewCloseButtonClicked() })
  }

  @get:VisibleForTesting val contentView: DetailsViewContentView = DetailsViewContentView(parentDisposable, project, logger)

  val rawTestLogConsoleView: ConsoleViewImpl = ConsoleViewImpl(project, /*viewer=*/true).apply {
    Disposer.register(parentDisposable, this)
  }

  private val myRawTestLogConsoleViewWithVerticalToolbar: NonOpaquePanel = NonOpaquePanel(BorderLayout()).apply {
    add(rawTestLogConsoleView.component, BorderLayout.CENTER)
    val rawTestLogToolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.ANDROID_TEST_SUITE_RAW_LOG,
      DefaultActionGroup(*rawTestLogConsoleView.createConsoleActions()),
      false)
    rawTestLogToolbar.setTargetComponent(rawTestLogConsoleView.component)
    add(rawTestLogToolbar.component, BorderLayout.EAST)
  }

  private val myDeviceSelectorListView: DetailsViewDeviceSelectorListView = DetailsViewDeviceSelectorListView(
    object : DetailsViewDeviceSelectorListViewListener {
      @UiThread
      override fun onDeviceSelected(selectedDevice: AndroidDevice) {
        this@AndroidTestSuiteDetailsView.selectedDevice = selectedDevice
        reloadAndroidTestResults()
        myComponentsSplitter.secondComponent = contentView.rootPanel
      }

      @UiThread
      override fun onRawOutputSelected() {
        myComponentsSplitter.secondComponent = myRawTestLogConsoleViewWithVerticalToolbar
      }
    })

  private val myComponentsSplitter: OnePixelSplitter = object: OnePixelSplitter(
    /*vertical=*/false,
    /*defaultProportion=*/0.4f,
    MIN_FIRST_COMPONENT_PROPORTION,
    MAX_FIRST_COMPONENT_PROPORTION) {

    init {
      setHonorComponentsMinimumSize(false)
      firstComponent = myDeviceSelectorListView.rootPanel
      secondComponent = myRawTestLogConsoleViewWithVerticalToolbar
      dividerPositionStrategy = DividerPositionStrategy.KEEP_FIRST_SIZE
    }

    override fun doLayout() {
      if (proportion * width > MAX_FIRST_COMPONENT_WIDTH && width > 0) {
        proportion = max(0f, min(MAX_FIRST_COMPONENT_WIDTH.toFloat() / width, 1f))
      }

      // The internal proportion value can be greater than maxProportion and smaller
      // than minProportion when you change component's side (b/170234515).
      proportion = max(MIN_FIRST_COMPONENT_PROPORTION, min(proportion, MAX_FIRST_COMPONENT_PROPORTION))

      super.doLayout()
    }
  }

  /**
   * Shows or hides a device selector list.
   */
  @get:UiThread
  @set:UiThread
  var isDeviceSelectorListVisible: Boolean
    get() = myComponentsSplitter.firstComponent.isVisible
    set(value) {
      val valueChanged = value != myComponentsSplitter.firstComponent.isVisible
      myComponentsSplitter.firstComponent.isVisible = value
      if (valueChanged) {
        myComponentsSplitter.doLayout()
        myComponentsSplitter.repaint()
      }
    }

  val rootPanel: JPanel = JPanel(BorderLayout()).apply {
    add(JPanel(BorderLayout()).apply {
      add(titleTextView, BorderLayout.CENTER)
      add(JPanel().apply {
        layout = BoxLayout(this, BoxLayout.LINE_AXIS)
        add(myChangeOrientationButton)
        add(closeButton)
      }, BorderLayout.EAST)
      border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.BOTTOM)
    }, BorderLayout.NORTH)
    add(myComponentsSplitter, BorderLayout.CENTER)
    minimumSize = Dimension()
  }

  private var myTestResults: AndroidTestResults? = null
  @get:VisibleForTesting var selectedDevice: AndroidDevice? = null

  /**
   * Updates the view with a given AndroidTestResults.
   */
  @UiThread
  fun setAndroidTestResults(results: AndroidTestResults) {
    myTestResults = results
    reloadAndroidTestResults()
  }

  /**
   * Reload AndroidTestResults set by [.setAndroidTestResults].
   */
  @UiThread
  fun reloadAndroidTestResults() {
    val testResults = myTestResults ?: return
    if (!Strings.isNullOrEmpty(testResults.methodName)) {
      titleTextView.text = testResults.getFullTestCaseName()
    }
    else if (!Strings.isNullOrEmpty(testResults.className)) {
      titleTextView.text = testResults.getFullTestClassName()
    }
    else {
      titleTextView.text = "Test Results"
    }
    titleTextView.icon = getIconFor(testResults.getTestResultSummary())
    titleTextView.minimumSize = Dimension()
    if (testResults.isRootAggregationResult()) {
      myDeviceSelectorListView.setShowRawOutputItem(true)
    }
    else {
      myDeviceSelectorListView.setShowRawOutputItem(false)
      selectedDevice?.let { selectDevice(it) }
    }
    myDeviceSelectorListView.setAndroidTestResults(testResults)
    contentView.setPackageName(testResults.packageName)
    selectedDevice?.let {
      contentView.setAndroidDevice(it)
      contentView.setAndroidTestCaseResult(testResults.getTestCaseResult(it))
      contentView.setAndroidTestCaseStartTime(testResults.getStartTime(it))
      contentView.setLogcat(testResults.getLogcat(it))
      contentView.setErrorStackTrace(testResults.getErrorStackTrace(it))
      contentView.setBenchmarkText(testResults.getBenchmark(it))
      contentView.setRetentionInfo(testResults.getRetentionInfo(it))
      contentView.setRetentionSnapshot(testResults.getRetentionSnapshot(it))
    }
  }

  /**
   * Adds a given Android device to the device selector list in the details view.
   */
  @UiThread
  fun addDevice(device: AndroidDevice) {
    myDeviceSelectorListView.addDevice(device)

    // If a user hasn't select a device yet, set the first come device as default.
    if (selectedDevice == null) {
      selectedDevice = device
    }
  }

  /**
   * Selects a given device and display test results specifically to the device.
   */
  @UiThread
  fun selectDevice(device: AndroidDevice) {
    myDeviceSelectorListView.selectDevice(device)
  }

  /**
   * Select the raw output item to be displayed in the content area.
   */
  @UiThread
  fun selectRawOutput() {
    myDeviceSelectorListView.selectRawOutputItem()
  }
}