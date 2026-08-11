// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0
// license that can be found in the LICENSE file.
package com.android.tools.idea.testing

import com.intellij.testFramework.TemporaryDirectory

@Deprecated(
  message = "Use com.intellij.testFramework.TemporaryDirectory instead",
  replaceWith = ReplaceWith("TemporaryDirectory", "com.intellij.testFramework.TemporaryDirectory"),
)
class TemporaryDirectoryRule : TemporaryDirectory()
