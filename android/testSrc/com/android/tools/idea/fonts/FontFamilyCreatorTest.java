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

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.google.common.base.Charsets;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Collections;

import static com.android.tools.idea.fonts.FontFamily.FontSource.DOWNLOADABLE;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;

public class FontFamilyCreatorTest extends FontTestCase {
  private FontFamilyCreator myCreator;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCreator = new FontFamilyCreator(myFacet);
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  public void testCreateFontUsingAppCompat() throws Exception {
    setMinSdk("25");

    FontDetail font = createFontDetail("Roboto", 400, 100, false);
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
      "    <string-array name=\"com_google_android_gms_fonts_certs\" translatable=\"false\">%n" +
      "        <item>67f20865aaa676c9ac84ae022aea8d4a37003665</item>%n" +
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
    assertThat(myFacet.getManifest().getXmlTag().getText()).isEqualTo(
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
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

  public void testCreateFontUsingLevel26() throws Exception {
    setMinSdk("26");

    FontDetail font = createFontDetail("Alegreya Sans SC", 900, 80, true);
    String newValue = myCreator.createFontFamily(font, "alegreya_sans_sc", true);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/alegreya_sans_sc");
    assertThat(getFontFileContent("alegreya_sans_sc.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
      "        android:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        android:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        android:fontProviderQuery=\"name=Alegreya Sans SC&amp;weight=900&amp;italics=1&amp;width=80\"%n" +
      "        android:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
  }

  public void testCreateFontUsingPreviewO() throws Exception {
    setMinSdk("O");

    FontDetail font = createFontDetail("Alegreya Sans SC", 900, 80, true);
    String newValue = myCreator.createFontFamily(font, "alegreya_sans_sc", true);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/alegreya_sans_sc");
    assertThat(getFontFileContent("alegreya_sans_sc.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
      "        xmlns:app=\"http://schemas.android.com/apk/res-auto\"%n" +
      "        android:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        android:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        android:fontProviderQuery=\"name=Alegreya Sans SC&amp;weight=900&amp;italics=1&amp;width=80\"%n" +
      "        android:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\"%n" +
      "        app:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        app:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        app:fontProviderQuery=\"name=Alegreya Sans SC&amp;weight=900&amp;italics=1&amp;width=80\"%n" +
      "        app:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
  }

  public void testCreateMultipleFiles() throws Exception {
    setMinSdk("26");

    myCreator.createFontFamily(createFontDetail("Roboto", 400, 100, false), "roboto", true);
    myCreator.createFontFamily(createFontDetail("Alegreya Sans SC", 900, 80, true), "alegreya_sans_sc", true);
    myCreator.createFontFamily(createFontDetail("Aladin", 400, 100, false), "aladin", true);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(getFontFileContent("roboto.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
      "        android:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        android:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        android:fontProviderQuery=\"Roboto\"%n" +
      "        android:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
    assertThat(getFontFileContent("alegreya_sans_sc.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
      "        android:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        android:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        android:fontProviderQuery=\"name=Alegreya Sans SC&amp;weight=900&amp;italics=1&amp;width=80\"%n" +
      "        android:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
    assertThat(getFontFileContent("aladin.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
      "        android:fontProviderAuthority=\"com.google.android.gms.fonts\"%n" +
      "        android:fontProviderPackage=\"com.google.android.gms\"%n" +
      "        android:fontProviderQuery=\"Aladin\"%n" +
      "        android:fontProviderCerts=\"@array/com_google_android_gms_fonts_certs\">%n" +
      "</font-family>%n"
    ));
    assertThat(getValuesFileContent("font_certs.xml")).isEqualTo(String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<resources>%n" +
      "    <string-array name=\"com_google_android_gms_fonts_certs\" translatable=\"false\">%n" +
      "        <item>67f20865aaa676c9ac84ae022aea8d4a37003665</item>%n" +
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
    assertThat(myFacet.getManifest().getXmlTag().getText()).isEqualTo(
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "    <uses-sdk android:minSdkVersion=\"26\"\n" +
      "              android:targetSdkVersion=\"26\" />\n" +
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
    FontDetail font = createFontDetail("Raleway", 700, 100, true);
    String newValue = myCreator.createFontFamily(font, "raleway_bold_italic", false);
    UIUtil.dispatchAllInvocationEvents();
    assertThat(newValue).isEqualTo("@font/raleway_bold_italic");
    assertThat(getFontFileContent("raleway_bold_italic.ttf")).isEqualTo("TrueType file");
  }

  public void testGetFontName() {
    FontDetail font = createFontDetail("Raleway", 700, 100, true);
    assertThat(FontFamilyCreator.getFontName(font)).isEqualTo("raleway_bold_italic");
  }

  @NotNull
  private static FontDetail createFontDetail(@NotNull String fontName, int weight, int width, boolean italics) {
    String folderName = DownloadableFontCacheServiceImpl.convertNameToFilename(fontName);
    String urlStart = "http://dontcare/fonts/" + folderName + "/v6/";
    FontFamily family = new FontFamily(GoogleFontProvider.INSTANCE, DOWNLOADABLE, fontName, urlStart + "some.ttf", null,
                                       Collections.singletonList(new FontDetail.Builder(weight, width, italics, urlStart + "other.ttf", null)));
    return family.getFonts().get(0);
  }

  @NotNull
  private String getFontFileContent(@NotNull String fontFileName) throws IOException {
    return getResourceFileContent(ResourceFolderType.FONT, fontFileName);
  }

  @NotNull
  private String getValuesFileContent(@NotNull String valuesFileName) throws IOException {
    return getResourceFileContent(ResourceFolderType.VALUES, valuesFileName);
  }

  @NotNull
  private String getResourceFileContent(@NotNull ResourceFolderType type, @NotNull String fileName) throws IOException {
    @SuppressWarnings("deprecation")
    VirtualFile resourceDirectory = checkNotNull(myFacet.getPrimaryResourceDir());
    VirtualFile resourceFolder = checkNotNull(resourceDirectory.findChild(type.getName()));
    VirtualFile file = checkNotNull(resourceFolder.findChild(fileName));
    file.refresh(false, false);
    return new String(file.contentsToByteArray(), CharsetToolkit.UTF8_CHARSET);
  }

  @SuppressWarnings("SameParameterValue")
  private void addFontFileToFontCache(@NotNull String fontFolder, @NotNull String versionFolder, @NotNull String fontFileName) throws IOException {
    File folder = makeFile(myFontPath, GoogleFontProvider.GOOGLE_FONT_AUTHORITY, "fonts", fontFolder, versionFolder);
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
      "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
      "    <uses-sdk android:minSdkVersion=\"%1$s\"\n" +
      "              android:targetSdkVersion=\"%1$s\" />\n" +
      "    <application android:icon=\"@drawable/icon\">\n" +
      "    </application>\n" +
      "</manifest>\n", level);
    myFixture.addFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, xml);
  }
}
