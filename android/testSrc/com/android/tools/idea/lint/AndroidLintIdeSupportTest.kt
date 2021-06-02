// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.lint

import com.android.tools.idea.lint.common.LintIdeSupport
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.HeavyPlatformTestCase
import junit.framework.TestCase

class AndroidLintIdeSupportTest : HeavyPlatformTestCase() {
  fun testCyclicExtensionInitialization() {
    val epName = ExtensionPointName.create<LintIdeSupport>("com.android.tools.idea.lint.common.lintIdeSupport")
    val inst = epName.extensionList;
    TestCase.assertNotNull(inst)
    TestCase.assertEquals(1, inst.size)
  }
}