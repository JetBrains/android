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
package com.android.tools.idea.editors.layoutInspector.impl

import com.android.ddmlib.Client
import com.android.tools.idea.editors.layoutInspector.AndroidLayoutInspectorService
import com.android.tools.idea.editors.layoutInspector.actions.LayoutInspectorAction
import com.intellij.openapi.project.Project

class AndroidLayoutInspectorServiceImpl : AndroidLayoutInspectorService {
  override fun getTask(project: Project?, client: Client): LayoutInspectorAction.GetClientWindowsTask {
    return LayoutInspectorAction.GetClientWindowsTask(project, client);
  }
}