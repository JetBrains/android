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
import com.android.ide.common.sdk.SdkVersionInfo;
import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.detector.api.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import lombok.ast.AstVisitor;
import lombok.ast.CompilationUnit;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.Node;
import org.jetbrains.annotations.NonNls;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.jetbrains.android.inspections.lint.IntellijLintUtils.SUPPRESS_LINT_FQCN;
import static org.jetbrains.android.inspections.lint.IntellijLintUtils.SUPPRESS_WARNINGS_FQCN;

/**
 * Intellij-specific version of the {@link ApiDetector} which uses the PSI structure
 * to check accesses
 * <p>
 * TODO:
 * <ul>
 *   <li>Unit tests, and compare to the bytecode based results</li>
 * </ul>
 */
public class IntellijApiDetector extends ApiDetector {
  @SuppressWarnings("unchecked")
  static final Implementation IMPLEMENTATION = new Implementation(
    IntellijApiDetector.class,
    EnumSet.of(Scope.RESOURCE_FILE, Scope.MANIFEST, Scope.JAVA_FILE),
    Scope.MANIFEST_SCOPE,
    Scope.RESOURCE_FILE_SCOPE,
    Scope.JAVA_FILE_SCOPE
  );

  @NonNls
  private static final String TARGET_API_FQCN = "android.annotation.TargetApi";

  @Nullable
  @Override
  public List<Class<? extends Node>> getApplicableNodeTypes() {
    return Collections.<Class<? extends Node>>singletonList(CompilationUnit.class);
  }

  @Nullable
  @Override
  public AstVisitor createJavaVisitor(@NonNull final JavaContext context) {
    return new ForwardingAstVisitor() {
      @Override
      public boolean visitCompilationUnit(CompilationUnit node) {
        check(context);
        return true;
      }
    };
  }

  private void check(final JavaContext context) {
    if (mApiDatabase == null) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final PsiFile psiFile = IntellijLintUtils.getPsiFile(context);
        if (!(psiFile instanceof PsiJavaFile)) {
          return;
        }
        PsiJavaFile javaFile = (PsiJavaFile)psiFile;
        for (PsiClass clz : javaFile.getClasses()) {
          PsiElementVisitor visitor = new ApiCheckVisitor(context, clz, psiFile);

          javaFile.accept(visitor);
        }
      }
    });
  }

  private static int getTargetApi(@NonNull PsiElement e, @NonNull PsiElement file) {
    PsiElement element = e;
    // Search upwards for target api annotations
    while (element != null && element != file) { // otherwise it will keep going into directories!
      if (element instanceof PsiModifierListOwner) {
        PsiModifierListOwner owner = (PsiModifierListOwner)element;
        PsiModifierList modifierList = owner.getModifierList();
        PsiAnnotation annotation = null;
        if (modifierList != null) {
          annotation = modifierList.findAnnotation(TARGET_API_FQCN);
        }
        if (annotation != null) {
          for (PsiNameValuePair pair : annotation.getParameterList().getAttributes()) {
            PsiAnnotationMemberValue v = pair.getValue();

            if (v instanceof PsiLiteral) {
              PsiLiteral literal = (PsiLiteral)v;
              Object value = literal.getValue();
              if (value instanceof Integer) {
                return (Integer) value;
              } else if (value instanceof String) {
                return codeNameToApi((String) value);
              }
            } else if (v instanceof PsiArrayInitializerMemberValue) {
              PsiArrayInitializerMemberValue mv = (PsiArrayInitializerMemberValue)v;
              for (PsiAnnotationMemberValue mmv : mv.getInitializers()) {
                if (mmv instanceof PsiLiteral) {
                  PsiLiteral literal = (PsiLiteral)mmv;
                  Object value = literal.getValue();
                  if (value instanceof Integer) {
                    return (Integer) value;
                  } else if (value instanceof String) {
                    return codeNameToApi((String) value);
                  }
                }
              }
            } else if (v instanceof PsiExpression) {
              if (v instanceof PsiReferenceExpression) {
                String fqcn = ((PsiReferenceExpression)v).getQualifiedName();
                return codeNameToApi(fqcn);
              } else {
                return codeNameToApi(v.getText());
              }
            }
          }
        }
      }
      element = element.getParent();
    }

    return -1;
  }

  private static int codeNameToApi(String text) {
    int dotIndex = text.lastIndexOf('.');
    if (dotIndex != -1) {
      text = text.substring(dotIndex + 1);
    }
    for (int api = 1; api <= SdkVersionInfo.HIGHEST_KNOWN_API; api++) {
      String code = SdkVersionInfo.getBuildCode(api);
      if (code != null && code.equalsIgnoreCase(text)) {
        return api;
      }
    }

    return -1;
  }

  private class ApiCheckVisitor extends JavaRecursiveElementVisitor {
    private final Context myContext;
    private boolean mySeenSuppress;
    private boolean mySeenTargetApi;
    private final PsiClass myClass;
    private final PsiFile myFile;
    private final boolean myCheckAccess;
    private boolean myCheckOverride;
    private String myFrameworkParent;

    public ApiCheckVisitor(Context context, PsiClass clz, PsiFile file) {
      myContext = context;
      myClass = clz;
      myFile = file;

      myCheckAccess = context.isEnabled(UNSUPPORTED) || context.isEnabled(INLINED);
      myCheckOverride = context.isEnabled(OVERRIDE)
                             && context.getMainProject().getBuildSdk() >= 1;
      if (myCheckOverride) {
        myFrameworkParent = null;
        PsiClass superClass = myClass.getSuperClass();
        while (superClass != null) {
          String fqcn = superClass.getQualifiedName();
          if (fqcn == null) {
            myCheckOverride = false;
          } else if (fqcn.startsWith("android.") //$NON-NLS-1$
              || fqcn.startsWith("java.")        //$NON-NLS-1$
              || fqcn.startsWith("javax.")) {    //$NON-NLS-1$
            if (!fqcn.equals(CommonClassNames.JAVA_LANG_OBJECT)) {
              myFrameworkParent = ClassContext.getInternalName(fqcn);
            }
            break;
          }
          superClass = superClass.getSuperClass();
        }
        if (myFrameworkParent == null) {
          myCheckOverride = false;
        }
      }
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);

      String fqcn = annotation.getQualifiedName();
      if (TARGET_API_FQCN.equals(fqcn)) {
        mySeenTargetApi = true;
      }
      else if (SUPPRESS_LINT_FQCN.equals(fqcn) || SUPPRESS_WARNINGS_FQCN.equals(fqcn)) {
        mySeenSuppress = true;
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);

      if (!myCheckOverride) {
        return;
      }

      int buildSdk = myContext.getMainProject().getBuildSdk();
      String name = method.getName();
      assert myFrameworkParent != null;
      String desc = IntellijLintUtils.getInternalDescription(method, false, false);
      if (desc == null) {
        // Couldn't compute description of method for some reason; probably
        // failure to resolve parameter types
        return;
      }
      int api = mApiDatabase.getCallVersion(myFrameworkParent, name, desc);
      if (api > buildSdk && buildSdk != -1) {
        if (mySeenSuppress &&
            IntellijLintUtils.isSuppressed(method, myFile, OVERRIDE)) {
          return;
        }

        // TODO: Don't complain if it's annotated with @Override; that means
        // somehow the build target isn't correct.

        String fqcn;
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          String className = containingClass.getName();
          String fullClassName = containingClass.getQualifiedName();
          if (fullClassName != null) {
            className = fullClassName;
          }
          fqcn = className + '#' + name;
        } else {
          fqcn = name;
        }

        String message = String.format(
          "This method is not overriding anything with the current build " +
          "target, but will in API level %1$d (current target is %2$d): %3$s",
          api, buildSdk, fqcn);

        PsiElement locationNode = method.getNameIdentifier();
        if (locationNode == null) {
          locationNode = method;
        }
        Location location = IntellijLintUtils.getLocation(myContext.file, locationNode);
        myContext.report(OVERRIDE, location, message, null);
      }
    }

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);

      if (!myCheckAccess) {
        return;
      }

      for (PsiClassType type : aClass.getSuperTypes()) {
        String signature = IntellijLintUtils.getInternalName(type);
        if (signature == null) {
          continue;
        }

        int api = mApiDatabase.getClassVersion(signature);
        if (api == -1) {
          continue;
        }
        int minSdk = getMinSdk(myContext);
        if (api <= minSdk) {
          continue;
        }
        if (mySeenTargetApi) {
          int target = getTargetApi(aClass, myFile);
          if (target != -1) {
            if (api <= target) {
              continue;
            }
          }
        }
        if (mySeenSuppress && IntellijLintUtils.isSuppressed(aClass, myFile, UNSUPPORTED)) {
          continue;
        }

        Location location;
        if (type instanceof PsiClassReferenceType) {
          PsiReference reference = ((PsiClassReferenceType)type).getReference();
          location = IntellijLintUtils.getLocation(myContext.file, reference.getElement());
        } else {
          location = IntellijLintUtils.getLocation(myContext.file, aClass);
        }
        String fqcn = type.getClassName();
        String message = String.format("Class requires API level %1$d (current min is %2$d): %3$s", api, minSdk, fqcn);
        myContext.report(UNSUPPORTED, location, message, null);
      }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);

      if (!myCheckAccess) {
        return;
      }

      PsiReference reference = expression.getReference();
      if (reference == null) {
        return;
      }
      PsiElement resolved = reference.resolve();
      if (resolved != null) {
        if (resolved instanceof PsiField) {
          PsiField field = (PsiField)resolved;
          PsiClass containingClass = field.getContainingClass();
          if (containingClass == null) {
            return;
          }
          String owner = IntellijLintUtils.getInternalName(containingClass);
          if (owner == null) {
            return; // Couldn't resolve type
          }
          String name = field.getName();

          int api = mApiDatabase.getFieldVersion(owner, name);
          if (api == -1) {
            return;
          }
          int minSdk = getMinSdk(myContext);
          if (api <= minSdk) {
            return;
          }
          if (mySeenTargetApi) {
            int target = getTargetApi(expression, myFile);
            if (target != -1) {
              if (api <= target) {
                return;
              }
            }
          }
          if (mySeenSuppress &&
              (IntellijLintUtils.isSuppressed(expression, myFile, UNSUPPORTED)
               || IntellijLintUtils.isSuppressed(expression, myFile, INLINED))) {
            return;
          }

          if (isWithinVersionCheckConditional(expression, api)) {
            return;
          }

          Location location = IntellijLintUtils.getLocation(myContext.file, expression);
          String fqcn = containingClass.getQualifiedName();
          String message = String.format(
              "Field requires API level %1$d (current min is %2$d): %3$s",
              api, minSdk, fqcn + '#' + name);

          Issue issue = UNSUPPORTED;
          // When accessing primitive types or Strings, the values get copied into
          // the class files (e.g. get inlined) which has a separate issue type:
          // INLINED.
          PsiType type = field.getType();
          if (type == PsiType.INT || type == PsiType.CHAR || type == PsiType.BOOLEAN
              || type == PsiType.DOUBLE || type == PsiType.FLOAT || type == PsiType.BYTE
              || type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
            issue = INLINED;

            // Some usages of inlined constants are okay:
            if (isBenignConstantUsage(expression, name, owner)) {
              return;
            }
          }

          myContext.report(issue, location, message, null);
        }
      }
    }

    public boolean isBenignConstantUsage(
      @NonNull PsiElement node,
      @NonNull String name,
      @NonNull String owner) {
      if (IntellijApiDetector.this.isBenignConstantUsage(null, name, owner)) {
        return true;
      }

      // It's okay to reference the constant as a case constant (since that
      // code path won't be taken) or in a condition of an if statement
      // or as a case value
      PsiElement curr = node.getParent();
      while (curr != null) {
        if (curr instanceof PsiSwitchLabelStatement) {
          PsiSwitchLabelStatement caseStatement = (PsiSwitchLabelStatement)curr;
          PsiExpression condition = caseStatement.getCaseValue();
          return condition != null && PsiTreeUtil.isAncestor(condition, node, false);
        } else if (curr instanceof PsiIfStatement) {
          PsiIfStatement ifStatement = (PsiIfStatement)curr;
          PsiExpression condition = ifStatement.getCondition();
          return condition != null && PsiTreeUtil.isAncestor(condition, node, false);
        } else if (curr instanceof PsiConditionalExpression) {
          // ?:-statement
          PsiConditionalExpression ifStatement = (PsiConditionalExpression)curr;
          PsiExpression condition = ifStatement.getCondition();
          return PsiTreeUtil.isAncestor(condition, node, false);
        }
        curr = curr.getParent();
      }

      return false;
    }

    @Override
    public void visitResourceVariable(PsiResourceVariable resourceVariable) {
      super.visitResourceVariable(resourceVariable);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
    }

    @Override
    public void visitCallExpression(PsiCallExpression expression) {
      if (!myCheckAccess) {
        return;
      }

      // TODO: How does this differ from visitMethodCallExpression?
      // Inferred super perhaps? No, I think it refers to constructor invocations!
      PsiMethod method = expression.resolveMethod();
      if (method != null) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
          return;
        }
        String fqcn = containingClass.getQualifiedName();
        String owner = IntellijLintUtils.getInternalName(containingClass);
        if (owner == null) {
          return; // Couldn't resolve type
        }
        String name = IntellijLintUtils.getInternalMethodName(method);
        String desc = IntellijLintUtils.getInternalDescription(method, false, false);
        if (desc == null) {
          // Couldn't compute description of method for some reason; probably
          // failure to resolve parameter types
          return;
        }

        int api = mApiDatabase.getCallVersion(owner, name, desc);
        if (api == -1) {
          return;
        }
        int minSdk = getMinSdk(myContext);
        if (api <= minSdk) {
          return;
        }

        // The lint API database contains two optimizations:
        // First, all members that were available in API 1 are omitted from the database, since that saves
        // about half of the size of the database, and for API check purposes, we don't need to distinguish
        // between "doesn't exist" and "available in all versions".
        // Second, all inherited members were inlined into each class, so that it doesn't have to do a
        // repeated search up the inheritance chain.
        //
        // Unfortunately, in this custom PSI detector, we look up the real resolved method, which can sometimes
        // have a different minimum API.
        //
        // For example, SQLiteDatabase had a close() method from API 1. Therefore, calling SQLiteDatabase is supported
        // in all versions. However, it extends SQLiteClosable, which in API 16 added "implements Closable". In
        // this detector, if we have the following code:
        //     void test(SQLiteDatabase db) { db.close }
        // here the call expression will be the close method on type SQLiteClosable. And that will result in an API
        // requirement of API 16, since the close method it now resolves to is in API 16.
        //
        // To work around this, we can now look up the type of the call expression ("db" in the above, but it could
        // have been more complicated), and if that's a different type than the type of the method, we look up
        // *that* method from lint's database instead. Furthermore, it's possible for that method to return "-1"
        // and we can't tell if that means "doesn't exist" or "present in API 1", we then check the package prefix
        // to see whether we know it's an API method whose members should all have been inlined.
        if (expression instanceof PsiMethodCallExpression) {
          PsiExpression qualifier = ((PsiMethodCallExpression)expression).getMethodExpression().getQualifierExpression();
          if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
            PsiType type = qualifier.getType();
            if (type != null && type instanceof PsiClassType) {
              String expressionOwner = IntellijLintUtils.getInternalName((PsiClassType)type);
              if (expressionOwner != null && !expressionOwner.equals(owner)) {
                int specificApi = mApiDatabase.getCallVersion(expressionOwner, name, desc);
                if (specificApi == -1) {
                  if (expressionOwner.startsWith("android/")
                           || expressionOwner.startsWith("java/")
                           || expressionOwner.startsWith("javax/")) {
                    return;
                  }
                } else if (specificApi <= minSdk) {
                  return;
                }
              }
            }
          } else {
            // Unqualified call; need to search in our super hierarchy
            PsiClass cls = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
            while (cls != null) {
              String expressionOwner = IntellijLintUtils.getInternalName(cls);
              if (expressionOwner == null) {
                break;
              }
              int specificApi = mApiDatabase.getCallVersion(expressionOwner, name, desc);
              if (specificApi == -1) {
                if (expressionOwner.startsWith("android/")
                    || expressionOwner.startsWith("java/")
                    || expressionOwner.startsWith("javax/")) {
                  return;
                }
              } else if (specificApi <= minSdk) {
                return;
              } else {
                break;
              }
              cls = cls.getSuperClass();
            }
          }
        }

        if (mySeenTargetApi) {
          int target = getTargetApi(expression, myFile);
          if (target != -1) {
            if (api <= target) {
              return;
            }
          }
        }
        if (mySeenSuppress &&
            (IntellijLintUtils.isSuppressed(expression, myFile, UNSUPPORTED)
              || IntellijLintUtils.isSuppressed(expression, myFile, INLINED))) {
          return;
        }

        if (isWithinVersionCheckConditional(expression, api)) {
          return;
        }

        PsiElement locationNode = IntellijLintUtils.getCallName(expression);
        if (locationNode == null) {
          locationNode = expression;
        }
        Location location = IntellijLintUtils.getLocation(myContext.file, locationNode);
        String message = String.format("Call requires API level %1$d (current min is %2$d): %3$s", api, minSdk,
                                       fqcn + '#' + method.getName());

        myContext.report(UNSUPPORTED, location, message, null);
      }

      super.visitCallExpression(expression);
    }
  }

  private static boolean isWithinVersionCheckConditional(PsiElement element, int api) {
    PsiElement current = element.getParent();
    PsiElement prev = current;
    while (true) {
      if (current instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)current;
        PsiExpression condition = ifStatement.getCondition();
        if (condition != prev && condition instanceof PsiBinaryExpression) {
          PsiBinaryExpression binary = (PsiBinaryExpression)condition;
          IElementType tokenType = binary.getOperationTokenType();
          if (tokenType == JavaTokenType.GT || tokenType == JavaTokenType.GE ||
              tokenType == JavaTokenType.LE || tokenType == JavaTokenType.LT) {
            PsiExpression left = binary.getLOperand();
            if (left instanceof PsiReferenceExpression) {
              PsiReferenceExpression ref = (PsiReferenceExpression)left;
              if ("SDK_INT".equals(ref.getReferenceName())) {
                PsiExpression right = binary.getROperand();
                int level = -1;
                if (right instanceof PsiReferenceExpression) {
                  PsiReferenceExpression ref2 = (PsiReferenceExpression)right;
                  String codeName = ref2.getReferenceName();
                  level = getApiForCodenameField(codeName);
                } else if (right instanceof PsiLiteralExpression) {
                  PsiLiteralExpression lit = (PsiLiteralExpression)right;
                  Object value = lit.getValue();
                  if (value instanceof Integer) {
                    level = ((Integer)value).intValue();
                  }
                }
                if (level != -1) {
                  boolean fromThen = prev == ifStatement.getThenBranch();
                  boolean fromElse = prev == ifStatement.getElseBranch();
                  assert fromThen == !fromElse;
                  if (tokenType == JavaTokenType.GE) {
                    // if (SDK_INT >= ICE_CREAM_SANDWICH) { <call> } else { ... }
                    return api >= level && fromThen;
                  }
                  else if (tokenType == JavaTokenType.GT) {
                    // if (SDK_INT > ICE_CREAM_SANDWICH) { <call> } else { ... }
                    return api > level && fromThen;
                  }
                  else if (tokenType == JavaTokenType.LE) {
                    // if (SDK_INT <= ICE_CREAM_SANDWICH) { ... } else { <call> }
                    return api > level && fromElse;
                  }
                  else if (tokenType == JavaTokenType.LT) {
                    // if (SDK_INT < ICE_CREAM_SANDWICH) { ... } else { <call> }
                    return api >= level && fromElse;
                  } else {
                    assert false : tokenType;
                  }
                }
              }
            }
          }
        }
        break;
      } else if (current instanceof PsiMethod || current instanceof PsiFile) {
        return false;
      }
      prev = current;
      current = current.getParent();
    }

    return false;
  }

  private static int getApiForCodenameField(@Nullable String codeName) {
    for (int level = 1; level < SdkVersionInfo.HIGHEST_KNOWN_API; level++) {
      String s = SdkVersionInfo.getBuildCode(level);
      if (s != null && s.equals(codeName)) {
        return level;
      }
    }

    return -1;
  }
}
