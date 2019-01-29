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

package org.jetbrains.android;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.base.Strings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlSchemaProvider;
import com.google.common.collect.ImmutableSet;
import gnu.trove.THashMap;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.dom.raw.RawDomFileDescription;
import org.jetbrains.android.dom.xml.XmlResourceDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

public class AndroidXmlSchemaProvider extends XmlSchemaProvider {
  private static final Key<Map<String, CachedValue<XmlFile>>> DESCRIPTORS_MAP_IN_MODULE = Key.create("ANDROID_DESCRIPTORS_MAP_IN_MODULE");

  @Override
  public XmlFile getSchema(@NotNull @NonNls String url, @Nullable final Module module, @NotNull PsiFile baseFile) {
    if (module == null || AndroidFacet.getInstance(module) == null) return null;

    Map<String, CachedValue<XmlFile>> descriptors = module.getUserData(DESCRIPTORS_MAP_IN_MODULE);
    if (descriptors == null) {
      descriptors = new THashMap<>();
      module.putUserData(DESCRIPTORS_MAP_IN_MODULE, descriptors);
    }
    CachedValue<XmlFile> reference = descriptors.get(url);
    if (reference != null) {
      return reference.getValue();
    }
    CachedValuesManager manager = CachedValuesManager.getManager(module.getProject());
    reference = manager.createCachedValue(() -> {
      final URL resource = AndroidXmlSchemaProvider.class.getResource("android.xsd");
      final VirtualFile fileByURL = VfsUtil.findFileByURL(resource);
      XmlFile result = (XmlFile)PsiManager.getInstance(module.getProject()).findFile(fileByURL).copy();
      return new CachedValueProvider.Result<>(result, PsiModificationTracker.MODIFICATION_COUNT);
    }, false);

    descriptors.put(url, reference);
    return reference.getValue();
  }

  @Override
  public boolean isAvailable(@NotNull final XmlFile file) {
    final PsiFile f = file.getOriginalFile();
    if (!(f instanceof XmlFile)) {
      return false;
    }
    final XmlFile originalFile = (XmlFile)f;

    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> {
      if (isXmlResourceFile(originalFile) || ManifestDomFileDescription.isManifestFile(originalFile) ||
          RawDomFileDescription.isRawFile(originalFile)) {
        return AndroidFacet.getInstance(originalFile) != null;
      }
      return false;
    });
  }

  private static boolean isXmlResourceFile(XmlFile file) {
    if (!AndroidResourceUtil.isInResourceSubdirectory(file, null)) {
      return false;
    }

    final PsiDirectory parent = file.getParent();
    if (parent == null) {
      return false;
    }

    final ResourceFolderType resType = ResourceFolderType.getFolderType(parent.getName());
    if (resType == null) {
      return false;
    }
    if (resType == ResourceFolderType.XML) {
      return XmlResourceDomFileDescription.isXmlResourceFile(file);
    }
    return resType != ResourceFolderType.RAW;
  }

  @NotNull
  @Override
  public Set<String> getAvailableNamespaces(@NotNull XmlFile file, @Nullable String tagName) {
    Set<String> result = new HashSet<>();
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet != null) {
      result.add(TOOLS_URI);
      result.add(NS_RESOURCES);
      if (ManifestDomFileDescription.isManifestFile(file)) {
        result.add(DIST_URI);
      }
      ResourceFolderType type = ResourceHelper.getFolderType(file.getOriginalFile());
      if (type != ResourceFolderType.MIPMAP && type != ResourceFolderType.RAW) {
        if (type == ResourceFolderType.DRAWABLE) {
          result.add(AAPT_URI);
        }
        // string and some xml files may contain xliff.
        if (type == ResourceFolderType.XML || type == ResourceFolderType.VALUES) {
          result.add(XLIFF_URI);
        }

        result.add(getLocalXmlNamespace(facet));

        result.addAll(getResourceNamespaces(facet));
      }
    }
    return result;
  }

  @Override
  public String getDefaultPrefix(@NotNull @NonNls String namespace, @NotNull XmlFile context) {
    if (ANDROID_URI.equals(namespace)) {
      return ANDROID_NS_NAME;
    }
    else if (namespace.equals(DIST_URI)) {
      return DIST_PREFIX;
    }
    else if (namespace.equals(TOOLS_URI)) {
      return TOOLS_PREFIX;
    }
    else if (namespace.equals(AUTO_URI) || namespace.startsWith(URI_PREFIX)) {
      return APP_PREFIX;
    }
    else if (namespace.equals(XLIFF_URI)) {
      return XLIFF_PREFIX;
    }
    else if (namespace.equals(AAPT_URI)) {
      return AAPT_PREFIX;
    }
    return null;
  }

  @NotNull
  private static String getLocalXmlNamespace(@NotNull AndroidFacet facet) {
    return ResourceRepositoryManager.getOrCreateInstance(facet).getNamespace().getXmlNamespaceUri();
  }

  @NotNull
  private static ImmutableSet<String> getResourceNamespaces(@NotNull AndroidFacet facet) {
    return ResourceRepositoryManager.getAppResources(facet).getNamespaces()
                                    .stream()
                                    .map(ResourceNamespace::getXmlNamespaceUri)
                                    .collect(toImmutableSet());
  }
}
