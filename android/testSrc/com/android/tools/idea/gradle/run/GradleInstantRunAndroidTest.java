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
package com.android.tools.idea.gradle.run;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.google.common.hash.HashCode;
import org.jetbrains.android.AndroidTestCase;

import static org.junit.Assert.assertNotEquals;

public class GradleInstantRunAndroidTest extends AndroidTestCase {
  public static final String BASEDIR = "projects/projectWithAppandLib/app/src/main/";

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  public void testResourceChangeIsDetected() throws Exception {
    myFixture.copyFileToProject(BASEDIR + "AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject(BASEDIR + "res/values/strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASEDIR + "res/values/styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject(BASEDIR + "res/drawable-hdpi/ic_launcher.png", "res/drawable-hdpi/ic_launcher.png");

    HashCode hash = GradleInstantRunContext.getManifestResourcesHash(myFacet);

    // change a resource not referenced from manifest
    AppResourceRepository repository = AppResourceRepository.getOrCreateInstance(myFacet);
    ResourceValue resValue = repository.getConfiguredValue(ResourceType.STRING, "title_section1", new FolderConfiguration());
    resValue.setValue("foo");
    assertEquals("Hash should not change if a resource not referenced from the manifest is changed",
                 hash, GradleInstantRunContext.getManifestResourcesHash(myFacet));

    // change the app_name referenced from manifest
    resValue = repository.getConfiguredValue(ResourceType.STRING, "app_name", new FolderConfiguration());
    resValue.setValue("testapp");
    assertNotEquals("Hash should change if a resource referenced from the manifest is changed",
                    hash, GradleInstantRunContext.getManifestResourcesHash(myFacet));

    // change the contents of the launcher icon referenced from manifest
    hash = GradleInstantRunContext.getManifestResourcesHash(myFacet);
    myFixture.copyFileToProject(BASEDIR + "res/drawable-mdpi/ic_launcher.png", "res/drawable-hdpi/ic_launcher.png");
    assertNotEquals("Hash should change if a resource referenced from the manifest is changed",
                    hash, GradleInstantRunContext.getManifestResourcesHash(myFacet));

    // change the contents of the theme referenced from manifest
    hash = GradleInstantRunContext.getManifestResourcesHash(myFacet);
    resValue = repository.getConfiguredValue(ResourceType.STYLE, "AppTheme", new FolderConfiguration());
    ((StyleResourceValue)resValue).getItem("colorPrimary", false).setValue("colorPrimaryDark");
    assertNotEquals("Hash should change if a resource referenced from the manifest is changed",
                    hash, GradleInstantRunContext.getManifestResourcesHash(myFacet));
  }
}
