/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.adtui.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.ui.content.impl.ContentImpl
import com.intellij.util.containers.DisposableWrapperList

/**
 * Receives events from all content managers in a hierarchy of a possibly split tool window.
 */
@Suppress("LeakingThis")
abstract class ContentManagerHierarchyAdapter(private val toolWindow: ToolWindow) : ContentManagerListener, Disposable {

  private val listener = object : ContentManagerListener, Disposable {

    private val contentManagers = DisposableWrapperList<ContentManager>()

    override fun contentAdded(event: ContentManagerEvent) {
      event.content.addPropertyChangeListener { evt ->
        if (evt.propertyName == ContentImpl.PROP_CONTENT_MANAGER) {
          val contentManager = evt.newValue as? ContentManager
          contentManager?.let { rememberContentManager(it) }
        }
      }
      this@ContentManagerHierarchyAdapter.contentAdded(event)
    }

    override fun contentRemoved(event: ContentManagerEvent) {
      if (Content.TEMPORARY_REMOVED_KEY.get(event.content, false)) {
        return
      }
      this@ContentManagerHierarchyAdapter.contentRemoved(event)
    }

    override fun contentRemoveQuery(event: ContentManagerEvent) {
      if (Content.TEMPORARY_REMOVED_KEY.get(event.content, false)) {
        return
      }
      this@ContentManagerHierarchyAdapter.contentRemoveQuery(event)
    }

    override fun selectionChanged(event: ContentManagerEvent) {
      if (Content.TEMPORARY_REMOVED_KEY.get(event.content, false)) {
        return
      }
      this@ContentManagerHierarchyAdapter.selectionChanged(event)
    }

    private fun rememberContentManager(contentManager: ContentManager) {
      if (contentManager != toolWindow.contentManager && contentManager !in contentManagers) {
        contentManagers.add(contentManager, contentManager)
        contentManager.addContentManagerListener(this)
      }
    }

    override fun dispose() {
      for (contentManager in contentManagers) {
        contentManager.removeContentManagerListener(this)
      }
      contentManagers.clear()
    }
  }

  init {
    toolWindow.addContentManagerListener(listener)
    Disposer.register(toolWindow.disposable, this)
    Disposer.register(this, listener)
  }

  override fun dispose() {
  }
}
