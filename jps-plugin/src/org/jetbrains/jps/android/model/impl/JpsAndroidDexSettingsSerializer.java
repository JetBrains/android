package org.jetbrains.jps.android.model.impl;

import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.JpsAndroidExtensionService;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;

public class JpsAndroidDexSettingsSerializer extends JpsProjectExtensionSerializer {

  private static final SkipDefaultValuesSerializationFilters FILTERS = new SkipDefaultValuesSerializationFilters();

  public JpsAndroidDexSettingsSerializer() {
    super("androidDexCompiler.xml", "AndroidDexCompilerConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsProject element, @NotNull Element componentTag) {
    JpsAndroidDexCompilerConfigurationImpl.MyState state = XmlSerializer.deserialize(
      componentTag, JpsAndroidDexCompilerConfigurationImpl.MyState.class);
    JpsAndroidExtensionService.getInstance().setDexCompilerConfiguration(element, new JpsAndroidDexCompilerConfigurationImpl(state));
  }
}
