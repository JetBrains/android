/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.manifest;

import com.android.SdkConstants;
import com.android.tools.idea.res.aar.ProtoXmlPullParser;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.*;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.res.FileResourceOpener.PROTO_XML_LEAD_BYTE;

/**
 * Methods for working with Android manifests.
 */
public class AndroidManifestUtils {

  /**
   * Reads package name from the AndroidManifest.xml of an AAR. Both, AARv1 and AARv2, formats are supported.
   *
   * @param aarDir the directory containing unpacked contents of an AAR, or unpacked contents of res.apk
   * @return the package name from the manifest
   * @deprecated this method assumes a certain layout of the unzipped AAR folder, when possible use other methods directly on the file you
   *             want to read.
   */
  @Nullable
  @Deprecated
  public static String getAarPackageName(@NotNull File aarDir) throws IOException {
    try {
      return getPackageNameFromManifestFile(new File(aarDir, FN_ANDROID_MANIFEST_XML));
    } catch (FileNotFoundException e) {
      File resApkFile = new File(aarDir, FN_RESOURCE_STATIC_LIBRARY);
      try (ZipFile zipFile = new ZipFile(resApkFile)) {
        return getPackageNameFromResApk(zipFile);
      }
    }
  }

  /**
   * Reads package name from the AndroidManifest.xml file in the given directory. The the AndroidManifest.xml
   * file can be in either text or proto format.
   *
   * @param aarDir the directory containing the AndroidManifest.xml file
   * @return the package name from the manifest
   */
  @Nullable
  public static String getPackageNameFromManifestFile(@NotNull File manifestFile) throws IOException {
    try (InputStream stream = new BufferedInputStream(new FileInputStream(manifestFile))) {
      return getPackageName(stream);
    } catch (XmlPullParserException e) {
      throw new IOException("File " + manifestFile.getPath() + " has invalid format");
    }
  }

  /**
   * Reads package name from the AndroidManifest.xml stored inside the given res.apk file.
   *
   * @param resApk the res.apk file
   * @return the package name from the manifest
   */
  @Nullable
  public static String getPackageNameFromResApk(@NotNull ZipFile resApk) throws IOException {
    ZipEntry zipEntry = resApk.getEntry(ANDROID_MANIFEST_XML);
    if (zipEntry == null) {
      throw new IOException("\"" + ANDROID_MANIFEST_XML + "\" not found in " + resApk.getName());
    }

    try (InputStream stream = new BufferedInputStream(resApk.getInputStream(zipEntry))) {
      return getPackageName(stream);
    } catch (XmlPullParserException e) {
      throw new IOException("Invalid " + ANDROID_MANIFEST_XML + " in " + resApk.getName());
    }
  }

  private static String getPackageName(InputStream stream) throws XmlPullParserException, IOException {
    stream.mark(1);
    // Instantiate an XML pull parser based on the contents of the stream.
    XmlPullParser parser;
    int c = stream.read();
    stream.reset();
    if (c == PROTO_XML_LEAD_BYTE) {
      parser = new ProtoXmlPullParser(); // Parser for proto XML used in AARs.
    } else {
      parser = new KXmlParser(); // Parser for regular text XML.
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
    }
    parser.setInput(stream, null);
    if (parser.nextTag() == XmlPullParser.START_TAG) {
      return parser.getAttributeValue(null, "package");
    }
    return null;
  }

  @Nullable
  public static String getPackageName(@NotNull AndroidFacet androidFacet) {
    return CachedValuesManager.getManager(androidFacet.getModule().getProject()).getCachedValue(androidFacet, () -> {
      // TODO(b/110188226): read the merged manifest
      Manifest manifest = androidFacet.getManifest();
      if (manifest != null) {
        String packageName = manifest.getPackage().getValue();
        if (!StringUtil.isEmptyOrSpaces(packageName)) {
          return CachedValueProvider.Result.create(packageName, manifest.getXmlTag());
        }
      }
      return null;
    });
  }

  public static boolean isRequiredAttribute(@NotNull XmlName attrName, @NotNull DomElement element) {
    if (element instanceof CompatibleScreensScreen && SdkConstants.NS_RESOURCES.equals(attrName.getNamespaceKey())) {
      final String localName = attrName.getLocalName();
      return "screenSize".equals(localName) || "screenDensity".equals(localName);
    }
    return false;
  }

  @Nullable
  public static Collection<String> getCustomPermissions(@NotNull AndroidFacet androidFacet) {
    // TODO(b/110188226): read the merged manifest
    Manifest manifest = androidFacet.getManifest();
    if (manifest == null) {
      return null;
    }

    return manifest.getPermissions()
                   .stream()
                   .map(permission -> permission.getName().getValue())
                   .filter(Objects::nonNull)
                   .collect(Collectors.toList());
  }

  @Nullable
  public static Collection<String> getCustomPermissionGroups(@NotNull AndroidFacet androidFacet) {
    // TODO(b/110188226): read the merged manifest
    Manifest manifest = androidFacet.getManifest();
    if (manifest == null) {
      return null;
    }

    return manifest.getPermissionGroups()
                   .stream()
                   .map(group -> group.getName())
                   .map(name -> name.getValue())
                   .filter(Objects::nonNull)
                   .collect(Collectors.toList());
  }

  private AndroidManifestUtils() {}
}
