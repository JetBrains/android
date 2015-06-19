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

import com.android.SdkConstants;
import org.jetbrains.android.AndroidTestCase;

public class SpecificActivityLocatorTest extends AndroidTestCase {
  public SpecificActivityLocatorTest() {
    super(false);
  }

  public void testValidLauncherActivity() throws ActivityLocator.ActivityLocatorException {
    myFixture.copyFileToProject("projects/runConfig/activity/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/activity/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "com.example.unittest.Launcher");
    locator.validate(myFacet);
  }

  public void testValidLauncherAlias() throws ActivityLocator.ActivityLocatorException {
    myFixture.copyFileToProject("projects/runConfig/alias/src/debug/AndroidManifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.copyFileToProject("projects/runConfig/alias/src/debug/java/com/example/unittest/Launcher.java",
                                "src/com/example/unittest/Launcher.java");
    SpecificActivityLocator locator = new SpecificActivityLocator(myFacet, "LauncherAlias");
    locator.validate(myFacet);
  }
}
