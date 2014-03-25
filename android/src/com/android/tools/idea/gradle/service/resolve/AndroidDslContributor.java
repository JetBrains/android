/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.resolve;

import com.google.common.base.Joiner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.service.resolve.GradleMethodContextContributor;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.Collections;
import java.util.List;

public class AndroidDslContributor implements GradleMethodContextContributor {
  private static final Logger LOG = Logger.getInstance(AndroidDslContributor.class);

  @NonNls private static final String DSL_ANDROID = "android";
  @NonNls private static final String DSL_DEFAULT_CONFIG = "defaultConfig";
  @NonNls private static final String DSL_LINT_OPTIONS = "lintOptions";
  @NonNls private static final String DSL_BUILD_TYPES = "buildTypes";
  @NonNls private static final String DSL_PRODUCT_FLAVORS = "productFlavors";
  @NonNls private static final String DSL_SIGNING_CONFIG = "signingConfigs";

  @NonNls private static final String ANDROID_FQCN = "com.android.build.gradle.AppExtension";
  @NonNls private static final String DEFAULT_PRODUCT_FLAVOR_FQCN = "com.android.builder.DefaultProductFlavor";
  @NonNls private static final String LINT_OPTIONS_FQCN = "com.android.build.gradle.internal.dsl.LintOptionsImpl";
  @NonNls private static final String BUILD_TYPE_FQCN = "com.android.build.gradle.internal.dsl.BuildTypeDsl";
  @NonNls private static final String PRODUCT_FLAVOR_FQCN = "com.android.build.gradle.internal.dsl.ProductFlavorDsl";
  @NonNls private static final String SIGNING_CONFIG_FQCN = "com.android.build.gradle.internal.dsl.SigningConfigDsl";

  @NonNls private List<VirtualFile> myLastClassPath = Collections.emptyList();

  @Override
  public void process(@NotNull List<String> callStack,
                      @NotNull PsiScopeProcessor processor,
                      @NotNull ResolveState state,
                      @NotNull PsiElement place) {
    // The Android DSL within a Gradle build script looks something like this:
    //     android {
    //         compileSdkVersion 18
    //         buildToolsVersion "19.0.0"
    //
    //         defaultConfig {
    //           minSdkVersion 8
    //         }
    //
    //         buildTypes {
    //           release {
    //             runProguard false
    //             proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'
    //           }
    //         }
    //
    //         productFlavors {
    //           flavor1 {
    //             packageName "com.example.flavor1"
    //           }
    //         }
    //
    //         signingConfigs {
    //           myConfig {
    //             storeFile file("other.keystore")
    //           }
    //         }
    //
    //         lintOptions {
    //           quiet true
    //         }
    //     }
    // This method receives a callstack leading to a particular symbol, e.g. android.compileSdkVersion, or
    // android.buildTypes.release.runProguard. Based on the given call stack, we have to resolve either to a particular class or to a
    // particular setter within the class.

    String topLevel = ContainerUtil.getLastItem(callStack, null);
    if (!DSL_ANDROID.equals(topLevel)) {
      return;
    }

    logClassPath(place.getProject());

    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());

    // associate context for items immediately within the android block (android.*)
    if (callStack.size() == 2 && DSL_ANDROID.equals(callStack.get(1))) {
      // TODO: map to AppExtension or LibExtension depending on whether this is a library or not
      GradleResolverUtil.processDeclarations(psiManager, processor, state, place, ANDROID_FQCN);
      return;
    }

    if (callStack.size() == 3) {
      final String container = callStack.get(1);

      // associate context for items immediately within the defaultConfig or lintOptions blocks (android.[defaultConfig|lintOptions].*)
      String fqName = null;
      if (DSL_DEFAULT_CONFIG.equals(container)) {
        fqName = DEFAULT_PRODUCT_FLAVOR_FQCN;
      }
      else if (DSL_LINT_OPTIONS.equals(container)) {
        fqName = LINT_OPTIONS_FQCN;
      }
      if (fqName != null) {
        GradleResolverUtil.processDeclarations(psiManager, processor, state, place, fqName);
        processSetter(psiManager, callStack.get(0), fqName, processor, state, place);
        return;
      }

      if (DSL_BUILD_TYPES.equals(container)) {
        fqName = BUILD_TYPE_FQCN;
      }
      else if (DSL_PRODUCT_FLAVORS.equals(container)) {
        fqName = PRODUCT_FLAVOR_FQCN;
      }
      else if (DSL_SIGNING_CONFIG.equals(container)) {
        fqName = SIGNING_CONFIG_FQCN;
      }
      else {
        return;
      }

      PsiClass contributorClass = psiManager.findClassWithCache(fqName, place.getResolveScope());
      if (contributorClass == null) {
        return;
      }

      // if this is a user defined build type, product flavor or signing config (android.[buildTypes|productFlavors|signingConfigs].*),
      // mark them as constructors with the following closure as its argument
      GrLightMethodBuilder methodWithClosure =
        GradleResolverUtil.createMethodWithClosure(StringUtil.getShortName(fqName), fqName, null, place, psiManager);
      if (methodWithClosure != null) {
        processor.execute(methodWithClosure, state);
      }
    }

    // associate context for items within a particular build type, flavor or signing config
    // (android.[buildTypes|productFlavors|signingConfigs].<name>.*)
    if (callStack.size() == 4) {
      final String container = callStack.get(2);

      String fqName;
      if (DSL_BUILD_TYPES.equals(container)) {
        fqName = BUILD_TYPE_FQCN;
      }
      else if (DSL_PRODUCT_FLAVORS.equals(container)) {
        fqName = PRODUCT_FLAVOR_FQCN;
      }
      else if (DSL_SIGNING_CONFIG.equals(container)) {
        fqName = SIGNING_CONFIG_FQCN;
      }
      else {
        return;
      }

      processSetter(psiManager, callStack.get(0), fqName, processor, state, place);
    }
  }

  private static void processSetter(GroovyPsiManager psiManager,
                                    String symbol,
                                    String fqcn,
                                    PsiScopeProcessor processor,
                                    ResolveState state,
                                    PsiElement place) {
    PsiClass contributorClass = psiManager.findClassWithCache(fqcn, place.getResolveScope());
    if (contributorClass == null) {
      return;
    }

    String methodName = GroovyPropertyUtils.getSetterName(symbol);
    GrLightMethodBuilder builder = new GrLightMethodBuilder(place.getManager(), methodName);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getManager().getProject());
    PsiType type = new PsiArrayType(factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope()));
    builder.addParameter(new GrLightParameter("param", type, builder));
    PsiClassType retType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope());
    builder.setReturnType(retType);
    processor.execute(builder, state);

    GrMethodCall call = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
    if (call == null) {
      return;
    }
    GrArgumentList args = call.getArgumentList();
    int argsCount = GradleResolverUtil.getGrMethodArumentsCount(args);

    final PsiMethod[] methodsByName = contributorClass.findMethodsByName(methodName, true);
    if (methodName.length() == 0) {
      return;
    }

    // first check to see if we can narrow down by # of arguments
    for (PsiMethod method : methodsByName) {
      if (method.getParameterList().getParametersCount() == argsCount) {
        builder.setNavigationElement(method);
        return;
      }
    }

    // if we couldn't narrow down by # of arguments, just use the first one
    builder.setNavigationElement(methodsByName[0]);
  }

  private void logClassPath(@NotNull Project project) {
    List<VirtualFile> files = GradleBuildClasspathManager.getInstance(project).getAllClasspathEntries();
    if (ContainerUtil.equalsIdentity(files, myLastClassPath)) {
      return;
    }
    myLastClassPath = files;

    List<String> paths = ContainerUtil.map(files, new NotNullFunction<VirtualFile, String>() {
      @NotNull
      @Override
      public String fun(VirtualFile vf) {
        return vf.getPath();
      }
    });
    String classPath = Joiner.on(':').join(paths);
    LOG.info(String.format("Android DSL resolver classpath (project %1$s): %2$s", project.getName(), classPath));
  }
}
