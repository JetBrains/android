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

import com.android.tools.idea.jps.model.JpsAndroidGradleModuleExtension;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModule;

public class JpsAndroidGradleModuleExtensionImpl extends JpsElementBase<JpsAndroidGradleModuleExtensionImpl>
  implements JpsAndroidGradleModuleExtension {

  public static final JpsElementChildRoleBase<JpsAndroidGradleModuleExtension> KIND =
    JpsElementChildRoleBase.create("android gradle extension");
  @NotNull private final JpsAndroidGradleModuleProperties myProperties;

  public JpsAndroidGradleModuleExtensionImpl(@NotNull JpsAndroidGradleModuleProperties properties) {
    myProperties = properties;
  }

  @Nullable
  @Override
  public JpsModule getModule() {
    return (JpsModule)getParent();
  }

  @NotNull
  @Override
  public JpsAndroidGradleModuleExtensionImpl createCopy() {
    return new JpsAndroidGradleModuleExtensionImpl(XmlSerializerUtil.createCopy(myProperties));
  }

  @Override
  public void applyChanges(@NotNull JpsAndroidGradleModuleExtensionImpl modified) {
    XmlSerializerUtil.copyBean(modified.myProperties, myProperties);
    fireElementChanged();
  }

  @NotNull
  @Override
  public JpsAndroidGradleModuleProperties getProperties() {
    return myProperties;
  }
}
