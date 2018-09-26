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

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.NS_RESOURCES;
import static com.android.tools.idea.res.FileResourceReader.PROTO_XML_LEAD_BYTE;

import com.android.builder.model.ProductFlavor;
import com.android.ide.common.util.PathString;
import com.android.ide.common.util.PathStrings;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.res.aar.ProtoXmlPullParser;
import com.android.tools.idea.util.FileExtensions;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Methods for working with Android manifests.
 */
public class AndroidManifestUtils {
  /**
   * Reads package name from the AndroidManifest.xml file in the given directory. The the AndroidManifest.xml
   * file can be in either text or proto format.
   *
   * @param pathOpener path opener that can produce input streams for
   * @param manifestFile the AndroidManifest.xml file
   * @return the package name from the manifest
   */
  @Nullable
  public static String getPackageNameFromManifestFile(@NotNull PathString manifestFile) throws IOException {
    try (InputStream stream = FileExtensions.buffered(PathStrings.inputStream(manifestFile))) {
      return getPackageName(stream);
    }
    catch (XmlPullParserException e) {
      throw new IOException("File " + manifestFile + " has invalid format");
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
      if (manifest == null) {
        // TODO(b/110188226): implement a ModificationTracker for the set of existing manifest files.
        // For now we just recompute every time, which is safer than never recomputing.
        return CachedValueProvider.Result.create(null, ModificationTracker.EVER_CHANGED);
      }
      String packageName = manifest.getPackage().getValue();
      return CachedValueProvider.Result.create(StringUtil.nullize(packageName, true), manifest.getXmlTag());
    });
  }

  @Nullable
  public static String getTestPackageName(@NotNull AndroidFacet androidFacet) {
    AndroidModuleModel moduleModel = AndroidModuleModel.get(androidFacet);
    if (moduleModel == null) {
      return null;
    }
    ProductFlavor flavor = moduleModel.getSelectedVariant().getMergedFlavor();
    String testApplicationId = flavor.getTestApplicationId();
    if (testApplicationId != null) {
      return testApplicationId;
    }

    String applicationId = flavor.getApplicationId();
    if (applicationId != null) {
      return applicationId + ".test";
    }

    return null;
  }

  public static boolean isRequiredAttribute(@NotNull XmlName attrName, @NotNull DomElement element) {
    if (element instanceof CompatibleScreensScreen && NS_RESOURCES.equals(attrName.getNamespaceKey())) {
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
