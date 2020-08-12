/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;

import com.android.tools.idea.mlkit.APIVersion;
import com.android.tools.idea.psi.light.DeprecatableLightMethodBuilder;
import com.android.tools.mlkit.MlNames;
import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.TensorGroupInfo;
import com.android.tools.mlkit.TensorInfo;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableSet;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a light class auto-generated for getting results from model output tensors.
 *
 * @see LightModelClass
 */
public class LightModelOutputsClass extends AndroidLightClassBase {
  /**
   * Tag for normal getter methods in output class. For each output tensor we have a getter method
   * called {@code getTensorNameAsType}.
   * <p>
   * This tag is used to distinguish with method with {@link #TAG_GROUP_METHOD}.
   */
  @NotNull
  public static final String TAG_NORMAL_METHOD = "normal method";
  /**
   * Tag for group getter methods in output class. If output class has {@link TensorGroupInfo}, it has a method
   * called {@code getTensorGroupName}.
   * <p>
   * This tag is used to distinguish with method with {@link #TAG_NORMAL_METHOD}.
   */
  @NotNull
  public static final String TAG_GROUP_METHOD = "group method";

  @NotNull
  private final PsiClass containingClass;
  @NotNull
  private final String qualifiedName;
  @NotNull
  private final CachedValue<PsiMethod[]> myMethodCache;
  @NotNull
  private final APIVersion myAPIVersion;
  private final boolean myGenerateFallbackApiOnly;

  public LightModelOutputsClass(@NotNull Module module, @NotNull ModelInfo modelInfo, @NotNull PsiClass containingClass) {
    super(PsiManager.getInstance(module.getProject()), ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL));
    this.qualifiedName = String.join(".", containingClass.getQualifiedName(), MlNames.OUTPUTS);
    this.containingClass = containingClass;
    myAPIVersion = APIVersion.fromProject(module.getProject());
    myGenerateFallbackApiOnly = myAPIVersion.generateFallbackApiOnly(modelInfo.getMinParserVersion());

    setModuleInfo(module, false);

    // Caches getter methods for output class.
    myMethodCache = CachedValuesManager.getManager(getProject()).createCachedValue(
      () -> {
        List<PsiMethod> methods = new ArrayList<>();

        if (myAPIVersion.isAtLeastVersion(APIVersion.API_VERSION_1)) {
          // Generated API added in version 1.
          for (TensorInfo tensorInfo : modelInfo.getOutputs()) {
            methods.add(buildGetterMethod(tensorInfo, false));
            if (!CodeUtils.getPsiClassType(tensorInfo, getProject(), getResolveScope(), myGenerateFallbackApiOnly).getClassName()
              .equals("TensorBuffer")) {
              // Adds fallback getter method.
              methods.add(buildGetterMethod(tensorInfo, true));
            }
          }
        }

        if (myAPIVersion.isAtLeastVersion(APIVersion.API_VERSION_2)) {
          // Generated API added in version 2.
          for (TensorGroupInfo tensorGroupInfo : modelInfo.getOutputTensorGroups()) {
            methods.add(buildGroupGetterMethod(tensorGroupInfo));
          }
        }

        return CachedValueProvider.Result.create(methods.toArray(PsiMethod.EMPTY_ARRAY), ModificationTracker.NEVER_CHANGED);
      }, false);
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return qualifiedName;
  }

  @NotNull
  @Override
  public String getName() {
    return MlNames.OUTPUTS;
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myMethodCache.getValue();
  }

  @NotNull
  private PsiMethod buildGetterMethod(@NotNull TensorInfo tensorInfo, boolean usedForFallback) {
    GlobalSearchScope scope = getResolveScope();
    PsiClassType returnType = usedForFallback
                              ? PsiType.getTypeByName(ClassNames.TENSOR_BUFFER, getProject(), scope)
                              : CodeUtils.getPsiClassType(tensorInfo, getProject(), scope, myGenerateFallbackApiOnly);
    String methodName = MlNames.formatGetterName(tensorInfo.getIdentifierName(), CodeUtils.getTypeName(returnType));
    DeprecatableLightMethodBuilder method = new DeprecatableLightMethodBuilder(myManager, JavaLanguage.INSTANCE, methodName);
    method
      .setMethodReturnType(returnType, true)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL)
      .setContainingClass(this)
      .setNavigationElement(this);
    method.setDeprecated(usedForFallback);
    method.setOriginInfo(TAG_NORMAL_METHOD);
    return method;
  }

  @NotNull
  private PsiMethod buildGroupGetterMethod(@NotNull TensorGroupInfo tensorGroupInfo) {
    Project project = getProject();
    GlobalSearchScope scope = getResolveScope();
    String groupClassName = StringHelper.usLocaleCapitalize(tensorGroupInfo.getIdentifierName());
    String groupQualifiedName = String.join(".", containingClass.getQualifiedName(), groupClassName);
    PsiClass listClass = JavaPsiFacade.getInstance(project).findClass(JAVA_UTIL_LIST, scope);
    PsiClassType returnType =
      PsiElementFactory.getInstance(project).createType(listClass, PsiType.getTypeByName(groupQualifiedName, project, scope));
    DeprecatableLightMethodBuilder method =
      new DeprecatableLightMethodBuilder(myManager, JavaLanguage.INSTANCE, MlNames.formatGroupGetterName(groupClassName));
    method
      .setMethodReturnType(returnType, true)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL)
      .setContainingClass(this)
      .setNavigationElement(this);
    method.setOriginInfo(TAG_GROUP_METHOD);
    return method;
  }


  @NotNull
  @Override
  public PsiClass getContainingClass() {
    return containingClass;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return containingClass.getNavigationElement();
  }
}
