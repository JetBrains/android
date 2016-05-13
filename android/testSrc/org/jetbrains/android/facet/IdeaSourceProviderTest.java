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


import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.AndroidGradleModelTest;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.Sets;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * Test for utility functions provided by IdeaSourceProvider
 *
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

    loadProject("projects/projectWithAppandLib");
    assertNotNull(myAndroidFacet);
    AndroidGradleModel androidModel = AndroidGradleModel.get(myAndroidFacet);
    assertNotNull(androidModel);

    // Set up modules
    for (Module m : ModuleManager.getInstance(getProject()).getModules()) {
      if (m.getName().equals("lib")) {
        myLibModule = m;
      } else if (m.getName().equals("app")) {
        myAppModule = m;
      }
    }
    assertNotNull(myLibModule);
    assertNotNull(myAppModule);

    myAppFacet = AndroidFacet.getInstance(myAppModule);
    myLibFacet = AndroidFacet.getInstance(myLibModule);

    assertNotNull(myAppFacet);
    assertNotNull(myLibFacet);

    addAndroidSdk(myLibModule, getTestSdkPath(), getPlatformDir());
    addAndroidSdk(myAppModule, getTestSdkPath(), getPlatformDir());

    assertNotNull(AndroidPlatform.getInstance(myAppModule));
    assertNotNull(AndroidPlatform.getInstance(myLibModule));
  }

  /** TODO: Move this test to {@link AndroidGradleModelTest}. */
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

  /** TODO: Move this test to {@link AndroidGradleModelTest}. */
  public void testGetAllSourceProviders() throws Exception {
    StringBuilder sb = new StringBuilder();
    File baseDir = new File(getProject().getBaseDir().getPath());
    for (SourceProvider provider : IdeaSourceProvider.getAllSourceProviders(myAppFacet)) {
      sb.append(getStringRepresentation(provider, baseDir));
    }
    assertEquals("Name: main\n" +
                 "Manifest File: app/src/main/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/main/java]\n" +
                 "Res Directories: [app/src/main/res]\n" +
                 "Assets Directories: [app/src/main/assets]\n" +
                 "AIDL Directories: [app/src/main/aidl]\n" +
                 "Renderscript Directories: [app/src/main/rs]\n" +
                 "Jni Directories: [app/src/main/jni]\n" +
                 "Resources Directories: [app/src/main/resources]\n" +
                 "Name: paid\n" +
                 "Manifest File: app/src/paid/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/paid/java]\n" +
                 "Res Directories: [app/src/paid/res]\n" +
                 "Assets Directories: [app/src/paid/assets]\n" +
                 "AIDL Directories: [app/src/paid/aidl]\n" +
                 "Renderscript Directories: [app/src/paid/rs]\n" +
                 "Jni Directories: [app/src/paid/jni]\n" +
                 "Resources Directories: [app/src/paid/resources]\n" +
                 "Name: basic\n" +
                 "Manifest File: app/src/basic/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/basic/java]\n" +
                 "Res Directories: [app/src/basic/res]\n" +
                 "Assets Directories: [app/src/basic/assets]\n" +
                 "AIDL Directories: [app/src/basic/aidl]\n" +
                 "Renderscript Directories: [app/src/basic/rs]\n" +
                 "Jni Directories: [app/src/basic/jni]\n" +
                 "Resources Directories: [app/src/basic/resources]\n" +
                 "Name: debug\n" +
                 "Manifest File: app/src/debug/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/debug/java]\n" +
                 "Res Directories: [app/src/debug/res]\n" +
                 "Assets Directories: [app/src/debug/assets]\n" +
                 "AIDL Directories: [app/src/debug/aidl]\n" +
                 "Renderscript Directories: [app/src/debug/rs]\n" +
                 "Jni Directories: [app/src/debug/jni]\n" +
                 "Resources Directories: [app/src/debug/resources]\n" +
                 "Name: release\n" +
                 "Manifest File: app/src/release/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/release/java]\n" +
                 "Res Directories: [app/src/release/res]\n" +
                 "Assets Directories: [app/src/release/assets]\n" +
                 "AIDL Directories: [app/src/release/aidl]\n" +
                 "Renderscript Directories: [app/src/release/rs]\n" +
                 "Jni Directories: [app/src/release/jni]\n" +
                 "Resources Directories: [app/src/release/resources]\n" +
                 "Name: paidDebug\n" +
                 "Manifest File: app/src/paidDebug/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/paidDebug/java]\n" +
                 "Res Directories: [app/src/paidDebug/res]\n" +
                 "Assets Directories: [app/src/paidDebug/assets]\n" +
                 "AIDL Directories: [app/src/paidDebug/aidl]\n" +
                 "Renderscript Directories: [app/src/paidDebug/rs]\n" +
                 "Jni Directories: [app/src/paidDebug/jni]\n" +
                 "Resources Directories: [app/src/paidDebug/resources]\n" +
                 "Name: paidRelease\n" +
                 "Manifest File: app/src/paidRelease/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/paidRelease/java]\n" +
                 "Res Directories: [app/src/paidRelease/res]\n" +
                 "Assets Directories: [app/src/paidRelease/assets]\n" +
                 "AIDL Directories: [app/src/paidRelease/aidl]\n" +
                 "Renderscript Directories: [app/src/paidRelease/rs]\n" +
                 "Jni Directories: [app/src/paidRelease/jni]\n" +
                 "Resources Directories: [app/src/paidRelease/resources]\n" +
                 "Name: basicDebug\n" +
                 "Manifest File: app/src/basicDebug/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/basicDebug/java]\n" +
                 "Res Directories: [app/src/basicDebug/res]\n" +
                 "Assets Directories: [app/src/basicDebug/assets]\n" +
                 "AIDL Directories: [app/src/basicDebug/aidl]\n" +
                 "Renderscript Directories: [app/src/basicDebug/rs]\n" +
                 "Jni Directories: [app/src/basicDebug/jni]\n" +
                 "Resources Directories: [app/src/basicDebug/resources]\n" +
                 "Name: basicRelease\n" +
                 "Manifest File: app/src/basicRelease/AndroidManifest.xml\n" +
                 "Java Directories: [app/src/basicRelease/java]\n" +
                 "Res Directories: [app/src/basicRelease/res]\n" +
                 "Assets Directories: [app/src/basicRelease/assets]\n" +
                 "AIDL Directories: [app/src/basicRelease/aidl]\n" +
                 "Renderscript Directories: [app/src/basicRelease/rs]\n" +
                 "Jni Directories: [app/src/basicRelease/jni]\n" +
                 "Resources Directories: [app/src/basicRelease/resources]\n", sb.toString());

    sb = new StringBuilder();
    for (SourceProvider provider : IdeaSourceProvider.getAllSourceProviders(myLibFacet)) {
      sb.append(getStringRepresentation(provider, baseDir));
    }
    assertEquals("Name: main\n" +
                 "Manifest File: lib/src/main/AndroidManifest.xml\n" +
                 "Java Directories: [lib/src/main/java]\n" +
                 "Res Directories: [lib/src/main/res]\n" +
                 "Assets Directories: [lib/src/main/assets]\n" +
                 "AIDL Directories: [lib/src/main/aidl]\n" +
                 "Renderscript Directories: [lib/src/main/rs]\n" +
                 "Jni Directories: [lib/src/main/jni]\n" +
                 "Resources Directories: [lib/src/main/resources]\n" +
                 "Name: debug\n" +
                 "Manifest File: lib/src/debug/AndroidManifest.xml\n" +
                 "Java Directories: [lib/src/debug/java]\n" +
                 "Res Directories: [lib/src/debug/res]\n" +
                 "Assets Directories: [lib/src/debug/assets]\n" +
                 "AIDL Directories: [lib/src/debug/aidl]\n" +
                 "Renderscript Directories: [lib/src/debug/rs]\n" +
                 "Jni Directories: [lib/src/debug/jni]\n" +
                 "Resources Directories: [lib/src/debug/resources]\n" +
                 "Name: release\n" +
                 "Manifest File: lib/src/release/AndroidManifest.xml\n" +
                 "Java Directories: [lib/src/release/java]\n" +
                 "Res Directories: [lib/src/release/res]\n" +
                 "Assets Directories: [lib/src/release/assets]\n" +
                 "AIDL Directories: [lib/src/release/aidl]\n" +
                 "Renderscript Directories: [lib/src/release/rs]\n" +
                 "Jni Directories: [lib/src/release/jni]\n" +
                 "Resources Directories: [lib/src/release/resources]\n", sb.toString());
  }

  public void testFindSourceProvider() throws Exception {
    assertNotNull(myAppFacet.getAndroidModel());
    VirtualFile moduleFile = myAppFacet.getAndroidModel().getRootDir();
    assertNotNull(moduleFile);

    // Try finding main flavor
    IdeaSourceProvider mainFlavorSourceProvider = myAppFacet.getMainIdeaSourceProvider();
    assertNotNull(mainFlavorSourceProvider);

    VirtualFile javaMainSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/");
    assertNotNull(javaMainSrcFile);

    Collection<IdeaSourceProvider> providers = IdeaSourceProvider.getIdeaSourceProvidersForFile(myAppFacet, javaMainSrcFile, null);
    assertEquals(1, providers.size());
    IdeaSourceProvider actualProvider = providers.iterator().next();
    assertEquals(mainFlavorSourceProvider.getManifestFile(),
                 actualProvider.getManifestFile());

    // Try finding paid flavor
    ProductFlavorContainer paidFlavor = AndroidGradleModel.get(myAppFacet).findProductFlavor("paid");
    assertNotNull(paidFlavor);
    IdeaSourceProvider paidFlavorSourceProvider = IdeaSourceProvider.create(paidFlavor.getSourceProvider());
    assertNotNull(paidFlavorSourceProvider);


    VirtualFile javaSrcFile = moduleFile.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid");
    assertNotNull(javaSrcFile);

    providers = IdeaSourceProvider.getIdeaSourceProvidersForFile(myAppFacet, javaSrcFile, null);
    assertEquals(1, providers.size());
    actualProvider = providers.iterator().next();
    assertEquals(paidFlavorSourceProvider.getManifestFile(),
                 actualProvider.getManifestFile());

  }

  public String getStringRepresentation(@NotNull SourceProvider sourceProvider, @Nullable File baseFile) {
    StringBuilder sb = new StringBuilder();
    sb.append("Name: ");
    sb.append(sourceProvider.getName());
    sb.append('\n');
    File manifestFile = sourceProvider.getManifestFile();
    String manifestPath = null;
    if (manifestFile != null) {
      if (baseFile != null) {
        manifestPath = FileUtil.getRelativePath(baseFile, manifestFile);
      } else {
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
    Set<File> jniDirectories = Sets.newHashSet();
    jniDirectories.addAll(sourceProvider.getCDirectories());
    jniDirectories.addAll(sourceProvider.getCppDirectories());
    sb.append(fileSetToString(jniDirectories, baseFile));
    sb.append('\n');
    sb.append("Resources Directories: ");
    sb.append(fileSetToString(sourceProvider.getResourcesDirectories(), baseFile));
    sb.append('\n');
    return sb.toString();
  }

  public String getStringRepresentation(@NotNull IdeaSourceProvider sourceProvider, @Nullable VirtualFile baseFile) {
    StringBuilder sb = new StringBuilder();
    VirtualFile manifestFile = sourceProvider.getManifestFile();
    String manifestPath = null;
    if (manifestFile != null) {
      if (baseFile != null) {
        manifestPath = VfsUtilCore.getRelativePath(manifestFile, baseFile, File.separatorChar);
      } else {
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
      } else {
        isFirst = false;
      }
      sb.append(PathUtil.toSystemIndependentName(path));
    }
    sb.append("]");
    return sb.toString();
  }

  @NotNull
  private static String fileSetToString(@NotNull Collection<File> files, @Nullable File baseFile) {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean isFirst = true;
    for (File f : files) {
      String path = baseFile != null ? FileUtil.getRelativePath(baseFile, f) : f.getPath();
      if (!isFirst) {
        sb.append(", ");
      } else {
        isFirst = false;
      }
      sb.append(PathUtil.toSystemIndependentName(path));
    }
    sb.append("]");
    return sb.toString();
  }

  public void testSourceProviderContainsFile() throws Exception {
    assertNotNull(myAppFacet.getAndroidModel());
    ProductFlavorContainer paidFlavor = AndroidGradleModel.get(myAppFacet).findProductFlavor("paid");
    assertNotNull(paidFlavor);
    IdeaSourceProvider paidFlavorSourceProvider = IdeaSourceProvider.create(paidFlavor.getSourceProvider());
    assertNotNull(paidFlavorSourceProvider);

    VirtualFile moduleFile = myAppFacet.getAndroidModel().getRootDir();
    assertNotNull(moduleFile);
    VirtualFile javaSrcFile = moduleFile.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid");
    assertNotNull(javaSrcFile);

    assertTrue(paidFlavorSourceProvider.containsFile(javaSrcFile));

    VirtualFile javaMainSrcFile = moduleFile.findFileByRelativePath("src/main/java/com/example/projectwithappandlib/");
    assertNotNull(javaMainSrcFile);

    assertFalse(paidFlavorSourceProvider.containsFile(javaMainSrcFile));
  }


  public void testSourceProviderIsContainedByFolder() throws Exception {
    assertNotNull(myAppFacet.getAndroidModel());
    ProductFlavorContainer paidFlavor = AndroidGradleModel.get(myAppFacet).findProductFlavor("paid");
    assertNotNull(paidFlavor);
    IdeaSourceProvider paidFlavorSourceProvider = IdeaSourceProvider.create(paidFlavor.getSourceProvider());
    assertNotNull(paidFlavorSourceProvider);

    VirtualFile moduleFile = myAppFacet.getAndroidModel().getRootDir();
    assertNotNull(moduleFile);
    VirtualFile javaSrcFile = moduleFile.findFileByRelativePath("src/paid/java/com/example/projectwithappandlib/app/paid");
    assertNotNull(javaSrcFile);

    assertFalse(paidFlavorSourceProvider.isContainedBy(javaSrcFile));

    VirtualFile flavorRoot = moduleFile.findFileByRelativePath("src/paid");
    assertNotNull(flavorRoot);

    assertTrue(paidFlavorSourceProvider.isContainedBy(flavorRoot));

    VirtualFile srcFile = moduleFile.findChild("src");
    assertNotNull(srcFile);

    assertTrue(paidFlavorSourceProvider.isContainedBy(srcFile));
  }
}
