/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.fonts;

import static com.android.tools.idea.instantapp.InstantApps.findBaseFeature;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.fonts.FontDetail;
import com.android.resources.ResourceFolderType;
import com.android.testutils.junit4.OldAgpTest;
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;

@OldAgpTest
public class InstantAppFontFamilyCreatorTest extends AndroidGradleTestCase {

  @OldAgpTest(agpVersions = "3.5.0", gradleVersions = "5.5")
  public void testCreateFontWithInstantApp() throws Exception {
    // Use a plugin with instant app support
    loadProject(INSTANT_APP, "instant-app", AgpVersionSoftwareEnvironmentDescriptor.AGP_35);
    AndroidFacet baseFacet = AndroidFacet.getInstance(findBaseFeature(myAndroidFacet));
    FontFamilyCreator creator = new FontFamilyCreator(myAndroidFacet);
    FontDetail font = FontTestUtils.createFontDetail("Roboto", 400, 100, false);
    String newValue = creator.createFontFamily(font, "roboto", true);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/roboto");
    assertThat(FontTestUtils.getResourceFileContent(baseFacet, ResourceFolderType.FONT, "roboto.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:app=\"http://schemas.android.com/apk/res-auto\"%n" +
      "        app:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        app:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        app:fontProviderQuery=\"Roboto\"%n" +
      "        app:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
    assertThat(FontTestUtils.getResourceFileContent(baseFacet, ResourceFolderType.VALUES, "font_certs.xml"))
      .isEqualTo(String.format(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
        "<resources>%n" +
        "    <array name=\"com_google_android_gms_fonts_certs\">%n" +
        "        <item>@array/com_google_android_gms_fonts_certs_dev</item>%n" +
        "        <item>@array/com_google_android_gms_fonts_certs_prod</item>%n" +
        "    </array>%n" +
        "    <string-array name=\"com_google_android_gms_fonts_certs_dev\">%n" +
        "        <item>%n" +
        "            MIIEqDCCA5CgAwIBAgIJANWFuGx90071MA0GCSqGSIb3DQEBBAUAMIGUMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9pZDEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDEiMCAGCSqGSIb3DQEJARYTYW5kcm9pZEBhbmRyb2lkLmNvbTAeFw0wODA0MTUyMzM2NTZaFw0zNTA5MDEyMzM2NTZaMIGUMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9pZDEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDEiMCAGCSqGSIb3DQEJARYTYW5kcm9pZEBhbmRyb2lkLmNvbTCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBANbOLggKv+IxTdGNs8/TGFy0PTP6DHThvbbR24kT9ixcOd9W+EaBPWW+wPPKQmsHxajtWjmQwWfna8mZuSeJS48LIgAZlKkpFeVyxW0qMBujb8X8ETrWy550NaFtI6t9+u7hZeTfHwqNvacKhp1RbE6dBRGWynwMVX8XW8N1+UjFaq6GCJukT4qmpN2afb8sCjUigq0GuMwYXrFVee74bQgLHWGJwPmvmLHC69EH6kWr22ijx4OKXlSIx2xT1AsSHee70w5iDBiK4aph27yH3TxkXy9V89TDdexAcKk/cVHYNnDBapcavl7y0RiQ4biu8ymM8Ga/nmzhRKya6G0cGw8CAQOjgfwwgfkwHQYDVR0OBBYEFI0cxb6VTEM8YYY6FbBMvAPyT+CyMIHJBgNVHSMEgcEwgb6AFI0cxb6VTEM8YYY6FbBMvAPyT+CyoYGapIGXMIGUMQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEQMA4GA1UEChMHQW5kcm9pZDEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDEiMCAGCSqGSIb3DQEJARYTYW5kcm9pZEBhbmRyb2lkLmNvbYIJANWFuGx90071MAwGA1UdEwQFMAMBAf8wDQYJKoZIhvcNAQEEBQADggEBABnTDPEF+3iSP0wNfdIjIz1AlnrPzgAIHVvXxunW7SBrDhEglQZBbKJEk5kT0mtKoOD1JMrSu1xuTKEBahWRbqHsXclaXjoBADb0kkjVEJu/Lh5hgYZnOjvlba8Ld7HCKePCVePoTJBdI4fvugnL8TsgK05aIskyY0hKI9L8KfqfGTl1lzOv2KoWD0KWwtAWPoGChZxmQ+nBli+gwYMzM1vAkP+aayLe0a1EQimlOalO762r0GXO0ks+UeXde2Z4e+8S/pf7pITEI/tP+MxJTALw9QUWEv9lKTk+jkbqxbsh8nfBUapfKqYn0eidpwq2AzVp3juYl7//fKnaPhJD9gs=%n" +
        "        </item>%n" +
        "    </string-array>%n" +
        "    <string-array name=\"com_google_android_gms_fonts_certs_prod\">%n" +
        "        <item>%n" +
        "            MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZApxsdyxMJZ2JORland2qSGT2y5b+3JKkedxiLDmpHpDsz2WCbdxgxRczfey5YZnTJ4VZbH0xqWVW/8lGmPav5xVwnIiJS6HXk+BVKZF+JcWjAsb/GEuq/eFdpuzSqeYTcfi6idkyugwfYwXFU1+5fZKUaRKYCwkkFQVfcAs1fXA5V+++FGfvjJ/CxURaSxaBvGdGDhfXE28LWuT9ozCl5xw4Yq5OGazvV24mZVSoOO0yZ31j7kYvtwYK6NeADwbSxDdJEqO4k//0zOHKrUiGYXtqw/A0LFFtqoZKFjnkCAQOjgdkwgdYwHQYDVR0OBBYEFMd9jMIhF1Ylmn/Tgt9r45jk14alMIGmBgNVHSMEgZ4wgZuAFMd9jMIhF1Ylmn/Tgt9r45jk14aloXikdjB0MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEUMBIGA1UEChMLR29vZ2xlIEluYy4xEDAOBgNVBAsTB0FuZHJvaWQxEDAOBgNVBAMTB0FuZHJvaWSCCQDC4IdGZEowjTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBAUAA4IBAQBt0lLO74UwLDYKqs6Tm8/yzKkEu116FmH4rkaymUIE0P9KaMftGlMexFlaYjzmB2OxZyl6euNXEsQH8gjwyxCUKRJNexBiGcCEyj6z+a1fuHHvkiaai+KL8W1EyNmgjmyy8AW7P+LLlkR+ho5zEHatRbM/YAnqGcFh5iZBqpknHf1SKMXFh4dd239FJ1jWYfbMDMy3NS5CTMQ2XFI1MvcyUTdZPErjQfTbQe3aDQsQcafEQPD+nqActifKZ0Np0IS9L9kR/wbNvyz6ENwPiTrjV2KRkEjH78ZMcUQXg0L3BYHJ3lc69Vs5Ddf9uUGGMYldX3WfMBEmh/9iFBDAaTCK%n" +
        "        </item>%n" +
        "    </string-array>%n" +
        "</resources>%n"
      ));
    assertThat(FontTestUtils.getResourceFileContent(baseFacet, ResourceFolderType.VALUES, "preloaded_fonts.xml"))
      .isEqualTo(String.format(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
        "<resources>%n" +
        "    <array name=\"preloaded_fonts\" translatable=\"false\">%n" +
        "        <item>@font/roboto</item>%n" +
        "    </array>%n" +
        "</resources>%n"
      ));
    assertThat(StringUtil.strip(Manifest.getMainManifest(baseFacet).getXmlTag().getText(), CharFilter.NOT_WHITESPACE_FILTER)).contains(
      StringUtil.strip(
        "        <meta-data\n" +
        "            android:name=\"preloaded_fonts\"\n" +
        "            android:resource=\"@array/preloaded_fonts\" />", CharFilter.NOT_WHITESPACE_FILTER)
    );
    verifyResourceDoesNotExist(myAndroidFacet, ResourceFolderType.FONT, "roboto.xml");
    verifyResourceDoesNotExist(myAndroidFacet, ResourceFolderType.VALUES, "font_certs.xml");
    verifyResourceDoesNotExist(myAndroidFacet, ResourceFolderType.VALUES, "preloaded_fonts.xml");
  }

  private static void verifyResourceDoesNotExist(@NotNull AndroidFacet facet, @NotNull ResourceFolderType type, @NotNull String fileName) {
    @SuppressWarnings("deprecation")
    VirtualFile resourceDirectory = ResourceFolderManager.getInstance(facet).getPrimaryFolder();
    if (resourceDirectory == null) {
      return;
    }
    VirtualFile resourceFolder = resourceDirectory.findChild(type.getName());
    if (resourceFolder == null) {
      return;
    }
    VirtualFile file = resourceFolder.findChild(fileName);
    if (resourceFolder == null) {
      return;
    }
    file.refresh(false, false);
    throw new RuntimeException("This resource file should not exist: " + file.getPath());
  }
}
