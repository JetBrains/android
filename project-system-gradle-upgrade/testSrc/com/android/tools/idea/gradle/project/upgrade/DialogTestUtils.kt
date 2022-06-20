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
package com.android.tools.idea.gradle.project.upgrade

import com.android.tools.idea.testing.DisposerExplorer
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.HeavyPlatformTestCase

fun <T : DialogWrapper> HeavyPlatformTestCase.registerDialogDisposable(dialog: T): T =
  dialog.also { Disposer.register(testRootDisposable, dialog.disposable) }

/**
 * Our dialog tests often construct the dialog in order to inspect it or its effect on its constructor arguments, without then
 * interacting with the test in any way.  This leads to the dialog not being disposed, which in turn leads to the memory leak detector
 * in the wider test suite firing at the end of a bazel run (but *not* when tests are run from the IDE, nor in a way that is obvious
 * from presubmit reports).  Putting this function in the tearDown() method of a test class involving dialogs (after super.tearDown())
 * causes the test to fail if any dialogs are not disposed (for arranging disposal, consider using [registerDialogDisposable])
 */
internal fun checkNoUndisposedDialogs() {
  // DialogWrappers themselves are not disposable, but define an anonymous nested class to implement their Disposable needs.  We use this
  // class to detect whether a given disposable is associated with a DialogWrapper, but to do so we have to get hold of that class (and we
  // check that the class that we find is indeed a subclass of Disposable).
  val c = Class.forName("com.intellij.openapi.ui.DialogWrapper\$1")
  if (!Disposable::class.java.isAssignableFrom(c)) throw RuntimeException("DialogWrapper\$1 is not Disposable")
  DisposerExplorer.visitTree {
    if (c.isInstance(it)) throw RuntimeException("Found undisposed DialogWrapper Disposable: $it")
    DisposerExplorer.VisitResult.CONTINUE
  }
}