// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.dsl.parser.elements

class GradleDslDelegatedProperty(
  parent: GradleDslElement?,
  name: GradleNameElement,
) : GradlePropertiesDslElement(parent, null, name)