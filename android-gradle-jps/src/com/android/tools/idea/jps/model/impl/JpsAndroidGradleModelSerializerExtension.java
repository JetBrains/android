/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.jps.model.impl;

import com.android.tools.idea.jps.AndroidGradleJps;
import com.android.tools.idea.jps.model.JpsAndroidGradleModuleExtension;
import com.google.common.collect.ImmutableList;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.android.model.AndroidModelSerializationConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.facet.JpsFacetConfigurationSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;

import java.util.List;

@SuppressWarnings("ConstantConditions")
public class JpsAndroidGradleModelSerializerExtension extends JpsModelSerializerExtension {
  private static final List<? extends JpsFacetConfigurationSerializer<JpsAndroidGradleModuleExtension>> FACET_PROPERTY_LOADERS =
    ImmutableList.of(new JpsAndroidGradleFacetConfigurationSerializer());

  private static final JpsAndroidSdkPropertiesSerializer SDK_PROPERTY_LOADER = new JpsAndroidSdkPropertiesSerializer();

  private static final String EXTERNAL_SYSTEM_ID_ATTRIBUTE = "external.system.id";
  private static final String GRADLE_EXTERNAL_SYSTEM_ID = "GRADLE";

  @NotNull
  @Override
  public List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return FACET_PROPERTY_LOADERS;
  }

  @Override
  public void loadModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    final String externalSystemId = rootElement.getAttributeValue(EXTERNAL_SYSTEM_ID_ATTRIBUTE);
    if (GRADLE_EXTERNAL_SYSTEM_ID.equals(externalSystemId)) {
      AndroidGradleJps.getOrCreateGradleSystemExtension(module);
    }
  }

  @Override
  public void saveModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    if (AndroidGradleJps.getGradleSystemExtension(module) != null) {
      rootElement.setAttribute(EXTERNAL_SYSTEM_ID_ATTRIBUTE, GRADLE_EXTERNAL_SYSTEM_ID);
    }
  }

  @NotNull
  @Override
  public List<? extends JpsSdkPropertiesSerializer<?>> getSdkPropertiesSerializers() {
    return ImmutableList.of(SDK_PROPERTY_LOADER);
  }

  private static class JpsAndroidGradleFacetConfigurationSerializer
    extends JpsFacetConfigurationSerializer<JpsAndroidGradleModuleExtension> {

    JpsAndroidGradleFacetConfigurationSerializer() {
      super(JpsAndroidGradleModuleExtensionImpl.KIND, AndroidModelSerializationConstants.ANDROID_GRADLE_FACET_ID, AndroidModelSerializationConstants.ANDROID_GRADLE_FACET_NAME);
    }

    @Override
    protected JpsAndroidGradleModuleExtension loadExtension(@NotNull Element facetConfigurationElement,
                                                            String name,
                                                            JpsElement parent,
                                                            JpsModule module) {
      JpsAndroidGradleModuleProperties properties =
        XmlSerializer.deserialize(facetConfigurationElement, JpsAndroidGradleModuleProperties.class);
      return new JpsAndroidGradleModuleExtensionImpl(properties);
    }

    @Override
    protected void saveExtension(JpsAndroidGradleModuleExtension extension, Element facetConfigurationTag, JpsModule module) {
      XmlSerializer.serializeInto(extension.getProperties(), facetConfigurationTag);
    }
  }

  private static class JpsAndroidSdkPropertiesSerializer extends JpsSdkPropertiesSerializer<JpsSimpleElement<JpsAndroidSdkProperties>> {
    private static final String JDK_ATTR = "jdk";
    private static final String SDK_ATTR = "sdk";

    JpsAndroidSdkPropertiesSerializer() {
      super("Android SDK", JpsAndroidSdkType.INSTANCE);
    }

    @NotNull
    @Override
    public JpsSimpleElement<JpsAndroidSdkProperties> loadProperties(@Nullable Element propertiesElement) {
      String buildTarget = null;
      String jdkName = null;
      if (propertiesElement != null) {
        buildTarget = propertiesElement.getAttributeValue(SDK_ATTR);
        jdkName = propertiesElement.getAttributeValue(JDK_ATTR);
      }
      return JpsElementFactory.getInstance().createSimpleElement(new JpsAndroidSdkProperties(buildTarget, jdkName));
    }

    @Override
    public void saveProperties(@NotNull JpsSimpleElement<JpsAndroidSdkProperties> properties, @NotNull Element element) {
      String jdkName = properties.getData().getJdkName();
      if (jdkName != null) {
        element.setAttribute(JDK_ATTR, jdkName);
      }
      String buildTarget = properties.getData().getBuildTargetHashString();
      if (buildTarget != null) {
        element.setAttribute(SDK_ATTR, buildTarget);
      }
    }
  }
}
