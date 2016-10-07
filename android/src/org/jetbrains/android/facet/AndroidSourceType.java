/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.facet;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public enum AndroidSourceType {
  // source roots
  MANIFEST("manifest", IdeaSourceProvider.MANIFEST_PROVIDER, AllIcons.Modules.SourceRoot),
  JAVA("java", IdeaSourceProvider.JAVA_PROVIDER, AllIcons.Modules.SourceRoot),
  CPP("cpp", IdeaSourceProvider.JNI_PROVIDER, AllIcons.Modules.SourceRoot),
  AIDL("aidl", IdeaSourceProvider.AIDL_PROVIDER, AllIcons.Modules.SourceRoot),
  RENDERSCRIPT("renderscript", IdeaSourceProvider.RENDERSCRIPT_PROVIDER, AllIcons.Modules.SourceRoot),
  SHADERS("shaders", IdeaSourceProvider.SHADERS_PROVIDER, AllIcons.Modules.SourceRoot),
  // resource roots
  ASSETS("assets", IdeaSourceProvider.ASSETS_PROVIDER, AllIcons.Modules.ResourcesRoot),
  JNILIBS("jniLibs", IdeaSourceProvider.JNI_LIBS_PROVIDER, AllIcons.Modules.ResourcesRoot),
  RES("res", IdeaSourceProvider.RES_PROVIDER, AllIcons.Modules.ResourcesRoot),
  RESOURCES("resources", IdeaSourceProvider.RESOURCES_PROVIDER, AllIcons.Modules.ResourcesRoot);

  private final String myName;
  private final Function<IdeaSourceProvider, List<VirtualFile>> mySourceExtractor;
  private final Icon myIcon;

  AndroidSourceType(String name,
                    Function<IdeaSourceProvider, List<VirtualFile>> sourceExtractor,
                    Icon icon) {
    myName = name;
    mySourceExtractor = sourceExtractor;
    myIcon = icon;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public List<VirtualFile> getSources(IdeaSourceProvider provider) {
    List<VirtualFile> files = mySourceExtractor.apply(provider);
    return files == null ? Collections.<VirtualFile>emptyList() : files;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }
}
