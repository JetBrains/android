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

import static org.jetbrains.android.util.AndroidUtils.loadDomElement;

import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.SubTagList;
import com.intellij.util.xmlb.annotations.Attribute;
import java.util.List;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.Styleable;
import org.jetbrains.android.dom.converters.AndroidPackageConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

@DefinesXml
@Styleable("AndroidManifest")
public interface Manifest extends ManifestElement {

  /**
   * Creates and returns a DOM representation of the main manifest. Note that most manifest information can be spread between multiple
   * manifest files that get merged at build time. Callers should consider using {@link com.android.tools.idea.model.MergedManifestManager}
   * or specialized methods in {@link AndroidManifestUtils}.
   *
   * <p>Calling this method may come with significant overhead, as the DOM layer needs to be initialized. In performance-critical
   * situations, callers may want to consider getting the manifest as a {@link VirtualFile} from {@link SourceProviders}.
   */
  @Nullable
  static Manifest getMainManifest(AndroidFacet facet) {
    if (facet.isDisposed()) return null;
    VirtualFile manifestFile = SourceProviderManager.getInstance(facet).getMainManifestFile();
    return manifestFile != null ? loadDomElement(facet.getModule(), manifestFile, Manifest.class) : null;
  }

  Application getApplication();

  CompatibleScreens getCompatibleScreens();

  @Convert(AndroidPackageConverter.class)
  GenericAttributeValue<String> getPackage();

  List<Instrumentation> getInstrumentations();

  List<Permission> getPermissions();
  Permission addPermission();

  List<PermissionGroup> getPermissionGroups();

  List<PermissionTree> getPermissionTrees();

  List<UsesPermission> getUsesPermissions();
  UsesPermission addUsesPermission();

  List<UsesPermissionSdk23> getUsesPermissionSdk23s();

  List<UsesSdk> getUsesSdks();
  UsesSdk addUsesSdk();

  List<UsesFeature> getUsesFeatures();
  UsesFeature addUsesFeature();

  List<UsesSplit> getUsesSplits();

  Queries addQueries();
  @SubTagList("queries")
  List<Queries> getQueries();

  @SubTagList("supports-gl-texture")
  List<SupportsGlTexture> getSupportsGlTextures();

  @SubTagList("supports-screens")
  List<SupportsScreens> getSupportsScreens();

  @SubTagList("uses-configuration")
  List<UsesConfiguration> getUsesConfigurations();

  @Attribute("versionCode")
  AndroidAttributeValue<Integer> getVersionCode();

  List<Overlay> getOverlays();

  List<AttributeTag> getAttributes();
}
