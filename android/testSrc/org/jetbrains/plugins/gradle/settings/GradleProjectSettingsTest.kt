/*
 * Copyright (C) 2020 The Android Open Source Project
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
package org.jetbrains.plugins.gradle.settings

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK

/**
Tests for [GradleProjectSettings].
 **/
class GradleProjectSettingsTest: AndroidGradleTestCase() {
    /**
     * Android Studio does not have a way to change Gradle JVM in the Gradle dialog, we use the PSD and thus this setting should be
     * reflected by using USE_PROJECT_JDK.
     */
    fun testGradleJvmIsUseProjectJdk() {
        loadSimpleApplication()
        val settings = GradleSettings.getInstance(project)
        val projectSettings = settings.linkedProjectsSettings
        assertThat(projectSettings).isNotEmpty()
        assertThat(projectSettings.map { it.gradleJvm }).containsExactly(USE_PROJECT_JDK)
    }
}
