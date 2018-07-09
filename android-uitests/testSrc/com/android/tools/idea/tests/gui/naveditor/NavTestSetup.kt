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
package com.android.tools.idea.tests.gui.naveditor

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.actionSystem.ActionManager
import org.jetbrains.android.actions.CreateResourceFileActionGroup

fun beforeNavTest() {
  StudioFlags.ENABLE_NAV_EDITOR.override(true)
  refresh()
}

fun afterNavTest() {
  StudioFlags.ENABLE_NAV_EDITOR.clearOverride()
  refresh()
}

private fun refresh() {
  // CreateResourceFileActionGroup is preloaded, and at that point we check whether the navigation type should be included.
  // Since the flag hasn't been set at that point, we have to recreate it now.
  ActionManager.getInstance().unregisterAction("Android.CreateResourcesActionGroup")
  ActionManager.getInstance().registerAction("Android.CreateResourcesActionGroup", CreateResourceFileActionGroup())
}