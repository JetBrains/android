/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer.dex.tree;

import com.android.tools.idea.apk.viewer.dex.PackageTreeCreator;
import com.android.tools.proguard.ProguardMap;
import com.android.tools.proguard.ProguardSeedsMap;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jf.dexlib2.iface.reference.TypeReference;

import javax.swing.*;

public class DexClassNode extends DexElementNode {

  public DexClassNode(@NotNull String displayName, @Nullable TypeReference reference) {
    super(displayName, true, reference);
  }

  @Override
  public Icon getIcon(){
    return PlatformIcons.CLASS_ICON;
  }

  @Override
  public boolean isSeed(@Nullable ProguardSeedsMap seedsMap, @Nullable ProguardMap map) {
    if (seedsMap != null){
      TypeReference reference = getReference();
      if (reference != null) {
        if (seedsMap.hasClass(PackageTreeCreator.decodeClassName(reference.getType(), map))) {
          return true;
        }
      }
    }
    return super.isSeed(seedsMap, map);
  }

  @Nullable
  @Override
  public TypeReference getReference() {
    return (TypeReference) super.getReference();
  }
}
