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
import com.android.tools.lint.client.api.IJavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Severity;
import com.google.common.base.Splitter;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import lombok.ast.*;

import java.io.File;
import java.util.Iterator;

public class LombokPsiParser implements IJavaParser {
  private final LintClient myClient;
  private AccessToken myLock;

  public LombokPsiParser(LintClient client) {
    myClient = client;
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
    if (context.compilationUnit != null) {
      myLock.finish();
      myLock = null;
      context.compilationUnit = null;
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
  public Node resolve(@NonNull JavaContext context, @NonNull Node node) {
    final PsiElement element = getPsiElement(node);
    if (element == null) {
      return null;
    }

    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      return resolve(element);
    }
    return application.runReadAction(new Computable<Node>() {
      @Nullable
      @Override
      public Node compute() {
        return resolve(element);
      }
    });
  }

  @Nullable
  @Override
  public TypeReference getType(@NonNull JavaContext context, @NonNull Node node) {
    final PsiElement element = getPsiElement(node);
    if (element == null) {
      return null;
    }

    Application application = ApplicationManager.getApplication();
    if (application.isReadAccessAllowed()) {
      return getType(element);
    }
    return application.runReadAction(new Computable<TypeReference>() {
      @Nullable
      @Override
      public TypeReference compute() {
        return getType(element);
      }
    });
  }

  @VisibleForTesting
  @Nullable
  static Node resolve(@NonNull PsiElement element) {
    PsiReference reference = element.getReference();
    if (reference != null) {
      PsiElement resolved = reference.resolve();
      if (resolved != null) {
        return LombokPsiConverter.toNode(resolved);
      }
    } else if (element instanceof PsiCall) {
      PsiMethod resolved = ((PsiCall)element).resolveMethod();
      if (resolved != null) {
        PsiClass containingClass = resolved.getContainingClass();
        if (containingClass != null) {
          ClassDeclaration declaration = new ClassDeclaration();
          declaration.astName(Identifier.of(containingClass.getName()));
          PackageDeclaration packageDeclaration = new PackageDeclaration();
          StrictListAccessor<Identifier, PackageDeclaration> identifiers = packageDeclaration.astParts();
          Iterator<String> iterator = Splitter.on('.').split(containingClass.getQualifiedName()).iterator();
          while (iterator.hasNext()) {
            String part = iterator.next();
            if (!iterator.hasNext()) {
              break;
            }
            Identifier identifier = Identifier.of(part);
            identifiers.addToEnd(identifier);
          }
          CompilationUnit unit = new CompilationUnit();
          unit.astPackageDeclaration(packageDeclaration);
          StrictListAccessor<TypeDeclaration, CompilationUnit> types = unit.astTypeDeclarations();
          types.addToEnd(declaration);
          MethodDeclaration method = new MethodDeclaration();
          method.astMethodName(Identifier.of(resolved.getName()));
          NormalTypeBody body = new NormalTypeBody();
          declaration.astBody(body);
          StrictListAccessor<TypeMember, NormalTypeBody> members = body.astMembers();
          members.addToEnd(method);

          StrictListAccessor<VariableDefinition, MethodDeclaration> parameters = method.astParameters();
          for (PsiParameter parameter : resolved.getParameterList().getParameters()) {
            VariableDefinition definition = LombokPsiConverter.toVariableDefinition(parameter);
            parameters.addToEnd(definition);
          }

          return method;
        }
      }
    }

    return null;
  }

  @Nullable
  private static TypeReference getType(@NonNull PsiElement element) {
    if (element instanceof PsiExpression) {
      PsiType type = ((PsiExpression)element).getType();
      if (type != null) {
        return LombokPsiConverter.toTypeReference(type);
      }
    } else if (element instanceof PsiVariable) {
      PsiType type = ((PsiVariable)element).getType();
      return LombokPsiConverter.toTypeReference(type);
    } else if (element instanceof PsiMethod) {
      PsiType type = ((PsiMethod)element).getReturnType();
      if (type != null) {
        return LombokPsiConverter.toTypeReference(type);
      }
    }

    return null;
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
}
