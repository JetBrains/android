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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidManifestUtils {

  public static boolean isRequiredAttribute(@NotNull XmlName attrName, @NotNull DomElement element) {
    if (element instanceof CompatibleScreensScreen && SdkConstants.NS_RESOURCES.equals(attrName.getNamespaceKey())) {
      final String localName = attrName.getLocalName();
      return "screenSize".equals(localName) || "screenDensity".equals(localName);
    }
    return false;
  }

  @Nullable
  public static String getPackageName(@NotNull AndroidFacet androidFacet) {
    return CachedValuesManager.getManager(androidFacet.getModule().getProject()).getCachedValue(androidFacet, () -> {
      // TODO(namespaces): read the merged manifest.
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

  private AndroidManifestUtils() {}
}
