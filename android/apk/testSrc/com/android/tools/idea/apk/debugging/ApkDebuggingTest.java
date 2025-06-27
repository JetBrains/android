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
package com.android.tools.idea.apk.debugging;

import static com.android.tools.idea.apk.debugging.ApkDebugging.APK_DEBUGGING_PROPERTY;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.HeavyPlatformTestCase;

/**
 * Tests for {@link ApkDebugging}.
 */
public class ApkDebuggingTest extends HeavyPlatformTestCase {
  public void testMarkAsApkDebuggingProject() {
    Project project = getProject();
    assertFalse(ApkDebugging.isMarkedAsApkDebuggingProject(project));

    ApkDebugging.markAsApkDebuggingProject(project);

    boolean marked = PropertiesComponent.getInstance(project).getBoolean(APK_DEBUGGING_PROPERTY, false);
    assertTrue(marked);

    assertTrue(ApkDebugging.isMarkedAsApkDebuggingProject(project));
  }
}