/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.projectsystem.TestProjectSystem;
import com.android.tools.idea.projectsystem.TestRepositories;
import com.android.tools.rendering.HtmlLinkManager;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.testFramework.LightPlatformTestCase;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class StudioHtmlLinkManagerTest extends LightPlatformTestCase {
  public void testRunnable() {
    StudioHtmlLinkManager manager = new StudioHtmlLinkManager();
    final AtomicBoolean result1 = new AtomicBoolean(false);
    final AtomicBoolean result2 = new AtomicBoolean(false);
    Runnable runnable1 = () -> result1.set(true);
    Runnable runnable2 = () -> result2.set(true);
    String url1 = manager.createRunnableLink(runnable1);
    String url2 = manager.createRunnableLink(runnable2);
    assertFalse(result1.get());
    manager.handleUrl(url1, null, null, false, HtmlLinkManager.NOOP_SURFACE);
    assertTrue(result1.get());
    assertFalse(result2.get());
    result1.set(false);
    manager.handleUrl(url2, null, null, false, HtmlLinkManager.NOOP_SURFACE);
    assertFalse(result1.get());
    assertTrue(result2.get());
  }

  public void testHandleAddDependency() {
    List<GradleCoordinate> accessibleDependencies = new ImmutableList.Builder<GradleCoordinate>()
      .addAll(TestRepositories.GOOGLE_PLAY_SERVICES)
      .addAll(TestRepositories.NON_PLATFORM_SUPPORT_LAYOUT_LIBS)
      .addAll(TestRepositories.PLATFORM_SUPPORT_LIBS)
      .build();
    TestProjectSystem testProjectSystem = new TestProjectSystem(getProject(), accessibleDependencies);
    testProjectSystem.useInTests();

    final String[] dialogMessage = new String[1]; // Making it an array to be accessible from an inner class.
    TestDialog testDialog = message -> {
      dialogMessage[0] = message.trim(); // Remove line break in the end of the message.
      return Messages.OK;
    };
    TestDialogManager.setTestDialog(testDialog);

    // try multiple invalid links
    StudioHtmlLinkManager.handleAddDependency("addDependency:", getModule());
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support", getModule());
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support:", getModule());
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.google.android.gms:palette-v7", getModule());
    assertThat(dialogMessage[0]).isEqualTo("Can't find com.google.android.gms:palette-v7:+");
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support:palette-v7-broken", getModule());
    assertThat(dialogMessage[0]).isEqualTo("Can't find com.android.support:palette-v7-broken:+");
    assertThat(testProjectSystem.getAddedDependencies(getModule())).isEmpty();

    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support:palette-v7", getModule());
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.google.android.gms:play-services", getModule());
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support.constraint:constraint-layout", getModule());
    StudioHtmlLinkManager.handleAddDependency("addDebugDependency:com.google.android:flexbox", getModule());
    assertThat(
      testProjectSystem.getAddedDependencies(getModule()).stream()
        .map(dependency ->
               dependency.getType()
               + "("
               + dependency.getCoordinate().getGroupId() + ":" + dependency.getCoordinate().getArtifactId()
               + ")")
        .collect(Collectors.toList()))
      .containsExactly("IMPLEMENTATION(com.android.support:palette-v7)",
                       "IMPLEMENTATION(com.google.android.gms:play-services)",
                       "IMPLEMENTATION(com.android.support.constraint:constraint-layout)",
                       "DEBUG_IMPLEMENTATION(com.google.android:flexbox)");
  }
}
