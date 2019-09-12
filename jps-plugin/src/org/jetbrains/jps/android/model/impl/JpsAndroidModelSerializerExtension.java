// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.android.model.impl;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetConfigurationSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
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
        return new JpsAndroidModuleExtensionImpl(XmlSerializer.deserialize(facetConfigurationElement, JpsAndroidModuleProperties.class));
      }

      @Override
      protected void saveExtension(JpsAndroidModuleExtension extension, Element facetConfigurationTag, JpsModule module) {
        XmlSerializer.serializeInto(((JpsAndroidModuleExtensionImpl)extension).getProperties(), facetConfigurationTag);
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

      @Override
      public void saveProperties(@NotNull JpsSimpleElement<JpsAndroidSdkProperties> properties, @NotNull Element element) {
        String jdkName = properties.getData().getJdkName();
        if (jdkName != null) {
          element.setAttribute("jdk", jdkName);
        }
        String buildTarget = properties.getData().getBuildTargetHashString();
        if (buildTarget != null) {
          element.setAttribute("sdk", buildTarget);
        }
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
