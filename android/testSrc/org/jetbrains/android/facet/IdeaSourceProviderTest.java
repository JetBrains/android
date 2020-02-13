/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.facet;

import static com.android.tools.idea.templates.SourceProviderUtilKt.getSourceProvidersForFile;
import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.Sdks;
import com.google.common.collect.MoreCollectors;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import org.jetbrains.android.sdk.AndroidPlatform;

/**
 * Test for utility functions provided by IdeaSourceProvider
 * <p>
 * This test uses the Gradle model as source data to test the implementation.
 */
public class IdeaSourceProviderTest extends AndroidGradleTestCase {
  private Module myAppModule;
  private Module myLibModule;
  private AndroidFacet myAppFacet;
  private AndroidFacet myLibFacet;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    loadProject(PROJECT_WITH_APPAND_LIB);
    assertNotNull(myAndroidFacet);

    // Set up modules
    for (Module m : ModuleManager.getInstance(getProject()).getModules()) {
      if (m.getName().equals("lib")) {
        myLibModule = m;
      }
      else if (m.getName().equals("app")) {
        myAppModule = m;
      }
    }
    assertNotNull(myLibModule);
    assertNotNull(myAppModule);

    myAppFacet = AndroidFacet.getInstance(myAppModule);
    myLibFacet = AndroidFacet.getInstance(myLibModule);

    assertNotNull(myAppFacet);
    assertNotNull(myLibFacet);

    Sdks.addLatestAndroidSdk(getTestRootDisposable(), myLibModule);
    Sdks.addLatestAndroidSdk(getTestRootDisposable(), myAppModule);

    assertNotNull(AndroidPlatform.getInstance(myAppModule));
    assertNotNull(AndroidPlatform.getInstance(myLibModule));
  }

  public void testFindSourceProvider() throws Exception {
    assertNotNull(AndroidModel.get(myAppFacet));
    VirtualFile moduleFile = findFileByIoFile(getProjectFolderPath(), true).findFileByRelativePath("app");
    assertNotNull(moduleFile);

    // Try finding main flavor
    NamedIdeaSourceProvider mainFlavorSourceProvider = SourceProviderManager.getInstance(myAppFacet).getMainIdeaSourceProvider();
    assertNotNull(mainFlavorSourceProvider);

    VirtualFile javaMainSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/");
    assertNotNull(javaMainSrcFile);

    Collection<NamedIdeaSourceProvider> providers = getSourceProvidersForFile(myAppFacet, javaMainSrcFile);
    assertNotNull(providers);
    assertEquals(1, providers.size());
    NamedIdeaSourceProvider actualProvider = providers.iterator().next();
    assertEquals(mainFlavorSourceProvider, actualProvider);

    // Try finding paid flavor
    NamedIdeaSourceProvider paidFlavorSourceProvider =
      SourceProviderManager.getInstance(myAppFacet).getCurrentAndSomeFrequentlyUsedInactiveSourceProviders().stream()
        .filter(it -> it.getName().equalsIgnoreCase("paid")).collect(MoreCollectors.onlyElement());

    VirtualFile javaSrcFile = moduleFile.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid");
    assertNotNull(javaSrcFile);

    providers = getSourceProvidersForFile(myAppFacet, javaSrcFile);
    assertNotNull(providers);
    assertEquals(1, providers.size());
    actualProvider = providers.iterator().next();
    assertEquals(paidFlavorSourceProvider, actualProvider);
  }

  public void testSourceProviderContainsFile() throws Exception {
    assertNotNull(AndroidModel.get(myAppFacet));
    IdeaSourceProvider paidFlavorSourceProvider =
      SourceProviderManager.getInstance(myAppFacet).getCurrentAndSomeFrequentlyUsedInactiveSourceProviders().stream()
        .filter(it -> it.getName().equalsIgnoreCase("paid")).collect(MoreCollectors.onlyElement());

    VirtualFile moduleFile = findFileByIoFile(getProjectFolderPath(), true).findFileByRelativePath("app");
    assertNotNull(moduleFile);
    VirtualFile javaSrcFile = moduleFile.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid");
    assertNotNull(javaSrcFile);

    assertTrue(IdeaSourceProviderUtil.containsFile(paidFlavorSourceProvider, javaSrcFile));

    VirtualFile javaMainSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/");
    assertNotNull(javaMainSrcFile);

    assertFalse(IdeaSourceProviderUtil.containsFile(paidFlavorSourceProvider, javaMainSrcFile));
  }
}
