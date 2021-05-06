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
package com.android.tools.idea.appinspection.ide.ui

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.stdui.EmptyStatePanel
import com.android.tools.idea.appinspection.ide.model.AppInspectionBundle
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import kotlinx.coroutines.CompletableDeferred
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane

/**
 * All relevant data needed to add a tab into a tabbed pane including a placeholder panel that can be set during runtime.
 *
 * This class allows us to collect information up front before adding all tabs into the tab pane,
 * instead of just adding tabs as threads return them. This ensures we can keep a consistent
 * ordering across inspector launches.
 */
class AppInspectorTabShell(
  val provider: AppInspectorTabProvider
) : Comparable<AppInspectorTabShell>, UserDataHolderBase(), Disposable {
  @VisibleForTesting
  val containerPanel = JPanel(BorderLayout())

  private val contentChangedDeferred = CompletableDeferred<JComponent>()

  /**
   * Will be false until [setComponent] is called
   */
  val isComponentSet: Boolean
    get() = contentChangedDeferred.isCompleted

  init {
    containerPanel.add(EmptyStatePanel(AppInspectionBundle.message("inspector.loading", provider.displayName)))
  }

  /**
   * Sets the content of this tab after clearing the previous content.
   *
   * This must be called in a UI thread because the containerPanel may have been rendered.
   */
  @UiThread
  fun setComponent(component: JComponent) {
    containerPanel.removeAll()
    containerPanel.add(component)
    contentChangedDeferred.complete(component)
  }

  @UiThread
  fun addTo(tabbedPane: JTabbedPane) {
    tabbedPane.addTab(provider.displayName, provider.icon, containerPanel)
  }

  override fun dispose() {
  }

  /**
   * Suspends until content is added to the containerPanel.
   *
   * For testing only.
   */
  @VisibleForTesting
  suspend fun waitForContent() = contentChangedDeferred.await()

  override fun compareTo(other: AppInspectorTabShell): Int = provider.compareTo(other.provider)
}