/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.inspections;

import com.android.annotations.NonNull;
import com.android.resources.ResourceType;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.DeclaredPermissionsLookup;
import com.android.tools.lint.checks.PermissionFinder;
import com.android.tools.lint.checks.PermissionHolder;
import com.android.tools.lint.checks.PermissionRequirement;
import com.android.tools.lint.detector.api.Issue;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.actions.*;
import com.intellij.codeInspection.*;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.psi.xml.XmlTag;
import com.intellij.slicer.*;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import gnu.trove.THashSet;
import lombok.ast.BinaryOperator;
import lombok.ast.NullLiteral;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.inspections.lint.LombokPsiParser;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.tools.lint.checks.PermissionFinder.Operation.*;
import static com.android.tools.lint.checks.SupportAnnotationDetector.*;
import static com.intellij.psi.CommonClassNames.DEFAULT_PACKAGE;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.psi.util.PsiFormatUtilBase.SHOW_CONTAINING_CLASS;
import static com.intellij.psi.util.PsiFormatUtilBase.SHOW_NAME;

/**
 * A custom version of the IntelliJ
 * {@link com.intellij.codeInspection.magicConstant.MagicConstantInspection},
 * with two changes:
 * <ol>
 *   <li>
 *     Checks for proper resource types, e.g. ensuring that you call
 *     getResources().getDimension(id) with an ID that is a constant from
 *     R.dimen, not for example R.string.
 *   </li>
 *   <li>
 *     Checks for typedef annotations. These are precisely like the IntelliJ
 *     MagicConstant annotations, but with different annotation names and
 *     fields (for example for the integer case there is an explicit flags= boolean
 *     annotation attribute.
 *   </li>
 * </ol>
 * <p>
 * We didn't necessarily need to customize the inspection itself to handle the
 * second case; instead, we could have translated the annotations.zip file at
 * Gradle sync time to generate a derived annotations file with the annotation
 * names and parameters rewritten as IntelliJ annotations. However, doing
 * a resource type check is similar enough the the inspector needs to do nearly
 * everything else anyway (check the same kinds of calls, load the external annotations
 * for each resolved element, and so on) so with all that code in place we might
 * as well also support the android support annotations natively. This also has
 * the advantage that it will work with non-Gradle (IntelliJ and Maven) projects.
 * <p>
 * Since a lot of the code is identical to the IntelliJ magic constant inspection,
 * I have left the code identical to that inspection as much as possible, in order
 * to facilitate diffing the two classes and migrating future changes to the base
 * inspection over to this one.
 * <p>
 * To diff this class with the inspection it was based on, check out tag
 * idea/135.445 in the IntelliJ community edition git repository.
 * <p>
 * This means the code isn't as clear as possible; I just added a {@link ResourceType}
 * field to the AllowedValues inner class to pass around the required ResourceType,
 * which if non-null is the set of resource types we're enforcing rather than the
 * other values held in the object.
 * <p>
 * The main methods that were modified are:
 * <ul>
 *   <li>
 *     {@link #getAllowedValues(PsiModifierListOwner, PsiType, Set)}:
 *     Changed to look for IntDef/StringDef instead of MagicConstant, as well as look for the Resource type annotations
 *     ({@code }@StringRes}, {@code @IdRes}, etc) and for these we have to loop since you can specify more than one.
 *   </li>
 *   <li>
 *     {@code getAllowedValuesFromMagic()}: Changed to extract attributes from
 *     the support library's IntDef and TypeDef annotations and split into
 *     {@link #getAllowedValuesFromTypedef} and {@link #getResourceTypeFromAnnotation(String)}
 *   </li>
 *   <li>
 *     Created a new class, Constraint, which AllowedValues extends. AllowedValues now represents a value object
 *     for typedef constants. There are additional new subclasses which represent other constraints, such as
 *     ResourceTypeAllowedValues, IntRangeAllowedValues, etc.
 *   </li>
 *   <li>
 *     {@link #isAllowed}: Added checking for other types of allowed values, and if applicable,
 *     call new methods to check these, similar to the existing constant checks.
 *   </li>
 *   <li>
 *     Removed {@code checkAnnotationsJarAttached()}, since we will always provide SDK annotations
 *     for use by the type def collector in the SDK. (Also removed the code to call it and
 *     to clean up the associated key.)
 *   </li>
 *   <li>
 *     Sprinkled {@code @Nullable} annotations on various parameters and return values to
 *     remove warnings
 *   </li>
 *   <li>
 *     Removed {@code readFromClass} and code associated with picking all fields from
 *     a class
 *   </li>
 *   <li>
 *     Plugin registration: Increased severity to error, and changed category to Android.
 *   </li>
 *   <li>
 *     Stripped out {@code parseBeanInfo}
 *   </li>
 * </ul>
 * <p>
 */
public class ResourceTypeInspection extends BaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    AndroidFacet facet = AndroidFacet.getInstance(holder.getFile());
    if (facet == null) {
      // No-op outside of Android modules
      return new PsiElementVisitor() {};
    }

    return new JavaElementVisitor() {
      @Override
      public void visitCallExpression(PsiCallExpression callExpression) {
        checkCall(callExpression, holder);
      }

      @Override
      public void visitEnumConstant(PsiEnumConstant enumConstant) {
        checkCall(enumConstant, holder);
      }

      @Override
      public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression r = expression.getRExpression();
        if (r == null) return;
        PsiExpression l = expression.getLExpression();
        if (l instanceof PsiArrayAccessExpression) {
          // This is an array access so we use the type of the array expression and not the access itself.
          l = ((PsiArrayAccessExpression)l).getArrayExpression();
        }
        if (!(l instanceof PsiReferenceExpression)) return;

        PsiElement resolved = ((PsiReferenceExpression)l).resolve();
        if (!(resolved instanceof PsiModifierListOwner)) return;
        PsiModifierListOwner owner = (PsiModifierListOwner)resolved;
        PsiType type = expression.getType();
        checkExpression(r, owner, type, holder);
      }

      @Override
      public void visitReturnStatement(PsiReturnStatement statement) {
        PsiExpression value = statement.getReturnValue();
        if (value == null) return;
        @SuppressWarnings("unchecked")
        PsiElement element = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
        PsiMethod method = element instanceof PsiMethod ? (PsiMethod)element : LambdaUtil.getFunctionalInterfaceMethod(element);
        if (method == null) return;
        checkExpression(value, method, value.getType(), holder);
      }

      @Override
      public void visitNameValuePair(PsiNameValuePair pair) {
        PsiAnnotationMemberValue value = pair.getValue();
        if (!(value instanceof PsiExpression)) return;
        PsiReference ref = pair.getReference();
        if (ref == null) return;
        PsiMethod method = (PsiMethod)ref.resolve();
        if (method == null) return;
        checkExpression((PsiExpression)value, method, method.getReturnType(), holder);
      }

      @Override
      public void visitBinaryExpression(PsiBinaryExpression expression) {
        IElementType tokenType = expression.getOperationTokenType();
        if (tokenType != JavaTokenType.EQEQ && tokenType != JavaTokenType.NE) return;
        PsiExpression l = expression.getLOperand();
        PsiExpression r = expression.getROperand();
        if (r == null) return;
        checkBinary(l, r);
        checkBinary(r, l);
      }

      private void checkBinary(PsiExpression l, PsiExpression r) {
        if (l instanceof PsiReference) {
          PsiElement resolved = ((PsiReference)l).resolve();
          if (resolved instanceof PsiModifierListOwner) {
            checkExpression(r, (PsiModifierListOwner)resolved, getType((PsiModifierListOwner)resolved), holder);
          }
        }
        else if (l instanceof PsiMethodCallExpression) {
          PsiMethod method = ((PsiMethodCallExpression)l).resolveMethod();
          if (method != null) {
            checkExpression(r, method, method.getReturnType(), holder);
          }
        }
      }
    };
  }

  /**
   * Class that contains the result of evaluating whether an expression is valid or not.
   */
  static class InspectionResult {
    private static final InspectionResult VALID_RESULT = new InspectionResult(Status.VALID);
    private static final InspectionResult UNCERTAIN_RESULT = new InspectionResult(Status.UNCERTAIN);
    private static final InspectionResult INVALID_RESULT_NO_NODE = new InspectionResult(Status.INVALID);

    private final Status myStatus;
    private final PsiExpression myErrorNode;

    private InspectionResult(@NotNull Status status) {
      myStatus = status;
      myErrorNode = null;
    }

    private InspectionResult(@NotNull PsiExpression element) {
      myStatus = Status.INVALID;
      myErrorNode = element;
    }

    /**
     * Returns an invalid result using the passed node as the expression that caused the error.
     */
    @NotNull
    public static InspectionResult invalid(@NotNull PsiExpression node) {
      return new InspectionResult(node);
    }

    /**
     * Returns an invalid result for which we don't know the error node yet. {@link #isInvalid} will return false until an error node is
     * assigned using {@link #useErrorNode}.
     */
    @NotNull
    public static InspectionResult invalidWithoutNode() {
      return INVALID_RESULT_NO_NODE;
    }

    @NotNull
    public static InspectionResult valid() {
      return VALID_RESULT;
    }

    @NotNull
    public static InspectionResult uncertain() {
      return UNCERTAIN_RESULT;
    }

    public boolean isValid() {
      return myStatus == Status.VALID;
    }

    /**
     * Returns whether this is an invalid result. If the result is invalid, {@link #getErrorNode()} will return the node that caused the
     * problem.
     */
    public boolean isInvalid() {
      return myStatus == Status.INVALID && myErrorNode != null;
    }

    /**
     * Returns true if the current result is uncertain. A result will be uncertain if the result it's not either valid or invalid.
     * A result can be also uncertain when, even being invalid, it hasn't been assigned an error location yet.
     *
     * @return
     */
    public boolean isUncertain() {
      return myStatus == Status.UNCERTAIN || (myStatus == Status.INVALID && myErrorNode == null);
    }

    /**
     * Returns a new InspectionResult that uses the passed {@link PsiExpression} as error node to report. This allows setting a new error
     * location to give better indication or what expression caused the error.
     */
    @NotNull
    public InspectionResult useErrorNode(@NotNull PsiExpression errorNode) {
      if (myStatus == Status.INVALID && errorNode != myErrorNode) {
        return invalid(errorNode);
      }

      return this;
    }

    /**
     * Returns the error node that caused the expression to be invalid. If this method is called on a result that is not invalid, it will
     * throw a {@link IllegalStateException}.
     */
    @NotNull
    public PsiExpression getErrorNode() {
      if (!isInvalid() || myErrorNode == null) {
        throw new IllegalStateException();
      }

      return myErrorNode;
    }

    private enum Status {
      VALID,
      INVALID,
      UNCERTAIN
    }
  }

  private static void checkExpression(@NotNull PsiExpression expression,
                                      @NotNull PsiModifierListOwner owner,
                                      @Nullable PsiType type,
                                      @NotNull ProblemsHolder holder) {
    Constraints allowed = getAllowedValues(owner, type, null);
    if (allowed == null) return;
    //noinspection ConstantConditions
    PsiElement scope = PsiUtil.getTopLevelEnclosingCodeBlock(expression, null);
    if (scope == null) scope = expression;

    checkConstraints(scope, expression, allowed, holder);
  }

  private static void checkConstraints(@NotNull PsiElement scope,
                                       @NotNull PsiExpression expression,
                                       @NotNull Constraints constraints,
                                       @NotNull ProblemsHolder holder) {
    if (expression.getTextRange().isEmpty()) {
      return;
    }
    PsiManager manager = expression.getManager();
    if (constraints.next != null && ExpressionUtils.isLiteral(expression)) {
      // The only possible combination of constraints is @IntDef and @IntRange, which allows you
      // to specify that an argument must be one of a set of constants, OR, a number in a range.
      InspectionResult result = isAllowed(scope, expression, constraints, manager, null);
      if (result.isInvalid() && isAllowed(scope, expression, constraints.next, manager, null).isInvalid()) {
        registerProblem(result.getErrorNode(), constraints, holder);
      }
    }
    else {
      InspectionResult result = isAllowed(scope, expression, constraints, manager, null);
      if (result.isInvalid()) {
        registerProblem(result.getErrorNode(), constraints, holder);
      }
    }
  }

  private static void checkCall(@NotNull PsiCall methodCall, @NotNull ProblemsHolder holder) {
    PsiMethod method = methodCall.resolveMethod();
    if (method == null) return;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length > 0) {
      PsiExpressionList argumentList = methodCall.getArgumentList();
      if (argumentList == null) return;
      PsiExpression[] arguments = argumentList.getExpressions();
      int parametersIdx = 0;
      for (int i = 0; i < arguments.length && parametersIdx < parameters.length; i++) {
        PsiParameter parameter = parameters[parametersIdx];
        // If it's not an ellipsis type, we keep walking through the parameters list. If it's an ellipsis, that the last parameter we have
        // to look into
        if (!(parameter.getType() instanceof PsiEllipsisType)) {
          parametersIdx++;
        }
        Constraints values = getAllowedValues(parameter, parameter.getType(), null);
        if (values == null) continue;
        if (i >= arguments.length) break;
        PsiExpression argument = arguments[i];
        argument = PsiUtil.deparenthesizeExpression(argument);
        if (argument == null) continue;

        checkConstraints(parameter.getDeclarationScope(), argument, values, holder);
      }
    }

    checkMethodAnnotations(methodCall, holder, method);
  }

  private static void checkMethodAnnotations(@NotNull PsiCall methodCall, @NotNull ProblemsHolder holder, @NotNull PsiMethod method) {
    for (PsiAnnotation annotation : getAllAnnotations(method)) {
      String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName == null) {
        continue;
      } else if (!qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
        if (qualifiedName.startsWith(DEFAULT_PACKAGE)) { // java.lang.Override, java.lang.SuppressWarnings etc, ignore
          continue;
        } else {
          // Look for annotation that itself is annotated; we allow this for the @RequiresPermission annotation
          PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
          PsiElement resolved = ref == null ? null : ref.resolve();
          if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) {
            continue;
          }
          PsiClass cls = (PsiClass)resolved;
          for (PsiAnnotation a : getAllAnnotations(cls)) {
            qualifiedName = a.getQualifiedName();
            if (qualifiedName != null && qualifiedName.endsWith(PERMISSION_ANNOTATION)) {
              checkPermissionRequirement(methodCall, holder, method, a);
            } else if (STRING_DEF_ANNOTATION.equals(qualifiedName)) {
              // Handle equals() as a special case: if you're invoking
              //   .equals on a method whose return value annotated with @StringDef
              //   we want to make sure that the equals parameter is compatible.
              // 186598: StringDef dont warn using a getter and equals
              PsiElement parent = methodCall.getParent();
              PsiType type = method.getReturnType();
              if (type != null && parent instanceof PsiReferenceExpression) {
                PsiReferenceExpression expression = (PsiReferenceExpression)parent;
                PsiElement name = expression.getReferenceNameElement();
                if (name != null && "equals".equals(name.getText())) {
                  PsiElement parent2 = parent.getParent();
                  if (parent2 instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression equalsCall = (PsiMethodCallExpression)parent2;
                    PsiExpression[] arguments = equalsCall.getArgumentList().getExpressions();
                    if (arguments.length == 1) {
                      Constraints constraints = getAllowedValuesFromTypedef(type, a, methodCall.getManager());
                      if (constraints != null) {
                        checkConstraints(parent2, arguments[0], constraints, holder);
                      }
                    }
                  }
                }
              }
            }
          }

          continue;
        }
      }

      if (PERMISSION_ANNOTATION.equals(qualifiedName)) {
        checkPermissionRequirement(methodCall, holder, method, annotation);
      } else if (CHECK_RESULT_ANNOTATION.equals(qualifiedName)) {
        checkReturnValueUsage(methodCall, holder, method);
      } else if (qualifiedName.endsWith(THREAD_SUFFIX) && qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
        checkThreadAnnotation(methodCall, holder, method, qualifiedName);
      }
    }

    PsiClass cls = method.getContainingClass();
    if (cls != null) {
      // Class annotations only apply to instance methods. Static methods in child classes do not inherit class annotations.
      PsiAnnotation[] annotations = method.hasModifierProperty(PsiModifier.STATIC) ? getLocalAnnotations(cls) : getAllAnnotations(cls);
      for (PsiAnnotation annotation : annotations) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) {
          continue;
        }

        if (qualifiedName.endsWith(THREAD_SUFFIX) && qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
          checkThreadAnnotation(methodCall, holder, method, qualifiedName);
        } else if (!qualifiedName.startsWith(DEFAULT_PACKAGE)) {
          // Look for annotation that itself is annotated; we allow this for the @RequiresPermission annotation
          PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
          PsiElement resolved = ref == null ? null : ref.resolve();
          if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) {
            continue;
          }
          cls = (PsiClass)resolved;
          for (PsiAnnotation a : getAllAnnotations(cls)) {
            qualifiedName = a.getQualifiedName();
            if (qualifiedName != null && qualifiedName.endsWith(PERMISSION_ANNOTATION)) {
              checkPermissionRequirement(methodCall, holder, method, a);
            }
          }
        }
      }
    }
  }

  @Nullable
  private static PermissionFinder.Result search(@NonNull PsiElement node, @NonNull PermissionFinder.Operation operation) {
    if (node instanceof NullLiteral) {
      return null;
    } else if (node instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression cast = (PsiTypeCastExpression) node;
      final PsiExpression operand = cast.getOperand();
      if (operand != null) {
        return search(operand, operation);
      }
    } else if (node instanceof PsiNewExpression && operation == ACTION) {
      // Identifies "new Intent(argument)" calls and, if found, continues
      // resolving the argument instead looking for the action definition
      PsiNewExpression call = (PsiNewExpression)node;
      PsiJavaCodeReferenceElement classOrAnonymousClassReference = call.getClassOrAnonymousClassReference();
      if (classOrAnonymousClassReference != null) {
        String qualifiedName = classOrAnonymousClassReference.getQualifiedName();
        if (CLASS_INTENT.equals(qualifiedName)) {
          PsiExpressionList argumentList = call.getArgumentList();
          if (argumentList != null) {
            PsiExpression[] expressions = argumentList.getExpressions();
            if (expressions.length > 0) {
              return search(expressions[0], operation);
            }
          }
        }
      }
      return null;
    } else if (node instanceof PsiJavaReference) {
      PsiElement resolved = ((PsiJavaReference)node).resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField)resolved;
        if (operation == ACTION) {
          for (PsiAnnotation annotation : getAllAnnotations(field)) {
            if (PERMISSION_ANNOTATION.equals(annotation.getQualifiedName())) {
              return getPermissionRequirement(field, annotation, operation);
            }
          }
        }
        else if (operation == READ || operation == WRITE) {
          String fqn = operation == READ ? PERMISSION_ANNOTATION_READ : PERMISSION_ANNOTATION_WRITE;
          PsiAnnotation annotation = null;
          for (PsiAnnotation a : getAllAnnotations(field)) {
            if (fqn.equals(a.getQualifiedName())) {
              annotation = null;
              if (AnnotationUtil.isExternalAnnotation(a)) {
                // The complex annotations used for read/write cannot be
                // expressed in the external annotations format, so they're inlined.
                // (See Extractor.AnnotationData#write).
                //
                // Instead we've inlined the fields of the annotation on the
                // outer one:
                annotation = a;
                break;
              } else {
                final PsiAnnotationMemberValue o = a.findAttributeValue(ATTR_VALUE);
                if (o instanceof PsiAnnotation) {
                  annotation = (PsiAnnotation)o;
                  if (PERMISSION_ANNOTATION.equals(annotation.getQualifiedName())) {
                    break;
                  }
                }
              }
            }
          }
          if (annotation != null) {
            return getPermissionRequirement(field, annotation, operation);
          } else {
            PsiExpression initializer = field.getInitializer();
            if (initializer instanceof PsiMethodCallExpression) {
              PsiMethodCallExpression call = (PsiMethodCallExpression)initializer;
              if (call.getMethodExpression().getQualifiedName().equals("Uri.withAppendedPath")) {
                PsiExpression[] expressions = call.getArgumentList().getExpressions();
                if (expressions.length == 2) {
                  return search(expressions[0], operation);
                }
              }
            }
          }
        }
        else {
          assert false : operation;
        }
      } else if (resolved instanceof PsiLocalVariable) {
        PsiStatement statement = PsiTreeUtil.getParentOfType(node, PsiStatement.class, false);
        while (statement != null) {
          if (statement instanceof PsiDeclarationStatement) {
            PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
            PsiElement[] declaredElements = declaration.getDeclaredElements();
            for (PsiElement declared : declaredElements) {
              if (declared == resolved && declared instanceof PsiLocalVariable) {
                // Found the declaration of the target variable; look at the right hand side to determine how
                // to proceed.
                PsiExpression initializer = ((PsiLocalVariable)declared).getInitializer();
                if (initializer != null) {
                  return search(initializer, operation);
                }
              }
            }
          } else if (statement instanceof PsiExpressionStatement) {
            PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
            if (expression instanceof PsiAssignmentExpression) {
              PsiAssignmentExpression assignment = (PsiAssignmentExpression)expression;
              if (assignment.getLExpression() instanceof PsiReferenceExpression) {
                PsiElement variable = ((PsiReferenceExpression)assignment.getLExpression()).resolve();
                if (variable == resolved) {
                  PsiExpression value = assignment.getRExpression();
                  if (value != null) {
                    return search(value, operation);
                  }
                }
              }
            }
          } else if (statement instanceof PsiBinaryExpression) {
            PsiBinaryExpression binary = (PsiBinaryExpression)statement;
            if (binary.getOperationTokenType() == JavaTokenType.EQ) {
              // Look at the LHS to see how to proceed
              if (binary.getLOperand() == resolved && binary.getROperand() != null) {
                // Found assignment: delegate to rhs.
                return search(binary.getROperand(), operation);
              }
            }
          }
          statement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
        }
      }
    }

    return null;
  }

  @NonNull
  private static PermissionFinder.Result getPermissionRequirement(@NonNull PsiField field,
                                                                  @NonNull PsiAnnotation annotation,
                                                                  @NonNull PermissionFinder.Operation operation) {
    PermissionRequirement requirement = PermissionRequirement.create(null, annotation);
    PsiClass containingClass = field.getContainingClass();
    String name;
    if (containingClass != null) {
      name = containingClass.getName() + "." + field.getName();
    } else {
      name = field.getName();
    }
    return new PermissionFinder.Result(operation, requirement, StringUtil.notNullize(name));
  }

  private static void checkThreadAnnotation(PsiCall methodCall, ProblemsHolder holder, PsiMethod method, String qualifiedName) {
    String threadContext = getThreadContext(methodCall);
    if (threadContext != null && !isCompatibleThread(threadContext, qualifiedName)) {
      String message = String.format("Method %1$s must be called from the %2$s thread, currently inferred thread is %3$s",
        method.getName(), describeThread(qualifiedName), describeThread(threadContext));
      registerProblem(holder, THREAD, methodCall, message);
    }
  }

  /** Attempts to infer the current thread context at the site of the given method call */
  @Nullable
  private static String getThreadContext(PsiCall methodCall) {
    PsiMethod method = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true);
    if (method != null) {
      PsiAnnotation[] annotations = getAllAnnotations(method);
      for (PsiAnnotation annotation : annotations) {
        String qualifiedName = annotation.getQualifiedName();
        if (qualifiedName == null) {
          continue;
        }

        if (qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX) && qualifiedName.endsWith(THREAD_SUFFIX)) {
          return qualifiedName;
        }
      }

      // See if we're extending a class with a known threading context
      PsiClass cls = method.getContainingClass();
      if (cls != null) {
        annotations = getAllAnnotations(cls);
        for (PsiAnnotation annotation : annotations) {
          String qualifiedName = annotation.getQualifiedName();
          if (qualifiedName == null) {
            continue;
          }

          if (qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX) && qualifiedName.endsWith(THREAD_SUFFIX)) {
            return qualifiedName;
          }
        }
      }
    }

    // TODO: Other heuristics I could use here are:
    // - if extending view, use @UiThread
    // - look at other calls in the method, and try to infer it based on those? (risky since if I have a method with two thread
    //   annotated calls and one of them is wrong, who do I choose as the authority?

    return null;
  }

  private static void checkPermissionRequirement(@NotNull PsiCall methodCall,
                                                 @NotNull ProblemsHolder holder,
                                                 @Nullable PsiMethod method,
                                                 @NotNull PsiAnnotation annotation) {
    PermissionRequirement requirement = PermissionRequirement.create(null, annotation);
    checkPermissionRequirement(methodCall, holder, method, null, requirement);
  }

  private static void checkPermissionRequirement(@NotNull PsiCall methodCall,
                                                 @NotNull ProblemsHolder holder,
                                                 @Nullable PsiMethod method,
                                                 @Nullable PermissionFinder.Result result,
                                                 PermissionRequirement requirement) {
    if (!requirement.isConditional()) {
      Project project = methodCall.getProject();
      final AndroidFacet facet = AndroidFacet.getInstance(methodCall);
      assert facet != null; // already checked early on in the inspection visitor
      PermissionHolder lookup = DeclaredPermissionsLookup.getPermissionHolder(facet.getModule());
      if (!requirement.isSatisfied(lookup)) {
        // See if it looks like we're holding the permission implicitly by @RequirePermission
        // annotations in the surrounding context
        lookup = addLocalPermissions(lookup, methodCall);
        if (requirement.isSatisfied(lookup)) {
          return;
        }

        String methodName;
        PermissionFinder.Operation operation;
        if (result != null) {
          operation = result.operation;
          methodName = result.name;
        } else {
          assert method != null;
          operation = CALL;
          PsiClass containingClass = method.getContainingClass();
          methodName = containingClass != null ? containingClass.getName() + "." + method.getName() : method.getName();
        }
        String message = getMissingPermissionMessage(requirement, methodName, lookup, operation);
        LocalQuickFix[] fixes = LocalQuickFix.EMPTY_ARRAY;
        List<LocalQuickFix> list = Lists.newArrayList();
        for (String permissionName : requirement.getMissingPermissions(lookup)) {
          list.add(new AddPermissionFix(facet, permissionName));
        }
        if (!list.isEmpty()) {
          fixes = list.toArray(new LocalQuickFix[list.size()]);
        }
        registerProblem(holder, MISSING_PERMISSION, methodCall, message, fixes);
      } else if (requirement.isRevocable(lookup) && AndroidModuleInfo.get(facet).getTargetSdkVersion().getFeatureLevel() >= 23) {
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        PsiClass securityException = psiFacade.findClass("java.lang.SecurityException", GlobalSearchScope.allScope(project));
        if (securityException != null &&
            // Can't just call ExceptionUtil#isHandled like this:
            //   ExceptionUtil.isHandled(PsiTypesUtil.getClassType(securityException), methodCall)) {
            // because we *don't* want to quietly accept catching some SecurityException superclass (like Throwable);
            // we want to warn about newly uncaught SecurityExceptions since users should be aware of this change
            // and a better fix than just catching it is to insert manual checks and calling APIs to request
            // permissions from the user.
            isHandled(methodCall, PsiTypesUtil.getClassType(securityException), methodCall.getContainingFile())) {
          return;
        }

        PsiMethod methodNode = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class, true);
        if (methodNode != null) {
          //noinspection unchecked
          for (PsiMethodCallExpression call : PsiTreeUtil.collectElementsOfType(methodNode, PsiMethodCallExpression.class)) {
            String name = call.getMethodExpression().getReferenceName();
            if (name != null && name.endsWith("Permission") && (name.startsWith("check") || name.startsWith("enforce"))) {
              return;
            }
          }

          Set<String> revocablePermissions = requirement.getRevocablePermissions(lookup);
          AddCheckPermissionFix fix = new AddCheckPermissionFix(facet, requirement, methodCall, revocablePermissions);
          registerProblem(holder, MISSING_PERMISSION, methodCall, getUnhandledPermissionMessage(), fix);
        }
      }
    }
  }

  @NotNull
  private static PermissionHolder addLocalPermissions(@NotNull PermissionHolder lookup, @NotNull PsiCall call) {
    // Accumulate @RequirePermissions available in the local context
    PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
    if (method == null) {
      return lookup;
    }
    PsiAnnotation annotation = AnnotationUtil.findAnnotationInHierarchy(method, Collections.singleton(PERMISSION_ANNOTATION));
    if (annotation == null) {
      return lookup;
    }

    PermissionRequirement requirement = PermissionRequirement.create(null, annotation);
    return PermissionHolder.SetPermissionLookup.join(lookup, requirement);
  }

  private static void checkReturnValueUsage(@NotNull PsiCall methodCall, ProblemsHolder holder, PsiMethod method) {
    if (methodCall.getParent() instanceof PsiExpressionStatement) {
      PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, CHECK_RESULT_ANNOTATION);
      if (annotation == null) {
        return;
      }
      String message = String.format("The result of '%1$s' is not used", method.getName());
      PsiAnnotationMemberValue value = annotation.findAttributeValue(ATTR_SUGGEST);
      if (value instanceof PsiLiteral) {
        PsiLiteral literal = (PsiLiteral)value;
        Object literalValue = literal.getValue();
        if (literalValue instanceof String) {
          String suggest = (String)literalValue;
          // TODO: Resolve suggest attribute (e.g. prefix annotation class if it starts
          // with "#" etc)?
          if (!suggest.isEmpty()) {
            String name = suggest;
            if (name.startsWith("#")) {
              name = name.substring(1);
            }
            message = String.format("The result of '%1$s' is not used; did you mean to call '%2$s'?", method.getName(), name);
            if (suggest.startsWith("#") && methodCall instanceof PsiMethodCallExpression) {
              registerProblem(holder, CHECK_RESULT, methodCall, message, new ReplaceCallFix((PsiMethodCallExpression)methodCall, suggest));
              return;
            }
          }
        }
      }
      registerProblem(holder, CHECK_RESULT, methodCall, message);
    }
  }

  private static class AddPermissionFix implements LocalQuickFix {
    private final AndroidFacet myFacet;
    private final String myPermissionName;

    public AddPermissionFix(@NotNull AndroidFacet facet, @NotNull String permissionName) {
      myFacet = facet;
      myPermissionName = permissionName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return String.format("Add Permission %1$s", myPermissionName.substring(myPermissionName.lastIndexOf('.')+1));
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Add permissions";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final VirtualFile manifestFile = AndroidRootUtil.getPrimaryManifestFile(myFacet);
      if (manifestFile == null || !ReadonlyStatusHandler.ensureFilesWritable(myFacet.getModule().getProject(), manifestFile)) {
        return;
      }

      final Manifest manifest = myFacet.getManifest();
      if (manifest == null) {
        return;
      }

      // I tried manipulating the file using DOM apis, using this:
      //    Permission permission = manifest.addPermission();
      //    permission.getName().setValue(myPermissionName);
      // (which required adding
      //      Permission addPermission();
      // to org.jetbrains.android.dom.manifest.Manifest).
      //
      // However, that will append a <permission name="something"/> element to the
      // *end* of the manifest, which is not right (and will trigger a lint warning).
      // So, instead we manipulate the XML document directly via PSI. (This is
      // incidentally also how the AndroidModuleBuilder#configureManifest method works.)
      final XmlTag manifestTag = manifest.getXmlTag();
      if (manifestTag == null) {
        return;
      }

      XmlTag permissionTag = manifestTag.createChildTag(TAG_USES_PERMISSION, "", null, false);
      if (permissionTag != null) {

        XmlTag before = null;
        // Find best insert position:
        //   (1) attempt to insert alphabetically among any permission tags
        //   (2) if no permission tags are found, put it before the application tag
        for (XmlTag tag : manifestTag.getSubTags()) {
          String tagName = tag.getName();
          if (tagName.equals(TAG_APPLICATION)) {
            before = tag;
            break;
          } else if (tagName.equals(TAG_USES_PERMISSION)
                           || tagName.equals(TAG_USES_PERMISSION_SDK_23)
                           || tagName.equals(TAG_USES_PERMISSION_SDK_M)) {
            String name = tag.getAttributeValue(ATTR_NAME, ANDROID_URI);
            if (name != null && name.compareTo(myPermissionName) > 0) {
              before = tag;
              break;
            }
          }
        }
        if (before == null) {
          permissionTag = manifestTag.addSubTag(permissionTag, false);
        } else {
          permissionTag = (XmlTag)manifestTag.addBefore(permissionTag, before);
        }

        // Do this *after* adding the tag to the document; otherwise, setting the
        // namespace prefix will not work correctly
        permissionTag.setAttribute(ATTR_NAME, ANDROID_URI, myPermissionName);

        CodeStyleManager.getInstance(project).reformat(permissionTag);

        DeclaredPermissionsLookup.getInstance(project).reset();
        FileDocumentManager.getInstance().saveAllDocuments();
        PsiFile containingFile = permissionTag.getContainingFile();
        // No edits were made to the current document, so trigger a rescan to ensure
        // that the inspection discovers that there is now a new available inspection
        if (containingFile != null) {
          DaemonCodeAnalyzer.getInstance(project).restart();
        }
      }
    }
  }

  private static class AddCheckPermissionFix implements LocalQuickFix {
    private final AndroidFacet myFacet;
    private final PermissionRequirement myRequirement;
    private final Set<String> myRevocablePermissions;
    private final SmartPsiElementPointer<PsiCall> myCall;

    public AddCheckPermissionFix(@NotNull AndroidFacet facet, @NotNull PermissionRequirement requirement, @NotNull PsiCall call,
                                 @NotNull Set<String> revocablePermissions) {
      myFacet = facet;
      myRequirement = requirement;
      myCall = SmartPointerManager.getInstance(call.getProject()).createSmartPsiElementPointer(call);
      myRevocablePermissions = revocablePermissions;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Add permission check";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiCall call = myCall.getElement();
      if (call == null) {
        return;
      }

      // Find the statement containing the method call;
      PsiStatement statement = PsiTreeUtil.getParentOfType(call, PsiStatement.class, true);
      if (statement == null) {
        return;
      }
      PsiElement parent = statement.getParent();
      if (parent == null) {
        return; // highly unlikely
      }

      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithLibrariesScope(myFacet.getModule());
      PsiClass manifest = facade.findClass("android.Manifest.permission", moduleScope);
      Map<String, PsiField> permissionNames;

      if (manifest != null) {
        PsiField[] fields = manifest.getFields();
        permissionNames = Maps.newHashMapWithExpectedSize(fields.length);
        for (PsiField field : fields) {
          PsiExpression initializer = field.getInitializer();
          if (initializer instanceof PsiLiteralExpression) {
            Object value = ((PsiLiteralExpression)initializer).getValue();
            if (value instanceof String) {
              permissionNames.put((String)value, field);
            }
          }
        }
      } else {
        permissionNames = Collections.emptyMap();
      }

      // Look up the operator combining the requirements, and *reverse it*.
      // That's because we're adding a check to exit if the permissions are *not* met.
      // For example, take the case of location permissions: you need COARSE OR FINE.
      // In that case, we check that you do not have COARSE, *and* that you do not have FINE,
      // before we exit.
      IElementType operator = myRequirement.getOperator();
      if (operator == null || operator == JavaTokenType.ANDAND) {
        operator = JavaTokenType.OROR;
      } else if (operator == JavaTokenType.OROR) {
        operator = JavaTokenType.ANDAND;
      }

      PsiElementFactory factory = facade.getElementFactory();
      StringBuilder sb = new StringBuilder(200);
      sb.append("if (");
      boolean first = true;

      PsiClass activityCompat = facade.findClass("android.support.v4.app.ActivityCompat", moduleScope);
      boolean usingAppCompat = activityCompat != null;
      if (usingAppCompat && (activityCompat.findMethodsByName("requestPermissions", false).length == 0)) {
        // Using an older version of appcompat than 23.0.1. Later we should prompt the user to
        // see if they'd like to upgrade instead; for now, revert to platform version.
        usingAppCompat = false;
      }

      for (String permission : myRevocablePermissions) {
        if (first) {
          first = false;
        } else {
          sb.append(' ');
          if (operator == JavaTokenType.ANDAND) {
            sb.append("&&");
          }
          else if (operator == JavaTokenType.OROR) {
            sb.append("||");
          }
          else if (operator == JavaTokenType.XOR) {
            sb.append("^");
          }
          sb.append(' ');
        }
        if (usingAppCompat) {
          sb.append("android.support.v4.app.ActivityCompat.");
        }
        sb.append("checkSelfPermission(");
        if (usingAppCompat) {
          sb.append("this, ");
        }

        // Try to map permission strings back to field references!
        PsiField field = permissionNames.get(permission);
        if (field != null && field.getContainingClass() != null) {
          sb.append(field.getContainingClass().getQualifiedName()).append('.').append(field.getName());
        } else {
          sb.append('"').append(permission).append('"');
        }
        sb.append(") != android.content.pm.PackageManager.PERMISSION_GRANTED");
      }
      sb.append(") {\n");
      sb.append(" // TODO: Consider calling\n" +
                " //    Activity").append(usingAppCompat ? "Compat" : "").append("#requestPermissions\n" +
                " // here to request the missing permissions, and then overriding\n" +
                " //   public void onRequestPermissionsResult(int requestCode, String[] permissions,\n" +
                " //                                          int[] grantResults)\n" +
                " // to handle the case where the user grants the permission. See the documentation\n" +
                " // for Activity").append(usingAppCompat ? "Compat" : "").append("#requestPermissions for more details.\n");
      PsiMethod method = PsiTreeUtil.getParentOfType(call, PsiMethod.class, true);

      // TODO: Add additional information here, perhaps pointing to
      //    http://android-developers.blogspot.com/2015/09/google-play-services-81-and-android-60.html
      // or adding more of a skeleton from that article.

      if (method != null && !PsiType.VOID.equals(method.getReturnType())) {
        sb.append("return TODO;\n");
      } else {
        sb.append("return;\n");
      }
      sb.append("}\n");
      String code = sb.toString();

      PsiStatement check = factory.createStatementFromText(code, call);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(check);
      parent.addBefore(check, statement);

      // Reformat from start of newly added element to end of statement following it
      CodeStyleManager.getInstance(project).reformatRange(parent, check.getTextOffset(),
                                                          statement.getTextOffset() + statement.getTextLength());
    }
  }

  private static class ReplaceCallFix implements LocalQuickFix {

    private final SmartPsiElementPointer<PsiMethodCallExpression> myMethodCall;
    private final String mySuggest;

    public ReplaceCallFix(@NotNull PsiMethodCallExpression methodCall, @NotNull String suggest) {
      myMethodCall = SmartPointerManager.getInstance(methodCall.getProject()).createSmartPsiElementPointer(methodCall);
      mySuggest = suggest;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return String.format("Call %1$s instead", getMethodName());
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace calls";
    }

    private String getMethodName() {
      assert mySuggest.startsWith("#");
      int start = 1;
      int parameters = mySuggest.indexOf('(', start);
      if (parameters == -1) {
        parameters = mySuggest.length();
      }
      return mySuggest.substring(start, parameters).trim();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression methodCall = myMethodCall.getElement();
      if (methodCall == null || !methodCall.isValid()) {
        return;
      }
      String name = getMethodName();
      final PsiFile file = methodCall.getContainingFile();
      if (file == null || !FileModificationService.getInstance().prepareFileForWrite(file)) {
        return;
      }

      Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
      if (document != null) {
        PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
        PsiElement referenceNameElement = methodExpression.getReferenceNameElement();
        if (referenceNameElement != null) {
          TextRange range = referenceNameElement.getTextRange();
          if (range != null) {
            // Also need to insert a message parameter
            // Currently hardcoded for the check*Permission to enforce*Permission code path. It's
            // tricky to figure out in general how to map existing parameters to new
            // parameters. Consider using MethodSignatureInsertHandler.
            if (name.startsWith("enforce") && methodExpression.getReferenceName() != null
                && methodExpression.getReferenceName().startsWith("check")) {
              PsiExpressionList argumentList = methodCall.getArgumentList();
              int offset = argumentList.getTextOffset() + argumentList.getTextLength() - 1;
              document.insertString(offset, ", \"TODO: message if thrown\"");
            }

            // Replace method call
            document.replaceString(range.getStartOffset(), range.getEndOffset(), name);
          }
        }
      }
    }
  }

  static class Constraints {
    public boolean isSubsetOf(@NotNull Constraints other, @NotNull PsiManager manager) {
      return false;
    }

    /** Linked list next reference, when more than one applies */
    @Nullable public Constraints next;
  }

  /**
   * A typedef constraint. Then name is kept as "AllowedValues" to keep all the surrounding code
   * which references this class unchanged (since it's based on MagicConstantInspection, so we
   * can more easily diff and incorporate recent MagicConstantInspection changes.)
   */
  static class AllowedValues extends Constraints {
    final PsiAnnotationMemberValue[] values;
    final boolean canBeOred;

    private AllowedValues(@NotNull PsiAnnotationMemberValue[] values, boolean canBeOred) {
      this.values = values;
      this.canBeOred = canBeOred;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AllowedValues a2 = (AllowedValues)o;
      if (canBeOred != a2.canBeOred) {
        return false;
      }
      Set<PsiAnnotationMemberValue> v1 = new THashSet<PsiAnnotationMemberValue>(Arrays.asList(values));
      Set<PsiAnnotationMemberValue> v2 = new THashSet<PsiAnnotationMemberValue>(Arrays.asList(a2.values));
      if (v1.size() != v2.size()) {
        return false;
      }
      for (PsiAnnotationMemberValue value : v1) {
        for (PsiAnnotationMemberValue value2 : v2) {
          if (same(value, value2, value.getManager())) {
            v2.remove(value2);
            break;
          }
        }
      }
      return v2.isEmpty();
    }

    @Override
    public int hashCode() {
      int result = Arrays.hashCode(values);
      result = 31 * result + (canBeOred ? 1 : 0);
      return result;
    }

    @Override
    public boolean isSubsetOf(@NotNull Constraints other, @NotNull PsiManager manager) {
      if (!(other instanceof AllowedValues)) {
        return false;
      }
      AllowedValues o = (AllowedValues)other;
      for (PsiAnnotationMemberValue value : values) {
        boolean found = false;
        for (PsiAnnotationMemberValue otherValue : o.values) {
          if (same(value, otherValue, manager)) {
            found = true;
            break;
          }
        }
        if (!found) return false;
      }
      return true;
    }
  }

  static class ResourceTypeAllowedValues extends Constraints {
    /**
     * Type of Android resource that we must be passing. An empty list means no
     * resource type is allowed; this is currently used for {@code @ColorInt},
     * stating that not only is it <b>not</b> supposed to be a {@code R.color.name},
     * but it should correspond to an ARGB integer.
     */
    @NotNull
    final List<ResourceType>  types;

    public ResourceTypeAllowedValues(@NotNull List<ResourceType> types) {
      this.types = types;
    }

    /** Returns true if this resource type constraint allows a type of the given name */
    public boolean isTypeAllowed(@NotNull ResourceType type) {
      return isTypeAllowed(type.getName());
    }

    public boolean isTypeAllowed(@NotNull String typeName) {
      for (ResourceType type : types) {
        if (type.getName().equals(typeName) ||
            type == ResourceType.DRAWABLE &&
            (ResourceType.COLOR.getName().equals(typeName) || ResourceType.MIPMAP.getName().equals(typeName))) {
          return true;
        }
      }
      return false;
    }

    /**
     * Returns true if the resource type constraint is compatible with the other resource type
     * constraint
     *
     * @param other the resource type constraint to compare it to
     * @return true if the two resource constraints are compatible
     */
    public boolean isCompatibleWith(@NotNull ResourceTypeAllowedValues other) {
      // Happy if *any* of the resource types on the annotation matches any of the
      // annotations allowed for this API
      if (other.types.isEmpty() && types.isEmpty()) {
        // Passing in a method call whose return value is @ColorInt
        // to a parameter which is @ColorInt: OK
        return true;
      }
      for (ResourceType type : other.types) {
        if (isTypeAllowed(type)) {
          return true;
        }
      }

      return false;
    }
  }

  static class RangeAllowedValues extends Constraints {
    @NotNull
    public String describe(@NotNull PsiExpression argument) {
      assert false;
      return "";
    }

    public InspectionResult isValid(@NotNull PsiExpression argument) {
      return InspectionResult.uncertain();
    }

    @Nullable
    protected Number guessSize(@NotNull PsiExpression argument) {
      if (argument instanceof PsiLiteral) {
        PsiLiteral literal = (PsiLiteral)argument;
        Object v = literal.getValue();
        if (v instanceof Number) {
          return (Number)v;
        }
      } else if (argument instanceof PsiBinaryExpression) {
        Object v = JavaConstantExpressionEvaluator.computeConstantExpression(argument, false);
        if (v instanceof Number) {
          return (Number)v;
        }
      } else if (argument instanceof PsiReferenceExpression) {
        PsiReferenceExpression ref = (PsiReferenceExpression)argument;
        PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiField) {
          PsiField field = (PsiField)resolved;
          PsiExpression initializer = field.getInitializer();
          if (initializer != null) {
            Number number = guessSize(initializer);
            if (number != null) {
              // If we're surrounded by an if check involving the variable, then don't validate
              // based on the initial value since it might be clipped to a valid range
              PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(argument, PsiIfStatement.class, true);
              if (ifStatement != null) {
                PsiExpression condition = ifStatement.getCondition();
                if (comparesReference(resolved, condition)) {
                  return null;
                }
              }
            }
            return number;
          }
        } else if (resolved instanceof PsiLocalVariable) {
          PsiLocalVariable variable = (PsiLocalVariable) resolved;
          PsiExpression initializer = variable.getInitializer();
          if (initializer != null) {
            Number number = guessSize(initializer);
            if (number != null) {
              // If we're surrounded by an if check involving the variable, then don't validate
              // based on the initial value since it might be clipped to a valid range
              PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(argument, PsiIfStatement.class, true);
              if (ifStatement != null) {
                PsiExpression condition = ifStatement.getCondition();
                if (comparesReference(resolved, condition)) {
                  return null;
                }
              }
            }
            return number;
          }
        }
      } else if (argument instanceof PsiPrefixExpression) {
        PsiPrefixExpression prefix = (PsiPrefixExpression)argument;
        if (prefix.getOperationTokenType() == JavaTokenType.MINUS) {
          PsiExpression operand = prefix.getOperand();
          if (operand != null) {
            Number number = guessSize(operand);
            if (number != null) {
              if (number instanceof Long) {
                return -number.longValue();
              } else if (number instanceof Integer) {
                return -number.intValue();
              } else if (number instanceof Double) {
                return -number.doubleValue();
              } else if (number instanceof Float) {
                return -number.floatValue();
              } else if (number instanceof Short) {
                return -number.shortValue();
              } else if (number instanceof Byte) {
                return -number.byteValue();
              }
            }
          }
        }
      }
      return null;
    }

    /**
     * Checks whether the given range is compatible with this one.
     * We err on the side of caution. E.g. if we have
     * <pre>
     *    method(x)
     * </pre>
     * and the parameter declaration says that x is between 0 and 10,
     * and then we have a parameter which is known to be in the range 5 to 15,
     * here we consider this a compatible range; we don't flag this as
     * an error. If however, the ranges don't overlap, *then* we complain.
     */
    public InspectionResult isCompatibleWith(@NotNull RangeAllowedValues other) {
      return InspectionResult.uncertain();
    }
  }

  static class IntRangeConstraint extends RangeAllowedValues {
    final long from;
    final long to;

    public IntRangeConstraint(@NotNull PsiAnnotation annotation) {
      PsiAnnotationMemberValue fromValue = annotation.findDeclaredAttributeValue(ATTR_FROM);
      PsiAnnotationMemberValue toValue = annotation.findDeclaredAttributeValue(ATTR_TO);
      from = getLongValue(fromValue, Long.MIN_VALUE);
      to = getLongValue(toValue, Long.MAX_VALUE);
    }

    @Override
    public InspectionResult isValid(@NotNull PsiExpression argument) {
      Number literalValue = guessSize(argument);
      if (literalValue != null) {
        long value = literalValue.longValue();
        return value >= from && value <= to ? InspectionResult.valid() : InspectionResult.invalid(argument);
      }

      return InspectionResult.uncertain();
    }

    @NotNull
    @Override
    public String describe(@NotNull PsiExpression argument) {
      StringBuilder sb = new StringBuilder(20);
      if (to == Long.MAX_VALUE) {
        sb.append("Value must be \u2265 ");
        sb.append(Long.toString(from));
      }
      else if (from == Long.MIN_VALUE) {
        sb.append("Value must be \u2264 ");
        sb.append(Long.toString(to));
      }
      else {
        sb.append("Value must be \u2265 ");
        sb.append(Long.toString(from));
        sb.append(" and \u2264 ");
        sb.append(Long.toString(to));
      }
      Number actual = guessSize(argument);
      if (actual != null) {
        sb.append(" (was ").append(Integer.toString(actual.intValue())).append(')');
      }
      return sb.toString();
    }

    @Override
    public InspectionResult isCompatibleWith(@NotNull RangeAllowedValues other) {
      if (other instanceof IntRangeConstraint) {
        IntRangeConstraint otherRange = (IntRangeConstraint)other;
        return otherRange.from > to || otherRange.to < from ? InspectionResult.invalidWithoutNode() : InspectionResult.valid();
      } else if (other instanceof FloatRangeConstraint) {
        FloatRangeConstraint otherRange = (FloatRangeConstraint)other;
        return otherRange.from > to || otherRange.to < from ? InspectionResult.invalidWithoutNode() : InspectionResult.valid();
      }
      return InspectionResult.uncertain();
    }
  }

  static class FloatRangeConstraint extends RangeAllowedValues {
    final double from;
    final double to;
    final boolean fromInclusive;
    final boolean toInclusive;

    public FloatRangeConstraint(@NotNull PsiAnnotation annotation) {
      PsiAnnotationMemberValue fromValue = annotation.findDeclaredAttributeValue(ATTR_FROM);
      PsiAnnotationMemberValue toValue = annotation.findDeclaredAttributeValue(ATTR_TO);
      PsiAnnotationMemberValue fromInclusiveValue = annotation.findDeclaredAttributeValue(ATTR_FROM_INCLUSIVE);
      PsiAnnotationMemberValue toInclusiveValue = annotation.findDeclaredAttributeValue(ATTR_TO_INCLUSIVE);
      from = getDoubleValue(fromValue, Double.NEGATIVE_INFINITY);
      to = getDoubleValue(toValue, Double.POSITIVE_INFINITY);
      fromInclusive = getBooleanValue(fromInclusiveValue, true);
      toInclusive = getBooleanValue(toInclusiveValue, true);
    }

    @Override
    public InspectionResult isValid(@NotNull PsiExpression argument) {
      Number number = guessSize(argument);
      if (number != null) {
        double value = number.doubleValue();
        if (!((fromInclusive && value >= from || !fromInclusive && value > from) &&
              (toInclusive && value <= to || !toInclusive && value < to))) {
          return InspectionResult.invalid(argument);
        }
        return InspectionResult.valid();
      }
      return InspectionResult.uncertain();
    }

    @NotNull
    @Override
    public String describe(@NotNull PsiExpression argument) {
      StringBuilder sb = new StringBuilder(20);
      if (from != Double.NEGATIVE_INFINITY) {
        if (to != Double.POSITIVE_INFINITY) {
          sb.append("Value must be ");
          if (fromInclusive) {
            sb.append('\u2265'); // >= sign
          } else {
            sb.append('>');
          }
          sb.append(' ');
          sb.append(Double.toString(from));
          sb.append(" and ");
          if (toInclusive) {
            sb.append('\u2264'); // <= sign
          } else {
            sb.append('<');
          }
          sb.append(' ');
          sb.append(Double.toString(to));
        } else {
          sb.append("Value must be ");
          if (fromInclusive) {
            sb.append('\u2265'); // >= sign
          } else {
            sb.append('>');
          }
          sb.append(' ');
          sb.append(Double.toString(from));
        }
      } else if (to != Double.POSITIVE_INFINITY) {
        sb.append("Value must be ");
        if (toInclusive) {
          sb.append('\u2264'); // <= sign
        } else {
          sb.append('<');
        }
        sb.append(' ');
        sb.append(Double.toString(to));
      }
      Number actual = guessSize(argument);
      if (actual != null) {
        sb.append(" (was ");
        // Try to avoid going through an actual double which introduces
        // potential rounding ugliness (e.g. source can say "2.49f" and this is printed as "2.490000009536743")
        if (argument instanceof PsiLiteral) {
          PsiLiteral literal = (PsiLiteral)argument;
          sb.append(literal.getText());
        } else {
          sb.append(Double.toString(actual.doubleValue()));
        }
        sb.append(')');
      }
      return sb.toString();
    }

    @Override
    public InspectionResult isCompatibleWith(@NotNull RangeAllowedValues other) {
      if (other instanceof FloatRangeConstraint) {
        FloatRangeConstraint otherRange = (FloatRangeConstraint)other;
        return otherRange.from > to || otherRange.to < from ? InspectionResult.invalidWithoutNode() : InspectionResult.valid();
      } else if (other instanceof IntRangeConstraint) {
        IntRangeConstraint otherRange = (IntRangeConstraint)other;
        return otherRange.from > to || otherRange.to < from ? InspectionResult.invalidWithoutNode() : InspectionResult.valid();
      }
      return InspectionResult.uncertain();
    }
  }

  static class SizeConstraint extends RangeAllowedValues {
    final long exact;
    final long min;
    final long max;
    final long multiple;

    public SizeConstraint(@NotNull PsiAnnotation annotation) {
      PsiAnnotationMemberValue exactValue = annotation.findAttributeValue(ATTR_VALUE);
      PsiAnnotationMemberValue fromValue = annotation.findDeclaredAttributeValue(ATTR_MIN);
      PsiAnnotationMemberValue toValue = annotation.findDeclaredAttributeValue(ATTR_MAX);
      PsiAnnotationMemberValue multipleValue = annotation.findDeclaredAttributeValue(ATTR_MULTIPLE);
      exact = getLongValue(exactValue, -1);
      min = getLongValue(fromValue, Long.MIN_VALUE);
      max = getLongValue(toValue, Long.MAX_VALUE);
      multiple = getLongValue(multipleValue, 1);
    }

    @Override
    public InspectionResult isValid(@NotNull PsiExpression argument) {
      Number size = guessSize(argument);
      if (size == null) {
        return InspectionResult.uncertain();
      }
      int actual = size.intValue();
      if (exact != -1) {
        if (exact != actual) {
          return InspectionResult.invalid(argument);
        }
      } else if (actual < min || actual > max || actual % multiple != 0) {
        return InspectionResult.invalid(argument);
      }

      return InspectionResult.valid();
    }

    @Override
    protected Number guessSize(@NotNull PsiExpression argument) {
      if (argument instanceof PsiNewExpression) {
        PsiNewExpression pne = (PsiNewExpression)argument;
        PsiArrayInitializerExpression initializer = pne.getArrayInitializer();
        if (initializer != null) {
          return initializer.getInitializers().length;
        }
        PsiExpression[] dimensions = pne.getArrayDimensions();
        if (dimensions.length > 0) {
          PsiExpression dimension = dimensions[0];
          return super.guessSize(dimension);
        }
      } else if (argument instanceof PsiLiteral) {
        PsiLiteral literal = (PsiLiteral)argument;
        Object o = literal.getValue();
        if (o instanceof String) {
          return ((String)o).length();
        }
      } else if (argument instanceof PsiBinaryExpression) {
        Object v = JavaConstantExpressionEvaluator.computeConstantExpression(argument, false);
        if (v instanceof String) {
          return ((String)v).length();
        }
      } else if (argument instanceof PsiReferenceExpression) {
        PsiReferenceExpression ref = (PsiReferenceExpression)argument;
        PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiField) {
          PsiField field = (PsiField)resolved;
          PsiExpression initializer = field.getInitializer();
          if (initializer != null) {
            return guessSize(initializer);
          }
        } else if (resolved instanceof PsiLocalVariable) {
          PsiLocalVariable variable = (PsiLocalVariable) resolved;
          PsiExpression initializer = variable.getInitializer();
          if (initializer != null) {
            return guessSize(initializer);
          }
        }
      }
      return null;
    }

    @NotNull
    @Override
    public String describe(@NotNull PsiExpression argument) {
      StringBuilder sb = new StringBuilder(20);
      if (argument.getType() != null && argument.getType().getCanonicalText().equals(JAVA_LANG_STRING)) {
        sb.append("Length");
      } else {
        sb.append("Size");
      }
      sb.append(" must be");
      if (exact != -1) {
        sb.append(" exactly ");
        sb.append(Long.toString(exact));
        return sb.toString();
      }
      boolean continued = true;
      if (min != Long.MIN_VALUE && max != Long.MAX_VALUE) {
        sb.append(" at least ");
        sb.append(Long.toString(min));
        sb.append(" and at most ");
        sb.append(Long.toString(max));
      } else if (min != Long.MIN_VALUE) {
        sb.append(" at least ");
        sb.append(Long.toString(min));
      } else  if (max != Long.MAX_VALUE) {
        sb.append(" at most ");
        sb.append(Long.toString(max));
      } else {
        continued = false;
      }
      if (multiple != 1) {
        if (continued) {
          sb.append(" and");
        }
        sb.append(" a multiple of ");
        sb.append(Long.toString(multiple));
      }
      Number actual = guessSize(argument);
      if (actual != null) {
        sb.append(" (was ").append(Integer.toString(actual.intValue())).append(')');
      }
      return sb.toString();
    }

    @Override
    public InspectionResult isCompatibleWith(@NotNull RangeAllowedValues other) {
      if (other instanceof SizeConstraint) {
        SizeConstraint otherRange = (SizeConstraint)other;
        if ((exact != -1 || otherRange.exact != -1) && exact != otherRange.exact) {
          return InspectionResult.invalidWithoutNode();
        }
        return otherRange.min > max || otherRange.max < min ? InspectionResult.invalidWithoutNode() : InspectionResult.valid();
      }
      return InspectionResult.uncertain();
    }
  }

  static class IndirectPermission extends Constraints {
    public final String signature;
    @Nullable public PermissionFinder.Result result;

    public IndirectPermission(String signature) {
      this.signature = signature;
    }
  }

  @Nullable
  private static Constraints getAllowedValuesFromTypedef(@NotNull PsiType type,
                                                           @NotNull PsiAnnotation magic,
                                                           @NotNull PsiManager manager) {
    PsiAnnotationMemberValue[] allowedValues;
    final boolean canBeOred;

    // Extract the actual type of the declaration. For examples, for int[], extract the int
    if (type instanceof PsiEllipsisType) {
      type = ((PsiEllipsisType)type).getComponentType();
    } else if (type instanceof PsiArrayType) {
      type = ((PsiArrayType)type).getComponentType();
    }
    boolean isInt = TypeConversionUtil.getTypeRank(type) <= TypeConversionUtil.LONG_RANK;
    boolean isString = !isInt && type.equals(PsiType.getJavaLangString(manager, GlobalSearchScope.allScope(manager.getProject())));
    if (isInt || isString) {
      PsiAnnotationMemberValue intValues = magic.findAttributeValue(TYPE_DEF_VALUE_ATTRIBUTE);
      allowedValues = intValues instanceof PsiArrayInitializerMemberValue ? ((PsiArrayInitializerMemberValue)intValues).getInitializers() : PsiAnnotationMemberValue.EMPTY_ARRAY;

      if (isInt) {
        PsiAnnotationMemberValue orValue = magic.findAttributeValue(TYPE_DEF_FLAG_ATTRIBUTE);
        canBeOred = orValue instanceof PsiLiteral && Boolean.TRUE.equals(((PsiLiteral)orValue).getValue());
      } else {
        canBeOred = false;
      }
    } else {
      return null; //other types not supported
    }

    if (allowedValues.length != 0) {
      return new AllowedValues(allowedValues, canBeOred);
    }

    return null;
  }

  @Nullable
  public static ResourceType getResourceTypeFromAnnotation(@NotNull String qualifiedName) {
    String resourceTypeName =
      Character.toLowerCase(qualifiedName.charAt(SUPPORT_ANNOTATIONS_PREFIX.length())) +
      qualifiedName.substring(SUPPORT_ANNOTATIONS_PREFIX.length() + 1, qualifiedName.length() - RES_SUFFIX.length());
    return ResourceType.getEnum(resourceTypeName);
  }

  @Nullable
  private static Constraints merge(@Nullable Constraints head, @Nullable Constraints tail) {
    if (head != null) {
      if (tail != null) {
        head.next = tail;

        // The only valid combination of multiple constraints are @IntDef and @IntRange.
        // In this case, always arrange for the IntDef constraint to be processed first
        if (tail instanceof AllowedValues) {
          head.next = tail.next;
          tail.next = head;
          head = tail;
        }

        return head;
      } else {
        return head;
      }
    }
    return tail;
  }

  @Nullable
  public static Constraints getAllowedValues(@NotNull PsiModifierListOwner element, @Nullable PsiType type, @Nullable Set<PsiClass> visited) {
    PsiAnnotation[] annotations = getAllAnnotations(element);
    PsiManager manager = element.getManager();
    List<ResourceType> resourceTypes = null;
    Constraints constraint = null;
    for (PsiAnnotation annotation : annotations) {
      String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName == null) {
        continue;
      }

      if (qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX) || qualifiedName.startsWith("test.pkg.")) {
        if (INT_DEF_ANNOTATION.equals(qualifiedName) || STRING_DEF_ANNOTATION.equals(qualifiedName)) {
          if (type != null) {
            constraint = merge(getAllowedValuesFromTypedef(type, annotation, manager), constraint);
          }
        }
        else if (INT_RANGE_ANNOTATION.equals(qualifiedName) || "test.pkg.IntRange".equals(qualifiedName)) {
          constraint = merge(new IntRangeConstraint(annotation), constraint);
        }
        else if (FLOAT_RANGE_ANNOTATION.equals(qualifiedName)) {
          constraint = merge(new FloatRangeConstraint(annotation), constraint);
        }
        else if (SIZE_ANNOTATION.equals(qualifiedName)) {
          constraint = merge(new SizeConstraint(annotation), constraint);
        }
        else if (COLOR_INT_ANNOTATION.equals(qualifiedName)) {
          constraint = merge(new ResourceTypeAllowedValues(Collections.<ResourceType>emptyList()), constraint);
        }
        else if (qualifiedName.startsWith(PERMISSION_ANNOTATION)) {
          // PERMISSION_ANNOTATION, PERMISSION_ANNOTATION_READ, PERMISSION_ANNOTATION_WRITE
          // When specified on a parameter, that indicates that we're dealing with
          // a permission requirement on this *method* which depends on the value
          // supplied by this parameter
          constraint = merge(new IndirectPermission(qualifiedName), constraint);
        }
        else if (qualifiedName.endsWith(RES_SUFFIX)) {
          ResourceType resourceType = getResourceTypeFromAnnotation(qualifiedName);
          if (resourceType != null) {
            if (resourceTypes == null) {
              resourceTypes = Lists.newArrayList();
            }
            resourceTypes.add(resourceType);
          }
        }
      }

      if (constraint == null) {
        PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
        PsiElement resolved = ref == null ? null : ref.resolve();
        if (!(resolved instanceof PsiClass) || !((PsiClass)resolved).isAnnotationType()) continue;
        PsiClass aClass = (PsiClass)resolved;
        if (visited == null) visited = new THashSet<PsiClass>();
        if (!visited.add(aClass)) continue;
        constraint = getAllowedValues(aClass, type, visited);
      }
    }

    if (resourceTypes != null) {
      constraint = merge(new ResourceTypeAllowedValues(resourceTypes), constraint);
    }

    return constraint;
  }

  @NotNull
  public static PsiAnnotation[] getAllAnnotations(@NotNull final PsiModifierListOwner element) {
    return CachedValuesManager.getCachedValue(element, new CachedValueProvider<PsiAnnotation[]>() {
      @Nullable
      @Override
      public Result<PsiAnnotation[]> compute() {
        return Result.create(AnnotationUtil.getAllAnnotations(element, true, null), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  /**
   * Method that only gets the class annotations and not the ones in any parents.
   */
  @NotNull
  public static PsiAnnotation[] getLocalAnnotations(@NotNull final PsiModifierListOwner element) {
    // This code is almost identical to getAllAnnotations, just changing the boolean value. The reason to have two separate call is that
    // CachedValuesManager uses the passed CachedValueProvider class as key for the caching and we want two separate caches.
    return CachedValuesManager.getCachedValue(element, new CachedValueProvider<PsiAnnotation[]>() {
      @Nullable
      @Override
      public Result<PsiAnnotation[]> compute() {
        return Result.create(AnnotationUtil.getAllAnnotations(element, false, null), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  @Nullable
  private static PsiType getType(@NotNull PsiModifierListOwner element) {
    return element instanceof PsiVariable ? ((PsiVariable)element).getType() : element instanceof PsiMethod ? ((PsiMethod)element).getReturnType() : null;
  }

  private static void registerProblem(@NotNull PsiExpression argument, @NotNull Constraints constraint,
                                      @NotNull ProblemsHolder holder) {
    if (constraint instanceof IndirectPermission) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(argument, PsiMethodCallExpression.class);
      IndirectPermission ip = (IndirectPermission)constraint;
      if (call != null && ip.result != null) {
        checkPermissionRequirement(call, holder, null, ip.result, ip.result.requirement);
      }
    }
    else if (constraint instanceof ResourceTypeAllowedValues) {
      Issue issue = RESOURCE_TYPE;
      List<ResourceType> types = ((ResourceTypeAllowedValues)constraint).types;
      String message;
      if (types.isEmpty()) {
        // Keep in sync with guessLintIssue
        message = String.format("Should pass resolved color instead of resource id here: `getResources().getColor(%1$s)`",
                                argument.getText());
        issue = COLOR_USAGE;
      }
      else if (types.size() == 1) {
        // Keep in sync with guessLintIssue
        message = "Expected resource of type " + types.get(0);
      }
      else {
        // Keep in sync with guessLintIssue
        message = "Expected resource type to be one of " + Joiner.on(", ").join(types);
      }
      registerProblem(holder, issue, argument, message);
    }
    else if (constraint instanceof RangeAllowedValues) {
      String message = ((RangeAllowedValues)constraint).describe(argument);
      registerProblem(holder, RANGE, argument, message);
    }
    else {
      assert constraint instanceof AllowedValues;
      AllowedValues typedef = (AllowedValues)constraint;
      Function<PsiAnnotationMemberValue, String> formatter = new Function<PsiAnnotationMemberValue, String>() {
        @Override
        public String fun(PsiAnnotationMemberValue value) {
          if (value instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression)value).resolve();
            if (resolved instanceof PsiVariable) {
              return PsiFormatUtil.formatVariable((PsiVariable)resolved, SHOW_NAME | SHOW_CONTAINING_CLASS,
                                                  PsiSubstitutor.EMPTY);
            }
          }
          return value.getText();
        }
      };
      String values = StringUtil.join(typedef.values, formatter, ", ");
      // Keep in sync with guessLintIssue
      String message;
      if (typedef.canBeOred) {
        message = "Must be one or more of: " + values;
      }
      else {
        message = "Must be one of: " + values;
      }

      // @IntDef and @IntRange can be combined
      if (constraint.next instanceof RangeAllowedValues) {
        message += " or " + StringUtil.decapitalize(((RangeAllowedValues)constraint.next).describe(argument));
      }

      registerProblem(holder, TYPE_DEF, argument, message);
    }
  }

  @NotNull
  private static InspectionResult isAllowed(@NotNull final PsiElement scope,
                                            @NotNull final PsiExpression argument,
                                            @NotNull final Constraints constraints,
                                            @NotNull final PsiManager manager,
                                            @Nullable final Set<PsiExpression> visited) {
    if (constraints instanceof ResourceTypeAllowedValues) {
      return isResourceTypeAllowed(scope, argument, (ResourceTypeAllowedValues)constraints, manager, visited);
    } else if (constraints instanceof RangeAllowedValues) {
      return isInRange(scope, argument, (RangeAllowedValues)constraints, manager, visited);
    } else if (constraints instanceof IndirectPermission) {
      return isGrantedPermission(argument, (IndirectPermission)constraints);
    } else {
      assert constraints instanceof AllowedValues;
      final AllowedValues a = (AllowedValues)constraints;

      InspectionResult result = isGoodExpression(argument, a, scope, manager, visited);
      if (result.isValid()) {
        return result;
      }

      InspectionResult flowResult = processValuesFlownTo(argument, scope, manager, new Function<PsiExpression, InspectionResult>() {
        @Override
        public InspectionResult fun(PsiExpression expression) {
          return isGoodExpression(expression, a, scope, manager, visited);
        }
      });

      if (flowResult.isUncertain()) {
        return result;
      }

      return flowResult;
    }
  }


  /**
   * Verifies that all elements in an array initializer expression are within the allowed values
   */
  private static InspectionResult checkArrayInitializerExpression(@NotNull PsiArrayInitializerExpression e,
                                                                  @NotNull AllowedValues allowedValues,
                                                                  @NotNull PsiElement scope,
                                                                  @NotNull PsiManager manager,
                                                                  @Nullable Set<PsiExpression> visited) {
    for (PsiExpression arrayValueExpression : e.getInitializers()) {
      // We use the arrayValueExpression as the error node to report exactly which value caused the problem
      InspectionResult result = isGoodExpression(arrayValueExpression, allowedValues, scope, manager, visited).useErrorNode(arrayValueExpression);

      if (result.isInvalid()) {
        return result;
      }
    }

    return InspectionResult.valid();
  }

  private static InspectionResult isGoodExpression(@NotNull PsiExpression e,
                                                   @NotNull AllowedValues allowedValues,
                                                   @NotNull PsiElement scope,
                                                   @NotNull PsiManager manager,
                                                   @Nullable Set<PsiExpression> visited) {
    PsiExpression expression = PsiUtil.deparenthesizeExpression(e);
    if (expression == null) return InspectionResult.valid();
    if (visited == null) visited = new THashSet<PsiExpression>();
    if (!visited.add(expression)) return InspectionResult.valid();
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      boolean thenAllowed = thenExpression == null || isAllowed(scope, thenExpression, allowedValues, manager, visited).isValid();
      if (!thenAllowed) return InspectionResult.invalid(thenExpression);
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      return (elseExpression == null || isAllowed(scope, elseExpression, allowedValues, manager, visited).isValid()) ? InspectionResult
        .valid() : InspectionResult.invalid(elseExpression);
    }
    else if (expression instanceof PsiNewExpression) {
      PsiArrayInitializerExpression arrayInitializerExpression = ((PsiNewExpression)expression).getArrayInitializer();
      if (arrayInitializerExpression != null) {
        return checkArrayInitializerExpression(arrayInitializerExpression, allowedValues, scope, manager, visited);
      }
    }
    else if (expression instanceof PsiArrayInitializerExpression) {
      return checkArrayInitializerExpression((PsiArrayInitializerExpression)expression, allowedValues, scope, manager, visited);
    }

    if (isOneOf(expression, allowedValues, manager)) {
      return InspectionResult.valid();
    }

    if (allowedValues.canBeOred) {
      PsiExpression zero = getLiteralExpression(expression, manager, "0");
      if (same(expression, zero, manager)) return InspectionResult.valid();
      PsiExpression one = getLiteralExpression(expression, manager, "-1");
      if (same(expression, one, manager)) return InspectionResult.valid();
      if (expression instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)expression).getOperationTokenType();
        if (JavaTokenType.OR.equals(tokenType) || JavaTokenType.AND.equals(tokenType) || JavaTokenType.PLUS.equals(tokenType)) {
          for (PsiExpression operand : ((PsiPolyadicExpression)expression).getOperands()) {
            if (isAllowed(scope, operand, allowedValues, manager, visited).isInvalid()) return InspectionResult.invalid(operand);
          }
          return InspectionResult.valid();
        }
      }
      if (expression instanceof PsiPrefixExpression &&
          JavaTokenType.TILDE.equals(((PsiPrefixExpression)expression).getOperationTokenType())) {
        PsiExpression operand = ((PsiPrefixExpression)expression).getOperand();
        return (operand == null || isAllowed(scope, operand, allowedValues, manager, visited).isValid())
               ? InspectionResult.valid()
               : InspectionResult.invalid(operand);
      }
    }

    PsiElement resolved = null;
    if (expression instanceof PsiReference) {
      resolved = ((PsiReference)expression).resolve();
    }
    else if (expression instanceof PsiCallExpression) {
      resolved = ((PsiCallExpression)expression).resolveMethod();
    }

    Constraints allowedForRef;
    if (resolved instanceof PsiModifierListOwner &&
        (allowedForRef = getAllowedValues((PsiModifierListOwner)resolved, getType((PsiModifierListOwner)resolved), null)) != null &&
        allowedForRef.isSubsetOf(allowedValues, manager)) {
      return InspectionResult.valid();
    }

    //noinspection ConstantConditions
    return PsiType.NULL.equals(expression.getType()) ? InspectionResult.valid() : InspectionResult.invalid(expression);
  }


  private static long getLongValue(@Nullable PsiElement value, long defaultValue) {
    if (value == null) {
      return defaultValue;
    } else if (value instanceof PsiLiteral) {
      Object o = ((PsiLiteral)value).getValue();
      if (o instanceof Number) {
        return ((Number)o).longValue();
      }
    } else if (value instanceof PsiPrefixExpression) {
      // negative number
      PsiPrefixExpression exp = (PsiPrefixExpression)value;
      if (exp.getOperationTokenType() == JavaTokenType.MINUS) {
        PsiExpression operand = exp.getOperand();
        if (operand instanceof PsiLiteral) {
          Object o = ((PsiLiteral)operand).getValue();
          if (o instanceof Number) {
            return -((Number)o).longValue();
          }
        }
      }
    } else if (value instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression)value).resolve();
      if (resolved instanceof PsiField) {
        return getLongValue(((PsiField)resolved).getInitializer(), defaultValue);
      }
    } // TODO: Allow inlined arithmetic here? If so look for operator nodes

    return defaultValue;
  }

  private static double getDoubleValue(@Nullable PsiAnnotationMemberValue value, double defaultValue) {
    if (value == null) {
      return defaultValue;
    } else if (value instanceof PsiLiteral) {
      Object o = ((PsiLiteral)value).getValue();
      if (o instanceof Number) {
        return ((Number)o).doubleValue();
      }
    } else if (value instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression)value).resolve();
      if (resolved instanceof PsiField) {
        return getDoubleValue(((PsiField)resolved).getInitializer(), defaultValue);
      }
    } // TODO: Allow inlined arithmetic here? If so look for operator nodes

    return defaultValue;
  }

  private static boolean getBooleanValue(@Nullable PsiAnnotationMemberValue value, boolean defaultValue) {
    if (value == null) {
      return defaultValue;
    } else if (value instanceof PsiLiteral) {
      Object o = ((PsiLiteral)value).getValue();
      if (o instanceof Boolean) {
        return ((Boolean)o).booleanValue();
      }
    }

    return defaultValue;
  }

  @NotNull
  private static InspectionResult isResourceTypeAllowed(@NotNull final PsiElement scope,
                                                        @NotNull final PsiExpression argument,
                                                        @NotNull final ResourceTypeAllowedValues allowedValues,
                                                        @NotNull final PsiManager manager,
                                                        @Nullable final Set<PsiExpression> visited) {
    InspectionResult result = isValidResourceTypeExpression(argument, allowedValues, scope, manager, visited);
    if (!result.isUncertain()) {
      return result;
    }

    return processValuesFlownTo(argument, scope, manager, new Function<PsiExpression, InspectionResult>() {
      @Override
      public InspectionResult fun(PsiExpression expression) {
        return isValidResourceTypeExpression(expression, allowedValues, scope, manager, visited);
      }
    });
  }

  @NotNull
  private static InspectionResult isValidResourceTypeExpression(@NotNull PsiExpression e,
                                                                @NotNull ResourceTypeAllowedValues allowedValues,
                                                                @NotNull PsiElement scope,
                                                                @NotNull PsiManager manager,
                                                                @Nullable Set<PsiExpression> visited) {
    PsiExpression expression = PsiUtil.deparenthesizeExpression(e);
    if (expression == null) return InspectionResult.valid();
    if (visited == null) visited = new THashSet<PsiExpression>();
    if (!visited.add(expression)) return InspectionResult.valid();
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      boolean thenAllowed = thenExpression == null || isAllowed(scope, thenExpression, allowedValues, manager, visited).isValid();
      if (!thenAllowed) return InspectionResult.invalid(expression);
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      return (elseExpression == null || isAllowed(scope, elseExpression, allowedValues, manager, visited).isValid()) ? InspectionResult
        .valid() : InspectionResult.invalid(expression);
    }

    // Resource type check
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpression = (PsiReferenceExpression)expression;
      PsiExpression qualifierExpression = refExpression.getQualifierExpression();
      if (qualifierExpression instanceof PsiReferenceExpression) {
        PsiReferenceExpression typeDef = (PsiReferenceExpression)qualifierExpression;
        PsiExpression r = typeDef.getQualifierExpression();
        if (r instanceof PsiReferenceExpression) {
          if (R_CLASS.equals(((PsiReferenceExpression)r).getReferenceName())) {
            String typeName = typeDef.getReferenceName();
            if (typeName != null) {
              return allowedValues.isTypeAllowed(typeName) ? InspectionResult.valid() : InspectionResult.invalid(expression);
            }
          }
        }
      }
    }
    else if (expression instanceof PsiLiteral) {
      if (expression instanceof PsiLiteralExpression) {
        PsiElement parent = expression.getParent();
        if (parent instanceof PsiField) {
          parent = parent.getParent();
          if (parent instanceof PsiClass) {
            PsiElement outerMost = parent.getParent();
            if (outerMost instanceof PsiClass && R_CLASS.equals(((PsiClass)outerMost).getName())) {
              PsiClass typeClass = (PsiClass)parent;
              String typeClassName = typeClass.getName();
              return typeClassName != null && allowedValues.isTypeAllowed(typeClassName)
                     ? InspectionResult.valid()
                     : InspectionResult.invalid(expression);
            }
          }
        }

        if (allowedValues.types.isEmpty() && PsiType.INT.equals(expression.getType())) {
          // Passing literal integer to a color
          return InspectionResult.valid();
        }
      }

      // Allow a literal '0' or '-1' as the resource type; this is sometimes used to communicate that
      // no id was specified (the support library does this in a few places for example)
      Object value = ((PsiLiteral)expression).getValue();
      if (value instanceof Integer) {
        return ((Integer)value).intValue() == 0 ? InspectionResult.valid() : InspectionResult.invalid(expression);
      }
    } else if (expression instanceof PsiPrefixExpression) {
      // Allow a literal '-1' as the resource type; this is sometimes used to communicate that
      // no id was specified
      PsiPrefixExpression ppe = (PsiPrefixExpression)expression;
      if (ppe.getOperationTokenType() == JavaTokenType.MINUS &&
          ppe.getOperand() instanceof PsiLiteral) {
        Object value = ((PsiLiteral)ppe.getOperand()).getValue();
        if (value instanceof Integer) {
          return ((Integer)value).intValue() == 1 ? InspectionResult.valid() : InspectionResult.invalid(expression);
        }
      }
    }

    PsiElement resolved = null;
    if (expression instanceof PsiReference) {
      resolved = ((PsiReference)expression).resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField)resolved;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass != null) {
          PsiClass r = containingClass.getContainingClass();
          if (r != null && R_CLASS.equals(r.getName())) {
            ResourceType type = ResourceType.getEnum(containingClass.getName());
            if (type != null) {
              if (allowedValues.isTypeAllowed(type)) {
                return InspectionResult.valid();
              }
              return InspectionResult.invalid(expression);
            }
          }
        }
      }
    }
    else if (expression instanceof PsiCallExpression) {
      resolved = ((PsiCallExpression)expression).resolveMethod();
    }

    Constraints allowedForRef;

    if (resolved instanceof PsiModifierListOwner) {
      PsiType type = getType((PsiModifierListOwner)resolved);
      allowedForRef = getAllowedValues((PsiModifierListOwner)resolved, type, null);
      if (allowedForRef instanceof ResourceTypeAllowedValues) {
        // Happy if *any* of the resource types on the annotation matches any of the
        // annotations allowed for this API
        return allowedValues.isCompatibleWith((ResourceTypeAllowedValues)allowedForRef)
               ? InspectionResult.valid()
               : InspectionResult.invalid(expression);
      }
    }

    return InspectionResult.uncertain();
  }

  @NotNull
  private static InspectionResult isInRange(@NotNull final PsiElement scope,
                                            @NotNull final PsiExpression argument,
                                            @NotNull final RangeAllowedValues allowedValues,
                                            @NotNull final PsiManager manager,
                                            @Nullable final Set<PsiExpression> visited) {
    InspectionResult result = isValidRangeExpression(argument, argument, allowedValues, scope, manager, visited);
    if (!result.isUncertain()) {
      return result;
    }

    return processValuesFlownTo(argument, scope, manager, new Function<PsiExpression, InspectionResult>() {
      @Override
      public InspectionResult fun(PsiExpression expression) {
        return isValidRangeExpression(expression, argument, allowedValues, scope, manager, visited);
      }
    });
  }

  private static boolean comparesReference(@NotNull PsiElement reference, @Nullable PsiExpression expression) {
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binary = (PsiBinaryExpression)expression;
      IElementType tokenType = binary.getOperationTokenType();
      if (tokenType == JavaTokenType.GE ||
          tokenType == JavaTokenType.GT ||
          tokenType == JavaTokenType.LT ||
          tokenType == JavaTokenType.LE ||
          tokenType == JavaTokenType.EQ) {
        PsiExpression lOperand = binary.getLOperand();
        PsiExpression rOperand = binary.getROperand();
        if (lOperand instanceof PsiReferenceExpression) {
          return reference.equals(((PsiReferenceExpression)lOperand).resolve());
        }
        if (rOperand instanceof PsiReferenceExpression) {
          return reference.equals(((PsiReferenceExpression)rOperand).resolve());
        }
      } else if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
        return comparesReference(reference, binary.getLOperand()) || comparesReference(reference, binary.getROperand());
      }
    }

    return false;
  }

  @NotNull
  private static InspectionResult isValidRangeExpression(@NotNull PsiExpression e,
                                                         @Nullable PsiExpression argument,
                                                         @NotNull RangeAllowedValues allowedValues,
                                                         @NotNull PsiElement scope,
                                                         @NotNull PsiManager manager,
                                                         @Nullable Set<PsiExpression> visited) {
    PsiExpression expression = PsiUtil.deparenthesizeExpression(e);
    if (expression == null) return InspectionResult.valid();
    if (visited == null) visited = new THashSet<PsiExpression>();
    if (!visited.add(expression)) return InspectionResult.valid();
    if (expression instanceof PsiConditionalExpression) {
      PsiExpression thenExpression = ((PsiConditionalExpression)expression).getThenExpression();
      if (thenExpression == null || isAllowed(scope, thenExpression, allowedValues, manager, visited).isInvalid()) {
        return InspectionResult.invalid(expression);
      }
      PsiExpression elseExpression = ((PsiConditionalExpression)expression).getElseExpression();
      return (elseExpression == null || isAllowed(scope, elseExpression, allowedValues, manager, visited).isValid()) ? InspectionResult
        .valid() : InspectionResult.invalid(expression);
    }

    if (e != argument && argument instanceof PsiReferenceExpression) {
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(argument, PsiIfStatement.class, true);
      if (ifStatement != null) {
        PsiElement resolved = ((PsiReferenceExpression)argument).resolve();
        if (resolved != null) {
          PsiExpression condition = ifStatement.getCondition();
          if (comparesReference(resolved, condition)) {
            return InspectionResult.uncertain();
          }
        }
      }
    }

    // Range check
    InspectionResult fieldValid = allowedValues.isValid(expression);
    if (!fieldValid.isUncertain()) {
      return fieldValid;
    }

    PsiElement resolved = null;
    if (expression instanceof PsiReference) {
      resolved = ((PsiReference)expression).resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField)resolved;
        if (field.getInitializer() != null) {
          fieldValid = allowedValues.isValid(field.getInitializer());
          if (!fieldValid.isUncertain()) {
            if (fieldValid.isInvalid() && argument != null) {
              PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(argument, PsiIfStatement.class, true);
              if (ifStatement != null) {
                PsiExpression condition = ifStatement.getCondition();
                if (comparesReference(resolved, condition)) {
                  return InspectionResult.uncertain();
                }
              }
            }
            return fieldValid;
          }
        }
      }
    }
    else if (expression instanceof PsiNewExpression || expression instanceof PsiArrayInitializerExpression) {
      PsiArrayInitializerExpression arrayInitializerExpression = expression instanceof PsiNewExpression
                                                                 ? ((PsiNewExpression)expression).getArrayInitializer()
                                                                 : (PsiArrayInitializerExpression)expression;
      if (arrayInitializerExpression != null) {
        for (PsiExpression initializer : arrayInitializerExpression.getInitializers()) {
          if (isAllowed(scope, initializer, allowedValues, manager, visited).isInvalid()) {
            return InspectionResult.invalid(expression);
          }
        }
      }
    }
    else if (expression instanceof PsiCallExpression) {
      resolved = ((PsiCallExpression)expression).resolveMethod();
    }

    Constraints allowedForRef;

    if (resolved instanceof PsiModifierListOwner) {
      PsiType type = getType((PsiModifierListOwner)resolved);
      allowedForRef = getAllowedValues((PsiModifierListOwner)resolved, type, null);
      if (allowedForRef instanceof RangeAllowedValues) {
        return allowedValues.isCompatibleWith((RangeAllowedValues)allowedForRef).useErrorNode(expression);
      }
    }

    return InspectionResult.uncertain();
  }

  @NotNull
  private static InspectionResult isGrantedPermission(@NotNull PsiExpression argument, @NotNull IndirectPermission permission) {
    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(argument, PsiMethodCallExpression.class);
    if (call != null) {
      String signature = permission.signature;
      PermissionFinder.Operation operation;
      if (signature.equals(PERMISSION_ANNOTATION_READ)) {
        operation = READ;
      }
      else if (signature.equals(PERMISSION_ANNOTATION_WRITE)) {
        operation = WRITE;
      }
      else {
        PsiType type = argument.getType();
        if (type == null || !CLASS_INTENT.equals(type.getCanonicalText())) {
          return InspectionResult.valid();
        }
        operation = ACTION;
      }
      permission.result = search(argument, operation);
      if (permission.result != null) {
        // Finish check in registerProblem
        return InspectionResult.invalid(argument);
      }
    }
    // No unsatisfied permission requirement found
    return InspectionResult.valid();
  }

  // Would be nice to reuse the MagicConstantInspection's cache for this, but it's not accessible
  private static final Key<Map<String, PsiExpression>> LITERAL_EXPRESSION_CACHE = Key.create("TYPE_DEF_LITERAL_EXPRESSION");
  private static PsiExpression getLiteralExpression(@NotNull PsiExpression context, @NotNull PsiManager manager, @NotNull String text) {
    Map<String, PsiExpression> cache = LITERAL_EXPRESSION_CACHE.get(manager);
    if (cache == null) {
      cache = ContainerUtil.createConcurrentSoftValueMap();
      cache = manager.putUserDataIfAbsent(LITERAL_EXPRESSION_CACHE, cache);
    }
    PsiExpression expression = cache.get(text);
    if (expression == null) {
      expression = JavaPsiFacade.getElementFactory(manager.getProject()).createExpressionFromText(text, context);
      cache.put(text, expression);
    }
    return expression;
  }

  private static boolean isOneOf(@NotNull PsiExpression expression, @NotNull AllowedValues allowedValues, @NotNull PsiManager manager) {
    for (PsiAnnotationMemberValue allowedValue : allowedValues.values) {
      if (same(allowedValue, expression, manager)) return true;
    }
    return false;
  }

  private static boolean same(@Nullable PsiElement e1, @Nullable PsiElement e2, @NotNull PsiManager manager) {
    if (e1 instanceof PsiLiteralExpression && e2 instanceof PsiLiteralExpression) {
      return Comparing.equal(((PsiLiteralExpression)e1).getValue(), ((PsiLiteralExpression)e2).getValue());
    }
    if (e1 instanceof PsiPrefixExpression && e2 instanceof PsiPrefixExpression && ((PsiPrefixExpression)e1).getOperationTokenType() == ((PsiPrefixExpression)e2).getOperationTokenType()) {
      return same(((PsiPrefixExpression)e1).getOperand(), ((PsiPrefixExpression)e2).getOperand(), manager);
    }
    if (e1 instanceof PsiReference && e2 instanceof PsiReference) {
      e1 = ((PsiReference)e1).resolve();
      e2 = ((PsiReference)e2).resolve();
    }
    return manager.areElementsEquivalent(e2, e1);
  }

  @NotNull
  private static InspectionResult processValuesFlownTo(@NotNull final PsiExpression argument,
                                                       @NotNull PsiElement scope,
                                                       @NotNull PsiManager manager,
                                                       @NotNull final Function<PsiExpression, InspectionResult> processor) {
    SliceAnalysisParams params = new SliceAnalysisParams();
    params.dataFlowToThis = true;
    params.scope = new AnalysisScope(new LocalSearchScope(scope), manager.getProject());

    SliceLanguageSupportProvider languageSlicing = LanguageSlicing.getProvider(argument);
    assert languageSlicing != null;
    SliceRootNode rootNode = new SliceRootNode(manager.getProject(), new DuplicateMap(),
                                               languageSlicing.createRootUsage(argument, params));

    @SuppressWarnings("unchecked")
    Collection<? extends AbstractTreeNode> children = rootNode.getChildren().iterator().next().getChildren();
    for (AbstractTreeNode child : children) {
      SliceUsage usage = (SliceUsage)child.getValue();
      if (usage == null) {
        continue;
      }
      PsiElement element = usage.getElement();
      if (element instanceof PsiExpression) {
        // If we report an error, use the current expression being evaluated. This will mark the error in the argument as opposed to using
        // the original field or variable declaration.
        InspectionResult result = processor.fun((PsiExpression)element).useErrorNode(argument);
        if (result.isInvalid()) {
          return result;
        }
      }
    }

    return !children.isEmpty() ? InspectionResult.valid() : InspectionResult.uncertain();
  }

  // Based on ExceptionUtil#isHandled and various methods it calls, but unlike that method, it checks to
  // see whether you are catching or throwing the *specific* exceptionType, not some type assignable from it.
  // (The code was also simplified quite a bit from the ExceptionUtil method, since we don't have to handle
  // many of the same cases (this method only supports exceptions thrown from methods in a normal Java method.)
  private static boolean isHandled(@Nullable PsiElement element, @NotNull PsiClassType exceptionType, PsiElement topElement) {
    if (element == null || element.getParent() == topElement || element.getParent() == null) return false;

    final PsiElement parent = element.getParent();

    if (parent instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)parent;
      return isHandledByMethodThrowsClause(method, exceptionType, PsiSubstitutor.EMPTY);
    }
    else if (parent instanceof PsiClass) {
      return parent instanceof PsiAnonymousClass && isHandled(parent, exceptionType, topElement);
    }
    else if (parent instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)parent;
      if (tryStatement.getTryBlock() == element && isCaught(tryStatement, exceptionType)) {
        return true;
      }
      if (tryStatement.getResourceList() == element && isCaught(tryStatement, exceptionType)) {
        return true;
      }
      PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
      if (element instanceof PsiCatchSection && finallyBlock != null && blockCompletesAbruptly(finallyBlock)) {
        // exception swallowed
        return true;
      }
    }
    else if (parent instanceof PsiFile) {
      return false;
    }
    return isHandled(parent, exceptionType, topElement);
  }

  private static boolean isHandledByMethodThrowsClause(@NotNull PsiMethod method,
                                                       @NotNull PsiClassType exceptionType,
                                                       PsiSubstitutor substitutor) {
    final PsiClassType[] referencedTypes = method.getThrowsList().getReferencedTypes();
    return isHandledBy(exceptionType, referencedTypes, substitutor);
  }

  public static boolean isHandledBy(@NotNull PsiClassType exceptionType,
                                    @NotNull PsiClassType[] referencedTypes,
                                    PsiSubstitutor substitutor) {
    for (PsiClassType classType : referencedTypes) {
      PsiType psiType = substitutor.substitute(classType);
      // This is where we diverge from ExceptionUtil:
      //if (psiType != null && psiType.isAssignableFrom(exceptionType)) return true;
      if (psiType != null && psiType.equals(exceptionType)) return true;
    }
    return false;
  }

  private static boolean isCaught(@NotNull PsiTryStatement tryStatement, @NotNull PsiClassType exceptionType) {
    // if finally block completes abruptly, exception gets lost
    PsiCodeBlock finallyBlock = tryStatement.getFinallyBlock();
    if (finallyBlock != null && blockCompletesAbruptly(finallyBlock)) return true;

    final PsiParameter[] catchBlockParameters = tryStatement.getCatchBlockParameters();
    for (PsiParameter parameter : catchBlockParameters) {
      PsiType paramType = parameter.getType();
      // This is where we diverge from ExceptionUtil:
      //if (paramType.isAssignableFrom(exceptionType)) return true;
      if (paramType instanceof PsiDisjunctionType) {
        for (PsiType multiCatchType : ((PsiDisjunctionType)paramType).getDisjunctions()) {
          if (multiCatchType.equals(exceptionType)) return true;
        }
      }
      else if (paramType.equals(exceptionType)) return true;
    }

    return false;
  }

  private static boolean blockCompletesAbruptly(@NotNull final PsiCodeBlock finallyBlock) {
    try {
      ControlFlow flow = ControlFlowFactory
        .getInstance(finallyBlock.getProject()).getControlFlow(finallyBlock, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);
      int completionReasons = ControlFlowUtil.getCompletionReasons(flow, 0, flow.getSize());
      if ((completionReasons & ControlFlowUtil.NORMAL_COMPLETION_REASON) == 0) return true;
    }
    catch (AnalysisCanceledException e) {
      return true;
    }
    return false;
  }

  private static void registerProblem(@NotNull ProblemsHolder holder,
                                      @NotNull Issue lintIssue,
                                      @NotNull PsiElement psiElement,
                                      @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                                      @Nullable LocalQuickFix... fixes) {
    // Look for aliases
    if (SuppressManager.getInstance().isSuppressedFor(psiElement, lintIssue.getId())) {
      return;
    }

    assert guessLintIssue(message) != null : message;

    holder.registerProblem(psiElement, message, fixes);
  }

  /** Given an error message produced by this inspection, guess the corresponding
   * lint issue id in {@link com.android.tools.lint.checks.SupportAnnotationDetector}
   *
   * @param message the error message
   * @return the corresponding lint issue, if recognized
   */
  @Nullable
  private static Issue guessLintIssue(@NotNull String message) {
    if (message.startsWith("Should pass resolved color ")) {
      return COLOR_USAGE;
    } else if (message.startsWith("The result of ")) {
      return CHECK_RESULT;
    } else if (message.startsWith("Call requires permission ") || message.startsWith("Missing permissions ")) {
      return MISSING_PERMISSION;
    } else if (message.startsWith("Value must ") || message.startsWith("Length ") || message.startsWith("Size ")) {
      return RANGE;
    } else if (message.startsWith("Must be one ")) {
      return TYPE_DEF;
    } else if (message.startsWith("Expected resource ")) {
      return RESOURCE_TYPE;
    } else if (message.contains("must be called from ")) {
      return THREAD;
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalArgumentException(message);
    }

    return null;
  }

  @NotNull
  @Override
  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    // The suppress actions are hardcoded to use the suppress id "ResourceType",
    // e.g. the inspection id for the whole ResourceTypeInspection.
    // However, we'd really like to have more specific resource id's instead;
    // in particular, the same ones used by the command line version of the same
    // check in lint (in SupportAnnotationDetector).
    //
    // We can't change the id used by the SuppressQuickFix, and we can't just
    // replace the SuppressQuickFix array passed to convertBatchToSuppressIntentionActions
    // because the SuppressQuickFix interface ony gives us the element, not the
    // warning error message (and we need to use the error message to figure out
    // the corresponding lint issue type for an inspection message).
    //
    // Therefore, we wrap the each SuppressIntentionAction with a delegator,
    // and when the user actually invokes the SuppressIntentionAction, we
    // look up the current message, and if we can find the corresponding
    // lint issue we create a new SuppressQuickFix (with the right id),
    // wrap it, and invoke that action instead.

    String shortName = getShortName();
    HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    SuppressQuickFix[] actions = SuppressManager.getInstance().createBatchSuppressActions(key);
    SuppressIntentionAction[] suppressActions = SuppressIntentionActionFromFix.convertBatchToSuppressIntentionActions(actions);
    if (suppressActions.length == actions.length) {
      int index = 0;
      List<SuppressIntentionAction> replaced = Lists.newArrayListWithExpectedSize(suppressActions.length);
      for (SuppressIntentionAction action : suppressActions) {
        replaced.add(new MyDelegatingSuppressAction(action, actions[index++]));
      }
      return replaced.toArray(new SuppressIntentionAction[replaced.size()]);
    }

    return suppressActions;
  }

  private static class MyDelegatingSuppressAction extends SuppressIntentionAction {
    /** The real action, used for looking up icons and names, etc */
    private final SuppressIntentionAction myDelegate;

    /** The quickfix that action is wrapping; we can't access it from the outside
     * but we know what it should be since we wrapped it in the first place
     * via {@link SuppressIntentionActionFromFix#convertBatchToSuppressIntentionActions}
     */
    private final SuppressQuickFix myFix;

    public MyDelegatingSuppressAction(SuppressIntentionAction delegate, SuppressQuickFix fix) {
      myDelegate = delegate;
      myFix = fix;
    }

    @Override
    public Icon getIcon(int flags) {
      return myDelegate.getIcon(flags);
    }

    @NotNull
    @Override
    public String getText() {
      return myDelegate.getText();
    }

    @Override
    protected void setText(@NotNull String text) {
    }

    @Override
    public boolean startInWriteAction() {
      return myDelegate.startInWriteAction();
    }

    @Override
    public String toString() {
      return myDelegate.toString();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
      DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
      Document hostDocument = editor.getDocument();
      int offset = editor.getCaretModel().getOffset();
      HighlightInfo infoAtCursor = codeAnalyzer.findHighlightByOffset(hostDocument, offset, true);
      if (infoAtCursor != null) {
        String description = infoAtCursor.getDescription();
        Issue issue = guessLintIssue(description);
        if (issue != null) {
          String id = issue.getId();
          SuppressQuickFix action = myFix;
          SuppressQuickFix replaced;
          HighlightDisplayKey localKey = HighlightDisplayKey.findOrRegister(id, id, id);
          if (action instanceof SuppressLocalWithCommentFix) {
            replaced = new SuppressLocalWithCommentFix(localKey);
          }
          else if (action instanceof SuppressByJavaCommentFix) {
            replaced = new SuppressByJavaCommentFix(localKey);
          }
          else if (action instanceof SuppressParameterFix) {
            replaced = new SuppressParameterFix(localKey);
          }
          else if (action instanceof SuppressForClassFix) {
            replaced = new SuppressForClassFix(localKey);
          }
          else if (action instanceof SuppressFix) {
            replaced = new SuppressFix(localKey);
          }
          else {
            myDelegate.invoke(project, editor, element);
            return;
          }

          SuppressIntentionAction wrapped = SuppressIntentionActionFromFix.convertBatchToSuppressIntentionAction(replaced);
          wrapped.invoke(project, editor, element);
          return;
        }
      }

      myDelegate.invoke(project, editor, element);
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
      return myDelegate.isAvailable(project, editor, element);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return myDelegate.getFamilyName();
    }
  }
}
