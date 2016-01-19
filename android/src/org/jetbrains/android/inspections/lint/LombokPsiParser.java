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
package org.jetbrains.android.inspections.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import lombok.ast.Catch;
import lombok.ast.Node;
import lombok.ast.Position;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.ATTR_VALUE;

public class LombokPsiParser extends JavaParser {
  private final LintClient myClient;
  private AccessToken myLock;

  public LombokPsiParser(LintClient client) {
    myClient = client;
  }

  @Override
  public void prepareJavaParse(@NonNull List<JavaContext> contexts) {
  }

  @Nullable
  @Override
  public Node parseJava(@NonNull final JavaContext context) {
    assert myLock == null;
    myLock = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      Node node = parse(context);
      if (node != null) {
        return node;
      }
    }
    catch (Throwable ignore) {
    }
    myLock.finish();
    myLock = null;
    return null;
  }

  @Override
  public void dispose(@NonNull JavaContext context, @NonNull Node compilationUnit) {
    if (context.getCompilationUnit() != null) {
      context.setCompilationUnit(null);
    }

    if (myLock != null) {
      myLock.finish();
      myLock = null;
    }
  }

  @Nullable
  private Node parse(@NonNull JavaContext context) {
    // Should only be called from read thread
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    final PsiFile psiFile = IntellijLintUtils.getPsiFile(context);
    if (!(psiFile instanceof PsiJavaFile)) {
      return null;
    }
    PsiJavaFile javaFile = (PsiJavaFile)psiFile;

    try {
      return LombokPsiConverter.convert(javaFile);
    } catch (ProcessCanceledException ignore) {
      context.getDriver().cancel();
      return null;
    } catch (Throwable t) {
      myClient.log(t, "Failed converting PSI parse tree to Lombok for file %1$s",
                    context.file.getPath());
      return null;
    }
  }

  @NonNull
  @Override
  public Location getLocation(@NonNull JavaContext context, @NonNull Node node) {
    Position position = node.getPosition();
    if (position == null) {
      myClient.log(Severity.WARNING, null, "No position data found for node %1$s", node);
      return Location.create(context.file);
    }
    return Location.create(context.file, null, position.getStart(), position.getEnd());
  }

  @NonNull
  @Override
  public Location getRangeLocation(@NonNull JavaContext context, @NonNull Node from, int fromDelta, @NonNull Node to, int toDelta) {
    Position position1 = from.getPosition();
    Position position2 = to.getPosition();
    if (position1 == null) {
      return getLocation(context, to);
    }
    else if (position2 == null) {
      return getLocation(context, from);
    }

    int start = Math.max(0, from.getPosition().getStart() + fromDelta);
    int end = to.getPosition().getEnd() + toDelta;
    return Location.create(context.file, null, start, end);
  }

  @NonNull
  @Override
  public Location.Handle createLocationHandle(@NonNull JavaContext context, @NonNull Node node) {
    return new LocationHandle(context.file, node);
  }

  @Nullable
  private static PsiElement getPsiElement(@NonNull Node node) {
    Object nativeNode = node.getNativeNode();
    if (nativeNode == null) {
      return null;
    }
    return (PsiElement)nativeNode;
  }

  @Nullable
  @Override
  public ResolvedNode resolve(@NonNull JavaContext context, @NonNull Node node) {
    final PsiElement element = getPsiElement(node);
    if (element == null) {
      return null;
    }

    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      return resolve(element);
    }
    return application.runReadAction(new Computable<ResolvedNode>() {
      @Nullable
      @Override
      public ResolvedNode compute() {
        return resolve(element);
      }
    });
  }

  @Nullable
  @Override
  public ResolvedClass findClass(@NonNull JavaContext context, @NonNull final String fullyQualifiedName) {
    Node compilationUnit = context.getCompilationUnit();
    if (compilationUnit == null) {
      return null;
    }
    final PsiElement element = getPsiElement(compilationUnit);
    if (element == null) {
      return null;
    }

    return ApplicationManager.getApplication().runReadAction(new Computable<ResolvedClass>() {
      @Nullable
      @Override
      public ResolvedClass compute() {
        PsiClass aClass = JavaPsiFacade.getInstance(element.getProject()).findClass(fullyQualifiedName, element.getResolveScope());
        if (aClass != null) {
          return new ResolvedPsiClass(aClass);
        }

        return null;
      }
    });
  }

  @Override
  public List<TypeDescriptor> getCatchTypes(@NonNull JavaContext context, @NonNull Catch catchBlock) {
    Object nativeNode = catchBlock.getNativeNode();
    if (nativeNode instanceof PsiCatchSection) {
      PsiCatchSection node = (PsiCatchSection)nativeNode;
      PsiType type = node.getCatchType();
      if (type != null) {
        if (type instanceof PsiDisjunctionType) {
          List<PsiType> disjunctions = ((PsiDisjunctionType)type).getDisjunctions();
          List<TypeDescriptor> list = Lists.newArrayListWithCapacity(disjunctions.size());
          for (PsiType t : disjunctions) {
            list.add(new LombokPsiParser.PsiTypeDescriptor(t));
          }
          return list;
        } else {
          return Collections.<TypeDescriptor>singletonList(new LombokPsiParser.PsiTypeDescriptor(type));
        }
      }
    }

    return super.getCatchTypes(context, catchBlock);
  }

  @Nullable
  @Override
  public TypeDescriptor getType(@NonNull JavaContext context, @NonNull Node node) {
    final PsiElement element = getPsiElement(node);
    if (element == null) {
      return null;
    }

    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      return getTypeDescriptor(element);
    }
    return application.runReadAction(new Computable<TypeDescriptor>() {
      @Nullable
      @Override
      public TypeDescriptor compute() {
        return getTypeDescriptor(element);
      }
    });
  }

  @VisibleForTesting
  @Nullable
  static ResolvedNode resolve(@NonNull PsiElement element) {
    if (element instanceof PsiCall) {
      PsiMethod resolved = ((PsiCall)element).resolveMethod();
      if (resolved != null) {
        return new ResolvedPsiMethod(resolved);
      }
      return null;
    }

    PsiReference reference = element.getReference();
    if (reference != null) {
      PsiElement resolved = reference.resolve();
      if (resolved != null) {
        element = resolved;
      }
    }
    if (element instanceof PsiField) {
      return new ResolvedPsiField((PsiField)element);
    } else if (element instanceof PsiMethod) {
      return new ResolvedPsiMethod((PsiMethod)element);
    } else if (element instanceof PsiVariable) {
      return new ResolvedPsiVariable((PsiVariable)element);
    } else if (element instanceof PsiClass) {
      return new ResolvedPsiClass((PsiClass)element);
    } else if (element instanceof PsiJavaCodeReferenceElement) {
      PsiJavaCodeReferenceElement r = (PsiJavaCodeReferenceElement)element;
      String qualifiedName = r.getQualifiedName();
      if (qualifiedName != null) {
        return new ResolvedPsiClassName(element.getManager(), qualifiedName);
      }
    } else if (element instanceof PsiAnnotation) {
      return new ResolvedPsiAnnotation((PsiAnnotation)element);
    }

    return null;
  }

  @Nullable
  private static TypeDescriptor getTypeDescriptor(@NonNull PsiElement element) {
    PsiType type = null;
    if (element instanceof PsiExpression) {
      type = ((PsiExpression)element).getType();
    } else if (element instanceof PsiVariable) {
      type = ((PsiVariable)element).getType();
    } else if (element instanceof PsiMethod) {
      type = ((PsiMethod)element).getReturnType();
    } else if (element instanceof PsiAnnotation) {
      final PsiAnnotation annotation = (PsiAnnotation)element;
      return new DefaultTypeDescriptor(annotation.getQualifiedName()) {
        @Nullable
        @Override
        public ResolvedClass getTypeClass() {
          GlobalSearchScope resolveScope = annotation.getResolveScope();
          if (resolveScope.getProject() != null) {
            ApplicationManager.getApplication().assertReadAccessAllowed();
            PsiClass aClass = JavaPsiFacade.getInstance(resolveScope.getProject()).findClass(getSignature(), resolveScope);
            if (aClass != null) {
              return new ResolvedPsiClass(aClass);
            }
          }

          return null;
        }
      };
    }

    return getTypeDescriptor(type);
  }

  @Contract("!null -> !null")
  @Nullable
  private static TypeDescriptor getTypeDescriptor(@Nullable PsiType type) {
    return type != null ? new PsiTypeDescriptor(type) : null;
  }

  private static class PsiTypeDescriptor extends DefaultTypeDescriptor {
    @NonNull private final PsiType myType;

    public PsiTypeDescriptor(@NonNull PsiType type) {
      super(type.getCanonicalText());
      myType = type;
    }

    @Override
    @Nullable
    public ResolvedClass getTypeClass() {
      if (!TypeConversionUtil.isPrimitiveAndNotNull(myType)) {
        GlobalSearchScope resolveScope = myType.getResolveScope();
        if (resolveScope != null && resolveScope.getProject() != null) {
          ApplicationManager.getApplication().assertReadAccessAllowed();
          PsiClass aClass = JavaPsiFacade.getInstance(resolveScope.getProject()).findClass(getSignature(), resolveScope);
          if (aClass != null) {
            return new ResolvedPsiClass(aClass);
          }
        }
      }

      return null;
    }

    @Override
    public boolean isPrimitive() {
      return myType.getDeepComponentType() instanceof PsiPrimitiveType;
    }

    @Override
    public boolean isArray() {
      return myType instanceof PsiArrayType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PsiTypeDescriptor that = (PsiTypeDescriptor)o;

      if (!myType.equals(that.myType)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myType.hashCode();
    }
  }

  private static int computeModifiers(@Nullable PsiModifierListOwner owner) {
    // TODO: Find out if there is a PSI utility method somewhere to handle this
    int modifiers = 0;
    if (owner != null) {
      if (owner.hasModifierProperty(PsiModifier.ABSTRACT)) {
        modifiers |= Modifier.ABSTRACT;
      }
      if (owner.hasModifierProperty(PsiModifier.PUBLIC)) {
        modifiers |= Modifier.PUBLIC;
      }
      if (owner.hasModifierProperty(PsiModifier.STATIC)) {
        modifiers |= Modifier.STATIC;
      }
      if (owner.hasModifierProperty(PsiModifier.PRIVATE)) {
        modifiers |= Modifier.PRIVATE;
      }
      if (owner.hasModifierProperty(PsiModifier.PROTECTED)) {
        modifiers |= Modifier.PROTECTED;
      }
      if (owner.hasModifierProperty(PsiModifier.FINAL)) {
        modifiers |= Modifier.FINAL;
      }
      // Other constants are not used by lint.
    }

    return modifiers;
  }

  @NonNull
  private static Iterable<ResolvedAnnotation> getAnnotations(@Nullable PsiModifierListOwner owner) {
    if (owner != null) {
      PsiModifierList modifierList = owner.getModifierList();
      if (modifierList != null) {
        PsiAnnotation[] annotations = modifierList.getAnnotations();
        if (annotations.length > 0) {
          List<ResolvedAnnotation> result = Lists.newArrayListWithExpectedSize(annotations.length);
          for (PsiAnnotation method : annotations) {
            result.add(new ResolvedPsiAnnotation(method));
          }
          return result;
        }
      }

      ExternalAnnotationsManager annotationsManager = ExternalAnnotationsManager.getInstance(owner.getProject());
      PsiAnnotation[] annotations = annotationsManager.findExternalAnnotations(owner);
      if (annotations != null) {
        List<ResolvedAnnotation> result = Lists.newArrayListWithExpectedSize(annotations.length);
        for (PsiAnnotation method : annotations) {
          result.add(new ResolvedPsiAnnotation(method));
        }
        return result;
      }
    }

    return Collections.emptyList();
  }

  @VisibleForTesting
  static boolean isInPackage(@Nullable PsiClass cls, @NonNull String pkg, boolean includeSubPackages) {
    if (cls != null) {
      PsiClass outer = cls.getContainingClass();
      while (outer != null) {
        cls = outer;
        outer = cls.getContainingClass();
      }
      String qualifiedName = cls.getQualifiedName();
      if (qualifiedName == null) {
        return false;
      }
      if (!qualifiedName.startsWith(pkg)) {
        return false;
      }
      if (!includeSubPackages) {
        return qualifiedName.length() - cls.getName().length() - 1 == pkg.length();
      } else {
        return qualifiedName.length() == pkg.length() || qualifiedName.charAt(pkg.length()) == '.';
      }
    }

    return false;
  }

  /* Handle for creating positions cheaply and returning full fledged locations later */
  private class LocationHandle implements Location.Handle {
    private final File myFile;
    private final Node myNode;
    private Object mClientData;

    public LocationHandle(File file, Node node) {
      myFile = file;
      myNode = node;
    }

    @NonNull
    @Override
    public Location resolve() {
      Position pos = myNode.getPosition();
      if (pos == null) {
        myClient.log(Severity.WARNING, null, "No position data found for node %1$s", myNode);
        return Location.create(myFile);
      }
      return Location.create(myFile, null /*contents*/, pos.getStart(), pos.getEnd());
    }

    @Override
    public void setClientData(@Nullable Object clientData) {
      mClientData = clientData;
    }

    @Override
    @Nullable
    public Object getClientData() {
      return mClientData;
    }
  }

  private static class ResolvedPsiMethod extends ResolvedMethod {
    private PsiMethod myMethod;

    private ResolvedPsiMethod(@NonNull PsiMethod method) {
      myMethod = method;
    }

    @NonNull
    @Override
    public String getName() {
      return myMethod.getName();
    }

    @Override
    public boolean matches(@NonNull String name) {
      return name.equals(myMethod.getName());
    }

    @NonNull
    @Override
    public ResolvedClass getContainingClass() {
      PsiClass containingClass = myMethod.getContainingClass();
      return new ResolvedPsiClass(containingClass);
    }

    @Override
    public int getArgumentCount() {
      return myMethod.getParameterList().getParametersCount();
    }

    @NonNull
    @Override
    public TypeDescriptor getArgumentType(int index) {
      PsiParameter parameter = myMethod.getParameterList().getParameters()[index];
      PsiType type = parameter.getType();
      return getTypeDescriptor(type);
    }

    @Nullable
    @Override
    public TypeDescriptor getReturnType() {
      if (myMethod.isConstructor()) {
        return null;
      } else {
        return getTypeDescriptor(myMethod.getReturnType());
      }
    }

    @Override
    public String getSignature() {
      return myMethod.toString();
    }


    @NonNull
    @Override
    public Iterable<ResolvedAnnotation> getAnnotations() {
      return LombokPsiParser.getAnnotations(myMethod);
    }

    @NonNull
    @Override
    public Iterable<ResolvedAnnotation> getParameterAnnotations(int index) {
      PsiParameter[] parameters = myMethod.getParameterList().getParameters();
      if (index >= 0 && index < parameters.length) {
        return LombokPsiParser.getAnnotations(parameters[index]);
      }

      return Collections.emptyList();
    }


    @Nullable
    @Override
    public ResolvedAnnotation getParameterAnnotation(@NonNull String type, int parameterIndex) {
      PsiParameter[] parameters = myMethod.getParameterList().getParameters();
      if (parameterIndex >= 0 && parameterIndex < parameters.length) {
        PsiParameter parameter = parameters[parameterIndex];
        PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameter, type);
        if (annotation != null) {
          return new ResolvedPsiAnnotation(annotation);
        }
      }

      return null;
    }

    @Nullable
    @Override
    public ResolvedAnnotation getAnnotation(@NonNull String type) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(myMethod, type);
      if (annotation != null) {
        return new ResolvedPsiAnnotation(annotation);
      }

      return null;
    }

    @Override
    public int getModifiers() {
      // TODO: Find out if there is a PSI utility method somewhere to handle this
      int modifiers = 0;
      if (myMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
        modifiers |= Modifier.ABSTRACT;
      }
      if (myMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
        modifiers |= Modifier.PUBLIC;
      }
      if (myMethod.hasModifierProperty(PsiModifier.STATIC)) {
        modifiers |= Modifier.STATIC;
      }
      if (myMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
        modifiers |= Modifier.PRIVATE;
      }
      if (myMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
        modifiers |= Modifier.PROTECTED;
      }
      if (myMethod.hasModifierProperty(PsiModifier.FINAL)) {
        modifiers |= Modifier.FINAL;
      }
      // Other constants are not used by lint.

      return modifiers;
    }

    @Override
    @Nullable
    public ResolvedMethod getSuperMethod() {
      final PsiMethod[] superMethods = myMethod.findSuperMethods();
      if (superMethods.length > 0) {
        return new ResolvedPsiMethod(superMethods[0]);
      }
      return null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResolvedPsiMethod that = (ResolvedPsiMethod)o;

      if (!myMethod.equals(that.myMethod)) return false;

      return true;
    }

    @Override
    public boolean isInPackage(@NonNull String pkg, boolean includeSubPackages) {
      return LombokPsiParser.isInPackage(myMethod.getContainingClass(), pkg, includeSubPackages);
    }


    @Nullable
    @Override
    public Node findAstNode() {
      return LombokPsiConverter.toNode(myMethod);
    }

    @Override
    public int hashCode() {
      return myMethod.hashCode();
    }
  }

  private static class ResolvedPsiVariable extends ResolvedVariable {
    private PsiVariable myVariable;

    private ResolvedPsiVariable(@NonNull PsiVariable variable) {
      myVariable = variable;
    }

    @NonNull
    @Override
    public String getName() {
      return myVariable.getName();
    }

    @Override
    public boolean matches(@NonNull String name) {
      return name.equals(myVariable.getName());
    }

    @NonNull
    @Override
    public TypeDescriptor getType() {
      return getTypeDescriptor(myVariable.getType());
    }

    @Override
    public int getModifiers() {
      return computeModifiers(myVariable);
    }

    @NonNull
    @Override
    public Iterable<ResolvedAnnotation> getAnnotations() {
      return LombokPsiParser.getAnnotations(myVariable);
    }

    @Override
    public String getSignature() {
      return myVariable.toString();
    }


    @Nullable
    @Override
    public Node findAstNode() {
      return LombokPsiConverter.toNode(myVariable);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResolvedPsiVariable that = (ResolvedPsiVariable)o;

      if (!myVariable.equals(that.myVariable)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myVariable.hashCode();
    }
  }

  private static class ResolvedPsiField extends ResolvedField {
    private PsiField myField;

    private ResolvedPsiField(@NonNull PsiField field) {
      myField = field;
    }

    @NonNull
    @Override
    public String getName() {
      return myField.getName();
    }

    @Override
    public boolean matches(@NonNull String name) {
      return name.equals(myField.getName());
    }

    @NonNull
    @Override
    public TypeDescriptor getType() {
      return getTypeDescriptor(myField.getType());
    }

    @NonNull
    @Override
    public ResolvedClass getContainingClass() {
      return new ResolvedPsiClass(myField.getContainingClass());
    }

    @Nullable
    @Override
    public Object getValue() {
      return myField.computeConstantValue();
    }

    @NonNull
    @Override
    public Iterable<ResolvedAnnotation> getAnnotations() {
      return LombokPsiParser.getAnnotations(myField);
    }

    @Override
    public int getModifiers() {
      return computeModifiers(myField);
    }

    @Override
    public String getSignature() {
      return myField.toString();
    }

    @Override
    public boolean isInPackage(@NonNull String pkg, boolean includeSubPackages) {
      return LombokPsiParser.isInPackage(myField.getContainingClass(), pkg, includeSubPackages);
    }


    @Nullable
    @Override
    public Node findAstNode() {
      return LombokPsiConverter.toNode(myField);
    }
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResolvedPsiField that = (ResolvedPsiField)o;

      if (!myField.equals(that.myField)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myField.hashCode();
    }
  }

  private static class ResolvedPsiClassName extends ResolvedPsiClass {
    private final String myName;
    private final PsiManager myManager;
    private boolean myInitialized;

    private ResolvedPsiClassName(PsiManager manager, @NonNull String name) {
      super(null);
      myManager = manager;
      myName = name;
    }

    @NonNull
    @Override
    public String getName() {
      return myName;
    }

    @Override
    public boolean matches(@NonNull String name) {
      return name.equals(myName);
    }

    private void ensureInitialized() {
      if (myInitialized) {
        return;
      }
      myInitialized = true;
      Project project = myManager.getProject();
      myClass = JavaPsiFacade.getInstance(project).findClass(myName, GlobalSearchScope.allScope(project));
    }

    @Nullable
    @Override
    public ResolvedClass getSuperClass() {
      ensureInitialized();
      if (myClass != null) {
        return super.getSuperClass();
      }
      return null;
    }

    @Nullable
    @Override
    public ResolvedClass getContainingClass() {
      ensureInitialized();
      if (myClass != null) {
        return super.getContainingClass();
      }
      return null;
    }

    @Override
    public boolean isSubclassOf(@NonNull String name, boolean strict) {
      if (!strict && name.equals(myName)) {
        return true;
      }
      ensureInitialized();
      if (myClass != null) {
        return super.isSubclassOf(name, strict);
      }
      return false;
    }

    @NonNull
    @Override
    public Iterable<ResolvedMethod> getConstructors() {
      ensureInitialized();
      if (myClass != null) {
        return super.getConstructors();
      }
      return Collections.emptyList();
    }

    @NonNull
    @Override
    public Iterable<ResolvedMethod> getMethods(@NonNull String name, boolean includeInherited) {
      ensureInitialized();
      if (myClass != null) {
        return super.getMethods(name, includeInherited);
      }
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public ResolvedField getField(@NonNull String name, boolean includeInherited) {
      ensureInitialized();
      if (myClass != null) {
        return super.getField(name, includeInherited);
      }
      return null;
    }

    @Override
    public int getModifiers() {
      ensureInitialized();
      return computeModifiers(myClass);
    }

    @Override
    public String getSignature() {
      return myName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResolvedPsiClassName that = (ResolvedPsiClassName)o;

      if (!myName.equals(that.myName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myName.hashCode();
    }
  }

  private static class ResolvedPsiClass extends ResolvedClass {
    @Nullable protected PsiClass myClass;

    private ResolvedPsiClass(@Nullable PsiClass cls) {
      myClass = cls;
    }

    @NonNull
    @Override
    public String getName() {
      if (myClass != null) {
        String qualifiedName = myClass.getQualifiedName();
        if (qualifiedName != null) {
          return qualifiedName;
        }
        else {
          return myClass.getName();
        }
      }
      return "";
    }

    @NonNull
    @Override
    public String getSimpleName() {
      if (myClass != null) {
        return myClass.getName();
      }
      return "";
    }

    @Override
    public boolean matches(@NonNull String name) {
      return name.equals(getName());
    }

    @Nullable
    @Override
    public ResolvedClass getSuperClass() {
      if (myClass != null) {
        PsiClass superClass = myClass.getSuperClass();
        // When you make an anonymous inner class from an interface like this,
        // we want to treat the interface itself as the "super class"
        //   public View.OnClickListener onSave = new View.OnClickListener() {
        //     @Override
        //     public void onClick(View v) {
        //       ..
        if (PsiUtil.isLocalOrAnonymousClass(myClass)) {
          PsiClass[] interfaces = myClass.getInterfaces();
          if (interfaces.length > 0) {
            return new ResolvedPsiClass(interfaces[0]);
          }
        }
        if (superClass != null) {
          return new ResolvedPsiClass(superClass);
        }
      }

      return null;
    }

    @NonNull
    @Override
    public Iterable<ResolvedClass> getInterfaces() {
      if (myClass != null) {
        PsiClass[] interfaces = myClass.getInterfaces();
        if (interfaces.length > 0) {
          List<ResolvedClass> list = Lists.newArrayListWithExpectedSize(interfaces.length);
          for (PsiClass cls : interfaces) {
            list.add(new ResolvedPsiClass(cls));
          }
          return list;
        }
      }
      return Collections.emptyList();
    }

    @Override
    public TypeDescriptor getType() {
      if (myClass != null) {
        return new PsiTypeDescriptor(PsiTypesUtil.getClassType(myClass)) {
          @Nullable
          @Override
          public ResolvedClass getTypeClass() {
            return ResolvedPsiClass.this;
          }
        };
      }

      return super.getType();
    }

    @Nullable
    @Override
    public ResolvedClass getContainingClass() {
      if (myClass != null) {
        PsiClass containingClass = myClass.getContainingClass();
        if (containingClass != null) {
          return new ResolvedPsiClass(containingClass);
        }
      }
      return null;
    }

    @Override
    public boolean isSubclassOf(@NonNull String name, boolean strict) {
      return isInheritingFrom(name, strict);
    }

    @Override
    public boolean isImplementing(@NonNull String name, boolean strict) {
      return isInheritingFrom(name, strict);
    }

    @Override
    public boolean isInheritingFrom(@NonNull String name, boolean strict) {
      PsiClass cls = myClass;
      if (cls != null) {
        if (strict) {
          cls = cls.getSuperClass();
        }
        if (cls != null) {
          return InheritanceUtil.isInheritor(cls, name);
        }
      }
      return false;
    }

    @NonNull
    @Override
    public Iterable<ResolvedMethod> getConstructors() {
      if (myClass != null) {
        PsiMethod[] methods = myClass.getConstructors();
        if (methods.length > 0) {
          List<ResolvedMethod> result = Lists.newArrayListWithExpectedSize(methods.length);
          for (PsiMethod method : methods) {
            result.add(new ResolvedPsiMethod(method));
          }
          return result;
        }
      }
      return Collections.emptyList();
    }

    @NonNull
    @Override
    public Iterable<ResolvedMethod> getMethods(boolean includeInherited) {
      if (myClass != null) {
        PsiMethod[] methods = includeInherited ? myClass.getAllMethods() : myClass.getMethods();
        if (methods.length > 0) {
          List<ResolvedMethod> result = Lists.newArrayListWithExpectedSize(methods.length);
          for (PsiMethod method : methods) {
            if (!method.isConstructor()) {
              result.add(new ResolvedPsiMethod(method));
            }
          }
          return result;
        }
      }
      return Collections.emptyList();
    }

    @NonNull
    @Override
    public Iterable<ResolvedMethod> getMethods(@NonNull String name, boolean includeInherited) {
      if (myClass != null) {
        PsiMethod[] methods = myClass.findMethodsByName(name, includeInherited);
        if (methods.length > 0) {
          List<ResolvedMethod> result = Lists.newArrayListWithExpectedSize(methods.length);
          for (PsiMethod method : methods) {
            if (!method.isConstructor()) {
              result.add(new ResolvedPsiMethod(method));
            }
          }
          return result;
        }
      }
      return Collections.emptyList();
    }

    @NonNull
    @Override
    public Iterable<ResolvedField> getFields(boolean includeInherited) {
      if (myClass != null) {
        PsiField[] fields = includeInherited ? myClass.getAllFields() : myClass.getFields();
        if (fields.length > 0) {
          List<ResolvedField> result = Lists.newArrayListWithExpectedSize(fields.length);
          for (PsiField field : fields) {
            result.add(new ResolvedPsiField(field));
          }
          return result;
        }
      }
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public ResolvedField getField(@NonNull String name, boolean includeInherited) {
      if (myClass != null) {
        PsiField field = myClass.findFieldByName(name, includeInherited);
        if (field != null) {
          return new ResolvedPsiField(field);
        }
      }
      return null;
    }

    @Nullable
    @Override
    public ResolvedPackage getPackage() {
      if (myClass != null) {
        PsiFile file = myClass.getContainingFile();
        PsiDirectory dir = file.getContainingDirectory();
        if (dir != null) {
          PsiPackage pkg = JavaDirectoryService.getInstance().getPackage(dir);
          if (pkg != null) {
            return new ResolvedPsiPackage(pkg);
          }
        }
      }
      return null;
    }

    @Override
    public boolean isInPackage(@NonNull String pkg, boolean includeSubPackages) {
      return LombokPsiParser.isInPackage(myClass, pkg, includeSubPackages);
    }

    @Nullable
    @Override
    public Node findAstNode() {
      return myClass != null ? LombokPsiConverter.toNode(myClass) : null;
    }

    @NonNull
    @Override
    public Iterable<ResolvedAnnotation> getAnnotations() {
      return LombokPsiParser.getAnnotations(myClass);
    }

    @Override
    public int getModifiers() {
      return computeModifiers(myClass);
    }

    @Override
    public String getSignature() {
      return getName();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResolvedPsiClass that = (ResolvedPsiClass)o;

      if (myClass != null ? !myClass.equals(that.myClass) : that.myClass != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myClass != null ? myClass.hashCode() : 0;
    }
  }

  @NotNull
  public static ResolvedAnnotation createResolvedAnnotation(@NonNull PsiAnnotation annotation) {
    return new ResolvedPsiAnnotation(annotation);
  }

  private static class ResolvedPsiAnnotation extends ResolvedAnnotation {
    private PsiAnnotation myAnnotation;

    private ResolvedPsiAnnotation(@NonNull PsiAnnotation annotation) {
      myAnnotation = annotation;
    }

    @NonNull
    @Override
    public String getName() {
      String qualifiedName = myAnnotation.getQualifiedName();
      if (qualifiedName == null) {
        return "?";
      }
      return qualifiedName;
    }

    @Override
    public boolean matches(@NonNull String name) {
      return name.equals(getName());
    }

    @NonNull
    @Override
    public TypeDescriptor getType() {
      TypeDescriptor typeDescriptor = getTypeDescriptor(myAnnotation);
      assert typeDescriptor != null;
      return typeDescriptor;
    }

    @Nullable
    @Override
    public ResolvedClass getClassType() {
      PsiJavaCodeReferenceElement reference = myAnnotation.getNameReferenceElement();
      if (reference != null) {
        PsiElement element = reference.resolve();
        if (element instanceof PsiClass) {
          return new ResolvedPsiClass((PsiClass)element);
        }
      }

      return null;
    }

    @NonNull
    @Override
    public List<Value> getValues() {
      PsiNameValuePair[] attributes = myAnnotation.getParameterList().getAttributes();
      if (attributes.length > 0) {
        List<Value> values = Lists.newArrayListWithExpectedSize(attributes.length);
        for (PsiNameValuePair pair : attributes) {
          String name = pair.getName();
          if (name == null) {
            name = "value";
          }
          values.add(new Value(name, getAnnotationPairValue(pair)));
        }
        return values;
      }
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public Object getValue(@NonNull String name) {
      PsiNameValuePair[] attributes = myAnnotation.getParameterList().getAttributes();
      if (attributes.length > 0) {
        for (PsiNameValuePair pair : attributes) {
          String pairName = pair.getName();
          if (name.equals(pairName) || pairName == null && name.equals(ATTR_VALUE)) {
            return getAnnotationPairValue(pair);
          }
        }
      }
      return null;
    }

    @Nullable
    private static Object getAnnotationPairValue(@NonNull PsiNameValuePair pair) {
      PsiAnnotationMemberValue v = pair.getValue();
      if (v instanceof PsiLiteral) {
        PsiLiteral literal = (PsiLiteral)v;
        return literal.getValue();
      } else if (v instanceof PsiArrayInitializerMemberValue) {
        PsiArrayInitializerMemberValue mv = (PsiArrayInitializerMemberValue)v;
        PsiAnnotationMemberValue[] values = mv.getInitializers();
        List<Object> list = Lists.newArrayListWithExpectedSize(values.length);
        boolean fields = false;
        for (PsiAnnotationMemberValue mmv : values) {
          if (mmv instanceof PsiLiteral) {
            PsiLiteral literal = (PsiLiteral)mmv;
            list.add(literal.getValue());
          } else if (mmv instanceof PsiExpression) {
            if (mmv instanceof PsiReferenceExpression) {
              PsiElement resolved = ((PsiReferenceExpression)mmv).resolve();
              if (resolved instanceof PsiField) {
                list.add(new ResolvedPsiField((PsiField)resolved));
                fields = true;
                continue;
              }
            }
            Object o = JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)mmv, false);
            if (o == null && mmv instanceof PsiReferenceExpression) {
              PsiElement resolved = ((PsiReferenceExpression)mmv).resolve();
              if (resolved instanceof PsiField) {
                o = new ResolvedPsiField((PsiField)resolved);
              }
            }
            list.add(o);
          }
        }

        if (fields) {
          return list.toArray();
        }

        PsiReference reference = pair.getReference();
        if (reference != null) {
          PsiElement resolved = reference.resolve();
          if (resolved instanceof PsiAnnotationMethod) {
            PsiType returnType = ((PsiAnnotationMethod)resolved).getReturnType();
            if (returnType != null) {
              returnType = returnType.getDeepComponentType();
            }
            if (returnType != null && returnType.getCanonicalText().equals(CommonClassNames.JAVA_LANG_STRING)) {
              //noinspection SSBasedInspection,SuspiciousToArrayCall
              return list.toArray(new String[list.size()]);
            } else if (returnType != null && returnType.getCanonicalText().equals(CommonClassNames.JAVA_LANG_ANNOTATION_ANNOTATION)) {
              //noinspection SSBasedInspection,SuspiciousToArrayCall
              return list.toArray(new Annotation[list.size()]);
            } else if (PsiType.INT.equals(returnType)) {
              //noinspection SSBasedInspection,SuspiciousToArrayCall
              return list.toArray(new Integer[list.size()]);
            } else if (PsiType.LONG.equals(returnType)) {
              //noinspection SSBasedInspection,SuspiciousToArrayCall
              return list.toArray(new Long[list.size()]);
            } else if (PsiType.DOUBLE.equals(returnType)) {
              //noinspection SSBasedInspection,SuspiciousToArrayCall
              return list.toArray(new Double[list.size()]);
            } else if (PsiType.FLOAT.equals(returnType)) {
              //noinspection SSBasedInspection,SuspiciousToArrayCall
              return list.toArray(new Float[list.size()]);
            }
          }
        }

        // Pick type of array. Annotations are limited to Strings, Classes
        // and Annotations
        if (!list.isEmpty()) {
          Object first = list.get(0);
          if (first instanceof String) {
            //noinspection SuspiciousToArrayCall,SSBasedInspection
            return list.toArray(new String[list.size()]);
          } else if (first instanceof Annotation) {
              //noinspection SuspiciousToArrayCall
            return list.toArray(new Annotation[list.size()]);
          } else if (first instanceof Class) {
            //noinspection SuspiciousToArrayCall
            return list.toArray(new Class[list.size()]);
          }
        } else {
          return ArrayUtil.EMPTY_STRING_ARRAY;
        }

        return list.toArray();
      } else if (v instanceof PsiExpression) {
        return JavaConstantExpressionEvaluator.computeConstantExpression((PsiExpression)v, false);
      }

      return null;
    }

    @Override
    public int getModifiers() {
      return 0;
    }

    @Override
    public String getSignature() {
      return myAnnotation.getQualifiedName();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ResolvedPsiAnnotation that = (ResolvedPsiAnnotation)o;

      if (!myAnnotation.equals(that.myAnnotation)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return myAnnotation.hashCode();
    }
  }

  private static class ResolvedPsiPackage extends ResolvedPackage {
    private PsiPackage myPackage;

    public ResolvedPsiPackage(@NonNull PsiPackage pkg) {
      myPackage = pkg;
    }

    @NonNull
    @Override
    public String getName() {
      return myPackage.getQualifiedName();
    }

    @Override
    public String getSignature() {
      return getName();
    }

    @Override
    public int getModifiers() {
      return 0;
    }

    @Override
    @Nullable
    public ResolvedPsiPackage getParentPackage() {
      PsiPackage parentPackage = myPackage.getParentPackage();
      if (parentPackage != null) {
        return new ResolvedPsiPackage(parentPackage);
      }

      return null;
    }

    @NonNull
    @Override
    public Iterable<ResolvedAnnotation> getAnnotations() {
      return LombokPsiParser.getAnnotations(myPackage);
    }
  }
}
