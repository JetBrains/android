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
package com.android.tools.idea.gradle.service;

import com.android.tools.idea.gradle.AndroidProjectKeys;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.sdk.Jdks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static org.easymock.EasyMock.*;

/**
 * Tests for {@link AndroidGradleModelDataService}.
 */
public class AndroidGradleModelDataServiceTest extends IdeaTestCase {
  private static final String DEBUG = "debug";

  private AndroidProjectStub myAndroidProject;
  private AndroidGradleModel myAndroidModel;
  private ModuleCustomizer<AndroidGradleModel> myCustomizer1;
  private ModuleCustomizer<AndroidGradleModel> myCustomizer2;

  private AndroidGradleModelDataService service;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myAndroidProject = new AndroidProjectStub(myModule.getName());
    myAndroidProject.setModelVersion("1.0.0");
    myAndroidProject.addVariant(DEBUG);
    myAndroidProject.addBuildType(DEBUG);
    File rootDir = myAndroidProject.getRootDir();
    myAndroidModel =
      new AndroidGradleModel(GRADLE_SYSTEM_ID, myAndroidProject.getName(), rootDir, myAndroidProject, DEBUG, ARTIFACT_ANDROID_TEST);
    //noinspection unchecked
    myCustomizer1 = createMock(ModuleCustomizer.class);
    //noinspection unchecked
    myCustomizer2 = createMock(ModuleCustomizer.class);
    service = new AndroidGradleModelDataService(ImmutableList.of(myCustomizer1, myCustomizer2));
  }

  @Override
  protected void tearDown() throws Exception {
    if (myAndroidProject != null) {
      myAndroidProject.dispose();
    }
    super.tearDown();
  }

  @Override
  protected void checkForSettingsDamage(@NotNull List<Throwable> exceptions) {
    // for this test we don't care for this check
  }

  public void testImportData() {
    String jdkPath = Jdks.getJdkHomePath(LanguageLevel.JDK_1_6);

    if (jdkPath != null) {
      VfsRootAccess.allowRootAccess(jdkPath);
    }
    List<DataNode<AndroidGradleModel>> nodes = Lists.newArrayList();
    Key<AndroidGradleModel> key = AndroidProjectKeys.ANDROID_MODEL;
    nodes.add(new DataNode<AndroidGradleModel>(key, myAndroidModel, null));

    assertEquals(key, service.getTargetDataKey());

    final IdeModifiableModelsProviderImpl modelsProvider = new IdeModifiableModelsProviderImpl(myProject);
    // ModuleCustomizers should be called.
    //noinspection ConstantConditions
    myCustomizer1.customizeModule(eq(myProject), eq(myModule), eq(modelsProvider), eq(myAndroidModel));
    expectLastCall();

    //noinspection ConstantConditions
    myCustomizer2.customizeModule(eq(myProject), eq(myModule), eq(modelsProvider), eq(myAndroidModel));
    expectLastCall();

    replay(myCustomizer1, myCustomizer2);

    service.importData(nodes, null, myProject, modelsProvider);
    modelsProvider.commit();

    verify(myCustomizer1, myCustomizer2);
  }
}
