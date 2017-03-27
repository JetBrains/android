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

import com.android.tools.idea.apk.viewer.dex.tree.DexElementNode;
import com.android.tools.idea.apk.viewer.dex.tree.DexElementNodeFactory;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.dexbacked.DexBackedMethod;
import org.jf.dexlib2.dexbacked.DexBackedMethodImplementation;
import org.jf.dexlib2.dexbacked.reference.DexBackedFieldReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedMethodReference;
import org.jf.dexlib2.dexbacked.reference.DexBackedTypeReference;
import org.jf.dexlib2.iface.instruction.DualReferenceInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.Reference;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.dexlib2.immutable.reference.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DexReferences {

  private final Multimap<Reference, ImmutableReference> myReferenceReferences =
    HashMultimap.create();

  public DexReferences(DexBackedDexFile file) {
    gatherBackReferences(file);
  }

  /**
   * We need to go through all the data available in a dex file
   * for methods, fields and classes and gather all possible
   * references from one to the others. Currently that means:
   * - method -> return type
   * - method -> parameter types
   * - method -> any type/field/method reference found in bytecode
   * - class -> superclass
   * - class -> implemented interfaces
   * - field -> field type
   *
   * TODO: we currently don't check annotations
   * TODO: check if bytecode of exception handlers is included
   *
   * @param a dex file
   */
  private void gatherBackReferences(@NotNull DexBackedDexFile file) {
    //build a map from class names (String) to actual TypeReferences,
    //as this information is not readily available to query through
    //the dexlib2 API.
    Map<String, ImmutableTypeReference> myTypesByName = new HashMap<>();
    for (int i = 0, m = file.getTypeCount(); i < m; i++) {
      ImmutableTypeReference immutableTypeRef = ImmutableTypeReference.of(new DexBackedTypeReference(file, i));
      myTypesByName.put(immutableTypeRef.getType(), immutableTypeRef);
    }

    //loop through all methods referenced in the dex file, mapping the following:
    for (int i = 0, m = file.getMethodCount(); i < m; i++) {
      ImmutableMethodReference immutableMethodRef = ImmutableMethodReference.of(new DexBackedMethodReference(file, i));
      //- method to return type
      ImmutableReference typeRef = myTypesByName.get(immutableMethodRef.getReturnType());
      myReferenceReferences.put(typeRef, immutableMethodRef);
      //- method to all parameter types
      for (String parameterType : immutableMethodRef.getParameterTypes()) {
        typeRef = myTypesByName.get(parameterType);
        myReferenceReferences.put(typeRef, immutableMethodRef);
      }
    }

    //loop through all classes defined in the dex file, mapping the following:
    for (DexBackedClassDef classDef : file.getClasses()) {
      ImmutableReference immutableClassRef = ImmutableReferenceFactory.of(classDef);
      //- class to superclass
      ImmutableReference typeRef = myTypesByName.get(classDef.getSuperclass());
      myReferenceReferences.put(typeRef, immutableClassRef);
      //- class to all implemented interfaces
      for (String iface : classDef.getInterfaces()) {
        typeRef = myTypesByName.get(iface);
        myReferenceReferences.put(typeRef, immutableClassRef);
      }
      //loop through all the methods defined in this class,
      for (DexBackedMethod method : classDef.getMethods()) {
        //if the method has an implementation, loop through the bytecode
        //mapping this method to any references that exist in dex instructions.
        //Fortunately, dexlib2 marks every bytecode instruction that accepts
        //a reference with one of the 2 interfaces: ReferenceInstruction
        //or DualReferenceInstructions.
        DexBackedMethodImplementation impl = method.getImplementation();
        if (impl != null) {
          for (Instruction instruction : impl.getInstructions()) {
            if (instruction instanceof ReferenceInstruction) {
              Reference reference = ((ReferenceInstruction)instruction).getReference();
              myReferenceReferences.put(ImmutableReferenceFactory.of(reference), ImmutableReferenceFactory.of(method));
            }
            if (instruction instanceof DualReferenceInstruction) {
              Reference reference = ((DualReferenceInstruction)instruction).getReference2();
              myReferenceReferences.put(ImmutableReferenceFactory.of(reference), ImmutableReferenceFactory.of(method));
            }
          }
        }
      }
    }

    //loop through all fields referenced in this dex file, creating
    //a mapping from the field to its type
    for (int i = 0, m = file.getFieldCount(); i < m; i++) {
      ImmutableFieldReference immutableFieldRef = ImmutableFieldReference.of(new DexBackedFieldReference(file, i));
      ImmutableReference typeRef = myTypesByName.get(immutableFieldRef.getType());
      myReferenceReferences.put(typeRef, immutableFieldRef);
    }
  }

  public DexElementNode getReferenceTreeFor(@NotNull Reference referenced) {
    DexElementNode rootNode = DexElementNodeFactory.from(referenced);
    createReferenceTree(rootNode, referenced);
    return rootNode;
  }

  private void createReferenceTree(@NotNull DexElementNode node, @NotNull Reference referenced) {
    Collection<? extends Reference> references = myReferenceReferences.get(referenced);
    for (Reference ref : references) {
      if (ref instanceof MethodReference || ref instanceof TypeReference || ref instanceof FieldReference) {
        DexElementNode parentNode = node;
        boolean hasCycle = false;
        while (parentNode != null) {
          if (ref.equals(parentNode.getReference())) {
            hasCycle = true;
          }
          parentNode = parentNode.getParent();
        }
        if (hasCycle) {
          continue;
        }
        DexElementNode newNode = DexElementNodeFactory.from(ref);
        node.setAllowsChildren(true);
        node.add(newNode);
        createReferenceTree(newNode, ref);
      }
    }
  }
}
