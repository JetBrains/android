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
package com.android.tools.idea.emulator

import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import java.awt.Component

/**
 * Installs a [DataManager] for headless environments that delegates calls to [DataProvider] components.
 * Use it to properly update button states. The data manager is uninstalled when [disposable] is disposed.
 */
fun installHeadlessTestDataManager(project: Project, disposable: Disposable) {
  ApplicationManager.getApplication().replaceService(DataManager::class.java, object : HeadlessDataManager() {
    override fun getDataContext(component: Component): DataContext {
      return TestDataContext(component, project)
    }
  }, disposable)
}

private class TestDataContext(private val component: Component, private val project: Project) : DataContext {

  override fun getData(dataId: String): Any? {
    var c: Component? = component
    while (c != null) {
      if (c is DataProvider) {
        val data = c.getData(dataId)
        if (data != null) {
          return data
        }
      }
      c = c.parent
    }
    return if (CommonDataKeys.PROJECT.`is`(dataId)) project else null
  }
}