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
package com.android.tools.idea.fonts;

import static com.android.ide.common.fonts.FontDetailKt.ITALICS;
import static com.android.ide.common.fonts.FontDetailKt.NORMAL;
import static com.android.ide.common.fonts.FontProviderKt.GOOGLE_FONT_AUTHORITY;
import static com.android.tools.idea.fonts.FontTestUtils.createFontDetail;
import static com.android.tools.idea.fonts.FontTestUtils.getResourceFileContent;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.ide.common.fonts.FontDetail;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.checks.FontDetector;
import com.google.common.base.Charsets;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ui.UIUtil;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.annotations.NotNull;

public class FontFamilyCreatorTest extends FontTestCase {
  private FontFamilyCreator myCreator;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCreator = new FontFamilyCreator(myFacet);
  }

  @Override
  protected void tearDown() throws Exception {
    myCreator = null;
    super.tearDown();
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  public void testCreateFontUsingAppCompat() throws Exception {
    setMinSdk("25");

    FontDetail font = createFontDetail("Roboto", 400, 100f, NORMAL);
    String newValue = myCreator.createFontFamily(font, "roboto", true);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/roboto");
    assertThat(getFontFileContent("roboto.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:app=\"http://schemas.android.com/apk/res-auto\"%n" +
      "        app:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        app:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        app:fontProviderQuery=\"Roboto\"%n" +
      "        app:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
    assertThat(getValuesFileContent("font_certs.xml")).isEqualTo(String.format(
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
    assertThat(getValuesFileContent("preloaded_fonts.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<resources>%n" +
      "    <array name=\"preloaded_fonts\" translatable=\"false\">%n" +
      "        <item>@font/roboto</item>%n" +
      "    </array>%n" +
      "</resources>%n"
    ));
    assertThat(Manifest.getMainManifest(myFacet).getXmlTag().getText()).isEqualTo(
      "<manifest package=\"p1.p2\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "    <uses-sdk android:minSdkVersion=\"25\"\n" +
      "              android:targetSdkVersion=\"25\" />\n" +
      "    <application android:icon=\"@drawable/icon\">\n" +
      "        <meta-data\n" +
      "            android:name=\"preloaded_fonts\"\n" +
      "            android:resource=\"@array/preloaded_fonts\" />\n" +
      "    </application>\n" +
      "</manifest>"
    );
  }

  public void testCreateFontUsingFrameworkFonts() throws Exception {
    setMinSdk(String.valueOf(FontDetector.FUTURE_API_VERSION_WHERE_DOWNLOADABLE_FONTS_WORK_IN_FRAMEWORK));

    FontDetail font = createFontDetail("Alegreya Sans SC", 900, 80f, ITALICS);
    String newValue = myCreator.createFontFamily(font, "alegreya_sans_sc", true);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/alegreya_sans_sc");
    assertThat(getFontFileContent("alegreya_sans_sc.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
      "        android:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        android:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        android:fontProviderQuery=\"name=Alegreya Sans SC&amp;weight=900&amp;italic=1&amp;width=80\"%n" +
      "        android:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
  }

  public void testCreateMultipleFiles() throws Exception {
    setMinSdk("28");

    myCreator.createFontFamily(createFontDetail("Roboto", 400, 100f, NORMAL), "roboto", true);
    myCreator.createFontFamily(createFontDetail("Alegreya Sans SC", 900, 80f, ITALICS), "alegreya_sans_sc", true);
    myCreator.createFontFamily(createFontDetail("Aladin", 400, 100f, NORMAL), "aladin", true);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(getFontFileContent("roboto.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:app=\"http://schemas.android.com/apk/res-auto\"%n" +
      "        app:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        app:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        app:fontProviderQuery=\"Roboto\"%n" +
      "        app:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
    assertThat(getFontFileContent("alegreya_sans_sc.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:app=\"http://schemas.android.com/apk/res-auto\"%n" +
      "        app:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        app:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        app:fontProviderQuery=\"name=Alegreya Sans SC&amp;weight=900&amp;italic=1&amp;width=80\"%n" +
      "        app:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
    assertThat(getFontFileContent("aladin.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:app=\"http://schemas.android.com/apk/res-auto\"%n" +
      "        app:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        app:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        app:fontProviderQuery=\"Aladin\"%n" +
      "        app:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
    assertThat(getValuesFileContent("font_certs.xml")).isEqualTo(String.format(
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
    assertThat(getValuesFileContent("preloaded_fonts.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<resources>%n" +
      "    <array name=\"preloaded_fonts\" translatable=\"false\">%n" +
      "        <item>@font/aladin</item>%n" +
      "        <item>@font/alegreya_sans_sc</item>%n" +
      "        <item>@font/roboto</item>%n" +
      "    </array>%n" +
      "</resources>%n"
    ));
    assertThat(Manifest.getMainManifest(myFacet).getXmlTag().getText()).isEqualTo(
      "<manifest package=\"p1.p2\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "    <uses-sdk android:minSdkVersion=\"28\"\n" +
      "              android:targetSdkVersion=\"28\" />\n" +
      "    <application android:icon=\"@drawable/icon\">\n" +
      "        <meta-data\n" +
      "            android:name=\"preloaded_fonts\"\n" +
      "            android:resource=\"@array/preloaded_fonts\" />\n" +
      "    </application>\n" +
      "</manifest>"
    );
  }

  public void testCreateEmbeddedFont() throws Exception {
    addFontFileToFontCache("raleway", "v6", "other.ttf");
    FontDetail font = createFontDetail("Raleway", 700, 100f, ITALICS);
    String newValue = myCreator.createFontFamily(font, "raleway_bold_italic", false);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/raleway_bold_italic");
    assertThat(getFontFileContent("raleway_bold_italic.ttf")).isEqualTo("TrueType file");
  }

  public void testGetFontName() {
    FontDetail font = createFontDetail("Raleway", 700, 100f, ITALICS);
    assertThat(FontFamilyCreator.getFontName(font)).isEqualTo("raleway_bold_italic");
  }


  @NotNull
  private String getFontFileContent(@NotNull String fontFileName) throws IOException {
    return getResourceFileContent(myFacet, ResourceFolderType.FONT, fontFileName);
  }

  @NotNull
  private String getValuesFileContent(@NotNull String valuesFileName) throws IOException {
    return getResourceFileContent(myFacet, ResourceFolderType.VALUES, valuesFileName);
  }

  @SuppressWarnings("SameParameterValue")
  private void addFontFileToFontCache(@NotNull String fontFolder, @NotNull String versionFolder, @NotNull String fontFileName) throws IOException {
    File folder = makeFile(myFontPath, GOOGLE_FONT_AUTHORITY, "fonts", fontFolder, versionFolder);
    FileUtil.ensureExists(folder);
    File file = new File(folder, fontFileName);
    InputStream inputStream = new ByteArrayInputStream("TrueType file".getBytes(Charsets.UTF_8.toString()));
    try (OutputStream outputStream = new FileOutputStream(file)) {
      FileUtil.copy(inputStream, outputStream);
    }
  }

  private void setMinSdk(@NotNull String level) {
    String xml = String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
      "<manifest package=\"p1.p2\" xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "    <uses-sdk android:minSdkVersion=\"%1$s\"\n" +
      "              android:targetSdkVersion=\"%1$s\" />\n" +
      "    <application android:icon=\"@drawable/icon\">\n" +
      "    </application>\n" +
      "</manifest>\n", level);
    myFixture.addFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, xml);
  }
}
