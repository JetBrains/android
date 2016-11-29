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

import com.intellij.testFramework.IdeaTestCase;

import java.io.File;

import static org.mockito.Mockito.*;

public class ResourceClassRegistryTest extends IdeaTestCase {
  ResourceClassRegistry myRegistry;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRegistry = spy(ResourceClassRegistry.get(getProject()));
  }

  public void testAddLibrary() {
    String pkg1 = "com.google.example1";
    String pkg2 = "com.google.example2";
    AppResourceRepository repository = mock(AppResourceRepository.class);
    myRegistry.addLibrary(repository, pkg1);
    assertSameElements(myRegistry.getGeneratorMap().keySet(), repository);
    assertSameElements(myRegistry.getPackages(), pkg1);
    myRegistry.addLibrary(repository, pkg2);
    assertSameElements(myRegistry.getGeneratorMap().keySet(), repository);
    assertSameElements(myRegistry.getPackages(), pkg1, pkg2);
  }

  public void testAarAddLibrary() {
    String pkg1 = "com.google.example.aar1";
    File aarDir1 = new File("/exploded-aar1");
    doReturn(pkg1).when(myRegistry).getAarPackage(aarDir1);
    String pkg2 = "com.google.example.aar2";
    File aarDir2 = new File("/exploded-aar2");
    doReturn(pkg2).when(myRegistry).getAarPackage(aarDir2);
    AppResourceRepository appRepository = mock(AppResourceRepository.class);
    FileResourceRepository fileRepository = mock(FileResourceRepository.class);
    when(appRepository.findRepositoryFor(aarDir1)).thenReturn(fileRepository);
    when(appRepository.findRepositoryFor(aarDir2)).thenReturn(fileRepository);
    myRegistry.addAarLibrary(appRepository, aarDir1);
    assertSameElements(myRegistry.getGeneratorMap().keySet(), appRepository);
    assertSameElements(myRegistry.getPackages(), pkg1);
    myRegistry.addAarLibrary(appRepository, aarDir2);
    assertSameElements(myRegistry.getGeneratorMap().keySet(), appRepository);
    assertSameElements(myRegistry.getPackages(), pkg1, pkg2);
  }

  public void testAddBothLibrary() {
    String pkg1 = "com.google.example.aar";
    File aarDir = new File("/exploded-aar");
    doReturn(pkg1).when(myRegistry).getAarPackage(aarDir);
    String pkg2 = "com.google.example";
    AppResourceRepository appRepository = mock(AppResourceRepository.class);
    FileResourceRepository fileRepository = mock(FileResourceRepository.class);
    when(appRepository.findRepositoryFor(aarDir)).thenReturn(fileRepository);
    myRegistry.addAarLibrary(appRepository, aarDir);
    assertSameElements(myRegistry.getGeneratorMap().keySet(), appRepository);
    assertSameElements(myRegistry.getPackages(), pkg1);
    myRegistry.addLibrary(appRepository, pkg2);
    assertSameElements(myRegistry.getGeneratorMap().keySet(), appRepository);
    assertSameElements(myRegistry.getPackages(), pkg1, pkg2);
  }
}
