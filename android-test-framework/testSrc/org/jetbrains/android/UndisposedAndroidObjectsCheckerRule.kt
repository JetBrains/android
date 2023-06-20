/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android

import com.android.tools.idea.testing.DisposerExplorer
import com.android.tools.idea.testing.NamedExternalResource
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiReferenceContributor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.util.containers.ContainerUtil
import org.junit.runner.Description

class UndisposedAndroidObjectsCheckerRule : NamedExternalResource() {

  override fun before(description: Description) {
  }

  override fun after(description: Description) {
    checkUndisposedAndroidRelatedObjects()
  }

  companion object {
    // Keeps track of each leaked disposable so that we can fail just the *first* test that leaks it.
    @JvmStatic
    private val allLeakedDisposables = ContainerUtil.createWeakSet<Disposable>()

    /**
     * Checks that there are no undisposed Android-related objects.
     */
    @JvmStatic
    fun checkUndisposedAndroidRelatedObjects() {
      val firstLeak = Ref<Disposable>()
      DisposerExplorer.visitTree { disposable: Disposable ->
        if (allLeakedDisposables.contains(disposable) ||
            disposable.javaClass.name.startsWith("com.android.tools.analytics.HighlightingStats") ||
            disposable is ProjectImpl && (disposable.isDefault || disposable.isLight) ||
            disposable.toString().startsWith("services of ") ||
            disposable is Module && disposable.name == LightProjectDescriptor.TEST_MODULE_NAME ||
            disposable is PsiReferenceContributor) {
          // Ignore application services and light projects and modules that are not disposed by tearDown.
          return@visitTree DisposerExplorer.VisitResult.SKIP_CHILDREN
        }
        if (disposable.javaClass.name.startsWith("com.android.") ||
            disposable.javaClass.name.startsWith("org.jetbrains.android.")) {
          firstLeak.setIfNull(disposable)
          allLeakedDisposables.add(disposable)
        }
        DisposerExplorer.VisitResult.CONTINUE
      }
      if (!firstLeak.isNull) {
        val disposable = firstLeak.get()
        val parent = DisposerExplorer.getParent(disposable)
        val baseMsg = "Undisposed object '" + disposable + "' of type '" + disposable.javaClass.name + "'"
        if (parent == null) {
          throw RuntimeException("$baseMsg, registered as a root disposable (see cause for creation trace)",
                                 DisposerExplorer.getTrace(disposable))
        }
        else {
          throw RuntimeException(baseMsg + ", with parent '" + parent + "' of type '" + parent.javaClass.name + "'")
        }
      }
    }
  }
}