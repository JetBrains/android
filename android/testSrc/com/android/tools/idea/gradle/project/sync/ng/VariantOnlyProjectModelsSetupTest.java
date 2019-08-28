/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.NativeVariantAbi;
import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.NdkVariant;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels.VariantOnlyModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels.VariantOnlyModuleModel.NativeVariantAbiModel;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModelsSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.android.AndroidVariantChangeModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkVariantChangeModuleSetup;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.InOrder;
import org.mockito.Mock;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link VariantOnlyProjectModelsSetup}.
 */
public class VariantOnlyProjectModelsSetupTest extends JavaProjectTestCase {
  @Mock private IdeModifiableModelsProvider myModelsProvider;
  @Mock private CachedProjectModels myCachedProjectModels;
  @Mock private ModuleSetupContext.Factory myContextFactory;
  @Mock private VariantOnlyProjectModels myProjectModels;
  @Mock private VariantOnlyModuleModel myModuleModels;
  @Mock private CachedProjectModels.Loader myCacheLoader;
  @Mock private AndroidVariantChangeModuleSetup myAndroidModuleSetup;
  @Mock private NdkVariantChangeModuleSetup myNdkModuleSetup;
  @Mock private ProjectStructure myProjectStructure;
  @Mock private ModuleFinder myFinder;
  @Mock private AndroidModuleModel myAndroidModuleModel;
  @Mock private NdkModuleModel myNdkModuleModel;
  @Mock private IdeDependenciesFactory myDependenciesFactory;

  private VariantOnlyProjectModelsSetup myVariantOnlyModelsSetup;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    ServiceContainerUtil
      .replaceService(myProject, ProjectStructure.class, myProjectStructure, getTestRootDisposable());

    myVariantOnlyModelsSetup =
      new VariantOnlyProjectModelsSetup(getProject(), myModelsProvider, myContextFactory, myDependenciesFactory, myCacheLoader,
                                        myAndroidModuleSetup, myNdkModuleSetup);
  }

  public void testSetUpModules() {
    // Create cached project model that contains two modules, app and java.
    setupCachedProjectModels();
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();

    // Create app module.
    String moduleId = "project::app";
    Module appModule = createAppModuleWithFacet();
    when(myProjectStructure.getModuleFinder()).thenReturn(myFinder);
    when(myFinder.findModuleByModuleId(moduleId)).thenReturn(appModule);

    // Setup VariantOnlySyncProjectModels.
    Variant variant = mock(Variant.class);
    when(myProjectModels.getModuleModels()).thenReturn(singletonList(myModuleModels));
    when(myModuleModels.getModuleId()).thenReturn(moduleId);
    when(myModuleModels.getVariants()).thenReturn(singletonList(variant));
    when(variant.getName()).thenReturn("release");

    ModuleSetupContext appModuleContext = mock(ModuleSetupContext.class);
    when(myContextFactory.create(appModule, myModelsProvider)).thenReturn(appModuleContext);

    // Invoke the method to test.
    myVariantOnlyModelsSetup.setUpModules(myProjectModels, indicator);

    // Verify the variant-only model was added to androidModuleModel.
    verify(myAndroidModuleModel).addVariantOnlyModuleModel(myModuleModels, myDependenciesFactory);
    // Verify the selected variant is updated.
    verify(myAndroidModuleModel).setSelectedVariantName("release");
    // Verify that the module setup steps were invoked.
    verify(myAndroidModuleSetup).setUpModule(appModuleContext, myAndroidModuleModel);
    // Verify cache is updated for app module, and unchanged for java module.
    verify(myCachedProjectModels.findCacheForModule("app")).addModel(myAndroidModuleModel);
    verify(myCachedProjectModels).saveToDisk(myProject);
    assertNotNull(myCachedProjectModels.findCacheForModule("java"));
    verify(myCachedProjectModels.findCacheForModule("java"), never()).addModel(any());
  }

  public void testSetUpModulesWithNdkModule() {
    // Create cached project model that contains two modules, app and java.
    setupCachedProjectModels();
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();

    // Create app module.
    String moduleId = "project::app";
    NdkVariant ndkVariant = mock(NdkVariant.class);
    when(ndkVariant.getName()).thenReturn("debug-x86");
    when(myNdkModuleModel.getSelectedVariant()).thenReturn(ndkVariant);
    Module appModule = createAppModuleWithNdkFacet();
    when(myProjectStructure.getModuleFinder()).thenReturn(myFinder);
    when(myFinder.findModuleByModuleId(moduleId)).thenReturn(appModule);

    // Setup VariantOnlySyncProjectModels.
    NativeVariantAbi variantAbi = mock(NativeVariantAbi.class);
    when(myProjectModels.getModuleModels()).thenReturn(singletonList(myModuleModels));
    when(myModuleModels.getModuleId()).thenReturn(moduleId);
    NativeVariantAbiModel abiModel = new NativeVariantAbiModel("release-x86", variantAbi);
    when(myModuleModels.getNativeVariantAbi()).thenReturn(abiModel);
    Variant variant = mock(Variant.class);
    when(myModuleModels.getVariants()).thenReturn(singletonList(variant));
    when(variant.getName()).thenReturn("release");

    ModuleSetupContext appModuleContext = mock(ModuleSetupContext.class);
    when(myContextFactory.create(appModule, myModelsProvider)).thenReturn(appModuleContext);

    // Invoke the method to test.
    myVariantOnlyModelsSetup.setUpModules(myProjectModels, indicator);

    // Verify the variant-only model was added to androidModuleModel.
    verify(myNdkModuleModel).addVariantOnlyModuleModel(any());
    // Verify the selected variant is updated.
    verify(myNdkModuleModel).setSelectedVariantName("release-x86");
    // Verify that ndk module setup steps were invoked before android module setup steps.
    InOrder inOrder = inOrder(myNdkModuleSetup, myAndroidModuleSetup);
    inOrder.verify(myNdkModuleSetup).setUpModule(appModuleContext, myNdkModuleModel);
    inOrder.verify(myAndroidModuleSetup).setUpModule(appModuleContext, myAndroidModuleModel);
    // Verify cache is updated for app module, and unchanged for java module.
    verify(myCachedProjectModels.findCacheForModule("app")).addModel(myNdkModuleModel);
    verify(myCachedProjectModels).saveToDisk(myProject);
    assertNotNull(myCachedProjectModels.findCacheForModule("java"));
    verify(myCachedProjectModels.findCacheForModule("java"), never()).addModel(any());
  }

  @NotNull
  private Module createAppModuleWithFacet() {
    Module module = createModule("app");
    FacetManager facetManager = FacetManager.getInstance(module);
    AndroidFacet facet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
    facet.getConfiguration().setModel(myAndroidModuleModel);
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableFacetModel model = facetManager.createModifiableModel();
      model.addFacet(facet);
      model.commit();
    });
    return module;
  }

  @NotNull
  private Module createAppModuleWithNdkFacet() {
    Module module = createModule("app");
    FacetManager facetManager = FacetManager.getInstance(module);
    NdkFacet ndkFacet = facetManager.createFacet(NdkFacet.getFacetType(), NdkFacet.getFacetName(), null);
    ndkFacet.setNdkModuleModel(myNdkModuleModel);
    // Ndk module also contains AndroidFacet.
    AndroidFacet androidFacet = facetManager.createFacet(AndroidFacet.getFacetType(), AndroidFacet.NAME, null);
    androidFacet.getConfiguration().setModel(myAndroidModuleModel);
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableFacetModel model = facetManager.createModifiableModel();
      model.addFacet(ndkFacet);
      model.addFacet(androidFacet);
      model.commit();
    });
    return module;
  }

  private void setupCachedProjectModels() {
    // create "app" module
    CachedModuleModels cachedAppModels = mock(CachedModuleModels.class);
    when(myCachedProjectModels.findCacheForModule("app")).thenReturn(cachedAppModels);
    when(cachedAppModels.findModel(AndroidModuleModel.class)).thenReturn(myAndroidModuleModel);

    // create "java" module
    when(myCachedProjectModels.findCacheForModule("java")).thenReturn(mock(CachedModuleModels.class));

    when(myCacheLoader.loadFromDisk(myProject)).thenReturn(myCachedProjectModels);
  }
}
