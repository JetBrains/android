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
import com.android.tools.idea.mlkit.MlkitModuleService;
import com.android.tools.mlkit.MlkitNames;
import com.android.tools.mlkit.TensorInfo;
import com.google.common.collect.ImmutableSet;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a light class auto-generated for a specific model file in the assets folder.
 *
 * The light class is based on specific model however has structure similar to:
 * <code>
 *   public final class ModelName {
 *     public static ModelName newInstance(Context context) throw IOException;
 *     public final class Outputs {
 *       // different get methods
 *       public TensorBuffer getSomeOutput();
 *     }
 *
 *     public final Outputs process(TensorImage image1) { ... }
 *   }
 * </code>
 *
 * @see MlkitOutputLightClass
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

    ModificationTracker modificationTracker = MlkitModuleService.getInstance(module).getModelFileModificationTracker();
    myCachedMembers = CachedValuesManager.getManager(getProject()).createCachedValue(
      () -> {
        List<TensorInfo> inputTensorInfos = myClassConfig.myModelMetadata.getInputTensorInfos();
        List<TensorInfo> outputTensorInfos = myClassConfig.myModelMetadata.getOutputTensorInfos();

        //Build methods
        PsiMethod[] methods = new PsiMethod[2];
        methods[0] = buildProcessMethod(inputTensorInfos);
        methods[1] = buildNewInstanceStaticMethod();

        //Build inner class
        Map<String, PsiClass> innerClassMap = new HashMap<>();
        MlkitOutputLightClass mlkitOutputClass = new MlkitOutputLightClass(module, outputTensorInfos, this);
        innerClassMap.putIfAbsent(mlkitOutputClass.getName(), mlkitOutputClass);

        MyClassMembers data = new MyClassMembers(methods, innerClassMap.values().toArray(PsiClass.EMPTY_ARRAY));
        return CachedValueProvider.Result.create(data, modificationTracker);
      }, false);
  }

  public static List<String> getInnerClassNames() {
    return Collections.singletonList(MlkitNames.OUTPUTS);
  }

  @NotNull
  private PsiMethod buildNewInstanceStaticMethod() {
    PsiType returnType = PsiType.getTypeByName(getQualifiedName(), getProject(), getResolveScope());
    LightMethodBuilder method = new LightMethodBuilder(getManager(), "newInstance")
      .setMethodReturnType(returnType)
      .addParameter("context", ClassNames.CONTEXT)
      .addException(ClassNames.IO_EXCEPTION)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL, PsiModifier.STATIC)
      .setContainingClass(this);
    method.setNavigationElement(this);
    return method;
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
  @Override
  public PsiMethod[] getConstructors() {
    if (myConstructors == null) {
      myConstructors = new PsiMethod[] {
        new LightMethodBuilder(this, JavaLanguage.INSTANCE)
          .addParameter("context", PsiType.getTypeByName(ClassNames.CONTEXT, getProject(), getResolveScope()))
          .setConstructor(true)
          .addException(ClassNames.IO_EXCEPTION)
          .addModifier(PsiModifier.PRIVATE)
      };
    }

    return myConstructors;
  }

  @NotNull
  private PsiMethod buildProcessMethod(@NotNull List<TensorInfo> tensorInfos) {
    GlobalSearchScope scope = getResolveScope();
    String outputClassName =
      String.join(".", myClassConfig.myPackageName, myClassConfig.myModelMetadata.myClassName, MlkitNames.OUTPUTS);

    PsiType returnType = PsiType.getTypeByName(outputClassName, getProject(), scope);

    LightMethodBuilder method = new LightMethodBuilder(getManager(), "process")
      .setMethodReturnType(returnType)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL)
      .setContainingClass(this);

    for (TensorInfo tensorInfo : tensorInfos) {
      method.addParameter(tensorInfo.getName(), CodeUtils.getTypeQualifiedName(tensorInfo));
    }
    method.setNavigationElement(this);

    return method;
  }

  @NotNull
  @Override
  public PsiClass[] getInnerClasses() {
    return myCachedMembers.getValue().myInnerClasses;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    VirtualFile modelVirtualFile = VirtualFileManager.getInstance().findFileByUrl(myClassConfig.myModelMetadata.myModelFileUrl);
    if (modelVirtualFile != null) {
      PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(modelVirtualFile);
      if (psiFile != null) {
        return psiFile;
      }
    }
    return super.getNavigationElement();
  }

  private static class MyClassMembers {
    public final PsiMethod[] myMethods;
    public final PsiClass[] myInnerClasses;

    private MyClassMembers(PsiMethod[] methods, PsiClass[] innerClass) {
      this.myMethods = methods;
      this.myInnerClasses = innerClass;
    }
  }
}