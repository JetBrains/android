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
package org.jetbrains.android.inspections.lint;

import com.android.tools.idea.lint.AndroidLintHardcodedTextInspection;
import com.android.tools.idea.lint.AndroidLintTypographyDashesInspection;
import com.google.common.base.Joiner;
import junit.framework.TestCase;

public class AndroidLintInspectionBaseTest extends TestCase {
  public void testGroup() {
    AndroidLintInspectionBase inspection;

    inspection = new AndroidLintHardcodedTextInspection();
    assertEquals("Android Lint: Internationalization", inspection.getGroupDisplayName());
    assertEquals("Android\nLint\nInternationalization", Joiner.on("\n").join(inspection.getGroupPath()));

    inspection = new AndroidLintTypographyDashesInspection();
    assertEquals("Android Lint: Usability", inspection.getGroupDisplayName());
    assertEquals("Android\nLint\nUsability\nTypography", Joiner.on("\n").join(inspection.getGroupPath()));
  }
}