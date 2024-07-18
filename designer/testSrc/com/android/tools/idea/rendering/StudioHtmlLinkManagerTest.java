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
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.rendering.HtmlLinkManager;
import com.google.common.collect.ImmutableList;
import com.intellij.mock.MockPsiFile;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class StudioHtmlLinkManagerTest extends LayoutTestCase {
  public void testAction() {
    StudioHtmlLinkManager manager = new StudioHtmlLinkManager();
    final AtomicBoolean result1 = new AtomicBoolean(false);
    final AtomicBoolean result2 = new AtomicBoolean(false);
    HtmlLinkManager.Action runnable1 = (module) -> result1.set(true);
    HtmlLinkManager.Action runnable2 = (module) -> result2.set(true);
    String url1 = manager.createActionLink(runnable1);
    String url2 = manager.createActionLink(runnable2);
    assertFalse(result1.get());
    manager.handleUrl(url1, null, new MockPsiFile(myFixture.getPsiManager()), false, HtmlLinkManager.NOOP_SURFACE);
    assertTrue(result1.get());
    assertFalse(result2.get());
    result1.set(false);
    manager.handleUrl(url2, null, new MockPsiFile(myFixture.getPsiManager()), false, HtmlLinkManager.NOOP_SURFACE);
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

    // try multiple invalid links
    StudioHtmlLinkManager.handleAddDependency("addDependency:", myModule);
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support", myModule);
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support:", myModule);
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.google.android.gms:palette-v7", myModule);
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support:palette-v7-broken", myModule);
    assertThat(testProjectSystem.getAddedDependencies(myModule)).isEmpty();

    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support:palette-v7", myModule);
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.google.android.gms:play-services", myModule);
    StudioHtmlLinkManager.handleAddDependency("addDependency:com.android.support.constraint:constraint-layout", myModule);
    StudioHtmlLinkManager.handleAddDependency("addDebugDependency:com.google.android:flexbox", myModule);
    assertThat(
      testProjectSystem.getAddedDependencies(myModule).stream()
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

  // Regression test for b/315080316. It should fail when reverting ag/25616101 or regressing in any other way.
  public void testOpenStackUrl() {
    myFixture.setTestDataPath(LayoutTestCase.getDesignerPluginHome() + "/testData/linkmanager");
    // Add both .class and source files for the target class
    myFixture.copyFileToProject(
      "MyClass.class", "src/com/google/example/MyClass.class"
    );
    myFixture.copyFileToProject(
      "MyClass.kt", "src/com/google/example/MyClass.kt"
    );
    assertThat(FileEditorManager.getInstance(getProject()).getSelectedEditor()).isNull();
    String url = "open:com.google.example.MyClass#myMethod;MyClass.kt";
    StudioHtmlLinkManager.handleOpenStackUrl(url, myModule);
    FileEditor selectedEditor = FileEditorManager.getInstance(getProject()).getSelectedEditor();
    assertThat(selectedEditor).isNotNull();
    // We should always navigate to the source file when it's available
    assertThat(selectedEditor.getFile().getName()).isEqualTo("MyClass.kt");
  }
}
