package com.android.tools.idea.jps.model.impl;

import com.android.tools.idea.jps.model.JpsGradleModuleExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

/**
 * @author Eugene.Kudelevsky
 */
public class JpsGradleModuleExtensionImpl extends JpsElementBase<JpsGradleModuleExtensionImpl> implements JpsGradleModuleExtension {

  public static final JpsElementChildRoleBase<JpsGradleModuleExtension> ROLE = JpsElementChildRoleBase.create("gradle extension");

  @NotNull
  @Override
  public JpsGradleModuleExtensionImpl createCopy() {
    return new JpsGradleModuleExtensionImpl();
  }

  @Override
  public void applyChanges(@NotNull JpsGradleModuleExtensionImpl modified) {
  }
}

