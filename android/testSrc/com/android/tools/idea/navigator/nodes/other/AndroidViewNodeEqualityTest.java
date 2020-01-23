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
package com.android.tools.idea.navigator.nodes.other;

import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.navigator.nodes.android.AndroidModuleNode;
import com.android.tools.idea.navigator.nodes.ndk.NdkModuleNode;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import org.jetbrains.android.AndroidTestCase;

import static org.mockito.Mockito.mock;

/**
 * Equality relationship tests for Android-specific subclasses of
 * {@link com.intellij.ide.projectView.impl.nodes.ProjectViewModuleNode}.
 */
public class AndroidViewNodeEqualityTest extends AndroidTestCase {
  public void testModuleNodeEquality() {
    ViewSettings viewSettings = mock(ViewSettings.class);
    AndroidProjectViewPane projectViewPane = mock(AndroidProjectViewPane.class);
    ProjectViewNode<?> androidModuleNode = new AndroidModuleNode(getProject(), myModule, projectViewPane, viewSettings);
    ProjectViewNode<?> nonAndroidModuleNode = new NonAndroidModuleNode(getProject(), myModule, projectViewPane, viewSettings);
    ProjectViewNode<?> ndkModuleNode = new NdkModuleNode(getProject(), myModule, projectViewPane, viewSettings);
    // Do not attempt fixing https://issuetracker.google.com/70635980 by breaking equality semantics. Different node types return different
    // children and are not interchangeable. Any issues with module type detection should be resolved at the place where the incorrect
    // module type is inferred.
    assertFalse(androidModuleNode.equals(nonAndroidModuleNode));
    assertFalse(nonAndroidModuleNode.equals(androidModuleNode));
    assertFalse(androidModuleNode.equals(ndkModuleNode));
    assertFalse(ndkModuleNode.equals(androidModuleNode));
    assertFalse(nonAndroidModuleNode.equals(ndkModuleNode));
    assertFalse(ndkModuleNode.equals(nonAndroidModuleNode));
    ProjectViewNode<?> nonAndroidSourceTypeNode =
        new NonAndroidSourceTypeNode(getProject(), myModule, viewSettings, NonAndroidSourceType.JAVA);
    // Check inequality related to https://issuetracker.google.com/37003106.
    assertFalse(androidModuleNode.equals(nonAndroidSourceTypeNode));
    assertFalse(nonAndroidSourceTypeNode.equals(androidModuleNode));
    assertFalse(nonAndroidModuleNode.equals(nonAndroidSourceTypeNode));
    assertFalse(nonAndroidSourceTypeNode.equals(nonAndroidModuleNode));
    assertFalse(ndkModuleNode.equals(nonAndroidSourceTypeNode));
    assertFalse(nonAndroidSourceTypeNode.equals(ndkModuleNode));
  }
}