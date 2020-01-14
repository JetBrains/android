/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit.lightpsi;

import com.android.SdkConstants;
import com.android.tools.idea.mlkit.LightModelClassConfig;
import com.android.tools.idea.mlkit.MlkitUtils;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.Param;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a light class auto-generated for a specific model file in the assets folder.
 */
public class LightModelClass extends AndroidLightClassBase {
  private final LightModelClassConfig myClassConfig;
  private final PsiJavaFile myContainingFile;
  private final CachedValue<MyClassMembers> myCachedMembers;
  private PsiMethod[] myConstructors;

  public LightModelClass(@NotNull Module module, @NotNull LightModelClassConfig classConfig) {
    super(PsiManager.getInstance(module.getProject()), ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL));
    myClassConfig = classConfig;

    myContainingFile = (PsiJavaFile)PsiFileFactory.getInstance(module.getProject()).createFileFromText(
      classConfig.myModelMetadata.myClassName + SdkConstants.DOT_JAVA,
      StdFileTypes.JAVA,
      "// This class is generated on-the-fly by the IDE.");
    myContainingFile.setPackageName(classConfig.myPackageName);

    setModuleInfo(module, false);

    //TODO(jackqdyulei): create a more accurate modification tracker
    ModificationTracker modificationTracker = ModificationTracker.EVER_CHANGED;
    myCachedMembers = CachedValuesManager.getManager(getProject()).createCachedValue(
      () -> {
        //Build methods
        PsiMethod[] methods = new PsiMethod[1];
        methods[0] = buildRunMethod(module);

        //Build inner class
        List<Param> params = myClassConfig.myModelMetadata.getOutputParams();

        Map<String, PsiClass> innerClassMap = new HashMap<>();
        MlkitOutputLightClass mlkitOutputClass = new MlkitOutputLightClass(module, params, this);
        innerClassMap.putIfAbsent(mlkitOutputClass.getName(), mlkitOutputClass);

        for (Param param : params) {
          PsiClass psiClass = getParamInnerClass(module, param);
          if (psiClass != null) {
            innerClassMap.putIfAbsent(psiClass.getName(), psiClass);
          }
        }

        MyClassMembers data = new MyClassMembers(methods, innerClassMap.values().toArray(PsiClass.EMPTY_ARRAY));
        return CachedValueProvider.Result.create(data, modificationTracker);
      }, false);
  }

  @Nullable
  private PsiClass getParamInnerClass(@NotNull Module module, @NotNull Param param) {
    if (param.getFileType() == Param.FileType.TENSOR_AXIS_LABELS) {
      return new MlkitLabelLightClass(module, param, this);
    }
    // TODO(jackqdyulei): Add more here when FileType has more types.
    return null;
  }

  @Override
  public String getName() {
    return myClassConfig.myModelMetadata.myClassName;
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return myClassConfig.myPackageName + "." + getName();
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiFile getContainingFile() {
    return myContainingFile;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    //TODO(jackqdyulei): Also return constructors here.
    return myCachedMembers.getValue().myMethods;
  }

  @NotNull
  private PsiMethod buildRunMethod(@NotNull Module module) {
    GlobalSearchScope scope = getResolveScope();
    String outputClassName =
      String.join(".", MlkitUtils.computeModelPackageName(module), myClassConfig.myModelMetadata.myClassName, MlkitNames.OUTPUT);

    PsiType returnType = PsiType.getTypeByName(outputClassName, getProject(), scope);
    LightMethodBuilder runMethodBuilder = new LightMethodBuilder(getManager(), "run")
      .setMethodReturnType(returnType)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL).setContainingClass(this);

    // Add parameters
    for (Param param : myClassConfig.myModelMetadata.getInputParams()) {
      PsiType paramType = PsiType.getTypeByName(CodeUtils.getTypeQualifiedName(param), getProject(), scope);
      runMethodBuilder.addParameter(param.getName(), paramType);
    }

    return runMethodBuilder;
  }

  @NotNull
  @Override
  public PsiMethod[] getConstructors() {
    if (myConstructors == null) {
      myConstructors = new PsiMethod[1];
      myConstructors[0] = new LightMethodBuilder(this, JavaLanguage.INSTANCE)
        .addParameter("context", PsiType.getTypeByName(ClassNames.CONTEXT, getProject(), getResolveScope()))
        .setConstructor(true)
        .addModifier(PsiModifier.PUBLIC);
    }

    return myConstructors;
  }

  @NotNull
  @Override
  public PsiClass[] getInnerClasses() {
    return myCachedMembers.getValue().myInnerClasses;
  }

  private static class MyClassMembers {
    public final PsiMethod[] myMethods;
    public final PsiClass[] myInnerClasses;

    public MyClassMembers(PsiMethod[] methods, PsiClass[] innerClass) {
      this.myMethods = methods;
      this.myInnerClasses = innerClass;
    }
  }
}