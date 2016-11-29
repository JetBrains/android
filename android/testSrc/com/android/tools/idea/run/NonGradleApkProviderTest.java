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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.compiler.artifact.AndroidApplicationArtifactType;
import org.jetbrains.android.compiler.artifact.AndroidFinalPackageElement;
import org.mockito.Mockito;

import java.util.Collection;

/**
 * Tests for {@link NonGradleApkProvider}.
 */
public class NonGradleApkProviderTest extends AndroidTestCase {

  public void testGetApks() throws Exception {
    IDevice device = Mockito.mock(IDevice.class);

    myFacet.getProperties().APK_PATH = "artifact.apk";

    NonGradleApkProvider provider = new NonGradleApkProvider(myFacet, new NonGradleApplicationIdProvider(myFacet), null);

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

    NonGradleApkProvider provider = new NonGradleApkProvider(myFacet, new NonGradleApplicationIdProvider(myFacet), "customApk");

    Collection<ApkInfo> apks = provider.getApks(device);
    assertNotNull(apks);
    assertEquals(1, apks.size());
    ApkInfo apk = apks.iterator().next();
    assertEquals("p1.p2", apk.getApplicationId());
    assertTrue(apk.getFile().getPath().endsWith("right.apk"));
  }
}
