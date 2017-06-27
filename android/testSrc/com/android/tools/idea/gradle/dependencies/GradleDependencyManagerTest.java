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
package com.android.tools.idea.gradle.dependencies;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.LEANBACK_V17_ARTIFACT;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APP_WITH_OLDER_SUPPORT_LIB;
import static com.android.tools.idea.testing.TestProjectPaths.SPLIT_BUILD_FILES;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleDependencyManager}.
 */
public class GradleDependencyManagerTest extends AndroidGradleTestCase {
  private static final GradleCoordinate APP_COMPAT_DEPENDENCY = new GradleCoordinate("com.android.support", "appcompat-v7", "+");
  private static final GradleCoordinate RECYCLER_VIEW_DEPENDENCY = new GradleCoordinate("com.android.support", "recyclerview-v7", "+");
  private static final GradleCoordinate DUMMY_DEPENDENCY = new GradleCoordinate("dummy.group", "dummy.artifact", "0.0.0");

  private static final List<GradleCoordinate> DEPENDENCIES = ImmutableList.of(APP_COMPAT_DEPENDENCY, DUMMY_DEPENDENCY);

  public void testDependsOn() throws Exception {
    loadSimpleApplication();
    GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(getProject());
    Module appModule = myModules.getAppModule();
    assertTrue(dependencyManager.dependsOn(appModule, APPCOMPAT_LIB_ARTIFACT));
    assertFalse(dependencyManager.dependsOn(appModule, LEANBACK_V17_ARTIFACT));
  }

  public void testFindMissingDependenciesWithRegularProject() throws Exception {
    loadSimpleApplication();
    GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(getProject());
    List<GradleCoordinate> missingDependencies = dependencyManager.findMissingDependencies(myModules.getAppModule(), DEPENDENCIES);
    assertThat(missingDependencies).containsExactly(DUMMY_DEPENDENCY);
  }

  public void testFindMissingDependenciesInProjectWithSplitBuildFiles() throws Exception {
    loadProject(SPLIT_BUILD_FILES);
    GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(getProject());
    List<GradleCoordinate> missingDependencies = dependencyManager.findMissingDependencies(myModules.getAppModule(), DEPENDENCIES);
    assertThat(missingDependencies).containsExactly(DUMMY_DEPENDENCY);
  }

  @SuppressWarnings("unused")
  public void ignore_testDependencyAarIsExplodedForLayoutLib() throws Exception {
    loadSimpleApplication();

    List<GradleCoordinate> dependencies = Collections.singletonList(RECYCLER_VIEW_DEPENDENCY);
    GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(getProject());
    assertThat(dependencyManager.findMissingDependencies(myModules.getAppModule(), dependencies)).isNotEmpty();

    Messages.setTestDialog(new TestMessagesDialog(Messages.OK));

    boolean found = dependencyManager.ensureLibraryIsIncluded(myModules.getAppModule(), dependencies, null);
    assertTrue(found);

    // @formatter:off
    List<ResourceItem> items = AppResourceRepository.getOrCreateInstance(myAndroidFacet)
                                                    .getResourceItem(ResourceType.DECLARE_STYLEABLE, "RecyclerView");
    // @formatter:on
    assertThat(items).isNotEmpty();
    assertThat(dependencyManager.findMissingDependencies(myModules.getAppModule(), dependencies)).isEmpty();
  }

  public void testDependencyCanBeCancelledByUser() throws Exception {
    loadSimpleApplication();

    List<GradleCoordinate> dependencies = Collections.singletonList(RECYCLER_VIEW_DEPENDENCY);
    GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(getProject());
    assertThat(dependencyManager.findMissingDependencies(myModules.getAppModule(), dependencies)).isNotEmpty();

    Messages.setTestDialog(new TestMessagesDialog(Messages.NO));

    boolean found = dependencyManager.ensureLibraryIsIncluded(myModules.getAppModule(), dependencies, null);
    assertFalse(found);
    assertThat(dependencyManager.findMissingDependencies(myModules.getAppModule(), dependencies)).isNotEmpty();
  }

  public void testAddedSupportDependencyIsSameVersionAsExistingSupportDependency() throws Exception {
    // Load a library with an explicit appcompat-v7 version that is older than the most recent version:
    loadProject(SIMPLE_APP_WITH_OLDER_SUPPORT_LIB);

    List<GradleCoordinate> dependencies = ImmutableList.of(APP_COMPAT_DEPENDENCY, RECYCLER_VIEW_DEPENDENCY);
    GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(getProject());
    List<GradleCoordinate> missing = dependencyManager.findMissingDependencies(myModules.getAppModule(), dependencies);
    assertThat(missing.size()).isEqualTo(1);
    assertThat(missing.get(0).getId()).isEqualTo(SdkConstants.RECYCLER_VIEW_LIB_ARTIFACT);
    assertThat(missing.get(0).toString()).isEqualTo("com.android.support:recyclerview-v7:25.3.1");
  }
}
