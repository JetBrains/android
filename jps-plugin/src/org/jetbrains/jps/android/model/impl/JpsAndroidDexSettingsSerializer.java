// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.android.model.impl;

import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidExtensionService;
import org.jetbrains.jps.android.model.impl.JpsAndroidDexCompilerConfigurationImpl.MyState;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

public final class JpsAndroidDexSettingsSerializer extends JpsProjectExtensionSerializer {
  private static final SkipDefaultValuesSerializationFilters FILTERS = new SkipDefaultValuesSerializationFilters();

  public JpsAndroidDexSettingsSerializer() {
    super("androidDexCompiler.xml", "AndroidDexCompilerConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject element, @NotNull Element componentTag) {
    MyState state = XmlSerializer.deserialize(componentTag, MyState.class);
    JpsAndroidExtensionService.getInstance().setDexCompilerConfiguration(element, new JpsAndroidDexCompilerConfigurationImpl(state));
  }

  @Override
  public void saveExtension(@NotNull JpsProject element, @NotNull Element componentTag) {
    JpsAndroidDexCompilerConfigurationImpl configuration =
      (JpsAndroidDexCompilerConfigurationImpl)JpsAndroidExtensionService.getInstance().getDexCompilerConfiguration(element);
    if (configuration != null) {
      XmlSerializer.serializeInto(configuration.getState(), componentTag, FILTERS);
    }
  }
}
