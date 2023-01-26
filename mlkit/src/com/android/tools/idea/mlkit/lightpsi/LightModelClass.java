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
import com.android.tools.idea.mlkit.APIVersion;
import com.android.tools.idea.mlkit.LightModelClassConfig;
import com.android.tools.idea.mlkit.LoggingUtils;
import com.android.tools.idea.psi.light.DeprecatableLightMethodBuilder;
import com.android.tools.idea.psi.light.NullabilityLightMethodBuilder;
import com.android.tools.mlkit.MlNames;
import com.android.tools.mlkit.ModelInfo;
import com.android.tools.mlkit.TensorGroupInfo;
import com.android.tools.mlkit.TensorInfo;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
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
 * Represents a light class auto-generated for a specific model file in the ml folder.
 *
 * <p>The light class is based on specific model however has structure similar to:
 * <pre>{@code
 *   public final class ModelName {
 *     public static ModelName newInstance(Context context) throw IOException;
 *     public static ModelName newInstance(Context context, Model.Options options) throw IOException;
 *     public final class Outputs {
 *       // different get methods
 *       public TensorBuffer getSomeOutput();
 *     }
 *
 *     public final Outputs process(TensorImage image1) { ... }
 *   }
 * }</pre>
 *
 * @see LightModelOutputsClass
 */
public class LightModelClass extends AndroidLightClassBase {
  @NotNull
  private final VirtualFile myModelFile;
  @NotNull
  private final LightModelClassConfig myClassConfig;
  @NotNull
  private final PsiJavaFile myContainingFile;
  @NotNull
  private final CachedValue<MyClassMembers> myCachedMembers;
  @NotNull
  private final APIVersion myAPIVersion;
  @NotNull
  private PsiMethod[] myConstructors;
  private boolean myGenerateFallbackApiOnly;

  public LightModelClass(@NotNull Module module, @NotNull VirtualFile modelFile, @NotNull LightModelClassConfig classConfig) {
    super(PsiManager.getInstance(module.getProject()), ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL));
    myModelFile = modelFile;
    myClassConfig = classConfig;
    myAPIVersion = APIVersion.fromProject(module.getProject());
    myGenerateFallbackApiOnly = myAPIVersion.generateFallbackApiOnly(getModelInfo().getMinParserVersion());

    myContainingFile = (PsiJavaFile)PsiFileFactory.getInstance(module.getProject()).createFileFromText(
      myClassConfig.myClassName + SdkConstants.DOT_JAVA,
      JavaFileType.INSTANCE,
      "// This class is generated on-the-fly by the IDE.");
    myContainingFile.setPackageName(classConfig.myPackageName);

    setModuleInfo(module, false);

    myCachedMembers = CachedValuesManager.getManager(getProject()).createCachedValue(
      () -> {
        ModelInfo modelInfo = getModelInfo();

        List<PsiMethod> methods = new ArrayList<>();
        Map<String, PsiClass> innerClassMap = new HashMap<>();
        if (myAPIVersion.isAtLeastVersion(APIVersion.API_VERSION_1)) {
          // Generated API added in version 1.
          methods.add(buildProcessMethod(modelInfo.getInputs(), false));
          if (!myGenerateFallbackApiOnly) {
            if (modelInfo.getInputs().stream().anyMatch(tensorInfo -> tensorInfo.isRGBImage())) {
              // Adds #process fallback method.
              methods.add(buildProcessMethod(modelInfo.getInputs(), true));
            }
          }
          methods.add(buildCloseMethod());
          methods.addAll(buildNewInstanceStaticMethods());

          // Builds inner Outputs class.
          LightModelOutputsClass mlkitOutputClass = new LightModelOutputsClass(module, modelInfo, this);
          innerClassMap.putIfAbsent(mlkitOutputClass.getName(), mlkitOutputClass);
        }

        if(myAPIVersion.isAtLeastVersion(APIVersion.API_VERSION_2)) {
          // Generated API added in version 2.
          for (TensorGroupInfo tensorGroupInfo : modelInfo.getOutputTensorGroups()) {
            LightModelGroupClass mlkitGroupClass = new LightModelGroupClass(module, modelInfo.getOutputs(), tensorGroupInfo, this);
            innerClassMap.putIfAbsent(mlkitGroupClass.getName(), mlkitGroupClass);
          }
        }

        MyClassMembers data =
          new MyClassMembers(methods.toArray(PsiMethod.EMPTY_ARRAY), innerClassMap.values().toArray(PsiClass.EMPTY_ARRAY));
        return CachedValueProvider.Result.create(data, ModificationTracker.NEVER_CHANGED);
      }, false);

    LoggingUtils.logEvent(EventType.MODEL_API_GEN, getModelInfo());
  }

  @NotNull
  private List<PsiMethod> buildNewInstanceStaticMethods() {
    List<PsiMethod> methods = new ArrayList<>();
    PsiType thisType = PsiType.getTypeByName(getQualifiedName(), getProject(), getResolveScope());
    PsiType context = PsiType.getTypeByName(ClassNames.CONTEXT, getProject(), getResolveScope());
    PsiType options = PsiType.getTypeByName(ClassNames.MODEL_OPTIONS, getProject(), getResolveScope());

    LightMethodBuilder method = new NullabilityLightMethodBuilder(getManager(), "newInstance")
      .setMethodReturnType(thisType, true)
      .addNullabilityParameter("context", context, true)
      .addException(ClassNames.IO_EXCEPTION)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL, PsiModifier.STATIC)
      .setContainingClass(this);
    method.setNavigationElement(this);
    methods.add(method);

    LightMethodBuilder methodWithOptions = new NullabilityLightMethodBuilder(getManager(), "newInstance")
      .setMethodReturnType(thisType, true)
      .addNullabilityParameter("context", context, true)
      .addNullabilityParameter("options", options, true)
      .addException(ClassNames.IO_EXCEPTION)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL, PsiModifier.STATIC)
      .setContainingClass(this);
    methodWithOptions.setNavigationElement(this);
    methods.add(methodWithOptions);

    return methods;
  }

  @NotNull
  private PsiMethod buildCloseMethod() {
    LightMethodBuilder closeMethod = new NullabilityLightMethodBuilder(getManager(), "close")
      .addModifier(PsiModifier.PUBLIC)
      .setMethodReturnType(PsiTypes.voidType())
      .setContainingClass(this);
    closeMethod.setNavigationElement(this);

    return closeMethod;
  }

  @Override
  public String getName() {
    return myClassConfig.myClassName;
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
    return myCachedMembers.getValue().myMethods;
  }

  @NotNull
  @Override
  public PsiMethod[] getConstructors() {
    if (myConstructors == null) {
      PsiType contextType = PsiType.getTypeByName(ClassNames.CONTEXT, getProject(), getResolveScope());
      myConstructors = new PsiMethod[]{
        new NullabilityLightMethodBuilder(this, JavaLanguage.INSTANCE)
          .addNullabilityParameter("context", contextType, true)
          .setConstructor(true)
          .addException(ClassNames.IO_EXCEPTION)
          .addModifier(PsiModifier.PRIVATE)
      };
    }

    return myConstructors;
  }

  @NotNull
  private PsiMethod buildProcessMethod(@NotNull List<TensorInfo> tensorInfos, boolean usedForFallback) {
    GlobalSearchScope scope = getResolveScope();
    String outputClassName =
      String.join(".", myClassConfig.myPackageName, myClassConfig.myClassName, MlNames.OUTPUTS);

    PsiType outputType = PsiType.getTypeByName(outputClassName, getProject(), scope);
    DeprecatableLightMethodBuilder method = new DeprecatableLightMethodBuilder(getManager(), JavaLanguage.INSTANCE, "process");
    method
      .setMethodReturnType(outputType, true)
      .addModifiers(PsiModifier.PUBLIC, PsiModifier.FINAL)
      .setContainingClass(this);
    method.setDeprecated(usedForFallback);

    for (TensorInfo tensorInfo : tensorInfos) {
      PsiType tensorType = usedForFallback
                           ? PsiType.getTypeByName(ClassNames.TENSOR_BUFFER, getProject(), scope)
                           : CodeUtils.getPsiClassType(tensorInfo, getProject(), scope, myGenerateFallbackApiOnly);
      method.addNullabilityParameter(tensorInfo.getIdentifierName(), tensorType, true);
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
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(myModelFile);
    return psiFile != null ? psiFile : super.getNavigationElement();
  }

  @NotNull
  public VirtualFile getModelFile() {
    return myModelFile;
  }

  @NotNull
  public ModelInfo getModelInfo() {
    return myClassConfig.myModelMetadata.myModelInfo;
  }

  @SuppressWarnings("EqualsHashCode")  // b/180537631
  @Override
  public boolean equals(@Nullable Object o) {
    return o instanceof LightModelClass && myClassConfig.myModelMetadata.equals(((LightModelClass)o).myClassConfig.myModelMetadata);
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