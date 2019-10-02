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

import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.Sdks;
import com.google.common.collect.MoreCollectors;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import java.io.File;
import java.util.Collection;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public void testGetCurrentSourceProviders() throws Exception {
    StringBuilder sb = new StringBuilder();
    VirtualFile baseDir = getProject().getBaseDir();
    for (IdeaSourceProvider provider : IdeaSourceProvider.getCurrentSourceProviders(myAppFacet)) {
      sb.append(getStringRepresentation(provider, baseDir));
    }
    assertEquals("Manifest File: app/src/main/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/main/java]\n" +
                 "Res Directories: [app/src/main/res]\n" +
                 "Assets Directories: []\n" +
                 "AIDL Directories: []\n" +
                 "Renderscript Directories: []\n" +
                 "Jni Directories: []\n" +
                 "Resources Directories: []\n" +
                 "Manifest File: null\n" +
                 "Java Directories: []\n" +
                 "Res Directories: []\n" +
                 "Assets Directories: []\n" +
                 "AIDL Directories: []\n" +
                 "Renderscript Directories: []\n" +
                 "Jni Directories: []\n" +
                 "Resources Directories: []\n" +
                 "Manifest File: null\n" +
                 "Java Directories: []\n" +
                 "Res Directories: []\n" +
                 "Assets Directories: []\n" +
                 "AIDL Directories: []\n" +
                 "Renderscript Directories: []\n" +
                 "Jni Directories: []\n" +
                 "Resources Directories: []\n" +
                 "Manifest File: null\n" +
                 "Java Directories: []\n" +
                 "Res Directories: []\n" +
                 "Assets Directories: []\n" +
                 "AIDL Directories: []\n" +
                 "Renderscript Directories: []\n" +
                 "Jni Directories: []\n" +
                 "Resources Directories: []\n", sb.toString());

    sb = new StringBuilder();
    for (IdeaSourceProvider provider : IdeaSourceProvider.getCurrentSourceProviders(myLibFacet)) {
      sb.append(getStringRepresentation(provider, baseDir));
    }
    assertEquals("Manifest File: lib/src/main/AndroidManifest.xml\n" +
                 "Java Directories: [lib/src/main/java]\n" +
                 "Res Directories: [lib/src/main/res]\n" +
                 "Assets Directories: []\n" +
                 "AIDL Directories: []\n" +
                 "Renderscript Directories: []\n" +
                 "Jni Directories: []\n" +
                 "Resources Directories: []\n" +
                 "Manifest File: null\n" +
                 "Java Directories: []\n" +
                 "Res Directories: []\n" +
                 "Assets Directories: []\n" +
                 "AIDL Directories: []\n" +
                 "Renderscript Directories: []\n" +
                 "Jni Directories: []\n" +
                 "Resources Directories: []\n", sb.toString());
  }

  public void testFindSourceProvider() throws Exception {
    assertNotNull(myAppFacet.getConfiguration().getModel());
    VirtualFile moduleFile = findFileByIoFile(getProjectFolderPath(), true).findFileByRelativePath("app");
    assertNotNull(moduleFile);

    // Try finding main flavor
    IdeaSourceProvider mainFlavorSourceProvider = SourceProviderManager.getInstance(myAppFacet).getMainIdeaSourceProvider();
    assertNotNull(mainFlavorSourceProvider);

    VirtualFile javaMainSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/");
    assertNotNull(javaMainSrcFile);

    Collection<IdeaSourceProvider> providers = IdeaSourceProvider.getSourceProvidersForFile(myAppFacet, javaMainSrcFile, null);
    assertEquals(1, providers.size());
    IdeaSourceProvider actualProvider = providers.iterator().next();
    assertEquals(mainFlavorSourceProvider.getManifestFile(),
                 actualProvider.getManifestFile());

    // Try finding paid flavor
    IdeaSourceProvider paidFlavorSourceProvider =
      IdeaSourceProvider.getAllIdeaSourceProviders(myAppFacet).stream()
        .filter(it -> it.getName().equalsIgnoreCase("paid")).collect(MoreCollectors.onlyElement());

    VirtualFile javaSrcFile = moduleFile.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid");
    assertNotNull(javaSrcFile);

    providers = IdeaSourceProvider.getSourceProvidersForFile(myAppFacet, javaSrcFile, null);
    assertEquals(1, providers.size());
    actualProvider = providers.iterator().next();
    assertEquals(paidFlavorSourceProvider.getManifestFile(),
                 actualProvider.getManifestFile());
  }

  public String getStringRepresentation(@NotNull IdeaSourceProvider sourceProvider, @Nullable VirtualFile baseFile) {
    StringBuilder sb = new StringBuilder();
    VirtualFile manifestFile = sourceProvider.getManifestFile();
    String manifestPath = null;
    if (manifestFile != null) {
      if (baseFile != null) {
        manifestPath = VfsUtilCore.getRelativePath(manifestFile, baseFile, File.separatorChar);
      }
      else {
        manifestPath = manifestFile.getPath();
      }
    }
    sb.append("Manifest File: ");
    sb.append(PathUtil.toSystemIndependentName(manifestPath));
    sb.append('\n');

    sb.append("Java Directories: ");
    sb.append(fileSetToString(sourceProvider.getJavaDirectories(), baseFile));
    sb.append('\n');
    sb.append("Res Directories: ");
    sb.append(fileSetToString(sourceProvider.getResDirectories(), baseFile));
    sb.append('\n');
    sb.append("Assets Directories: ");
    sb.append(fileSetToString(sourceProvider.getAssetsDirectories(), baseFile));
    sb.append('\n');
    sb.append("AIDL Directories: ");
    sb.append(fileSetToString(sourceProvider.getAidlDirectories(), baseFile));
    sb.append('\n');
    sb.append("Renderscript Directories: ");
    sb.append(fileSetToString(sourceProvider.getRenderscriptDirectories(), baseFile));
    sb.append('\n');
    sb.append("Jni Directories: ");
    sb.append(fileSetToString(sourceProvider.getJniDirectories(), baseFile));
    sb.append('\n');
    sb.append("Resources Directories: ");
    sb.append(fileSetToString(sourceProvider.getResourcesDirectories(), baseFile));
    sb.append('\n');
    return sb.toString();
  }

  @NotNull
  private static String fileSetToString(@NotNull Collection<VirtualFile> files, @Nullable VirtualFile baseFile) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean isFirst = true;
    for (VirtualFile vf : files) {
      String path = baseFile != null ? VfsUtilCore.getRelativePath(vf, baseFile, File.separatorChar) : vf.getPath();
      if (!isFirst) {
        sb.append(", ");
      }
      else {
        isFirst = false;
      }
      sb.append(PathUtil.toSystemIndependentName(path));
    }
    sb.append("]");
    return sb.toString();
  }

  public void testSourceProviderContainsFile() throws Exception {
    assertNotNull(myAppFacet.getConfiguration().getModel());
    IdeaSourceProvider paidFlavorSourceProvider =
      IdeaSourceProvider.getAllIdeaSourceProviders(myAppFacet).stream()
        .filter(it -> it.getName().equalsIgnoreCase("paid")).collect(MoreCollectors.onlyElement());

    VirtualFile moduleFile = findFileByIoFile(getProjectFolderPath(), true).findFileByRelativePath("app");
    assertNotNull(moduleFile);
    VirtualFile javaSrcFile = moduleFile.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid");
    assertNotNull(javaSrcFile);

    assertTrue(IdeaSourceProvider.containsFile(paidFlavorSourceProvider, javaSrcFile));

    VirtualFile javaMainSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/");
    assertNotNull(javaMainSrcFile);

    assertFalse(IdeaSourceProvider.containsFile(paidFlavorSourceProvider, javaMainSrcFile));
  }


  public void testSourceProviderIsContainedByFolder() throws Exception {
    assertNotNull(myAppFacet.getConfiguration().getModel());
    IdeaSourceProvider paidFlavorSourceProvider =
      IdeaSourceProvider.getAllIdeaSourceProviders(myAppFacet).stream()
        .filter(it -> it.getName().equalsIgnoreCase("paid")).collect(MoreCollectors.onlyElement());

    VirtualFile moduleFile = findFileByIoFile(getProjectFolderPath(), true).findFileByRelativePath("app");
    assertNotNull(moduleFile);
    VirtualFile javaSrcFile = moduleFile.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid");
    assertNotNull(javaSrcFile);

    assertFalse(IdeaSourceProvider.isContainedBy(paidFlavorSourceProvider, javaSrcFile));

    VirtualFile flavorRoot = moduleFile.findFileByRelativePath("src/paid");
    assertNotNull(flavorRoot);

    assertTrue(IdeaSourceProvider.isContainedBy(paidFlavorSourceProvider, flavorRoot));

    VirtualFile srcFile = moduleFile.findChild("src");
    assertNotNull(srcFile);

    assertTrue(IdeaSourceProvider.isContainedBy(paidFlavorSourceProvider, srcFile));
  }
}
