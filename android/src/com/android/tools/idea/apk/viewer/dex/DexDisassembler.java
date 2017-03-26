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
package com.android.tools.idea.apk.viewer.dex;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import org.jetbrains.annotations.NotNull;
import org.jf.baksmali.Adaptors.ClassDefinition;
import org.jf.baksmali.Adaptors.MethodDefinition;
import org.jf.baksmali.BaksmaliOptions;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedMethod;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.util.ReferenceUtil;
import org.jf.util.IndentingWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class DexDisassembler {
  private final DexBackedDexFile myDex;

  public DexDisassembler(@NotNull DexBackedDexFile dexFile) {
    myDex = dexFile;
  }

  @NotNull
  public String disassembleMethod(@NotNull String fqcn, @NotNull String methodDescriptor) throws IOException {
    Optional<? extends DexBackedClassDef> classDef = getClassDef(fqcn);
    if (!classDef.isPresent()) {
      throw new IllegalStateException("Unable to locate class definition for " + fqcn);
    }

    Optional<? extends DexBackedMethod> method = StreamSupport.stream(classDef.get().getMethods().spliterator(), false)
      .filter(m -> methodDescriptor.equals(ReferenceUtil.getMethodDescriptor(m)))
      .findFirst();

    if (!method.isPresent()) {
      throw new IllegalStateException("Unable to locate method definition in class for method " + methodDescriptor);
    }

    BaksmaliOptions options = new BaksmaliOptions();
    ClassDefinition classDefinition = new ClassDefinition(options, classDef.get());

    StringWriter writer = new StringWriter(1024);
    try (IndentingWriter iw = new IndentingWriter(writer)) {
      MethodImplementation methodImpl = method.get().getImplementation();
      if (methodImpl == null) {
        MethodDefinition.writeEmptyMethodTo(iw, method.get(), options);
      }
      else {
        MethodDefinition methodDefinition = new MethodDefinition(classDefinition, method.get(), methodImpl);
        methodDefinition.writeTo(iw);
      }
    }

    return writer.toString().replace("\r", "");
  }

  @NotNull
  public String disassembleClass(@NotNull String fqcn) throws IOException {
    Optional<? extends DexBackedClassDef> classDef = getClassDef(fqcn);
    if (!classDef.isPresent()) {
      throw new IllegalStateException("Unable to locate class definition for " + fqcn);
    }

    BaksmaliOptions options = new BaksmaliOptions();
    ClassDefinition classDefinition = new ClassDefinition(options, classDef.get());

    StringWriter writer = new StringWriter(1024);
    try (IndentingWriter iw = new IndentingWriter(writer)) {
      classDefinition.writeTo(iw);
    }
    return writer.toString().replace("\r", "");
  }

  @NotNull
  private Optional<? extends DexBackedClassDef> getClassDef(@NotNull String fqcn) {
    return myDex.getClasses()
      .stream()
      .filter(c -> fqcn.equals(DebuggerUtilsEx.signatureToName(c.getType())))
      .findFirst();
  }
}