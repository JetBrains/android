/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding.model;

import android.databinding.tool.reflection.ModelClass;
import android.databinding.tool.reflection.ModelField;
import android.databinding.tool.reflection.ModelMethod;
import com.google.common.collect.Lists;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PsiModelClass extends ModelClass {

  private static final ModelField[] EMPTY_MODEL_FIELD_ARRAY = new ModelField[0];
  private static final ModelMethod[] EMPTY_MODEL_METHOD_ARRAY = new ModelMethod[0];

  @NotNull PsiType myType;
  ModelClass myComponentType;

  public PsiModelClass(@NotNull PsiType psiClassType) {
    myType = psiClassType;
  }

  public PsiClass getPsiClass() {
    if (myType instanceof PsiClassType) {
      return ((PsiClassType) myType).resolve();
    }
    return null;
  }

  @Override
  public String toJavaCode() {
    throw new UnsupportedOperationException("why generate");
  }

  @Override
  public boolean isArray() {
    return myType instanceof PsiArrayType;
  }

  @Override
  public ModelClass getComponentType() {
    if (myComponentType == null) {
      if (isArray()) {
        myComponentType = new PsiModelClass(((PsiArrayType)myType).getComponentType());
      }
      //else {
      //  // TODO: Support list and map type.
      //  // For list, it's the return type of the method get(int). For method, it's the second generic type.
      //}
    }
    return myComponentType;
  }

  @Override
  public boolean isNullable() {
    return !isPrimitive();
  }

  @Override
  public boolean isPrimitive() {
    String canonicalText = myType.getCanonicalText(false);
    String boxed = PsiTypesUtil.boxIfPossible(canonicalText);
    return boxed != null && !boxed.equals(canonicalText);
  }

  @Override
  public boolean isBoolean() {
    return PsiType.BOOLEAN.equalsToText(myType.getCanonicalText());
  }

  @Override
  public boolean isChar() {
    return PsiType.CHAR.equalsToText(myType.getCanonicalText());
  }

  @Override
  public boolean isByte() {
    return PsiType.BYTE.equalsToText(myType.getCanonicalText());
  }

  @Override
  public boolean isShort() {
    return PsiType.SHORT.equalsToText(myType.getCanonicalText());
  }

  @Override
  public boolean isInt() {
    return PsiType.INT.equalsToText(myType.getCanonicalText());
  }

  @Override
  public boolean isLong() {
    return PsiType.LONG.equalsToText(myType.getCanonicalText());
  }

  @Override
  public boolean isFloat() {
    return PsiType.FLOAT.equalsToText(myType.getCanonicalText());
  }

  @Override
  public boolean isDouble() {
    return PsiType.DOUBLE.equalsToText(myType.getCanonicalText());
  }

  @Override
  public boolean isGeneric() {
    return myType instanceof PsiClassType && ((PsiClassType)myType).hasParameters();
  }

  @Override
  public List<ModelClass> getTypeArguments() {
    if (myType instanceof PsiClassType) {
      PsiClassType classType = (PsiClassType)myType;
      PsiType[] typeParameters = classType.getParameters();

      List<ModelClass> result = Lists.newArrayListWithCapacity(typeParameters.length);
      for (PsiType typeParameter : typeParameters) {
        result.add(new PsiModelClass(typeParameter));
      }
      return result;
    }
    return Lists.newArrayList();
  }

  @Override
  public boolean isTypeVar() {
    return false;
  }

  @Override
  public boolean isInterface() {
    if (myType instanceof PsiClassType) {
      PsiClassType psiClassType = (PsiClassType)myType;
      PsiClass resolved = psiClassType.resolve();
      return resolved != null && resolved.isInterface();
    }
    return false;
  }

  @Override
  public boolean isVoid() {
    return PsiType.VOID.equalsToText(myType.getCanonicalText());
  }

  @Override
  public ModelClass unbox() {
    return this;
  }

  @Override
  public ModelClass box() {
    return this;
  }

  @Override
  public boolean isAssignableFrom(ModelClass modelClass) {
    return modelClass instanceof PsiModelClass && myType.isAssignableFrom(((PsiModelClass)modelClass).myType);
  }

  @Override
  public ModelClass getSuperclass() {

    PsiType[] supers = myType.getSuperTypes();
    if (supers.length == 0) {
      return null;
    }
    PsiType superClass = supers[0];
    if (superClass == null) {
      return null;
    }
    return new PsiModelClass(superClass);
  }

  @Override
  public ModelClass erasure() {
    return this;
  }

  @Override
  public String getJniDescription() {
    return getCanonicalName();
  }

  @Override
  public ModelField[] getDeclaredFields() {
    if (myType instanceof PsiClassType) {
      PsiClassType myPsiClassType = (PsiClassType)myType;
      PsiClass resolved = myPsiClassType.resolve();
      if (resolved == null) {
        return EMPTY_MODEL_FIELD_ARRAY;
      }

      PsiField[] fields = resolved.getFields();
      ModelField[] result = new ModelField[fields.length];
      for (int i = 0; i < fields.length; i ++) {
        result[i] = new PsiModelField(fields[i]);
      }
      return result;
    }
    return EMPTY_MODEL_FIELD_ARRAY;
  }

  @Override
  public ModelMethod[] getDeclaredMethods() {
    if (myType instanceof PsiClassType) {
      PsiClassType myPsiClassType = (PsiClassType)myType;
      PsiClass resolved = myPsiClassType.resolve();
      if (resolved == null) {
        return EMPTY_MODEL_METHOD_ARRAY;
      }

      PsiMethod[] fields = resolved.getMethods();
      ModelMethod[] result = new ModelMethod[fields.length];
      for (int i = 0; i < fields.length; i ++) {
        result[i] = new PsiModelMethod(fields[i]);
      }
      return result;
    }
    return EMPTY_MODEL_METHOD_ARRAY;
  }

  @Override
  public boolean isWildcard() {
    // TODO
    return false;
  }
}
