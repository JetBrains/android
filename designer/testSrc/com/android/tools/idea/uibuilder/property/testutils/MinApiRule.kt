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
package com.android.tools.idea.uibuilder.property.testutils

import com.android.SdkConstants
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.DEFAULT_MIN_API_LEVEL
import com.android.tools.idea.uibuilder.MOST_RECENT_API_LEVEL
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.rules.ExternalResource
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Rule that adds a manifest file and populates the api level based on the name of the test method.
 * The minSdkVersion can be specified by the test name: testXyzMinApi17 - will cause a manifest with
 * minSdkVersion set to 17. If the test name has no MinApi specified in the test name, the default
 * [DEFAULT_MIN_API_LEVEL] is used.
 */
class MinApiRule(private val projectRule: AndroidProjectRule) : ExternalResource() {
  private var testName: String? = null

  override fun apply(base: Statement, description: Description): Statement {
    testName = description.methodName
    return super.apply(base, description)
  }

  override fun before() {
    setUpManifest(projectRule.fixture, testName)
  }

  private fun setUpManifest(fixture: CodeInsightTestFixture, testName: String? = null) {
    val minApiAsString = testName?.substringAfter("MinApi", DEFAULT_MIN_API_LEVEL.toString())
    val minApi = minApiAsString?.toIntOrNull() ?: DEFAULT_MIN_API_LEVEL
    val manifest =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example">
              <uses-sdk android:minSdkVersion="$minApi"
                        android:targetSdkVersion="$MOST_RECENT_API_LEVEL"/>
      </manifest>
"""
        .trimIndent()
    fixture.addFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, manifest)
  }
}
