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
package com.android.tools.idea.testing.fixtures

import com.android.projectmodel.AndroidModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.android.AndroidTestCase

/**
 * JUnit3 superclass for android tests based on [AndroidModel]. Should work as a drop-in replacement for [AndroidTestCase] in simple cases.
 *
 * Extends [UsefulTestCase] to inherit threading rules.
 *
 * @see CommonModelFactories
 */
abstract class AndroidModelTestCase(
  private val modelFactory: ModelFactory,
  private val mode: ModelToTestProjectConverter.Mode
) : UsefulTestCase() {
  lateinit var fixture: JavaCodeInsightTestFixture

  // AndroidTestCase API compatibility:
  @JvmField var myFixture: JavaCodeInsightTestFixture? = null
  @JvmField var myModule: Module? = null
  val project: Project get() = fixture.project

  override fun setUp() {
    super.setUp()
    fixture = ModelToTestProjectConverter.convert(modelFactory, mode, name)
    myModule = fixture.module
  }

  override fun tearDown() {
    try {
      fixture.tearDown()
    }
    finally {
      super.tearDown()
    }
  }
}

