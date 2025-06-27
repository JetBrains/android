/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.adblib.testing

import com.android.adblib.AdbSession
import com.android.adblib.testing.FakeAdbSession
import com.android.tools.idea.adblib.AdbLibService
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** A test implementation of [AdbLibService]. Can be given any [AdbSession]. */
class TestAdbLibService(override val session: AdbSession) : AdbLibService

/** Replaces production [AdbLibService] with [TestAdbLibService]. */
open class TestAdbLibServiceRule<T : AdbSession>(
  private val projectRule: ProjectRule,
  val adbSession: T,
) : ExternalResource() {

  private val disposable = Disposer.newDisposable("ProjectServiceRule")

  override fun before() {
    projectRule.project.registerOrReplaceServiceInstance(
      AdbLibService::class.java,
      TestAdbLibService(adbSession),
      disposable,
    )
  }

  override fun after() {
    Disposer.dispose(disposable)
  }
}

/** Replaces production [AdbLibService] with [TestAdbLibService] with a [FakeAdbSession]. */
class FakeAdbSessionRule(
  projectRule: ProjectRule,
) : TestAdbLibServiceRule<FakeAdbSession>(projectRule, FakeAdbSession()) {

  override fun after() {
    runBlocking { adbSession.closeAndJoin() }
    super.after()
  }
}
