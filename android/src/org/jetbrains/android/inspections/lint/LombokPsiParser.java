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
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import lombok.ast.*;
import org.jetbrains.annotations.Contract;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

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
    Node compilationUnit = parse(context);
    if (compilationUnit == null) {
      myLock.finish();
      myLock = null;
    }
    return compilationUnit;
  }

  @Override
  public void dispose(@NonNull JavaContext context, @NonNull Node compilationUnit) {
    if (context.getCompilationUnit() != null) {
      myLock.finish();
      myLock = null;
      context.setCompilationUnit(null);
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
    }

    return getTypeDescriptor(type);
  }

  @Contract("!null -> !null")
  @Nullable
  private static TypeDescriptor getTypeDescriptor(@Nullable PsiType type) {
    return type != null ? new DefaultTypeDescriptor(type.getCanonicalText()) : null;
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
  }

  private static class ResolvedPsiVariable extends ResolvedVariable {
    private PsiVariable myVariable;

    private ResolvedPsiVariable(PsiVariable variable) {
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

    @Override
    public String getSignature() {
      return myVariable.toString();
    }
  }

  private static class ResolvedPsiField extends ResolvedField {
    private PsiField myField;

    private ResolvedPsiField(PsiField field) {
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

    @Override
    public int getModifiers() {
      return computeModifiers(myField);
    }

    @Override
    public String getSignature() {
      return myField.toString();
    }
  }

  private static class ResolvedPsiClassName extends ResolvedPsiClass {
    private final String myName;
    private final PsiManager myManager;
    private boolean myInitialized;

    private ResolvedPsiClassName(PsiManager manager, String name) {
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
    public Iterable<ResolvedMethod> getMethods(@NonNull String name) {
      ensureInitialized();
      if (myClass != null) {
        return super.getMethods(name);
      }
      return Collections.emptyList();
    }

    @Nullable
    @Override
    public ResolvedField getField(@NonNull String name) {
      ensureInitialized();
      if (myClass != null) {
        return super.getField(name);
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

    @Override
    public boolean matches(@NonNull String name) {
      return name.equals(getName());
    }

    @Nullable
    @Override
    public ResolvedClass getSuperClass() {
      if (myClass != null) {
        PsiClass superClass = myClass.getSuperClass();
        if (superClass != null) {
          return new ResolvedPsiClass(superClass);
        }
      }

      return null;
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
      if (myClass != null) {
        PsiClass cls = myClass;
        if (strict) {
          cls = cls.getSuperClass();
        }
        while (cls != null) {
          if (name.equals(cls.getQualifiedName())) {
            return true;
          }
          cls = cls.getSuperClass();
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
    public Iterable<ResolvedMethod> getMethods(@NonNull String name) {
      if (myClass != null) {
        PsiMethod[] methods = myClass.findMethodsByName(name, true);
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

    @Nullable
    @Override
    public ResolvedField getField(@NonNull String name) {
      if (myClass != null) {
        PsiField field = myClass.findFieldByName(name, true);
        if (field != null) {
          return new ResolvedPsiField(field);
        }
      }
      return null;
    }

    @Override
    public int getModifiers() {
      return computeModifiers(myClass);
    }

    @Override
    public String getSignature() {
      return getName();
    }
  }
}
