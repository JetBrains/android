package com.android.tools.idea.uibuilder.options;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableProvider;
import org.jetbrains.annotations.Nullable;

public class NlOptionsConfigurableProvider extends ConfigurableProvider {
  public NlOptionsConfigurableProvider() {
  }

  @Nullable
  @Override
  public Configurable createConfigurable() {
    return new NlOptionsConfigurable();
  }
}
