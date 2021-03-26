// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.compiler;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "AndroidDexCompilerConfiguration",
  storages = @Storage("androidDexCompiler.xml")
)
public class AndroidDexCompilerConfiguration implements PersistentStateComponent<AndroidDexCompilerConfiguration> {
  public String VM_OPTIONS = "";
  public int MAX_HEAP_SIZE = 700;
  public boolean OPTIMIZE = true;
  public boolean FORCE_JUMBO = false;
  public boolean CORE_LIBRARY = false;
  public String PROGUARD_VM_OPTIONS = "";

  @Override
  public AndroidDexCompilerConfiguration getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull AndroidDexCompilerConfiguration state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public static AndroidDexCompilerConfiguration getInstance(final Project project) {
    return project.getService(AndroidDexCompilerConfiguration.class);
  }
}
