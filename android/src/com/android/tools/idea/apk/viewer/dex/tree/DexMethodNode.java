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
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jf.dexlib2.iface.reference.MethodReference;

import javax.swing.*;

public class DexMethodNode extends DexElementNode {

  public DexMethodNode(@NotNull String displayName, @Nullable MethodReference reference) {
    super(displayName, false, reference);
  }

  @Override
  public Icon getIcon(){
    return PlatformIcons.METHOD_ICON;
  }

  @Nullable
  @Override
  public MethodReference getReference() {
    return (MethodReference) super.getReference();
  }

  @Override
  public boolean isSeed(@Nullable ProguardSeedsMap seedsMap, @Nullable ProguardMap map) {
    if (seedsMap != null){
      MethodReference reference = getReference();
      if (reference != null) {
        String className = PackageTreeCreator.decodeClassName(reference.getDefiningClass(), map);
        String methodName = PackageTreeCreator.decodeMethodName(reference, map);
        String params = PackageTreeCreator.decodeMethodParams(reference, map);
        if ("<init>".equals(methodName)) {
          methodName = DebuggerUtilsEx.getSimpleName(className);
        }
        return seedsMap.hasMethod(className, methodName + params);
      }
    }
    return false;
  }
}
