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
package com.android.tools.idea.editors.layoutInspector.actions

import com.android.ddmlib.Client
import com.android.layoutinspector.model.ClientWindow
import com.android.layoutinspector.model.ViewNode
import com.android.tools.idea.editors.layoutInspector.AndroidLayoutInspectorService
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import java.io.File
import java.util.concurrent.TimeUnit

class LayoutInspectorDebugStubber {
  fun mockOutDebugger(project: Project, layoutDumpFile: File, layoutImageFile: File) {
    project.replaceService(AndroidLayoutInspectorService::class.java, MockLayoutInspectorService(layoutDumpFile, layoutImageFile), project)
  }

  private class MockLayoutInspectorService(val layoutDump: File, val layoutImage: File) : AndroidLayoutInspectorService {
    override fun getTask(project: Project?, client: Client): LayoutInspectorAction.GetClientWindowsTask {
      return LayoutInspectorAction.GetClientWindowsTask(project, client, MockWindowRetriever(layoutDump, layoutImage))
    }
  }

  private class MockWindowRetriever(val layoutDump: File, val layoutImage: File) : LayoutInspectorAction.ClientWindowRetriever {
    override fun getAllWindows(
      client: Client,
      timeout: Long,
      timeoutUnits: TimeUnit): MutableList<ClientWindow> {

      return mutableListOf(ClientWindow("Mock client window", client, MockClientViewInspector(layoutDump, layoutImage)))
    }
  }

  private class MockClientViewInspector(val layoutDump: File, val layoutImage: File) : ClientWindow.ClientViewInspector {
    override fun dumpViewHierarchy(
      client: Client,
      title: String,
      skipChildren: Boolean,
      includeProperties: Boolean,
      useV2: Boolean,
      timeout: Long,
      timeUnit: TimeUnit): ByteArray? {

      return layoutDump.readBytes()
    }

    override fun captureView(client: Client, title: String, node: ViewNode, timeout: Long, timeUnit: TimeUnit): ByteArray? {
      return layoutImage.readBytes()
    }
  }
}