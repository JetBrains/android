// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.android.model.impl;

import com.intellij.util.xmlb.XmlSerializer;
import java.util.Collections;
import java.util.List;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacetProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetConfigurationSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;

public class JpsAndroidModelSerializerExtension extends JpsModelSerializerExtension {

  private static final List<? extends JpsFacetConfigurationSerializer<JpsAndroidModuleExtension>> FACET_PROPERTIES_LOADERS =
    Collections.singletonList(new JpsFacetConfigurationSerializer<JpsAndroidModuleExtension>(
      JpsAndroidModuleExtensionImpl.KIND,
      AndroidJpsUtil.ANDROID_FACET_TYPE_ID,
      AndroidJpsUtil.ANDROID_FACET_NAME) {

      @Override
      public JpsAndroidModuleExtension loadExtension(@NotNull Element facetConfigurationElement,
                                                     String name,
                                                     JpsElement parent, JpsModule module) {
        return new JpsAndroidModuleExtensionImpl(XmlSerializer.deserialize(facetConfigurationElement, AndroidFacetProperties.class));
      }
    });
  private static final JpsSdkPropertiesSerializer<JpsSimpleElement<JpsAndroidSdkProperties>> SDK_PROPERTIES_LOADER =
    new JpsSdkPropertiesSerializer<JpsSimpleElement<JpsAndroidSdkProperties>>("Android SDK", JpsAndroidSdkType.INSTANCE) {
      @NotNull
      @Override
      public JpsSimpleElement<JpsAndroidSdkProperties> loadProperties(@Nullable Element propertiesElement) {
        String buildTarget;
        String jdkName;
        if (propertiesElement != null) {
          buildTarget = propertiesElement.getAttributeValue("sdk");
          jdkName = propertiesElement.getAttributeValue("jdk");
        }
        else {
          buildTarget = null;
          jdkName = null;
        }
        return JpsElementFactory.getInstance().createSimpleElement(new JpsAndroidSdkProperties(buildTarget, jdkName));
      }
    };

  @NotNull
  @Override
  public List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return FACET_PROPERTIES_LOADERS;
  }

  @NotNull
  @Override
  public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
    return Collections.singletonList(new JpsAndroidFinalPackageElementSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsArtifactPropertiesSerializer<?>> getArtifactTypePropertiesSerializers() {
    return Collections.singletonList(new JpsAndroidApplicationArtifactPropertiesSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new JpsAndroidDexSettingsSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsSdkPropertiesSerializer<?>> getSdkPropertiesSerializers() {
    return Collections.singletonList(SDK_PROPERTIES_LOADER);
  }
}
