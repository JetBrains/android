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

import com.intellij.debugger.impl.DebuggerUtilsEx;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.TypeReference;

public class DexElementNodeFactory {

  @NotNull
  public static DexElementNode from(@NotNull Reference ref){
    if (ref instanceof TypeReference){
      return new DexClassNode(DebuggerUtilsEx.signatureToName(((TypeReference)ref).getType()), (TypeReference)ref);
    } else if (ref instanceof FieldReference){
      return new DexFieldNode(((FieldReference)ref).getName(), (FieldReference)ref);
    } else if (ref instanceof MethodReference){
      return new DexMethodNode(((MethodReference)ref).getName(), (MethodReference)ref);
    } else {
      throw new IllegalArgumentException("This method accepts a Type/Field/MethodReference");
    }

  }
}
