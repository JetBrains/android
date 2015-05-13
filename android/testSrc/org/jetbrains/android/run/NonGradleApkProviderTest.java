/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.compiler.artifact.AndroidApplicationArtifactType;
import org.jetbrains.android.compiler.artifact.AndroidFinalPackageElement;
import org.mockito.Mockito;

import java.util.Collection;

/**
 * Tests for {@link org.jetbrains.android.run.NonGradleApkProvider}.
 */
public class NonGradleApkProviderTest extends AndroidTestCase {

  public void testGetPackageName() throws Exception {
    NonGradleApkProvider provider = new NonGradleApkProvider(myFacet, null);
    assertEquals("p1.p2", provider.getPackageName());
    // The test package name matches the main APK package name.
    assertEquals("p1.p2", provider.getTestPackageName());
  }

  public void testGetApks() throws Exception {
    IDevice device = Mockito.mock(IDevice.class);

    myFacet.getProperties().APK_PATH = "artifact.apk";

    NonGradleApkProvider provider = new NonGradleApkProvider(myFacet, null);

    Collection<ApkInfo> apks = provider.getApks(device);
    assertNotNull(apks);
    assertEquals(1, apks.size());
    ApkInfo apk = apks.iterator().next();
    assertEquals("p1.p2", apk.getApplicationId());
    assertTrue(apk.getFile().getPath().endsWith("artifact.apk"));
  }

  public void testGetApksWithArtifactName() throws Exception{
    IDevice device = Mockito.mock(IDevice.class);

    ArtifactManager artifactManager = ArtifactManager.getInstance(myFacet.getModule().getProject());
    CompositePackagingElement<?> archive = PackagingElementFactory.getInstance().createArchive("right.apk");
    archive.addFirstChild(new AndroidFinalPackageElement(myFacet.getModule().getProject(), myFacet));
    artifactManager.addArtifact("customApk", AndroidApplicationArtifactType.getInstance(), archive);

    myFacet.getProperties().APK_PATH = "wrong.apk";

    NonGradleApkProvider provider = new NonGradleApkProvider(myFacet, "customApk");

    Collection<ApkInfo> apks = provider.getApks(device);
    assertNotNull(apks);
    assertEquals(1, apks.size());
    ApkInfo apk = apks.iterator().next();
    assertEquals("p1.p2", apk.getApplicationId());
    assertTrue(apk.getFile().getPath().endsWith("right.apk"));
  }

  /**
   * A non-Gradle APK provider can be used when an IdeaAndroidProject is present if Projects.isBuildWithGradle is false.
   */
  public void testGetPackageNameForIdeaAndroidProject() throws Exception {
    myFacet.setIdeaAndroidProject(getIdeaAndroidProject());

    NonGradleApkProvider provider = new NonGradleApkProvider(myFacet, null);
    assertEquals("app.variantname", provider.getPackageName());
    // The test package name matches the main APK package name.
    assertEquals("app.variantname", provider.getTestPackageName());
  }

  public void testGetApksForIdeaAndroidProject() throws Exception {
    IDevice device = Mockito.mock(IDevice.class);
    myFacet.setIdeaAndroidProject(getIdeaAndroidProject());

    NonGradleApkProvider provider = new NonGradleApkProvider(myFacet, null);
    Collection<ApkInfo> apks = provider.getApks(device);
    assertNotNull(apks);
    assertEquals(1, apks.size());
    ApkInfo apk = apks.iterator().next();
    assertEquals("app.variantname", apk.getApplicationId());
    assertTrue(apk.getFile().getPath().endsWith("_main_-variantName.apk"));
  }

  private IdeaAndroidProject getIdeaAndroidProject() throws Exception {
    AndroidProjectStub androidProject = new AndroidProjectStub("projectName");
    VariantStub variant = androidProject.addVariant("variantName");
    return new IdeaAndroidProject(
        new ProjectSystemId("systemId"),
        myFacet.getModule().getName(),
        androidProject.getRootDir(),
        androidProject,
        variant.getName(),
        variant.getInstrumentTestArtifact().getName());
  }
}
