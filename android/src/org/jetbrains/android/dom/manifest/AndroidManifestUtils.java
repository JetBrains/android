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
import com.android.builder.model.ProductFlavor;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import java.util.ArrayList;
import java.util.Collection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Methods for working with Android manifests.
 */
public class AndroidManifestUtils {
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

    // That's how AGP works today: in apps the applicationId from the model is used with the ".test" suffix (ignoring the manifest), in libs
    // there is no applicationId and the package name from the manifest is used with the suffix.
    String applicationId = androidFacet.getConfiguration().isLibraryProject() ? getPackageName(androidFacet) : flavor.getApplicationId();

    if (StringUtil.isNotEmpty(applicationId)) {
      return applicationId + ".test";
    }

    return null;
  }

  public static boolean isRequiredAttribute(@NotNull XmlName attrName, @NotNull DomElement element) {
    if (element instanceof CompatibleScreensScreen && SdkConstants.ANDROID_URI.equals(attrName.getNamespaceKey())) {
      final String localName = attrName.getLocalName();
      return "screenSize".equals(localName) || "screenDensity".equals(localName);
    }
    return false;
  }

  @Nullable
  public static Collection<String> getCustomPermissions(@NotNull AndroidFacet androidFacet) {
    // TODO(b/110188226): read the merged manifest
    XmlFile manifest = getManifest(androidFacet);
    return manifest == null? null : getAndroidNamesForTags(manifest, SdkConstants.TAG_PERMISSION);
  }

  @Nullable
  public static Collection<String> getCustomPermissionGroups(@NotNull AndroidFacet androidFacet) {
    // TODO(b/110188226): read the merged manifest
    XmlFile manifest = getManifest(androidFacet);
    return manifest == null? null : getAndroidNamesForTags(manifest, SdkConstants.TAG_PERMISSION_GROUP);
  }

  @Nullable
  private static XmlFile getManifest(@NotNull AndroidFacet androidFacet) {
    VirtualFile file = androidFacet.getManifestFile();
    if (file == null) {
      return null;
    }
    PsiFile manifest = AndroidPsiUtils.getPsiFileSafely(androidFacet.getModule().getProject(), file);
    if (!(manifest instanceof XmlFile)) {
      return null;
    }
    return (XmlFile) manifest;
  }

  /**
   * Returns the android:name attribute of each {@link XmlTag} of the given type in the {@link XmlFile}.
   */
  private static Collection<String> getAndroidNamesForTags(@NotNull XmlFile xmlFile, @NotNull String tagName) {
    ArrayList<String> androidNames = new ArrayList<>();
    xmlFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        if (!tagName.equals(tag.getName())) {
          return;
        }
        String androidName = tag.getAttributeValue(SdkConstants.ATTR_NAME, SdkConstants.ANDROID_URI);
        if (androidName != null) {
          androidNames.add(androidName);
        }
      }
    });
    return androidNames;
  }

  private AndroidManifestUtils() {}
}
