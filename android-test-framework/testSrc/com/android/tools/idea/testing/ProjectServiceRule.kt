/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerOrReplaceServiceInstance
import org.junit.rules.ExternalResource

/** A rule that installs a temporary project service in a test. */
class ProjectServiceRule<T : Any>(
  private val projectRule: ProjectRule,
  private val serviceInterface: Class<T>,
  private val instance: T,
) : ExternalResource() {
  private val disposable = Disposer.newDisposable("ProjectServiceRule")

  override fun before() {
    projectRule.project.registerOrReplaceServiceInstance(serviceInterface, instance, disposable)
  }

  override fun after() {
    Disposer.dispose(disposable)
  }
}
