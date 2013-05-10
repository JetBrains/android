/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISystemImage;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.resources.ScreenSize.*;

@SuppressWarnings("javadoc")
public class ManifestInfoTest extends AndroidTestCase {
  public void testGetActivityThemes1() throws Exception {
    ManifestInfo info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                                        "    package='com.android.unittest'>\n" +
                                        "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='4'/>\n" +
                                        "</manifest>\n");
    Map<String, String> map = info.getActivityThemes();
    assertEquals(map.toString(), 0, map.size());
    assertEquals("com.android.unittest", info.getPackage());
    assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(null, NORMAL)));
    assertEquals("@android:style/Theme", info.getDefaultTheme(null, null));
    assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(null, XLARGE)));
  }

  public void testGetActivityThemes2() throws Exception {
    ManifestInfo info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                                        "    package='com.android.unittest'>\n" +
                                        "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='11'/>\n" +
                                        "</manifest>\n");
    Map<String, String> map = info.getActivityThemes();
    assertEquals(map.toString(), 0, map.size());
    assertEquals("com.android.unittest", info.getPackage());
    assertEquals("Theme.Holo", ResourceHelper.styleToTheme(info.getDefaultTheme(null, XLARGE)));
    assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(null, LARGE)));
  }

  public void testGetActivityThemes3() throws Exception {
    ManifestInfo info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                                        "    package='com.android.unittest'>\n" +
                                        "    <uses-sdk android:minSdkVersion='11'/>\n" +
                                        "</manifest>\n");
    Map<String, String> map = info.getActivityThemes();
    assertEquals(map.toString(), 0, map.size());
    assertEquals("com.android.unittest", info.getPackage());
    assertEquals("Theme.Holo", ResourceHelper.styleToTheme(info.getDefaultTheme(null, XLARGE)));
    assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(null, NORMAL)));
  }

  public void testGetActivityThemes4() throws Exception {
    ManifestInfo info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                                        "    package='com.android.unittest'>\n" +
                                        "    <application\n" +
                                        "        android:label='@string/app_name'\n" +
                                        "        android:name='.app.TestApp' android:icon='@drawable/app_icon'>\n" +
                                        "\n" +
                                        "        <activity\n" +
                                        "            android:name='.prefs.PrefsActivity'\n" +
                                        "            android:label='@string/prefs_title' />\n" +
                                        "\n" +
                                        "        <activity\n" +
                                        "            android:name='.app.IntroActivity'\n" +
                                        "            android:label='@string/intro_title'\n" +
                                        "            android:theme='@android:style/Theme.Dialog' />\n" +
                                        "    </application>\n" +
                                        "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='4'/>\n" +
                                        "</manifest>\n" +
                                        "");
    assertEquals("com.android.unittest", info.getPackage());
    assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(null, XLARGE)));

    Map<String, String> map = info.getActivityThemes();
    assertEquals(map.toString(), 1, map.size());
    assertNull(map.get("com.android.unittest.prefs.PrefsActivity"));
    assertEquals("@android:style/Theme.Dialog", map.get("com.android.unittest.app.IntroActivity"));
  }

  public void testGetActivityThemes5() throws Exception {
    ManifestInfo info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                                        "    package='com.android.unittest'>\n" +
                                        "    <application\n" +
                                        "        android:label='@string/app_name'\n" +
                                        "        android:theme='@style/NoBackground'\n" +
                                        "        android:name='.app.TestApp' android:icon='@drawable/app_icon'>\n" +
                                        "\n" +
                                        "        <activity\n" +
                                        "            android:name='.prefs.PrefsActivity'\n" +
                                        "            android:label='@string/prefs_title' />\n" +
                                        "\n" +
                                        "        <activity\n" +
                                        "            android:name='.app.IntroActivity'\n" +
                                        "            android:label='@string/intro_title'\n" +
                                        "            android:theme='@android:style/Theme.Dialog' />\n" +
                                        "    </application>\n" +
                                        "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='4'/>\n" +
                                        "</manifest>\n" +
                                        "");

    assertEquals("@style/NoBackground", info.getDefaultTheme(null, XLARGE));
    assertEquals("@style/NoBackground", info.getDefaultTheme(null, NORMAL));
    assertEquals("NoBackground", ResourceHelper.styleToTheme(info.getDefaultTheme(null, NORMAL)));

    Map<String, String> map = info.getActivityThemes();
    assertEquals(map.toString(), 1, map.size());
    assertNull(map.get("com.android.unittest.prefs.PrefsActivity"));
    assertEquals("@android:style/Theme.Dialog", map.get("com.android.unittest.app.IntroActivity"));
  }

  public void testGetActivityThemes6() throws Exception {
    // Ensures that when the *rendering* target is less than version 11, we don't
    // use Holo even though the manifest SDK version calls for it.
    ManifestInfo info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                                        "    package='com.android.unittest'>\n" +
                                        "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='11'/>\n" +
                                        "</manifest>\n");
    Map<String, String> map = info.getActivityThemes();
    assertEquals(map.toString(), 0, map.size());
    assertEquals("com.android.unittest", info.getPackage());
    assertEquals("Theme.Holo", ResourceHelper.styleToTheme(info.getDefaultTheme(null, XLARGE)));

    // Here's the check
    IAndroidTarget olderVersion = new TestAndroidTarget(4);
    assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(olderVersion, XLARGE)));

  }

  public void testGetApplicationLabelAndIcon() throws Exception {
    ManifestInfo info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                                        "    package='com.android.unittest'>\n" +
                                        "    <application android:icon=\"@drawable/icon\"\n" +
                                        "                 android:label=\"@string/app_name\">\n" +
                                        "    </application>\n" +
                                        "" +
                                        "</manifest>\n");
    Map<String, String> map = info.getActivityThemes();
    assertEquals(map.toString(), 0, map.size());
    assertEquals("com.android.unittest", info.getPackage());

    assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(null, NORMAL)));
    assertEquals("@drawable/icon", info.getApplicationIcon());
    assertEquals("@string/app_name", info.getApplicationLabel());
  }

  public void testGetApplicationNoLabelOrIcon() throws Exception {
    ManifestInfo info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                                        "    package='com.android.unittest'>\n" +
                                        "    <application>\n" +
                                        "    </application>\n" +
                                        "" +
                                        "</manifest>\n");
    Map<String, String> map = info.getActivityThemes();
    assertEquals(map.toString(), 0, map.size());
    assertEquals("com.android.unittest", info.getPackage());

    assertEquals("Theme", ResourceHelper.styleToTheme(info.getDefaultTheme(null, NORMAL)));
    assertNull(info.getApplicationIcon());
    assertNull(info.getApplicationLabel());
  }

  private ManifestInfo getManifestInfo(String manifestContents) throws Exception {
    myFixture.addFileToProject("AndroidManifest.xml", manifestContents);

    // No sharing between tests:
    myModule.putUserData(ManifestInfo.MANIFEST_FINDER, null);
    ManifestInfo info = ManifestInfo.get(myModule);

    info.clear();
    return info;
  }

  public void testGetMinSdkVersionName() throws Exception {
    ManifestInfo info;

    info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                           "    package='com.android.unittest'>\n" +
                           "    <uses-sdk android:minSdkVersion='3' android:targetSdkVersion='4'/>\n" +
                           "</manifest>\n");
    assertEquals(3, info.getMinSdkVersion());
    assertEquals("3", info.getMinSdkName());
    assertEquals(4, info.getTargetSdkVersion());
    assertNull(info.getMinSdkCodeName());

    info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                           "    package='com.android.unittest'>\n" +
                           "    <uses-sdk android:targetSdkVersion='4'/>\n" +
                           "</manifest>\n");
    assertEquals("1", info.getMinSdkName());
    assertEquals(1, info.getMinSdkVersion());
    assertEquals(4, info.getTargetSdkVersion());
    assertNull(info.getMinSdkCodeName());

    info = getManifestInfo("<manifest xmlns:android='http://schemas.android.com/apk/res/android'\n" +
                           "    package='com.android.unittest'>\n" +
                           "    <uses-sdk android:minSdkVersion='JellyBean' />\n" +
                           "</manifest>\n");
    assertEquals("JellyBean", info.getMinSdkName());
    assertEquals("JellyBean", info.getMinSdkCodeName());
  }

  private static class TestAndroidTarget implements IAndroidTarget {
    private final int mApiLevel;

    public TestAndroidTarget(int apiLevel) {
      mApiLevel = apiLevel;
    }

    @Override
    public boolean canRunOn(IAndroidTarget target) {
      return false;
    }

    @Override
    public String getClasspathName() {
      return null;
    }

    @Override
    public String getDefaultSkin() {
      return null;
    }

    @Override
    public String getDescription() {
      return null;
    }

    @Override
    public String getFullName() {
      return null;
    }

    @Override
    public ISystemImage getSystemImage(String abiType) {
      return null;
    }

    @Override
    public ISystemImage[] getSystemImages() {
      return new ISystemImage[0];
    }

    @Override
    public String getLocation() {
      return null;
    }

    @Override
    public String getName() {
      return null;
    }

    @Override
    public IOptionalLibrary[] getOptionalLibraries() {
      return null;
    }

    @Override
    public IAndroidTarget getParent() {
      return null;
    }

    @Override
    public String getPath(int pathId) {
      return null;
    }

    @Override
    public BuildToolInfo getBuildToolInfo() {
      return null;
    }

    @Override
    public String[] getPlatformLibraries() {
      return null;
    }

    @Override
    public Map<String, String> getProperties() {
      return null;
    }

    @Override
    public String getProperty(String name) {
      return null;
    }

    @Override
    public Integer getProperty(String name, Integer defaultValue) {
      return null;
    }

    @Override
    public Boolean getProperty(String name, Boolean defaultValue) {
      return null;
    }

    @Override
    public int getRevision() {
      return 0;
    }

    @Override
    public String[] getSkins() {
      return null;
    }

    @Override
    public int getUsbVendorId() {
      return 0;
    }

    @Override
    public String getVendor() {
      return null;
    }

    @Override
    public AndroidVersion getVersion() {
      return new AndroidVersion(mApiLevel, null);
    }

    @Override
    public String getVersionName() {
      return null;
    }

    @Override
    public String hashString() {
      return null;
    }

    @Override
    public boolean isPlatform() {
      return false;
    }

    @Override
    public int compareTo(IAndroidTarget o) {
      return 0;
    }

    @Override
    public boolean hasRenderingLibrary() {
      return false;
    }

    @Override
    public String getShortClasspathName() {
      return null;
    }

    @Override
    public List<String> getBootClasspath() {
      return new ArrayList<String>();
    }
  }
}
