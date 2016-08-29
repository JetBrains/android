/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui;

import com.android.tools.idea.tests.gui.apiintegration.DeveloperServicesTest;
import com.android.tools.idea.tests.gui.avdmanager.AvdListDialogTest;
import com.android.tools.idea.tests.gui.editors.ApkViewerTest;
import com.android.tools.idea.tests.gui.emulator.LaunchAndroidApplicationTest;
import com.android.tools.idea.tests.gui.gradle.GradleBuildTest;
import com.android.tools.idea.tests.gui.gradle.flavors.CreateNewFlavorsTest;
import com.android.tools.idea.tests.gui.gradle.flavors.FlavorsExecutionTest;
import com.android.tools.idea.tests.gui.gradle.BuildTypesTest;
import com.android.tools.idea.tests.gui.gradle.GradleSyncTest;
import com.android.tools.idea.tests.gui.gradle.NewModuleTest;
import com.android.tools.idea.tests.gui.instantrun.InstantRunTest;
import com.android.tools.idea.tests.gui.layout.LayoutInspectorTest;
import com.android.tools.idea.tests.gui.layout.NewProjectTest;
import com.android.tools.idea.tests.gui.layout.NlEditorTest;
import com.android.tools.idea.tests.gui.npw.NewActivityTest;
import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Categories.class)
@Categories.IncludeCategory(GuiSanityTestSuite.class)
@Suite.SuiteClasses({
  ApkViewerTest.class,
  AvdListDialogTest.class,
  BuildTypesTest.class,
  CreateNewFlavorsTest.class,
  DeveloperServicesTest.class,
  FlavorsExecutionTest.class,
  GradleBuildTest.class,
  GradleSyncTest.class,
  InstantRunTest.class,
  LaunchAndroidApplicationTest.class,
  LayoutInspectorTest.class,
  NewActivityTest.class,
  NewModuleTest.class,
  NewProjectTest.class,
  NlEditorTest.class,
})
public class GuiSanityTestSuite {
}
