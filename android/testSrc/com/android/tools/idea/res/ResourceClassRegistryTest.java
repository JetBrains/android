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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.android.AndroidFacetProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.SdkConstants.DOT_AAR;
import static org.mockito.Mockito.*;

public class ResourceClassRegistryTest extends LightCodeInsightFixtureTestCase {
  ResourceClassRegistry myRegistry;
  private ResourceIdManager myIdManager;

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return AndroidFacetProjectDescriptor.INSTANCE;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRegistry = spy(ResourceClassRegistry.get(getProject()));
    myIdManager = ResourceIdManager.get(myModule);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      myIdManager.resetDynamicIds();
    }
    finally {
      super.tearDown();
    }
  }

  public void testAddLibrary() {
    String pkg1 = "com.google.example1";
    String pkg2 = "com.google.example2";
    AppResourceRepository repository = AppResourceRepository.getOrCreateInstance(myModule);
    myRegistry.addLibrary(repository, myIdManager, pkg1, ResourceNamespace.fromPackageName(pkg1));
    assertSameElements(myRegistry.getGeneratorMap().keySet(), repository);
    assertSameElements(myRegistry.getPackages(), pkg1);
    myRegistry.addLibrary(repository, myIdManager, pkg2, ResourceNamespace.fromPackageName(pkg2));
    assertSameElements(myRegistry.getGeneratorMap().keySet(), repository);
    assertSameElements(myRegistry.getPackages(), pkg1, pkg2);
  }

  public void testAarAddLibrary() {
    String pkg1 = "com.google.example.aar1";
    File aarDir1 = new File("/exploded-aar1");
    String pkg2 = "com.google.example.aar2";
    File aarDir2 = new File("/exploded-aar2");
    AppResourceRepository appRepository = mock(AppResourceRepository.class);
    FileResourceRepository fileRepository = mock(FileResourceRepository.class);
    when(appRepository.findRepositoryFor(aarDir1)).thenReturn(fileRepository);
    when(appRepository.findRepositoryFor(aarDir2)).thenReturn(fileRepository);
    addAarLibrary(myRegistry, appRepository, myIdManager, aarDir1, pkg1);
    assertSameElements(myRegistry.getGeneratorMap().keySet(), appRepository);
    assertSameElements(myRegistry.getPackages(), pkg1);
    addAarLibrary(myRegistry, appRepository, myIdManager, aarDir2, pkg2);
    assertSameElements(myRegistry.getGeneratorMap().keySet(), appRepository);
    assertSameElements(myRegistry.getPackages(), pkg1, pkg2);
  }

  public void testAddBothLibrary() {
    String pkg1 = "com.google.example.aar";
    File aarDir = new File("/exploded-aar");
    String pkg2 = "com.google.example";
    AppResourceRepository appRepository = mock(AppResourceRepository.class);
    FileResourceRepository fileRepository = mock(FileResourceRepository.class);
    when(appRepository.findRepositoryFor(aarDir)).thenReturn(fileRepository);
    addAarLibrary(myRegistry, appRepository, myIdManager, aarDir, pkg1);
    assertSameElements(myRegistry.getGeneratorMap().keySet(), appRepository);
    assertSameElements(myRegistry.getPackages(), pkg1);
    myRegistry.addLibrary(appRepository, myIdManager, pkg2, ResourceNamespace.fromPackageName(pkg2));
    assertSameElements(myRegistry.getGeneratorMap().keySet(), appRepository);
    assertSameElements(myRegistry.getPackages(), pkg1, pkg2);
  }

  private static void addAarLibrary(@NotNull ResourceClassRegistry registry,
                                    @NotNull AppResourceRepository appResources,
                                    @NotNull ResourceIdManager idManager,
                                    @NotNull File aarDir,
                                    @NotNull String packageName) {
    String path = aarDir.getPath();
    if (path.endsWith(DOT_AAR) || path.contains(FilenameConstants.EXPLODED_AAR)) {
      FileResourceRepository repository = appResources.findRepositoryFor(aarDir);
      if (repository != null) {
        registry.addLibrary(appResources, idManager, packageName, ResourceNamespace.fromPackageName(packageName));
      }
    }
  }
}
