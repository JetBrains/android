/*
 * Copyright (C) 2017 The Android Open Source Project
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

import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * Base class for [TestRule]s that need to know the name of their own test during setup.
 * This is a replacement for [ExternalResource] that provides access to the [Description]
 * object.
 */
abstract class NamedExternalResource : TestRule {
  final override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      @Throws(Throwable::class)
      override fun evaluate() {
        // Unlike ExternalResource, we make sure that exceptions thrown from after() do not hide exceptions thrown from base.evaluate().
        val errors = mutableListOf<Throwable>()
        before(description)
        try {
          base.evaluate()
        }
        catch (e: Throwable) {
          errors.add(e)
        }
        try {
          after(description)
        }
        catch (e: Throwable) {
          errors.add(e)
        }
        MultipleFailureException.assertEmpty(errors)
      }
    }
  }

  @Throws(Throwable::class)
  abstract fun before(description: Description)
  abstract fun after(description: Description)
}