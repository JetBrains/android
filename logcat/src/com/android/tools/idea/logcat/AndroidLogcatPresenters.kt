// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.logcat

import com.intellij.openapi.components.Service

@Service(Service.Level.APP)
class AndroidLogcatPresenters {
  internal val logcatPresenters = mutableListOf<LogcatPresenter>()
}