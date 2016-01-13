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
import com.google.common.base.Splitter;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import lombok.ast.*;

/**
 * Converter which takes a PSI hierarchy for a Java file, and creates a corresponding
 * Lombok AST tree.
 * <p>
 * NOTE: The tree may not be semantically equivalent yet; this converter only attempts
 * to make the AST correct as far as Lint cares (meaning that it only worries about
 * the details Lint cares about.)
 *
 * This is just a one way conversion. For Lombok AST they've made it two way (since
 * Lombok needs it) and they perform completeness tests by running the AST in both directions
 * and comparing the before and after results to make sure it retains the shape.
 * <p>
 * The following parts of the AST conversion are known not to be complete/correct:
 * <ul>
 *   <li> Generic type parameters. Type parameters, such as {@code class Foo<K,V>}
 *        and {@code Collections.<String>emptyList()} are just erased.
 *   <li> Varargs parameters are not preserved as varargs, but get turned into
 *        arrays (e.g. {@code foo(String... args)} becomes {@code foo(String[] args)}
 *   <li> Not all modifiers ({@code volatile}, {@code strictfp}, etc are preserved</li>
 *   <li> Javadoc comment nodes are not converted</li>
 *   <li> Many position are missing (for example for modifiers) or incomplete
 *        (for example for type references, where the overall type reference
 *        has positions but the individual parts do not)</li>
 * </ul>
 *
 * To handle positions specifically I'd like to add a new interface to the Lombok AST
 * where position information (as well as type resolution) can be performed
 * lazily, by stashing a reference to the PSI element and adding a location manager
 * (and a type manager) interface for the nodes to call back to a resolver
 * which looks up the corresponding PSI element and provides the necessary data.
 */
public class LombokPsiConverter {
  /**
   * If true, insert fully qualified types even when not
   * present in the source, to make lint not have to worry
   * about checking imports etc
   */
  private static final boolean EXPAND_TYPES = false;

  private static final Splitter DOT_SPLITTER = Splitter.on('.').omitEmptyStrings();

  private static final PositionFactory POSITION_FACTORY = new PositionFactory() {
    @Override
    @Nullable
    public Position getPosition(@NonNull final Node node) {
      Application application = ApplicationManager.getApplication();
      if (application.isReadAccessAllowed()) {
        return getPositionImmediate(node);
      }
      return application.runReadAction(new Computable<Position>() {
        @Override
        @Nullable
        public Position compute() {
          return getPositionImmediate(node);
        }
      });
    }

    @Nullable
    private Position getPositionImmediate(@NonNull Node node) {
      Object nativeNode = node.getNativeNode();
      if (nativeNode != null) {
        PsiElement element = (PsiElement)nativeNode;
        // Note: We have to use getTextRange() rather than element.getTextOffset() here
        // because the offsets aren't just the same as the individual getTextRange() values;
        // in particular, for method declarations etc getTextOffset will point to the
        // type signature, excluding modifiers, and for selects, it will point to the last
        // identifier rather than the whole qualified expression -- whereas getTextRange()
        // gives us what we want.
        TextRange textRange = element.getTextRange();
        int start = textRange.getStartOffset();
        int end = textRange.getEndOffset();

        // TODO: Compute lbrace/rbrace for classes lazily!
        //if (element instanceof PsiClass) {
        //  PsiElement lBrace = psiClass.getLBrace();
        //  PsiElement rBrace = psiClass.getRBrace();
        //  if (lBrace != null && rBrace != null) {
        //    int start = lBrace.getTextOffset();
        //    int end = rBrace.getTextOffset() + 1;
        //    body.setPosition(new Position(start, end));
        //  }
        //}

        PsiElement curr = element;
        while (curr != null) {
          if (curr instanceof PsiQualifiedReference) {
            PsiQualifiedReference p = (PsiQualifiedReference)curr;
            PsiElement qualifier = p.getQualifier();
            if (qualifier != null) {
              start = Math.min(start, qualifier.getTextOffset());
            }
            curr = qualifier;
          } else if (curr instanceof PsiQualifiedExpression) {
            PsiQualifiedExpression p = (PsiQualifiedExpression)curr;
            PsiElement qualifier = p.getQualifier();
            if (qualifier != null) {
              start = Math.min(start, qualifier.getTextOffset());
            }
            curr = qualifier;
          } else {
            break;
          }
        }

        return new Position(start, end);
      } else {
        // We don't have native node hooks on every single node,
        // but we can search up for a node, and attempt to use it instead
        // along with a search for the corresponding item
        Node n = node;
        while (n != null) {
          nativeNode = n.getNativeNode();
          if (nativeNode != null) {
            PsiElement element = (PsiElement)nativeNode;
            TextRange textRange = element.getTextRange();
            int start = textRange.getStartOffset();
            int end = textRange.getEndOffset();

            String substring;
            if (node instanceof Identifier) {
               substring = ((Identifier) node).astValue();
            } else if (node instanceof TypeReference) {
              substring = ((TypeReference) node).getTypeName();
            } else if (node instanceof KeywordModifier) {
              substring = ((KeywordModifier) node).astName();
            } else if (node instanceof TypeReferencePart) {
              substring = ((TypeReferencePart) node).getTypeName();
            } else {
              substring = node.toString();
            }
            if (substring != null) {
              int delta = element.getText().indexOf(substring);
              if (delta != -1) {
                start += delta;
                end = start + substring.length();
              }
            }

            return new Position(start, end);
          }
          n = n.getParent();
        }
      }

      return null;
    }
  };

  private LombokPsiConverter() {
  }

  /**
   * Convert the given {@link PsiJavaFile} to a Lombok AST {@link Node} tree
   *
   * @param javaFile the file to be converted
   * @return a corresponding Lombok AST tree
   */
  @Nullable
  public static CompilationUnit convert(@NonNull PsiJavaFile javaFile) {
    try {
      return toCompilationUnit(javaFile);
    } catch (ProcessCanceledException e) {
      // Ignore: common occurrence, e.g. we're running lint as part of an editor background
      // and while lint is running the user switches files: the inspections framework will
      // then cancel the process from within the PSI machinery (which asks the progress manager
      // periodically whether the operation is cancelled) and we find ourselves here
      return null;
    } catch (Exception e) {
      String path = javaFile.getName();
      VirtualFile virtualFile = javaFile.getVirtualFile();
      if (virtualFile != null) {
        path = virtualFile.getPath();
      }
      throw new RuntimeException("Could not convert file " + path, e);
    }
  }

  public static Node toNode(@NonNull PsiElement element) {
    if (element instanceof PsiClass) {
      return toTypeDeclaration((PsiClass)element);
    }
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)element;
      if (method.isConstructor()) {
        return toConstructorDeclaration(method);
      } else {
        return toMethodDeclaration(method);
      }
    }
    if (element instanceof PsiField) {
      return toField((PsiField) element);
    }
    if (element instanceof PsiVariable) {
      return toVariableDeclaration((PsiVariable)element);
    }
    if (element instanceof PsiIdentifier) {
      return toIdentifier((PsiIdentifier)element);
    }
    if (element instanceof PsiType) {
      return toTypeReference((PsiType)element);
    }
    if (element instanceof PsiJavaFile) {
      return toCompilationUnit(((PsiJavaFile)element));
    }
    throw new UnsupportedOperationException("Converting element of type " + element + " not yet supported");
  }

  private static void bind(@NonNull Node node, @Nullable PsiElement element) {
    if (element != null) {
      node.setNativeNode(element);
    }
    node.setPositionFactory(POSITION_FACTORY);
  }

  @NonNull
  private static CompilationUnit toCompilationUnit(@NonNull PsiJavaFile psiJavaFile) {
    CompilationUnit unit = new CompilationUnit();
    bind(unit, psiJavaFile);

    PsiPackageStatement packageStatement = psiJavaFile.getPackageStatement();
    if (packageStatement != null) {
      PackageDeclaration packageDeclaration = new PackageDeclaration();
      bind(packageDeclaration, packageStatement);
      PsiModifierList annotationList = packageStatement.getAnnotationList();
      if (annotationList != null) {
        StrictListAccessor<Annotation, PackageDeclaration> annotations = packageDeclaration.astAnnotations();
        for (PsiAnnotation annotation : annotationList.getAnnotations()) {
          annotations.addToEnd(toAnnotation(annotation));
        }
      }
      StrictListAccessor<Identifier, PackageDeclaration> identifiers = packageDeclaration.astParts();
      String pkg = packageStatement.getPackageReference().getQualifiedName();
      for (String part : DOT_SPLITTER.split(pkg)) {
        Identifier identifier = Identifier.of(part);
        bind(identifier, null);
        identifiers.addToEnd(identifier);
      }

      unit.astPackageDeclaration(packageDeclaration);
    }


    StrictListAccessor<ImportDeclaration, CompilationUnit> imports = unit.astImportDeclarations();
    PsiImportList importList = psiJavaFile.getImportList();
    if (importList != null) {
      for (PsiImportStatementBase importStatement : importList.getAllImportStatements()) {
        ImportDeclaration imp = new ImportDeclaration();
        StrictListAccessor<Identifier, ImportDeclaration> importParts = imp.astParts();
        PsiJavaCodeReferenceElement importReference = importStatement.getImportReference();
        if (importReference != null) {
          for (String part : DOT_SPLITTER.split(importReference.getQualifiedName())) {
            Identifier identifier = Identifier.of(part);
            bind(identifier, null);
            importParts.addToEnd(identifier);
          }
        }

        imp.astStarImport(importStatement.isOnDemand());
        if (importStatement instanceof PsiImportStaticStatement) {
          imp.astStaticImport(true);
        }
        bind(imp, importStatement);
        imports.addToEnd(imp);
      }
    }

    StrictListAccessor<TypeDeclaration, CompilationUnit> types = unit.astTypeDeclarations();
    for (PsiClass psiClass : psiJavaFile.getClasses()) {
      TypeDeclaration type = toTypeDeclaration(psiClass);
      types.addToEnd(type);
    }

    return unit;
  }

  @NonNull
  private static TypeDeclaration toTypeDeclaration(@NonNull PsiClass psiClass) {
    if (psiClass.isAnnotationType()) {
      AnnotationDeclaration declaration = new AnnotationDeclaration();
      bind(declaration, psiClass);
      PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
      if (nameIdentifier != null) {
        declaration.astName(toIdentifier(nameIdentifier));
      }
      PsiModifierList modifierList = psiClass.getModifierList();
      if (modifierList != null) {
        declaration.astModifiers(toModifiers(modifierList));
      }
      declaration.astBody(toTypeBody(psiClass));
      return declaration;
    } else if (psiClass.isEnum()) {
      EnumDeclaration declaration = new EnumDeclaration();
      bind(declaration, psiClass);
      PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
      if (nameIdentifier != null) {
        declaration.astName(toIdentifier(nameIdentifier));
      }
      declaration = declaration.astBody(toEnumTypeBody(psiClass));
      PsiModifierList modifierList = psiClass.getModifierList();
      if (modifierList != null) {
        declaration.astModifiers(toModifiers(modifierList));
      }
      PsiReferenceList implementsList = psiClass.getImplementsList();
      if (implementsList != null) {
        StrictListAccessor<TypeReference, EnumDeclaration> implementing = declaration.astImplementing();
        for (PsiJavaCodeReferenceElement ref : implementsList.getReferenceElements()) {
          TypeReference typeReference = toTypeReference(ref);
          implementing.addToEnd(typeReference);
        }
      }
      return declaration;
    } else if (psiClass.isInterface()) {
      InterfaceDeclaration declaration = new InterfaceDeclaration();
      bind(declaration, psiClass);
      PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
      if (nameIdentifier != null) {
        declaration.astName(toIdentifier(nameIdentifier));
      }
      PsiModifierList modifierList = psiClass.getModifierList();
      if (modifierList != null) {
        declaration.astModifiers(toModifiers(modifierList));
      }
      PsiReferenceList extendsList = psiClass.getExtendsList();
      if (extendsList != null) {
        StrictListAccessor<TypeReference, InterfaceDeclaration> extending = declaration.astExtending();
        for (PsiJavaCodeReferenceElement ref : extendsList.getReferenceElements()) {
          TypeReference typeReference = toTypeReference(ref);
          extending.addToEnd(typeReference);
        }
      }
      PsiTypeParameterList typeParameterList = psiClass.getTypeParameterList();
      if (typeParameterList != null) {
        StrictListAccessor<TypeVariable, InterfaceDeclaration> typeVariables = declaration.astTypeVariables();
        for (PsiTypeParameter parameter : typeParameterList.getTypeParameters()) {
          TypeVariable v = new TypeVariable();
          String name = parameter.getName();
          if (name != null) {
            v.astName(toIdentifier(name));
          }
          bind(v, null);
          typeVariables.addToEnd(v);
        }
      }

      declaration.astBody(toTypeBody(psiClass));
      return declaration;
    }  else {
      ClassDeclaration declaration = new ClassDeclaration();
      bind(declaration, psiClass);
      PsiModifierList modifierList = psiClass.getModifierList();
      if (modifierList != null) {
        declaration.astModifiers(toModifiers(modifierList));
      }
      PsiIdentifier nameIdentifier = psiClass.getNameIdentifier();
      if (nameIdentifier != null) {
        declaration.astName(toIdentifier(nameIdentifier));
      }
      PsiTypeParameterList typeParameterList = psiClass.getTypeParameterList();
      if (typeParameterList != null) {
        StrictListAccessor<TypeVariable, ClassDeclaration> typeVariables = declaration.astTypeVariables();
        for (PsiTypeParameter parameter : typeParameterList.getTypeParameters()) {
          TypeVariable v = new TypeVariable();
          String name = parameter.getName();
          if (name != null) {
            v.astName(toIdentifier(name));
          }
          bind(v, null);
          typeVariables.addToEnd(v);
        }
      }

      PsiReferenceList implementsList = psiClass.getImplementsList();
      if (implementsList != null) {
        StrictListAccessor<TypeReference, ClassDeclaration> implementing = declaration.astImplementing();
        for (PsiJavaCodeReferenceElement ref : implementsList.getReferenceElements()) {
          TypeReference typeReference = toTypeReference(ref);
          implementing.addToEnd(typeReference);
        }
      }

      PsiReferenceList extendsList = psiClass.getExtendsList();
      if (extendsList != null) {
        PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
        if (referenceElements.length > 0) {
          TypeReference typeReference = toTypeReference(referenceElements[0]);
          declaration.astExtending(typeReference);
        }
      }

      declaration.astBody(toTypeBody(psiClass));
      return declaration;
    }
  }

  @NonNull
  private static NormalTypeBody toTypeBody(@NonNull PsiClass psiClass) {
    NormalTypeBody body = new NormalTypeBody();
    bind(body, psiClass);
    StrictListAccessor<TypeMember, NormalTypeBody> members = body.astMembers();

    for (PsiClassInitializer initializer : psiClass.getInitializers()) {
      PsiCodeBlock codeBlock = initializer.getBody();
      StaticInitializer s = new StaticInitializer();
      bind(s, codeBlock);
      s.astBody(toBlock(codeBlock));
      members.addToEnd(s);
    }
    for (PsiField field : psiClass.getFields()) {
      members.addToEnd(toField(field));
    }
    for (PsiMethod method : psiClass.getMethods()) {
      if (method.isConstructor()) {
        members.addToEnd(toConstructorDeclaration(method));
      } else {
        members.addToEnd(toMethodDeclaration(method));
      }
    }
    for (PsiClass innerClass : psiClass.getInnerClasses()) {
      TypeDeclaration typeDeclaration = toTypeDeclaration(innerClass);
      if (typeDeclaration instanceof TypeMember) {
        members.addToEnd((TypeMember) typeDeclaration);
      }
    }

    PsiElement lBrace = psiClass.getLBrace();
    PsiElement rBrace = psiClass.getRBrace();
    if (lBrace != null && rBrace != null) {
      int start = lBrace.getTextOffset();
      int end = rBrace.getTextOffset() + 1;
      body.setPosition(new Position(start, end));
    }
    return body;
  }

  private static EnumConstant toEnumConstant(@NonNull PsiEnumConstant enumConstant) {
    EnumConstant constant = new EnumConstant();
    bind(constant, enumConstant);

    constant.astName(toIdentifier(enumConstant.getNameIdentifier()));
    PsiExpressionList argumentList = enumConstant.getArgumentList();
    if (argumentList != null) {
      StrictListAccessor<Expression, EnumConstant> arguments = constant.astArguments();
      for (PsiExpression argument : argumentList.getExpressions()) {
        arguments.addToEnd(toExpression(argument));
      }
    }
    StrictListAccessor<Annotation, EnumConstant> annotations = constant.astAnnotations();
    PsiModifierList modifierList = enumConstant.getModifierList();
    if (modifierList != null) {
      for (PsiAnnotation annotation : modifierList.getAnnotations()) {
        annotations.addToEnd(toAnnotation(annotation));
      }
    }
    PsiEnumConstantInitializer initializer = enumConstant.getInitializingClass();
    if (initializer != null) {
      NormalTypeBody body = toTypeBody(initializer);
      constant.astBody(body);
    }

    return constant;
  }

  @NonNull
  private static EnumTypeBody toEnumTypeBody(@NonNull PsiClass psiClass) {
    EnumTypeBody body = new EnumTypeBody();
    bind(body, null);
    StrictListAccessor<TypeMember, EnumTypeBody> members = body.astMembers();

    for (PsiClassInitializer initializer : psiClass.getInitializers()) {
      PsiCodeBlock codeBlock = initializer.getBody();
      StaticInitializer s = new StaticInitializer();
      bind(s, codeBlock);
      s.astBody(toBlock(codeBlock));
      members.addToEnd(s);
    }
    for (PsiField field : psiClass.getFields()) {
      if (field instanceof PsiEnumConstant) {
        PsiEnumConstant pec = (PsiEnumConstant)field;
        EnumConstant enumConstant = toEnumConstant(pec);
        body.astConstants().addToEnd(enumConstant);
      }
    }
    for (PsiMethod method : psiClass.getMethods()) {
      if (method.isConstructor()) {
        members.addToEnd(toConstructorDeclaration(method));
      } else {
        members.addToEnd(toMethodDeclaration(method));
      }
    }
    for (PsiClass innerClass : psiClass.getInnerClasses()) {
      TypeDeclaration typeDeclaration = toTypeDeclaration(innerClass);
      if (typeDeclaration instanceof TypeMember) {
        members.addToEnd((TypeMember) typeDeclaration);
      }
    }

    return body;
  }

  @Nullable
  private static Modifiers toModifiers(@NonNull PsiModifierList list) {
    Modifiers modifiers = new Modifiers();
    bind(modifiers, list);

    StrictListAccessor<Annotation, Modifiers> annotations = modifiers.astAnnotations();
    for (PsiAnnotation annotation : list.getAnnotations()) {
      annotations.addToEnd(toAnnotation(annotation));
    }

    StrictListAccessor<KeywordModifier, Modifiers> keywords = modifiers.astKeywords();
    if (list.hasExplicitModifier(PsiModifier.PUBLIC)) {
      KeywordModifier keyword = KeywordModifier.PUBLIC();
      bind(keyword, null);
      keywords.addToEnd(keyword);
    } else if (list.hasExplicitModifier(PsiModifier.PROTECTED)) {
      KeywordModifier keyword = KeywordModifier.PROTECTED();
      bind(keyword, null);
      keywords.addToEnd(keyword);
    } else if (list.hasExplicitModifier(PsiModifier.PRIVATE)) {
      KeywordModifier keyword = KeywordModifier.PRIVATE();
      bind(keyword, null);
      keywords.addToEnd(keyword);
    }
    if (list.hasExplicitModifier(PsiModifier.STATIC)) {
      KeywordModifier keyword = KeywordModifier.STATIC();
      bind(keyword, null);
      keywords.addToEnd(keyword);
    }
    if (list.hasExplicitModifier(PsiModifier.ABSTRACT)) {
      KeywordModifier keyword = new KeywordModifier().astName("abstract");
      bind(keyword, null);
      keywords.addToEnd(keyword);
    }
    if (list.hasExplicitModifier(PsiModifier.FINAL)) {
      KeywordModifier keyword = KeywordModifier.FINAL();
      bind(keyword, null);
      keywords.addToEnd(keyword);
    }
    // TODO: Finish the rest. PsiFormatUtils#formatModifiers has some sample code
    //KeywordModifier.fromReflectModifiers(0);

    return modifiers;
  }

  @Nullable
  private static AnnotationValue toAnnotationValue(@NonNull PsiAnnotationMemberValue value) {
    if (value instanceof PsiLiteral) {
      PsiLiteral literal = (PsiLiteral)value;
      Object v = literal.getValue();
      if (v instanceof String) {
        StringLiteral string = new StringLiteral();
        bind(string, value);
        string.astValue((String)v);
        return string;
      } else if (v instanceof Integer) {
        IntegralLiteral number = new IntegralLiteral();
        bind(number, value);
        number.astIntValue((Integer)v);
        return number;
      } else if (v instanceof Long) {
        IntegralLiteral number = new IntegralLiteral();
        number.astLongValue((Long)v);
        bind(number, value);
        return number;
      } else if (v instanceof Float) {
        FloatingPointLiteral number = new FloatingPointLiteral();
        bind(number, value);
        number.astFloatValue((Float)v);
        return number;
      } else if (v instanceof Double) {
        FloatingPointLiteral number = new FloatingPointLiteral();
        bind(number, value);
        number.astDoubleValue((Double)v);
        return number;
      } else if (v instanceof Character) {
        CharLiteral charLiteral = new CharLiteral();
        bind(charLiteral, value);
        charLiteral.astValue((Character)v);
        return charLiteral;
      }
    } else if (value instanceof PsiArrayInitializerMemberValue) {
      PsiArrayInitializerMemberValue mv = (PsiArrayInitializerMemberValue)value;
      ArrayInitializer initializer = new ArrayInitializer();
      bind(initializer, value);
      StrictListAccessor<Expression, ArrayInitializer> expressions = initializer.astExpressions();
      for (PsiAnnotationMemberValue mmv : mv.getInitializers()) {
        AnnotationValue annotationValue = toAnnotationValue(mmv);
        if (annotationValue instanceof Expression) {
          expressions.addToEnd((Expression) annotationValue);
        }
      }
      return initializer;
    } else if (value instanceof PsiExpression) {
      return toExpression((PsiExpression) value);
    } else if (value instanceof PsiAnnotation) {
      return toAnnotation((PsiAnnotation) value);
    }

    return null;
  }

  @NonNull
  private static Annotation toAnnotation(@NonNull PsiAnnotation annotation) {
    Annotation a = new Annotation();
    bind(a, annotation);
    TypeReference typeReference = null;
    if (EXPAND_TYPES) {
      typeReference = toTypeReference(annotation.getQualifiedName(), annotation);
    } else {
      PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null) {
        typeReference = toTypeReference(referenceElement);
      }
    }
    if (typeReference != null) {
      a.astAnnotationTypeReference(typeReference);
    }
    StrictListAccessor<AnnotationElement, Annotation> elements = a.astElements();
    PsiNameValuePair[] attributes = annotation.getParameterList().getAttributes();
    for (PsiNameValuePair pair : attributes) {
      PsiAnnotationMemberValue value = pair.getValue();
      assert value != null : pair.getName();
      AnnotationValue v = toAnnotationValue(value);
      if (v != null) {
        AnnotationElement element = new AnnotationElement();
        bind(element, pair);
        PsiIdentifier nameIdentifier = pair.getNameIdentifier();
        if (nameIdentifier != null) {
          element.astName(toIdentifier(nameIdentifier));
        }
        element.astValue(v);
        elements.addToEnd(element);
      }
    }
    return a;
  }

  @NonNull
  public static TypeReference toTypeReference(@NonNull PsiType type) {
    String fqcn = type.getCanonicalText();
    if (fqcn.startsWith("java.lang.")) { //$NON-NLS-1$
      fqcn = fqcn.substring(10);
    }
    return toTypeReference(fqcn);
  }

  @NonNull
  private static TypeReference toTypeReference(@NonNull PsiJavaCodeReferenceElement reference) {
    //noinspection ConstantConditions
    return toTypeReference(EXPAND_TYPES ? reference.getQualifiedName() : reference.getText(), reference);
  }

  @NonNull
  private static TypeReference toTypeReference(@NonNull PsiTypeElement type) {
    //noinspection ConstantConditions
    return toTypeReference(EXPAND_TYPES ? type.getType().getCanonicalText() : type.getText(), type);
  }

  @NonNull
  private static TypeReference toTypeReference(@NonNull String fqcn, @Nullable PsiElement element) {
    TypeReference reference = toTypeReference(fqcn);
    bind(reference, element);
    return reference;
  }

  /**
   * Parses the given type description string, which can include array and type parameters, and returns
   * a corresponding Lombok AST {@link TypeReference}
   *
   * @param type the type string to parse
   * @return a corresponding {@link TypeReference}
   */
  @NonNull
  private static TypeReference toTypeReference(@NonNull String type) {
    // Type can be something like Map<Map<String[], List<Integer[]>>,List<String[]>>[]
    //
    TypeReference reference = new TypeReference();
    bind(reference, null);

    StrictListAccessor<TypeReferencePart, TypeReference> parts = reference.astParts();
    TypeReferencePart part = null;
    int n = type.length();
    int index = 0;
    int segmentStart = 0;
    char c = 0;
    while (index < n) {
      c = type.charAt(index);
      if (c == '<' || c == '[') {
        break;
      } else if (c == '.') {
        // TODO: Handle varargs ...
        part = new TypeReferencePart();
        bind(part, null);
        part.astIdentifier(toIdentifier(type.substring(segmentStart, index)));
        parts.addToEnd(part);
        segmentStart = index + 1;
      }
      index++;
    }
    if (segmentStart < index) {
      part = new TypeReferencePart();
      bind(part, null);
      // In the common case, this is a type like "int" with nothing else; don't bother
      // creating a substring of itself
      String segment = (segmentStart == 0 && index == n) ? type : type.substring(segmentStart, index);
      //if (n - index == 1 && type.charAt(index) == '?') {
      //  reference.astWildcard(WildcardKind.UNBOUND);
      //}
      part.astIdentifier(toIdentifier(segment));
      parts.addToEnd(part);
    }
    if (index != n && part != null) {
      if (c == '<') {
        int end = type.lastIndexOf('>');
        if (end != -1) {
          StrictListAccessor<TypeReference, TypeReferencePart> typeArguments = part.astTypeArguments();
          int typeArgStart = index + 1;
          int balance = 0;
          for (int i = typeArgStart; i < end; i++) {
            c = type.charAt(i);
            if (c == '<') {
              balance++;
            } else if (c == '>') {
              balance--;
            } else if (c == ',' && balance == 0) {
              // Trim whitespace
              int typeArgEnd = i;
              for (int j = i - 1; j >= typeArgStart; j--) {
                if (Character.isWhitespace(type.charAt(j))) {
                  typeArgEnd--;
                } else {
                  break;
                }
              }
              typeArguments.addToEnd(toTypeReference(type.substring(typeArgStart, typeArgEnd)));
              typeArgStart = i + 1;
              while (typeArgStart < end) {
                if (!Character.isWhitespace(type.charAt(typeArgStart))) {
                  break;
                }
                typeArgStart++;
              }
            }
          }
          if (typeArgStart < end) {
            if (end == typeArgStart + 1 && type.charAt(typeArgStart) == '?') {
              TypeReference r = new TypeReference();
              bind(r, null);
              r.astWildcard(WildcardKind.UNBOUND);
              typeArguments.addToEnd(r);
            } else {
              String substring = type.substring(typeArgStart, end);
              typeArguments.addToEnd(toTypeReference(substring));
            }
          }
        }
        index = end;
      }
      if (index != -1) {
        int arrayDimensions = 0;
        for (int i = index; i < n; i++) {
          if (type.charAt(i) == '[') {
            arrayDimensions++;
          }
        }
        if (arrayDimensions > 0) {
          reference.astArrayDimensions(arrayDimensions);
        }
      }
    }

    return reference;
  }

  @Nullable
  private static Expression toExpression(@NonNull PsiExpression expression) {
    if (expression instanceof PsiLiteralExpression) {
      // Literal does not implement Expression, but StringLiteral, BooleanLiteral, IntegralLiteral etc all do
      return (Expression) toLiteral((PsiLiteralExpression)expression);
    } else if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression p = (PsiBinaryExpression)expression;
      BinaryExpression binary = new BinaryExpression();
      bind(binary, expression);
      PsiExpression rExpression = p.getROperand();
      if (rExpression == null) {
        return null;
      }
      Expression left = toExpression(p.getLOperand());
      Expression right = toExpression(rExpression);
      if (left == null || right == null) {
        // Errors in source code
        return null;
      }
      binary.astRight(right);
      binary.astLeft(left);

      IElementType operation = p.getOperationTokenType();
      BinaryOperator operator = convertOperation(operation);

      if (operator != null) {
        binary.astOperator(operator);
      } else {
        assert false : operation;
      }

      return binary;
    } else if (expression instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression p = (PsiAssignmentExpression)expression;
      BinaryExpression binary = new BinaryExpression();
      bind(binary, expression);
      BinaryOperator operator = BinaryOperator.ASSIGN;
      IElementType operation = p.getOperationTokenType();
      if (operation == JavaTokenType.PLUSEQ) {
        operator = BinaryOperator.PLUS_ASSIGN;
      }
      else if (operation == JavaTokenType.MINUSEQ) {
        operator = BinaryOperator.MINUS_ASSIGN;
      }
      else if (operation == JavaTokenType.ASTERISKEQ) {
        operator = BinaryOperator.MULTIPLY_ASSIGN;
      }
      else if (operation == JavaTokenType.DIVEQ) {
        operator = BinaryOperator.DIVIDE_ASSIGN;
      }
      else if (operation == JavaTokenType.PERCEQ) {
        operator = BinaryOperator.REMAINDER_ASSIGN;
      }
      else if (operation == JavaTokenType.ANDEQ) {
        operator = BinaryOperator.AND_ASSIGN;
      }
      else if (operation == JavaTokenType.XOREQ) {
        operator = BinaryOperator.XOR_ASSIGN;
      }
      else if (operation == JavaTokenType.OREQ) {
        operator = BinaryOperator.OR_ASSIGN;
      }
      else if (operation == JavaTokenType.LTLTEQ) {
        operator = BinaryOperator.SHIFT_LEFT_ASSIGN;
      }
      else if (operation == JavaTokenType.GTGTEQ) {
        operator = BinaryOperator.SHIFT_RIGHT_ASSIGN;
      }
      else if (operation == JavaTokenType.GTGTGTEQ) {
        operator = BinaryOperator.BITWISE_SHIFT_RIGHT_ASSIGN;
      }
      binary.astOperator(operator);
      PsiExpression rExpression = p.getRExpression();
      if (rExpression == null) {
        return null;
      }
      Expression left = toExpression(p.getLExpression());
      Expression right = toExpression(rExpression);
      if (left == null || right == null) {
        // Error in source code
        return null;
      }
      binary.astLeft(left);
      binary.astRight(right);
      return binary;
    } else if (expression instanceof PsiQualifiedExpression) {
      PsiQualifiedExpression p = (PsiQualifiedExpression)expression;
      PsiJavaCodeReferenceElement qualifier = p.getQualifier();
      if (qualifier != null) {
        Select operand = toSelect(qualifier);
        Select select = new Select();
        bind(select, expression);
        select.astOperand(operand);
        PsiReference reference = p.getReference();
        if (reference != null) {
          PsiElement referenceElement = reference.getElement();
          Identifier identifier = toIdentifier(referenceElement.getText());
          bind(identifier, referenceElement);
          select.astIdentifier(identifier);
        }
        return select;
      }
      PsiReference reference = p.getReference();
      if (reference != null) {
        return toVariableReference(reference);
      }
      if (p instanceof PsiSuperExpression) {
        Super superExpression = new Super();
        bind(superExpression, p);
        return superExpression;
      }
      return toVariableReference(p.getText(), p);
    } else if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpression = (PsiReferenceExpression)expression;
      PsiElement qualifier = refExpression.getQualifier();
      if (qualifier == null) {
        assert refExpression.getReferenceName() != null;
        PsiReference reference = refExpression.getReference();
        if (reference != null) {
          return toVariableReference(reference);
        }
        return null;
      }
      return toSelect(refExpression);
    } else if (expression instanceof PsiCallExpression) {
      return toMethodInvocation(expression);
    } else if (expression instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression p = (PsiArrayAccessExpression)expression;
      ArrayAccess arrayAccess = new ArrayAccess();
      bind(arrayAccess, p);

      PsiExpression indexExpression = p.getIndexExpression();
      if (indexExpression != null) {
        Expression e = toExpression(indexExpression);
        if (e != null) {
          arrayAccess.astIndexExpression(e);
        }
      }
      Expression e = toExpression(p.getArrayExpression());
      if (e == null) {
        return null;
      }
      arrayAccess.astOperand(e);
      bind(arrayAccess, expression);
      return arrayAccess;
    } else if (expression instanceof PsiArrayInitializerExpression) {
      return toArrayInitializer(expression);
    } else if (expression instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression p = (PsiInstanceOfExpression)expression;
      InstanceOf instanceOf = new InstanceOf();
      bind(instanceOf, expression);
      PsiTypeElement checkType = p.getCheckType();
      if (checkType != null) {
        instanceOf.astTypeReference(toTypeReference(checkType));
      }
      Expression e = toExpression(p.getOperand());
      if (e == null) {
        return null;
      }
      instanceOf.astObjectReference(e);
      return instanceOf;
    } else if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression p = (PsiConditionalExpression)expression;
      InlineIfExpression inlineIf = new InlineIfExpression();
      bind(inlineIf, expression);

      Expression condition = toExpression(p.getCondition());
      PsiExpression thenExpression = p.getThenExpression();
      PsiExpression elseExpression = p.getElseExpression();
      if (condition == null || thenExpression == null || elseExpression == null) {
        return null;
      }
      Expression ifTrue = toExpression(thenExpression);
      Expression ifFalse = toExpression(elseExpression);
      if (ifTrue == null || ifFalse == null) {
        return null;
      }
      inlineIf.astCondition(condition);
      inlineIf.astIfTrue(ifTrue);
      inlineIf.astIfFalse(ifFalse);
      return inlineIf;
    } else if (expression instanceof PsiClassObjectAccessExpression) {
      PsiClassObjectAccessExpression p = (PsiClassObjectAccessExpression)expression;
      ClassLiteral literal = new ClassLiteral();
      bind(literal, p);
      literal.astTypeReference(toTypeReference(p.getOperand()));
      return literal;
    } else if (expression instanceof PsiParenthesizedExpression) {
      PsiParenthesizedExpression p = (PsiParenthesizedExpression)expression;
      PsiExpression e = p.getExpression();
      if (e != null) {
        return toExpression(e);
      }
      return null;
    } else if (expression instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression p = (PsiTypeCastExpression)expression;
      Cast cast = new Cast();
      bind(cast, expression);
      PsiTypeElement castType = p.getCastType();
      if (castType != null) {
        cast.astTypeReference(toTypeReference(castType));
      }
      PsiExpression operand = p.getOperand();
      if (operand != null) {
        Expression e = toExpression(operand);
        if (e == null) {
          return null;
        }
        cast.astOperand(e);
      }
      return cast;
    } else if (expression instanceof PsiPostfixExpression) {
      PsiPostfixExpression p = (PsiPostfixExpression)expression;
      UnaryExpression unary = new UnaryExpression();
      bind(unary, expression);
      IElementType operation = p.getOperationTokenType();
      UnaryOperator operator = null;
      if (operation == JavaTokenType.MINUSMINUS) {
        operator = UnaryOperator.POSTFIX_DECREMENT;
      } else if (operation == JavaTokenType.PLUSPLUS) {
         operator = UnaryOperator.POSTFIX_INCREMENT;
      }
      if (operator != null) {
        unary.astOperator(operator);
      } else {
        assert false : operation;
      }
      Expression operand = toExpression(p.getOperand());
      if (operand == null) {
        return null;
      }
      unary.astOperand(operand);
      return unary;
    } else if (expression instanceof PsiPrefixExpression) {
      PsiPrefixExpression p = (PsiPrefixExpression)expression;
      UnaryExpression unary = new UnaryExpression();
      bind(unary, expression);

      IElementType operation = p.getOperationTokenType();
      UnaryOperator operator = null;
      if (operation == JavaTokenType.MINUSMINUS) {
        operator = UnaryOperator.PREFIX_DECREMENT;
      } else if (operation == JavaTokenType.PLUSPLUS) {
        operator = UnaryOperator.PREFIX_INCREMENT;
      } else if (operation == JavaTokenType.MINUS) {
        operator = UnaryOperator.UNARY_MINUS;
      } else if (operation == JavaTokenType.PLUS) {
        operator = UnaryOperator.UNARY_PLUS;
      } else if (operation == JavaTokenType.EXCL) {
        operator = UnaryOperator.LOGICAL_NOT;
      } else if (operation == JavaTokenType.TILDE) {
        operator = UnaryOperator.BINARY_NOT;
      }
      if (operator != null) {
        unary.astOperator(operator);
      } else {
        assert false : operation;
      }
      PsiExpression operand = p.getOperand();
      if (operand != null) {
        Expression e = toExpression(operand);
        if (e == null) {
          return null;
        }
        unary.astOperand(e);
      }
      return unary;
    } else if (expression instanceof PsiPolyadicExpression) {
      // Example: A + B + C + D; IDEA parses this as a single
      // polyadic expression with operator type + of A, B, C and D.
      // Fold them into nested BinaryExpressions for Lombok.
      PsiPolyadicExpression p = (PsiPolyadicExpression)expression;
      IElementType operation = p.getOperationTokenType();
      BinaryOperator operator = convertOperation(operation);
      if (operator == null) {
        assert false : operation;
      }
      PsiExpression[] operands = p.getOperands();
      assert operands.length >= 1;
      Expression left = toExpression(operands[0]);
      if (left == null) {
        return null;
      }
      for (int i = 1, n = operands.length; i < n; i++) {
        Expression right = toExpression(operands[i]);
        if (right == null) {
          // Error in source code
          break;
        }
        BinaryExpression binary = new BinaryExpression();
        bind(binary, expression);
        binary.astOperator(operator);

        binary.astLeft(left);
        binary.astRight(right);

        left = binary;
      }

      return left;
    } else //noinspection IfStatementWithIdenticalBranches
      if (expression instanceof PsiLambdaExpression) {
      // Is this used in Java?
      // TODO: Implement. Not yet used by lint.
      return null;
    }
    return null;
  }

  @Nullable
  private static BinaryOperator convertOperation(IElementType operation) {
    BinaryOperator operator = null;
    if (operation == JavaTokenType.EQEQ) {
      operator = BinaryOperator.EQUALS;
    }
    else if (operation == JavaTokenType.EQ) {
      operator = BinaryOperator.ASSIGN;
    }
    else if (operation == JavaTokenType.OROR) {
      operator = BinaryOperator.LOGICAL_OR;
    }
    else if (operation == JavaTokenType.ANDAND) {
      operator = BinaryOperator.LOGICAL_AND;
    }
    else if (operation == JavaTokenType.NE) {
      operator = BinaryOperator.NOT_EQUALS;
    }
    else if (operation == JavaTokenType.GT) {
      operator = BinaryOperator.GREATER;
    }
    else if (operation == JavaTokenType.GE) {
      operator = BinaryOperator.GREATER_OR_EQUAL;
    }
    else if (operation == JavaTokenType.LT) {
      operator = BinaryOperator.LESS;
    }
    else if (operation == JavaTokenType.LE) {
      operator = BinaryOperator.LESS_OR_EQUAL;
    }
    else if (operation == JavaTokenType.OR) {
      operator = BinaryOperator.BITWISE_OR;
    }
    else if (operation == JavaTokenType.AND) {
      operator = BinaryOperator.BITWISE_AND;
    }
    else if (operation == JavaTokenType.XOR) {
      operator = BinaryOperator.BITWISE_XOR;
    }
    else if (operation == JavaTokenType.LTLT) {
      operator = BinaryOperator.SHIFT_LEFT;
    }
    else if (operation == JavaTokenType.GTGT) {
      operator = BinaryOperator.SHIFT_RIGHT;
    }
    else if (operation == JavaTokenType.GTGTGT) {
      operator = BinaryOperator.BITWISE_SHIFT_RIGHT;
    }
    else if (operation == JavaTokenType.PLUS) {
      operator = BinaryOperator.PLUS;
    }
    else if (operation == JavaTokenType.MINUS) {
      operator = BinaryOperator.MINUS;
    }
    else if (operation == JavaTokenType.ASTERISK) {
      operator = BinaryOperator.MULTIPLY;
    }
    else if (operation == JavaTokenType.DIV) {
      operator = BinaryOperator.DIVIDE;
    }
    else if (operation == JavaTokenType.PERC) {
      operator = BinaryOperator.REMAINDER;
    }
    return operator;
  }

  @NonNull
  private static ArrayInitializer toArrayInitializer(@NonNull PsiExpression expression) {
    PsiArrayInitializerExpression p = (PsiArrayInitializerExpression)expression;
    ArrayInitializer arrayInitializer = new ArrayInitializer();
    bind(arrayInitializer, expression);
    StrictListAccessor<Expression, ArrayInitializer> expressions = arrayInitializer.astExpressions();
    for (PsiExpression psiExpression : p.getInitializers()) {
      expressions.addToEnd(toExpression(psiExpression));
    }
    bind(arrayInitializer, expression);
    return arrayInitializer;
  }

  @Nullable
  private static Expression toMethodInvocation(@NonNull PsiExpression expression) {
    if (expression instanceof PsiNewExpression) {
      PsiNewExpression p = (PsiNewExpression)expression;

      // Array?
      PsiExpression[] arrayDimensions = p.getArrayDimensions();
      PsiArrayInitializerExpression arrayInitializer = p.getArrayInitializer();
      if (arrayDimensions.length > 0 || arrayInitializer != null) {
        ArrayCreation creation = new ArrayCreation();
        bind(creation, arrayInitializer);
        PsiType type = p.getType();
        if (type != null) {
          TypeReference typeReference = toTypeReference(type);
          bind(typeReference, p);

          // Workaround to avoid getting double array dimensions from this
          // and the ArrayCreation code below
          if (arrayDimensions.length != 0) {
            typeReference.astArrayDimensions(0);
          }

          creation.astComponentTypeReference(typeReference);
        }
        if (arrayInitializer != null) {
          creation.astInitializer(toArrayInitializer(arrayInitializer));
        }
        StrictListAccessor<ArrayDimension, ArrayCreation> dimensions = creation.astDimensions();
        for (PsiExpression dimension : arrayDimensions) {
          ArrayDimension d = new ArrayDimension();
          bind(d, dimension);
          d.astDimension(toExpression(dimension));
          dimensions.addToEnd(d);
        }

        return creation;
      }

      ConstructorInvocation invocation = new ConstructorInvocation();
      bind(invocation, p);
      PsiJavaCodeReferenceElement classOrAnonymousClassReference = p.getClassOrAnonymousClassReference();
      if (classOrAnonymousClassReference != null) {
        invocation.astTypeReference(toTypeReference(classOrAnonymousClassReference));
      }
      StrictListAccessor<Expression, ConstructorInvocation> arguments = invocation.astArguments();
      PsiExpressionList argumentList = p.getArgumentList();
      if (argumentList != null) {
        for (PsiExpression argument : argumentList.getExpressions()) {
          arguments.addToEnd(toExpression(argument));
        }
      }
      PsiAnonymousClass anonymousClass = p.getAnonymousClass();
      if (anonymousClass != null) {
        NormalTypeBody body = toTypeBody(anonymousClass);
        invocation.astAnonymousClassBody(body);
      }

      bind(invocation, expression);
      return invocation;
    } else if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression p = (PsiMethodCallExpression)expression;
      MethodInvocation invocation = new MethodInvocation();
      bind(invocation, expression);

      StrictListAccessor<TypeReference, MethodInvocation> types = invocation.astMethodTypeArguments();
      for (PsiTypeElement t : p.getTypeArgumentList().getTypeParameterElements()) {
        TypeReference typeReference = toTypeReference(t);
        types.addToEnd(typeReference);
      }

      StrictListAccessor <Expression, MethodInvocation> arguments = invocation.astArguments();
      PsiExpressionList argumentList = p.getArgumentList();
      for (PsiExpression argument : argumentList.getExpressions()) {
        arguments.addToEnd(toExpression(argument));
      }
      PsiReferenceExpression methodExpression = p.getMethodExpression();
      PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      if (qualifierExpression != null) {
        invocation.astOperand(toExpression(qualifierExpression));
      }
      String referenceName = methodExpression.getReferenceName();
      if (referenceName != null) {
        Identifier name = toIdentifier(referenceName);
        PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
        if (referenceNameElement != null) {
          bind(name, referenceNameElement);
        }
        invocation.astName(name);
      }

      return invocation;
    } else {
      // TODO: When is something a PsiCallExpression but not constructor or method? Perhaps for other languages?
      return null;
    }
  }

  @Nullable
  private static Statement toStatement(@NonNull PsiStatement statement) {
    if (statement instanceof PsiExpressionStatement) {
      PsiExpressionStatement p = (PsiExpressionStatement)statement;
      ExpressionStatement s = new ExpressionStatement();
      bind(s, statement);
      Expression expression = toExpression(p.getExpression());
      if (expression == null) {
        // Error node in source
        return null;
      }
      s.astExpression(expression);
      return s;
    } else if (statement instanceof PsiDeclarationStatement) {
      PsiDeclarationStatement pds = (PsiDeclarationStatement)statement;
      return toVariableDeclaration(pds);
    } else if (statement instanceof PsiBlockStatement) {
      PsiBlockStatement p = (PsiBlockStatement)statement;
      return toBlock(p.getCodeBlock());
    } else if (statement instanceof PsiIfStatement) {
      PsiIfStatement p = (PsiIfStatement)statement;
      If ifStatement = new If();
      bind(ifStatement, statement);
      PsiExpression condition = p.getCondition();
      if (condition != null) {
        Expression ifCondition = toExpression(condition);
        assert ifCondition != null;
        ifStatement.astCondition(ifCondition);
      }
      PsiStatement thenBranch = p.getThenBranch();
      if (thenBranch != null) {
        Statement thenStatement = toStatement(thenBranch);
        if (thenStatement == null) {
          // Error node in source
          return null;
        }
        ifStatement.astStatement(thenStatement);
      }
      PsiStatement elseBranch = p.getElseBranch();
      if (elseBranch != null) {
        Statement elseStatement = toStatement(elseBranch);
        if (elseStatement != null) {
          ifStatement.astElseStatement(elseStatement);
        }
      }
      return ifStatement;
    } else if (statement instanceof PsiReturnStatement) {
      PsiReturnStatement p = (PsiReturnStatement)statement;
      Return r = new Return();
      bind(r, statement);
      PsiExpression returnValue = p.getReturnValue();
      if (returnValue != null) {
        r.astValue(toExpression(returnValue));
      }
      return r;
    } else if (statement instanceof PsiLoopStatement) {
      if (statement instanceof PsiForStatement) {
        PsiForStatement p = (PsiForStatement)statement;
        For f = new For();
        bind(f, statement);
        PsiExpression condition = p.getCondition();
        if (condition != null) {
          f.astCondition(toExpression(condition));
        }
        PsiStatement update = p.getUpdate();
        if (update != null) {
          StrictListAccessor<Expression, For> updates = f.astUpdates();
          if (update instanceof PsiExpressionListStatement) {
            PsiExpressionListStatement pl = (PsiExpressionListStatement)update;
            for (PsiExpression e : pl.getExpressionList().getExpressions()) {
              updates.addToEnd(toExpression(e));
            }
          } else {
            assert update instanceof PsiExpressionStatement : update;
            PsiExpressionStatement ps = (PsiExpressionStatement)update;
            updates.addToEnd(toExpression(ps.getExpression()));
          }
        }
        PsiStatement initialization = p.getInitialization();
        if (initialization != null) {
          if (initialization instanceof PsiDeclarationStatement) {
            PsiDeclarationStatement pds = (PsiDeclarationStatement)initialization;
            f.astVariableDeclaration(toVariableDefinition(pds, pds.getDeclaredElements()));
          } else if (initialization instanceof PsiExpressionStatement) {
            PsiExpressionStatement expressionStatement = (PsiExpressionStatement)initialization;
            f.astExpressionInits().addToEnd(toExpression(expressionStatement.getExpression()));
          } else if (initialization instanceof PsiExpression) {
            PsiExpression expression = (PsiExpression)initialization;
            f.astExpressionInits().addToEnd(toExpression(expression));
          } else if (initialization instanceof PsiExpressionListStatement) {
            PsiExpressionList expressionList = ((PsiExpressionListStatement)initialization).getExpressionList();
            if (expressionList != null) {
              for (PsiExpression expression : expressionList.getExpressions()) {
                f.astExpressionInits().addToEnd(toExpression(expression));
              }
            }
          } else //noinspection StatementWithEmptyBody
            if (initialization instanceof PsiEmptyStatement) {
            // Do nothing; we don't need an explicit lombok.ast.EmptyStatement here
          } else {
            // Unexpected type of initializer
            assert false : initialization + " for code " + initialization.getText();
          }
        }
        PsiStatement body = p.getBody();
        if (body != null) {
          Statement s = toStatement(body);
          if (s != null) {
            f.astStatement(s);
          }
        }
        return f;
      } else if (statement instanceof PsiForeachStatement) {
        PsiForeachStatement p = (PsiForeachStatement)statement;
        ForEach f = new ForEach();
        bind(f, statement);
        PsiExpression iteratedValue = p.getIteratedValue();
        if (iteratedValue != null) {
          Expression e = toExpression(iteratedValue);
          if (e != null) {
            f.astIterable(e);
          }
        }
        f.astVariable(toVariableDefinition(p.getIterationParameter()));
        PsiStatement body = p.getBody();
        if (body != null) {
          Statement s = toStatement(body);
          if (s != null) {
            f.astStatement(s);
          }
        }
        return f;
      } else if (statement instanceof PsiDoWhileStatement) {
        PsiDoWhileStatement p = (PsiDoWhileStatement)statement;
        DoWhile w = new DoWhile();
        bind(w, statement);
        PsiExpression condition = p.getCondition();
        if (condition != null) {
          Expression e = toExpression(condition);
          if (e != null) {
            w.astCondition(e);
          }
        }
        PsiStatement body = p.getBody();
        if (body != null) {
          Statement s = toStatement(body);
          if (s != null) {
            w.astStatement(s);
          }
        }
        return w;
      } else if (statement instanceof PsiWhileStatement) {
        PsiWhileStatement p = (PsiWhileStatement)statement;
        While w = new While();
        bind(w, statement);
        PsiExpression condition = p.getCondition();
        if (condition != null) {
          Expression e = toExpression(condition);
          if (e != null) {
            w.astCondition(e);
          }
        }
        PsiStatement body = p.getBody();
        if (body != null) {
          Statement s = toStatement(body);
          if (s != null) {
            w.astStatement(s);
          }
        }
        return w;
      } else {
        assert false : statement; // Shouldn't happen; PSI currently only has the above 4
        return null;
      }
    } else if (statement instanceof PsiSwitchStatement) {
      PsiSwitchStatement p = (PsiSwitchStatement)statement;
      Switch s = new Switch();
      bind(s, statement);
      PsiExpression expression = p.getExpression();
      if (expression != null) {
        Expression e = toExpression(expression);
        if (e != null) {
          s.astCondition(e);
        }
      }
      PsiCodeBlock body = p.getBody();
      if (body != null) {
        s.astBody(toBlock(body));
      }
      return s;
    } else if (statement instanceof PsiBreakStatement) {
      PsiBreakStatement p = (PsiBreakStatement)statement;
      Break s = new Break();
      bind(s, p);
      PsiIdentifier labelIdentifier = p.getLabelIdentifier();
      if (labelIdentifier != null) {
        s.astLabel(toIdentifier(labelIdentifier));
      }
      return s;
    } else if (statement instanceof PsiSwitchLabelStatement) {
      PsiSwitchLabelStatement p = (PsiSwitchLabelStatement)statement;
      Case c = new Case();
      bind(c, statement);
      PsiExpression caseValue = p.getCaseValue();
      if (caseValue != null) {
        Expression e = toExpression(caseValue);
        if (e != null) {
          c.astCondition(e);
        }
      }
      return c;
    } else if (statement instanceof PsiLabeledStatement) {
      PsiLabeledStatement p = (PsiLabeledStatement)statement;
      LabelledStatement l = new LabelledStatement();
      bind(l, statement);
      l.astLabel(toIdentifier(p.getLabelIdentifier()));
      PsiStatement s = p.getStatement();
      if (s != null) {
        Statement st = toStatement(s);
        if (st != null) {
          l.astStatement(st);
        }
      }
      return l;
    } else if (statement instanceof PsiSynchronizedStatement) {
      PsiSynchronizedStatement p = (PsiSynchronizedStatement)statement;
      Synchronized s = new Synchronized();
      bind(s, statement);
      PsiExpression lockExpression = p.getLockExpression();
      if (lockExpression != null) {
        Expression e = toExpression(lockExpression);
        if (e != null) {
          s.astLock(e);
        }
      }
      PsiCodeBlock body = p.getBody();
      if (body != null) {
        s.astBody(toBlock(body));
      }
      return s;
    } else if (statement instanceof PsiContinueStatement) {
      PsiContinueStatement p = (PsiContinueStatement)statement;
      Continue c = new Continue();
      bind(c, statement);
      PsiIdentifier labelIdentifier = p.getLabelIdentifier();
      if (labelIdentifier != null) {
        c.astLabel(toIdentifier(labelIdentifier));
      }
      return c;
    } else {
      if (statement instanceof PsiTryStatement) {
        PsiTryStatement p = (PsiTryStatement)statement;
        Try t = new Try();
        bind(t, statement);

        StrictListAccessor<Catch, Try> catches = t.astCatches();
        for (PsiCatchSection catchSection : p.getCatchSections()) {
          Catch c = new Catch();
          bind(c, catchSection);
          PsiParameter parameter = catchSection.getParameter();
          if (parameter != null) {
            c.astExceptionDeclaration(toVariableDefinition(parameter));
          }
          PsiCodeBlock catchBlock = catchSection.getCatchBlock();
          if (catchBlock != null) {
            c.astBody(toBlock(catchBlock));
          }
          catches.addToEnd(c);
        }

        PsiCodeBlock tryBlock = p.getTryBlock();
        if (tryBlock != null) {
          t.astBody(toBlock(tryBlock));
        }

        PsiCodeBlock finallyBlock = p.getFinallyBlock();
        if (finallyBlock != null) {
          t.astFinally(toBlock(finallyBlock));
        }

        return t;
      }
      else if (statement instanceof PsiEmptyStatement) {
        EmptyStatement emptyStatement = new EmptyStatement();
        bind(emptyStatement, statement);
        return emptyStatement;
      }
      else if (statement instanceof PsiAssertStatement) {
        PsiAssertStatement p = (PsiAssertStatement)statement;
        Assert a = new Assert();
        bind(a, statement);
        PsiExpression assertCondition = p.getAssertCondition();
        if (assertCondition != null) {
          Expression assertion = toExpression(assertCondition);
          if (assertion != null) {
            a.astAssertion(assertion);
          }
        }
        PsiExpression assertDescription = p.getAssertDescription();
        if (assertDescription != null) {
          a.astMessage(toExpression(assertDescription));
        }
        return a;
      }
      else if (statement instanceof PsiThrowStatement) {
        PsiThrowStatement p = (PsiThrowStatement)statement;
        Throw t = new Throw();
        bind(t, statement);
        PsiExpression exception = p.getException();
        if (exception != null) {
          Expression throwable = toExpression(exception);
          if (throwable != null) {
            t.astThrowable(throwable);
          }
        }
        return t;
      }
      else //noinspection IfStatementWithIdenticalBranches
        if (statement instanceof PsiExpressionListStatement) {
        // This shouldn't happen; we should never call this method, since
        // PsiExpressionListStatement is only allowed as part of a for statement,
        // and we deliberately handle this above as part of the for label conversion
        // (since Lombok does not have a separate AST class for this)
        return null;
      } else if (statement instanceof PsiClassLevelDeclarationStatement) {
        //PsiClassLevelDeclarationStatement p = (PsiClassLevelDeclarationStatement)statement;
        // Not clear what this is used for...
        return null;
      }
      else {
        // Surprise!! What's this?
        // TODO: Implement. Not yet used by lint.
        throw new UnsupportedOperationException("Unknown statement type for " + statement);
      }
    }
  }

  @NonNull
  private static Block toBlock(@NonNull PsiCodeBlock block) {
    Block b = new Block();
    bind(b, block);

    StrictListAccessor<Statement, Block> statements = b.astContents();
    for (PsiStatement statement : block.getStatements()) {
      Statement s = toStatement(statement);
      // In theory all statements should be non null, but since I haven't mapped
      // ALL constructs to Lombok yet (since Lint doesn't need it), I'm returning null
      // here for stuff we don't care about yet
      if (s != null) {
        statements.addToEnd(s);
      }
    }

    return b;
  }

  @NonNull
  private static Identifier toIdentifier(@NonNull String identifierText) {
    assert identifierText.indexOf('.') == -1 : identifierText;
    Identifier identifier = Identifier.of(identifierText);
    bind(identifier, null);
    return identifier;
  }

  @NonNull
  private static Identifier toIdentifier(@NonNull PsiIdentifier element) {
    Identifier identifier = Identifier.of(element.getText());
    bind(identifier, element);
    return identifier;
  }

  @Nullable
  private static Literal toLiteral(@NonNull PsiLiteralExpression expression) {
    Literal literal = null;
    PsiType type = expression.getType();
    if (type == PsiType.NULL) {
      literal = new NullLiteral();
      bind(literal, expression);
    } else if (PsiType.INT.equals(type) || PsiType.LONG.equals(type) || PsiType.SHORT.equals(type)
               || PsiType.BYTE.equals(type)) {
      literal = new IntegralLiteral().rawValue(expression.getText());
      bind(literal, expression);
    } else if (PsiType.BOOLEAN.equals(type)) {
      literal = new BooleanLiteral().rawValue(expression.getText());
      bind(literal, expression);
    } else if (PsiType.DOUBLE.equals(type) || PsiType.FLOAT.equals(type)) {
      literal = new FloatingPointLiteral().rawValue(expression.getText());
      bind(literal, expression);
    } else if (PsiType.CHAR.equals(type)) {
      literal = new CharLiteral().rawValue(expression.getText());
      bind(literal, expression);
    } else if (type != null && type.getCanonicalText().equals(CommonClassNames.JAVA_LANG_STRING)) {
      StringLiteral stringLiteral = new StringLiteral();
      literal = stringLiteral.rawValue(expression.getText());
      bind(literal, expression);
      if (stringLiteral.astValue() == null) {
        Object value = expression.getValue();
        String string = value instanceof String ? (String)value : expression.getText();
        stringLiteral.astValue(string);
      }
    }

    return literal;
  }

  @NonNull
  private static VariableReference toVariableReference(@NonNull PsiIdentifier identifier) {
    VariableReference variableReference = new VariableReference();
    bind(variableReference, identifier);
    variableReference.astIdentifier(toIdentifier(identifier));
    return variableReference;
  }

  @NonNull
  private static VariableReference toVariableReference(@NonNull PsiReference reference) {
    PsiElement element = reference.getElement();
    VariableReference variableReference = new VariableReference();
    bind(variableReference, element);
    @SuppressWarnings("ConstantConditions")
    Identifier identifier = toIdentifier(EXPAND_TYPES ? reference.getCanonicalText() : reference.getElement().getText());
    bind(identifier, element);
    variableReference.astIdentifier(identifier);
    return variableReference;
  }

  @NonNull
  private static VariableReference toVariableReference(@NonNull String identifier, @Nullable PsiElement element) {
    VariableReference variableReference = new VariableReference();
    bind(variableReference, element);
    Identifier name = toIdentifier(identifier);
    variableReference.astIdentifier(name);
    return variableReference;
  }

  @NonNull
  private static VariableDefinitionEntry toVariableDefinitionEntry(@NonNull PsiVariable variable) {
    VariableDefinitionEntry entry = new VariableDefinitionEntry();
    bind(entry, variable);
    PsiIdentifier nameIdentifier = variable.getNameIdentifier();
    if (nameIdentifier != null) {
      entry.astName(toIdentifier(nameIdentifier));
    }
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      entry.astInitializer(toExpression(initializer));
    }
    return entry;
  }

  @NonNull
  static VariableDefinition toVariableDefinition(@NonNull PsiVariable variable) {
    VariableDefinition definition = new VariableDefinition();
    bind(definition, variable);
    PsiModifierList modifierList = variable.getModifierList();
    if (modifierList != null) {
      definition.astModifiers(toModifiers(modifierList));
    }

    PsiTypeElement type = variable.getTypeElement();
    if (type != null) {
      definition.astTypeReference(toTypeReference(type));
      // TODO: Handle
      //if (variable.getType().getCanonicalText().endsWith(VARARG_SUFFIX)) {
      //  definition.astVarargs(true);
      //}
    }

    int arrayDimensions = variable.getType().getArrayDimensions();
    if (arrayDimensions != 0) {
      TypeReference typeReference = definition.astTypeReference();
      if (typeReference != null) {
        typeReference.astArrayDimensions(arrayDimensions);
      }
    }

    VariableDefinitionEntry entry = toVariableDefinitionEntry(variable);
    definition.astVariables().addToEnd(entry);

    return definition;
  }

  private static void setOperand(@NonNull Select select, @NonNull PsiElement qualifier) {
    if (qualifier instanceof PsiIdentifier) {
      // A little weird but this is how Lombok models it:
      select.astOperand(toVariableReference((PsiIdentifier)qualifier));
    } else if (qualifier instanceof PsiSuperExpression) {
      Super operand = new Super();
      bind(operand, qualifier);
      select.astOperand(operand);
    } else if (qualifier instanceof PsiThisExpression) {
      This operand = new This();
      bind(operand, qualifier);
      select.astOperand(operand);
    } else if (qualifier instanceof PsiReferenceExpression) {
      Expression operand = toSelect((PsiReferenceExpression)qualifier);
      if (operand != null) {
        select.astOperand(operand);
      }
    } else if (qualifier instanceof PsiCallExpression) {
      Expression operand = toMethodInvocation((PsiCallExpression)qualifier);
      if (operand != null) {
        select.astOperand(operand);
      }
    } else if (qualifier instanceof PsiExpression) {
      Expression operand = toExpression((PsiExpression)qualifier);
      if (operand != null) {
        select.astOperand(operand);
      }
    } else {
      throw new UnsupportedOperationException("Unknown select qualifier type: " + qualifier);
    }
  }

  @NonNull
  private static Select toSelect(@NonNull PsiQualifiedReferenceElement reference) {
    Select select = new Select();
    bind(select, reference);

    Identifier nameIdentifier = Identifier.of(reference.getReferenceName());
    bind(nameIdentifier, null);
    select.astIdentifier(nameIdentifier);

    PsiElement qualifier = reference.getQualifier();
    if (qualifier != null) {
      setOperand(select, qualifier);
    }

    return select;
  }

  @Nullable
  private static Expression toSelect(@NonNull PsiReferenceExpression reference) {
    PsiExpression qualifier = reference.getQualifierExpression();
    String referenceName = reference.getReferenceName();
    if (qualifier != null && referenceName != null) {
      Select select = new Select();
      bind(select, reference);

      Identifier nameIdentifier = Identifier.of(referenceName);
      bind(nameIdentifier, null);
      select.astIdentifier(nameIdentifier);

      setOperand(select, qualifier);
      return select;
    } else if (qualifier != null) {
      return toExpression(qualifier);
    } else if (referenceName != null) {
      return toVariableReference(referenceName, reference);
    }

    return null;
  }

  @NonNull
  private static VariableDeclaration toField(@NonNull PsiVariable field) {
    // Lombok does not distinguish between fields and variables
    return toVariableDeclaration(field);
  }

  @NonNull
  private static Statement toVariableDeclaration(@NonNull PsiDeclarationStatement statement) {
    PsiElement[] declaredElements = statement.getDeclaredElements();
    if (declaredElements.length == 1 && declaredElements[0].getNode().getElementType() == JavaElementType.CLASS) {
      // Class declaration inside method
      TypeDeclaration typeDeclaration = toTypeDeclaration((PsiClass)declaredElements[0]);
      if (typeDeclaration instanceof Statement) {
        return (Statement)typeDeclaration;
      }
      throw new UnsupportedOperationException("Non-statement type declaration: " + statement);
    }

    VariableDeclaration declaration = new VariableDeclaration();
    bind(declaration, statement);
    VariableDefinition definition = toVariableDefinition(statement, declaredElements);
    declaration.astDefinition(definition);

    return declaration;
  }

  @NonNull
  private static VariableDefinition toVariableDefinition(@NonNull PsiDeclarationStatement statement,
                                                         @NonNull PsiElement[] declaredElements) {
    VariableDefinition definition = new VariableDefinition();
    bind(definition, statement);

    Modifiers modifiers = null;
    for (PsiElement element : declaredElements) {
      if (element instanceof PsiVariable) {
        PsiVariable variable = (PsiVariable)element;
        if (modifiers == null) {
          PsiModifierList modifierList = variable.getModifierList();
          if (modifierList != null) {
            modifiers = toModifiers(modifierList);
          }
        }
        PsiTypeElement typeElement = variable.getTypeElement();
        if (typeElement != null) {
          definition.astTypeReference(toTypeReference(typeElement));
        }
        VariableDefinitionEntry entry = toVariableDefinitionEntry(variable);
        definition.astVariables().addToEnd(entry);
      } else {
        throw new UnsupportedOperationException("Not yet supporting variable declarations for " + element);
      }
    }

    if (modifiers != null) {
      definition.astModifiers(modifiers);
    }

    return definition;
  }

  @NonNull
  private static VariableDeclaration toVariableDeclaration(@NonNull PsiVariable variable) {
    VariableDeclaration declaration = new VariableDeclaration();
    bind(declaration, variable);

    VariableDefinition definition = toVariableDefinition(variable);
    bind(definition, variable);
    declaration.astDefinition(definition);

    return declaration;
  }

  @NonNull
  private static ConstructorDeclaration toConstructorDeclaration(@NonNull PsiMethod method) {
    assert method.isConstructor();
    ConstructorDeclaration m = new ConstructorDeclaration();
    bind(m, method);
    PsiIdentifier nameIdentifier = method.getNameIdentifier();
    if (nameIdentifier != null) {
      m.astTypeName(toIdentifier(nameIdentifier));
    }
    m.astModifiers(toModifiers(method.getModifierList()));
    for (PsiJavaCodeReferenceElement reference : method.getThrowsList().getReferenceElements()) {
      m.astThrownTypeReferences().addToEnd(toTypeReference(reference));
    }

    StrictListAccessor<VariableDefinition, ConstructorDeclaration> parameters = m.astParameters();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      VariableDefinition definition = toVariableDefinition(parameter);
      parameters.addToEnd(definition);
    }

    PsiCodeBlock body = method.getBody();
    if (body != null) {
      m.astBody(toBlock(body));
    }

    return m;
  }

  @NonNull
  private static MethodDeclaration toMethodDeclaration(@NonNull PsiMethod method) {
    assert !method.isConstructor();
    MethodDeclaration m = new MethodDeclaration();
    bind(m, method);

    PsiIdentifier nameIdentifier = method.getNameIdentifier();
    if (nameIdentifier != null) {
      m.astMethodName(toIdentifier(nameIdentifier));
    }
    m.astModifiers(toModifiers(method.getModifierList()));
    PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement != null) {
      m.astReturnTypeReference(toTypeReference(returnTypeElement));
    }
    for (PsiJavaCodeReferenceElement reference : method.getThrowsList().getReferenceElements()) {
      m.astThrownTypeReferences().addToEnd(toTypeReference(reference));
    }

    StrictListAccessor<VariableDefinition, MethodDeclaration> parameters = m.astParameters();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      VariableDefinition definition = toVariableDefinition(parameter);
      parameters.addToEnd(definition);
    }

    PsiCodeBlock body = method.getBody();
    if (body != null) {
      m.astBody(toBlock(body));
    }

    return m;
  }
}
