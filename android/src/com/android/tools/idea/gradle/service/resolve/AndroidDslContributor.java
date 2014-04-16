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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleBuildClasspathManager;
import org.jetbrains.plugins.gradle.service.resolve.GradleMethodContextContributor;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.*;

public class AndroidDslContributor implements GradleMethodContextContributor {
  private static final Logger LOG = Logger.getInstance(AndroidDslContributor.class);

  @NonNls private static final String DSL_ANDROID = "android";
  @NonNls private static final String ANDROID_FQCN = "com.android.build.gradle.AppExtension";
  @NonNls private static final String ANDROID_LIB_FQCN = "com.android.build.gradle.LibraryExtension";

  private static final Key<PsiElement> CONTRIBUTOR_KEY = Key.create("AndroidDslContributor.key");

  @NonNls private List<VirtualFile> myLastClassPath = Collections.emptyList();

  private static final Map<String, String> ourDslForClassMap = ImmutableMap.of(
    "com.android.builder.DefaultProductFlavor", "com.android.build.gradle.internal.dsl.ProductFlavorDsl",
    "com.android.builder.DefaultBuildType", "com.android.build.gradle.internal.dsl.BuildTypeDsl",
    "com.android.builder.model.SigningConfig", "com.android.build.gradle.internal.dsl.SigningConfigDsl");

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
    //
    // The blocks are processed top down i.e. android is resolved before any symbols within the android closure are resolved.
    // When a particular symbol is resolved, the resolution is cached as user data of that PsiElement under CONTRIBUTOR_KEY.
    // All symbols inside the android block first attempt to determine their parent method, and look at the parent contributor.
    // Depending on the parent contributor (method or class), the symbols are resolved to be either method calls of a class or
    // new domain objects.

    // we only care about symbols within the android closure
    String topLevel = ContainerUtil.getLastItem(callStack, null);
    if (!DSL_ANDROID.equals(topLevel)) {
      return;
    }

    logClassPathOnce(place.getProject());

    GroovyPsiManager psiManager = GroovyPsiManager.getInstance(place.getProject());

    // top level android block
    if (callStack.size() == 1) {
      String clz = resolveAndroidExtension(place.getContainingFile());
      if (clz == null) {
        return;
      }
      PsiClass contributorClass = psiManager.findClassWithCache(clz, place.getResolveScope());
      if (contributorClass == null) {
        return;
      }

      GrLightMethodBuilder methodWithClosure = GradleResolverUtil.createMethodWithClosure("android", ANDROID_FQCN, null, place, psiManager);
      if (methodWithClosure != null) {
        processor.execute(methodWithClosure, state);
      }

      cacheContributorInfo(place, contributorClass);
      return;
    }

    // For all blocks within android, we first figure out who contributed the parent block. We do this by first obtaining the closeable
    // block that contains this element, and figuring out the method whose closure argument is the closeable block. We do this instead of
    // directly looking for a parent element of type method call since this scheme allows us to handle both the following two cases:
    //   sourceSets {
    //      ^main {}
    //      ^debug.setRoot()
    //   }
    // In the above example, parent(parent('main')) == parent('debug') == 'sourceSets'.
    GrClosableBlock closeableBlock = PsiTreeUtil.getParentOfType(place, GrClosableBlock.class);
    if (closeableBlock == null || !(closeableBlock.getParent() instanceof GrMethodCall)) {
      return;
    }
    PsiElement parentContributor = closeableBlock.getParent().getUserData(CONTRIBUTOR_KEY);
    if (parentContributor == null) {
      return;
    }

    // if the parent object is a class, then process the current identifier as a method of the parent class
    if (parentContributor instanceof PsiClass) {
      PsiMethod method = findAndProcessContributingMethod(callStack.get(0), processor, state, place, (PsiClass)parentContributor);
      cacheContributorInfo(place, method);
      return;
    }

    // if the parent object is a method, then the type of the current object depends on the arguments of the parent:
    // (In the snippets below, ^ points to the current symbol being resolved).
    //     1. lintOptions { ^quiet = true; }
    //        lintOptions is declared as: lintOptions(Action<LintOptionsImpl> action).
    //        So 'quiet' is simply a method on the LintOptionsImpl class.
    //     2. buildTypes { debug^ { } }
    //        buildTypes is declared as: buildTypes(Action<NamedDomainObjectContainer<DefaultBuildType>> action)
    //        So debug is a named domain object of type DefaultBuildType
    //     3. sourceSets {
    //             main {}
    //             debug.setRoot {}
    //        }
    //        This is similar to case 2, we just need to make sure that debug is resolved as a variable of type
    //        AndroidSourceSet
    if (!(parentContributor instanceof PsiMethod)) {
      return;
    }

    PsiParameter[] parameters = ((PsiMethod)parentContributor).getParameterList().getParameters();

    // The method must have had atleast 1 closure argument.
    if (parameters.length < 1) {
      LOG.info("inside the closure of a method, but method has " + parameters.length + " arguments (expected atleast 1).");
      return;
    }

    PsiParameter param = parameters[parameters.length-1];
    String parameterType = param.getType().getCanonicalText();
    ParametrizedTypeExtractor typeExtractor = new ParametrizedTypeExtractor(parameterType);

    if (typeExtractor.hasNamedDomainObjectContainer()) {
      String namedDomainObject = typeExtractor.getNamedDomainObject();
      assert namedDomainObject != null : parameterType; // because hasNamedDomainObjectContainer()

      PsiClass contributorClass = findClassByName(psiManager, place.getResolveScope(), namedDomainObject);
      if (contributorClass == null) {
        return;
      }

      if (place.getParent() instanceof GrMethodCallExpression) {
        // define a new named domain object as a method that takes a closure
        GrLightMethodBuilder methodWithClosure = GradleResolverUtil
          .createMethodWithClosure(place.getText(), namedDomainObject, null, place, GroovyPsiManager.getInstance(place.getProject()));
        if (methodWithClosure != null) {
          processor.execute(methodWithClosure, state);
        }
        cacheContributorInfo(place, contributorClass);
      }
      else if (place.getParent() instanceof GrReferenceExpression) {
        // resolve the symbol as a variable of type namedDomainObject
        GrLightVariable variable = new GrLightVariable(place.getManager(), place.getText(), namedDomainObject, place);
        processor.execute(variable, state);
      }

      return;
    }

    if (typeExtractor.isClosure()) {
      String clz = typeExtractor.getClosureType();
      assert clz != null : parameterType; // because typeExtractor.isClosure()

      PsiClass contributorClass = findClassByName(psiManager, place.getResolveScope(), clz);
      if (contributorClass == null) {
        return;
      }

      PsiMethod method = findAndProcessContributingMethod(callStack.get(0), processor, state, place, contributorClass);
      cacheContributorInfo(place, method);
    }
  }

  @Nullable
  private static PsiClass findClassByName(GroovyPsiManager psiManager, GlobalSearchScope resolveScope, @NotNull String fqcn) {
    if (ourDslForClassMap.containsKey(fqcn)) {
      fqcn = ourDslForClassMap.get(fqcn);
    }

    return psiManager.findClassWithCache(fqcn, resolveScope);
  }

  private static void cacheContributorInfo(@NotNull PsiElement place, @Nullable PsiElement contributor) {
    if (contributor == null) {
      return;
    }

    // only cache info if this is a method call (and not a reference expression or something else),
    // as only method calls can contain closure arguments where this might be needed
    if (!(place.getParent() instanceof GrMethodCall)) {
      return;
    }

    // A method call of form "lintOptions { quiet = true }" has a PSI structure like:
    //   |- Method call
    //   |---- Reference Expression
    //   |--------PsiElement (identifier) (place usually points to this)
    //   |---- Arguments
    //   |---- Closeable block
    // Rather than caching information at the method call identifier, we cache it at the
    // root method call.
    GrMethodCall method = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
    if (method != null) {
      method.putUserData(CONTRIBUTOR_KEY, contributor);
    }
  }

  @Nullable
  private static PsiMethod findAndProcessContributingMethod(String symbol,
                                                            PsiScopeProcessor processor,
                                                            ResolveState state,
                                                            PsiElement place,
                                                            PsiClass contributorClass) {
    PsiMethod method = getContributingMethod(place, contributorClass, symbol);
    if (method == null) {
      return null;
    }

    GrLightMethodBuilder builder = new GrLightMethodBuilder(place.getManager(), method.getName());
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getManager().getProject());
    PsiType type = new PsiArrayType(factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope()));
    builder.addParameter(new GrLightParameter("param", type, builder));
    PsiClassType retType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope());
    builder.setReturnType(retType);
    processor.execute(builder, state);

    builder.setNavigationElement(method);
    return method;
  }

  @Nullable
  private static PsiMethod getContributingMethod(PsiElement place,
                                                 PsiClass contributorClass,
                                                 String methodName) {
    GrMethodCall call = PsiTreeUtil.getParentOfType(place, GrMethodCall.class);
    if (call == null) {
      return null;
    }

    GrArgumentList args = call.getArgumentList();
    int argsCount = GradleResolverUtil.getGrMethodArumentsCount(args);

    PsiMethod[] methodsByName = findMethodByName(contributorClass, methodName);

    // first check to see if we can narrow down by # of arguments
    for (PsiMethod method : methodsByName) {
      if (method.getParameterList().getParametersCount() == argsCount) {
        return method;
      }
    }

    // if we couldn't narrow down by # of arguments, just use the first one
    return methodsByName.length > 0 ? methodsByName[0] : null;
  }

  @NotNull
  private static PsiMethod[] findMethodByName(PsiClass contributorClass, String methodName) {
    // Search for methods that match the given name, or a setter or getter.
    List<String> possibleMethods = Arrays.asList(methodName,
                                                 GroovyPropertyUtils.getSetterName(methodName),
                                                 GroovyPropertyUtils.getGetterNameNonBoolean(methodName),
                                                 GroovyPropertyUtils.getGetterNameBoolean(methodName));

    for (String possibleMethod : possibleMethods) {
      PsiMethod[] methods = contributorClass.findMethodsByName(possibleMethod, true);
      if (methods.length > 0) {
        return methods;
      }
    }

    return PsiMethod.EMPTY_ARRAY;
  }

  private void logClassPathOnce(@NotNull Project project) {
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

  /** Returns the class corresponding to the android extension for given file. */
  @Nullable
  private static String resolveAndroidExtension(PsiFile file) {
    assert file instanceof GroovyFile;
    List<String> plugins = GradleBuildFile.getPlugins((GroovyFile)file);
    if (plugins.contains("android")) {
      return ANDROID_FQCN;
    }
    else if (plugins.contains("android-library")) {
      return ANDROID_LIB_FQCN;
    }
    else {
      return null;
    }
  }

  /**
   * {@link ParametrizedTypeExtractor} is a simple utility class that allows a few queries to be made on a parameterized type
   * such as {@code Action<NamedDomainObject<X>>}.
   */
  @VisibleForTesting
  static class ParametrizedTypeExtractor {
    private static final String GRADLE_ACTION_FQCN = "org.gradle.api.Action";
    private static final String GRADLE_NAMED_DOMAIN_OBJECT_CONTAINER_FQCN = "org.gradle.api.NamedDomainObjectContainer";

    private static final Splitter SPLITTER = Splitter.onPattern("[<>]").trimResults().omitEmptyStrings();
    private final ArrayList<String> myParameterTypes;

    public ParametrizedTypeExtractor(String canonicalType) {
      myParameterTypes = Lists.newArrayList(SPLITTER.split(canonicalType));
    }

    public boolean isClosure() {
      return myParameterTypes.contains(GRADLE_ACTION_FQCN);
    }

    @Nullable
    public String getClosureType() {
      if (!isClosure()) {
        return null;
      }

      StringBuilder sb = new StringBuilder(100);
      for (int i = 1; i < myParameterTypes.size(); i++) {
        String type = myParameterTypes.get(i);
        type = type.replace("? extends ", ""); // remove wildcards
        type = type.replace("? super ", ""); // remove wildcards
        sb.append(type);
        if (i != myParameterTypes.size() - 1) {
          sb.append('<');
        }
      }
      for (int i = 1; i < myParameterTypes.size() - 1; i++) {
        sb.append('>');
      }

      return sb.toString();
    }

    @Nullable
    public String getNamedDomainObject() {
      return hasNamedDomainObjectContainer() ? ContainerUtil.getLastItem(myParameterTypes) : null;
    }

    public boolean hasNamedDomainObjectContainer() {
      for (String type : myParameterTypes) {
        if (type.contains(GRADLE_NAMED_DOMAIN_OBJECT_CONTAINER_FQCN)) {
          return true;
        }
      }

      return false;
    }
  }
}
