/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.lint.checks.GradleDetector;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import groovy.lang.Closure;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.resolve.GradleMethodContextContributor;
import org.jetbrains.plugins.gradle.service.resolve.GradleResolverUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt;
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo;

/**
 * {@link AndroidDslContributor} provides symbol resolution for identifiers inside the android block
 * in a Gradle build script.
 */
public class AndroidDslContributor implements GradleMethodContextContributor {
  @Override
  public @Nullable DelegatesToInfo getDelegatesToInfo(@NotNull GrClosableBlock closure) {
    PsiElement parent = closure.getParent();
    if (parent instanceof GrMethodCallExpression) {
      GrMethodCallExpression methodCallExpression = (GrMethodCallExpression)parent;
      if (methodCallExpression.getInvokedExpression().getText().equals("kotlinOptions")) {
        PsiType kotlinOptions = TypesUtil.createType("org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions", closure);
        return new DelegatesToInfo(kotlinOptions, Closure.DELEGATE_FIRST);
      }
    }

    // This looks plausible, but in fact it doesn't work, because kotlinOptions does not resolve in BaseAppModuleExtension.  This whole
    // mechanism looks plausible for some of the other things we want to do (add delegation information) but the pattern language fails
    // to match most of our blocks because of the multiple resolutions (to a method accepting a Function1<> *and* a method accepting
    // an Action).
    //GroovyClosurePattern pattern = groovyClosure().inMethod(psiMethod(ANDROID_FQCN, "kotlinOptions"));
    //ProcessingContext context = new ProcessingContext();
    //if (pattern.accepts(closure, context)) {
    //  PsiType kotlinOptions = TypesUtil.createType("org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions", closure);
    //  // TODO(xof): save in context
    //  return new DelegatesToInfo(kotlinOptions, Closure.DELEGATE_FIRST);
    //}
    return null;
  }

  @NonNls private static final String DSL_ANDROID = "android";
  @NonNls private static final String ANDROID_FQCN = "com.android.build.gradle.internal.dsl.BaseAppModuleExtension";
  @NonNls private static final String ANDROID_LIB_FQCN = "com.android.build.gradle.LibraryExtension";

  private static final Key<PsiElement> CONTRIBUTOR_KEY = Key.create("AndroidDslContributor.key");

  private static final Map<String, String> ourDslForClassMap = ImmutableMap.of(
    "com.android.builder.DefaultProductFlavor", "com.android.build.gradle.internal.dsl.ProductFlavorDsl",
    "com.android.builder.DefaultBuildType", "com.android.build.gradle.internal.dsl.BuildTypeDsl",
    "com.android.builder.model.SigningConfig", "com.android.build.gradle.internal.dsl.SigningConfigDsl");

  @Override
  public boolean process(@NotNull List<String> callStack,
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


    // There are two issues that necessitate this custom processing: 1. Groovy doesn't know what the block corresponding to
    // 'android' with a closure means i.e. it doesn't know that it is an extension provided by the android Gradle plugin.
    // 2. Once it understands that 'android' is a closure of a certain type, it still stumbles over methods that take in
    // either an Action<T> or a Action<NamedDomainObject<T>>. So most of the code simply tries to match the former to a method
    // that takes a closure<T> and the latter to be a closure that defines objects with closure<T>

    // we only care about symbols within the android closure
    String topLevel = ContainerUtil.getLastItem(callStack, null);
    if (!DSL_ANDROID.equals(topLevel)) {
      return true;
    }

    // top level android block
    if (callStack.size() == 1) {
      String fqcn = resolveAndroidExtension(place.getContainingFile());
      PsiClass contributorClass = fqcn == null ? null : findClassByName(place.getProject(), place.getResolveScope(), fqcn);
      if (contributorClass != null) {
        String qualifiedName = contributorClass.getQualifiedName();
        if (qualifiedName == null) {
          qualifiedName = fqcn;
        }

        // resolve 'android' as a method that takes a closure
        resolveToMethodWithClosure(place, contributorClass, qualifiedName, processor, state);
        cacheContributorInfo(place, contributorClass);
      }
      return true;
    }

    // For all blocks within android, we first figure out who contributed the parent block.
    PsiElement parentContributor = getParentContributor(place);
    if (parentContributor == null) {
      return true;
    }

    // if the parent object is a class, then process the current identifier as a method of the parent class
    if (parentContributor instanceof PsiClass) {
      PsiMethod method =
        findAndProcessContributingMethod(callStack.get(0), processor, state, place, (PsiClass)parentContributor);
      cacheContributorInfo(place, method);
      return true;
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
    //        This is similar to case 2, we just need to make sure that debug is resolved as a variable of type NamedModuleTemplate
    if (!(parentContributor instanceof PsiMethod)) {
      return true;
    }

    // determine the type variable present in the parent method
    ParametrizedTypeExtractor typeExtractor = getTypeExtractor((PsiMethod)parentContributor);
    if (typeExtractor == null) {
      Logger.getInstance(AndroidDslContributor.class)
        .info("inside the closure of a method, but unable to extract the closure parameter's type.");
      return true;
    }

    if (typeExtractor.hasNamedDomainObjectContainer()) {
      // this symbol must be a NamedDomainObject<T>
      // so define a it as a method with the given name (place.getText()) with an argument Closure<T>
      String namedDomainObject = typeExtractor.getNamedDomainObject();
      assert namedDomainObject != null : typeExtractor.getCanonicalType(); // because hasNamedDomainObjectContainer()

      PsiClass contributorClass = findClassByName(place.getProject(), place.getResolveScope(), namedDomainObject);
      if (contributorClass != null) {
        String qualifiedName = contributorClass.getQualifiedName();
        if (qualifiedName == null) {
          qualifiedName = namedDomainObject;
        }
        resolveToMethodWithClosure(place, contributorClass, qualifiedName, processor, state);
        cacheContributorInfo(place, contributorClass);
      }
      return true;
    }

    if (typeExtractor.isClosure()) {
      // the parent method was of type Action<T>, so this is simply a method of class T
      String clz = typeExtractor.getClosureType();
      assert clz != null : typeExtractor.getCanonicalType(); // because typeExtractor.isClosure()

      PsiClass contributorClass = findClassByName(place.getProject(), place.getResolveScope(), clz);
      if (contributorClass == null) {
        return true;
      }

      PsiMethod method = findAndProcessContributingMethod(callStack.get(0), processor, state, place, contributorClass);
      cacheContributorInfo(place, method);
    }
    return true;
  }

  private static void resolveToMethodWithClosure(PsiElement place,
                                                 PsiElement resolveToElement,
                                                 String closureTypeFqcn,
                                                 PsiScopeProcessor processor,
                                                 ResolveState state) {
    if (place.getParent() instanceof GrMethodCallExpression && ResolveUtilKt.shouldProcessMethods(processor)) {
      GrLightMethodBuilder methodWithClosure =
        GradleResolverUtil.createMethodWithClosure(place.getText(), closureTypeFqcn, null, place);
      if (methodWithClosure != null) {
        processor.execute(methodWithClosure, state);
        methodWithClosure.setNavigationElement(resolveToElement);
      }
    } else if (place.getParent() instanceof GrReferenceExpression && ResolveUtilKt.shouldProcessLocals(processor)) {
      GrLightVariable variable = new GrLightVariable(place.getManager(), place.getText(), closureTypeFqcn, place);
      processor.execute(variable, state);
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

    ParametrizedTypeExtractor typeExtractor = getTypeExtractor(method);
    if (typeExtractor != null && !typeExtractor.hasNamedDomainObjectContainer() && typeExtractor.isClosure()) {
      // method takes a closure argument
      String clz = typeExtractor.getClosureType();
      if (clz == null) {
        clz = CommonClassNames.JAVA_LANG_OBJECT;
      }
      if (ourDslForClassMap.containsKey(clz)) {
        clz = ourDslForClassMap.get(clz);
      }
      resolveToMethodWithClosure(place, method, clz, processor, state);
    } else {
      if (ResolveUtilKt.shouldProcessMethods(processor)) {
        GrLightMethodBuilder builder = new GrLightMethodBuilder(place.getManager(), method.getName());
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(place.getManager().getProject());
        PsiType type = new PsiArrayType(factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope()));
        builder.addParameter(new GrLightParameter("param", type, builder));
        PsiClassType retType = factory.createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, place.getResolveScope());
        builder.setReturnType(retType);
        builder.setContainingClass(contributorClass);
        processor.execute(builder, state);

        builder.setNavigationElement(method);
      }
    }

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
    List<String> possibleMethods = Arrays.asList(methodName, GroovyPropertyUtils.getSetterName(methodName),
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

  @Nullable
  private static ParametrizedTypeExtractor getTypeExtractor(PsiMethod parentContributor) {
    PsiParameter[] parameters = parentContributor.getParameterList().getParameters();

    // The method must have had at least 1 closure argument.
    if (parameters.length < 1) {
      return null;
    }

    PsiParameter param = parameters[parameters.length-1];
    String parameterType = param.getType().getCanonicalText();

    return new ParametrizedTypeExtractor(parameterType);
  }

  /**
   * Returns the contributor of the enclosing block.
   * This is performed by first obtaining the closeable block that contains this element, and figuring out the method whose
   * closure argument is the closeable block. We do this instead of directly looking for a parent element of type method call
   * since this scheme allows us to handle both the following two cases:
   *   sourceSets {
   *      ^main {}
   *      ^debug.setRoot()
   *   }
   * In the above example, parent(parent('main')) == parent('debug') == 'sourceSets'.
   */
  @Nullable
  private static PsiElement getParentContributor(PsiElement place) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    GrClosableBlock closeableBlock = PsiTreeUtil.getParentOfType(place, GrClosableBlock.class);
    if (closeableBlock == null || !(closeableBlock.getParent() instanceof GrMethodCall)) {
      return null;
    }
    PsiElement parentContributor = closeableBlock.getParent().getUserData(CONTRIBUTOR_KEY);
    if (parentContributor == null) {
      return null;
    }
    return parentContributor;
  }

  @Nullable
  public static PsiClass findClassByName(Project project, GlobalSearchScope resolveScope, @NotNull String fqcn) {
    if (ourDslForClassMap.containsKey(fqcn)) {
      fqcn = ourDslForClassMap.get(fqcn);
    }

    return JavaPsiFacade.getInstance(project).findClass(fqcn, resolveScope);
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

  /** Returns the class corresponding to the android extension for given file. */
  @Nullable
  private static String resolveAndroidExtension(PsiFile file) {
    // 1 - First attempt to find the module for the given build file.
    Module[] modules = ModuleManager.getInstance(file.getProject()).getModules();
    Module found = null;
    for (Module module : modules) {
      GradleFacet facet = GradleFacet.getInstance(module);
      if (facet == null) {
        continue;
      }

      GradleModuleModel gradleModuleModel = facet.getGradleModuleModel();
      if (gradleModuleModel == null) {
        continue;
      }

      if (Objects.equals(gradleModuleModel.getBuildFile(), file.getVirtualFile())) {
        found = module;
        break;
      }
    }

    // 2 - If we found it, check the type of the module
    if (found != null) {
      GradleAndroidModel gradleAndroidModel = GradleAndroidModel.get(found);
      if (gradleAndroidModel != null) {
        if (gradleAndroidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_APP) {
          return ANDROID_FQCN;
        }
        else if (gradleAndroidModel.getAndroidProject().getProjectType() == IdeAndroidProjectType.PROJECT_TYPE_LIBRARY) {
          return ANDROID_LIB_FQCN;
        }
      }
    }

    // 3 - Otherwise fall back to simply searching for the string.
    assert file instanceof GroovyFile;

    String fileContents = file.getText();
    if (fileContents.contains(GradleDetector.APP_PLUGIN_ID) || fileContents.contains(GradleDetector.OLD_APP_PLUGIN_ID)) {
      return ANDROID_FQCN;
    }
    else if (fileContents.contains(GradleDetector.LIB_PLUGIN_ID) || fileContents.contains(GradleDetector.OLD_LIB_PLUGIN_ID)) {
      return ANDROID_LIB_FQCN;
    }

    // Nothing was found.
    return null;
  }

  /**
   * {@link ParametrizedTypeExtractor} is a simple utility class that allows a few queries to be made on a parameterized type
   * such as {@code Action<NamedDomainObject<X>>}.
   */
  @VisibleForTesting
  public static class ParametrizedTypeExtractor {
    private static final String GRADLE_ACTION = "org.gradle.api.Action";
    private static final Pattern GRADLE_ACTION_PATTERN = Pattern.compile(Pattern.quote(GRADLE_ACTION) + "<([^<>]+)(?:<([^<>]+)>)?>");
    private static final String KOTLIN_FUNCTION1 = "kotlin.jvm.functions.Function1";
    private static final Pattern KOTLIN_FUNCTION1_PATTERN = Pattern.compile(Pattern.quote(KOTLIN_FUNCTION1) + "<([^<>]+)(?:<([^<>]+)>)?,kotlin.Unit>");
    private static final String GRADLE_NAMED_DOMAIN_OBJECT_CONTAINER_FQCN = "org.gradle.api.NamedDomainObjectContainer";

    private final List<String> myParameterTypes;
    private final String myCanonicalType;

    public ParametrizedTypeExtractor(String canonicalType) {
      myCanonicalType = canonicalType;
      myParameterTypes = parseCanonicalType(canonicalType);
    }

    private ArrayList<String> parseCanonicalType(String canonicalType) {
      ArrayList<String> result = new ArrayList<>();
      Pattern pattern;
      if (canonicalType.startsWith(GRADLE_ACTION)) {
        result.add(GRADLE_ACTION);
        pattern = GRADLE_ACTION_PATTERN;
      }
      else if (canonicalType.startsWith(KOTLIN_FUNCTION1)) {
        result.add(KOTLIN_FUNCTION1);
        pattern = KOTLIN_FUNCTION1_PATTERN;
      }
      else {
        result.add(canonicalType);
        // no further parsing needed.
        return result;
      }

      Matcher matcher = pattern.matcher(canonicalType);
      if (matcher.matches()) {
        result.add(matcher.group(1));
        if (matcher.group(2) != null) {
          result.add(matcher.group(2));
        }
      }
      return result;
    }

    public String getCanonicalType() {
      return myCanonicalType;
    }

    public boolean isClosure() {
      return myParameterTypes.contains(GRADLE_ACTION) || myParameterTypes.contains(KOTLIN_FUNCTION1);
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
