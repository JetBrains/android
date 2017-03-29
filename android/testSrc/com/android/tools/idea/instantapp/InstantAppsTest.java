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
package com.android.tools.idea.instantapp;

import com.android.tools.idea.testing.AndroidGradleTestCase;

import static com.android.tools.idea.instantapp.InstantApps.*;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;
import static com.android.tools.idea.testing.TestProjectPaths.MULTI_ATOM;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.intellij.openapi.util.io.FileUtil.join;

public class InstantAppsTest extends AndroidGradleTestCase {

  public void testFindBaseSplitWithInstantApp() throws Exception {
    loadProject(INSTANT_APP, "instant-app");
    assertEquals(myModules.getModule("baseatom"), findInstantAppBaseSplit(myAndroidFacet));
  }

  public void testFindBaseSplitWithoutInstantApp() throws Exception {
    loadProject(SIMPLE_APPLICATION, "app");
    assertNull(findInstantAppBaseSplit(myAndroidFacet));
  }

  public void testGetBaseSplitInInstantAppWithInstantApp() throws Exception {
    loadProject(INSTANT_APP, "baseatom");
    assertEquals(myAndroidFacet.getModule(), getBaseSplitInInstantApp(getProject()));
  }

  public void testFindInstantAppModuleWithInstantApp() throws Exception {
    loadProject(INSTANT_APP, "instant-app");
    assertEquals(myAndroidFacet.getModule(), findInstantAppModule(getProject()));
  }

  public void testFindInstantAppModuleWithoutInstantApp() throws Exception {
    loadProject(SIMPLE_APPLICATION);
    assertNull(findInstantAppModule(getProject()));
  }

  public void testGetInstantAppPackage() throws Exception {
    loadProject(INSTANT_APP, "baseatom");
    assertEquals("com.example.instantapp", getInstantAppPackage(myAndroidFacet.getModule()));
  }

  public void testGetBaseSplitOutDirSimple() throws Exception {
    loadProject(INSTANT_APP, "baseatom");
    assertEquals(join(getProjectFolderPath().getAbsolutePath(), "baseatom"), getBaseSplitOutDir(myAndroidFacet.getModule()));
  }

  public void testGetBaseSplitOutDirMultiAtom() throws Exception {
    loadProject(MULTI_ATOM, "baseatom");
    assertEquals(join(getProjectFolderPath().getAbsolutePath(), "baselib"), getBaseSplitOutDir(myAndroidFacet.getModule()));
  }

  public void testGetContainingSplitWithSplit() throws Exception {
    loadProject(INSTANT_APP, "baseatom");
    assertEquals(myAndroidFacet.getModule(), getContainingSplit(myAndroidFacet.getModule()));
  }

  public void testGetContainingSplitWithLibrary() throws Exception {
    loadProject(MULTI_ATOM, "baselib");
    assertEquals(myModules.getModule("baseatom"), getContainingSplit(myAndroidFacet.getModule()));
  }

  public void testGetContainingSplitWithoutInstantApp() throws Exception {
    loadProject(SIMPLE_APPLICATION, "app");
    assertNull(getContainingSplit(myAndroidFacet.getModule()));
  }


  public void testGetDefaultInstantAppUrlWithInstantApp() throws Exception {
    loadProject(INSTANT_APP, "instant-app");
    assertEquals("http://example.com/parameter", getDefaultInstantAppUrl(myAndroidFacet));
  }

  public void testGetDefaultInstantAppUrlWithoutInstantApp() throws Exception {
    loadProject(SIMPLE_APPLICATION, "app");
    assertEquals("<<ERROR - NO URL SET>>", getDefaultInstantAppUrl(myAndroidFacet));
  }
}