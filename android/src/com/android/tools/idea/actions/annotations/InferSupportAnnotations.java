/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.actions.annotations;

import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.tools.lint.checks.SupportAnnotationDetector.*;
import static com.android.tools.lint.detector.api.ResourceEvaluator.*;

/**
 * Infer support annotations, e.g. if a method returns {@code R.drawable.something},
 * the method should be annotated with {@code @DrawableRes}.
 * <p>
 * TODO:
 * <ul>
 * <li>Control flow analysis on method calls</li>
 * <li>Check for resource type errors and warn if any are found, since they will lead to incorrect inferences!</li>
 * <li>Can I do a custom dialog UI? There I could let you choose things like whether to infer ranges, control whether to show a report, and explain issue with false positives</li>
 * <li>Look at reflection calls and proguard keep rules to add in @Keep</li>
 * <li>Make sure I flow all annotations not inferred (such as range annotations)</li>
 * <li>Check overridden methods: when doing resolve and hitting an interface I should check what implementations do</li>
 * <li>When analyzing overriding methods, also see if I find *conflicting* annotations. For the Nullable/Nonnull
 * scenario for example, it's possible for an overriding method to have a NonNull return value whereas that's
 * not true for its super implementation. Make sure I don't come up with false annotation inferences like that.</li>
 * <li>Look into inferring @IntDef. Approach: If we have a javadoc which lists multiple? Or what if we have a
 * getter or setter for a field and I can tell how the field is being used wrt bits? How do I name it?</li>
 * <li>Look at return statements to figure out more constraints</li>
 * <li>Look into inferring range restrictions</li>
 * <li>Setting for whether we should look at callsites for some annotations and use that for inference. E.g. if we
 * know nothing about foo(int) but somebody calls it with foo(R.string.name), then we have foo(@StringRes int)</li>
 * <li>Add setting for intdef inference (since it won't be accurate)</li>
 * </ul>
 * </p>
 */
@SuppressWarnings("ALL")
public class InferSupportAnnotations {
  static final boolean CREATE_INFERENCE_REPORT = true;

  public static final String KEEP_ANNOTATION = SUPPORT_ANNOTATIONS_PREFIX + "Keep"; //$NON-NLS-1$

  private static final int MAX_PASSES = 10;
  private int numAnnotationsAdded;
  private final Map<SmartPsiElementPointer<? extends PsiModifierListOwner>, Constraints> myConstraints =
    Maps.newHashMapWithExpectedSize(400);
  private final boolean myAnnotateLocalVariables;
  private final SmartPointerManager myPointerManager;
  private final Project myProject;

  private static class Constraints {
    public List<String> inferences;
    public boolean readOnly;

    @Nullable public EnumSet<ResourceType> types;

    @Nullable public Set<Object> permissionReferences;
    public boolean requireAllPermissions;

    public boolean keep;

    public void addResourceType(@NotNull ResourceType type) {
      if (types == null) {
        types = EnumSet.of(type);
      }
      else {
        types.add(type);
      }
    }

    public void addResourceTypes(@NotNull EnumSet<ResourceType> types) {
      if (types != null) {
        if (this.types == null) {
          this.types = EnumSet.copyOf(types);
        }
        else {
          this.types.addAll(types);
        }
      }
    }

    public void addReport(PsiModifierListOwner annotated, String message) {
      if (CREATE_INFERENCE_REPORT) {
        PsiClass cls = null;
        PsiMember member = null;
        PsiParameter parameter = null;

        if (annotated instanceof PsiClass) {
          cls = (PsiClass)annotated;
        }
        else if (annotated instanceof PsiMethod || annotated instanceof PsiField) {
          member = (PsiMember)annotated;
          cls = member.getContainingClass();
        }
        else if (annotated instanceof PsiParameter) {
          parameter = (PsiParameter)annotated;
          PsiMethod method = PsiTreeUtil.getParentOfType(parameter, PsiMethod.class, true);
          if (method != null) {
            member = method;
            cls = method.getContainingClass();
          }
        }

        StringBuilder sb = new StringBuilder();
        if (cls != null) {
          sb.append("Class{").append(cls.getName()).append('}');
        }
        if (member instanceof PsiMethod) {
          sb.append(" Method{").append(member.getName()).append('}');
        }
        if (member instanceof PsiField) {
          sb.append("Field{").append(member.getName()).append('}');
        }
        if (parameter != null) {
          sb.append("Parameter");
          sb.append("{");
          sb.append(parameter.getType().getCanonicalText()).append(" ").append(parameter.getName());
          sb.append("}");
        }

        sb.append(":");
        sb.append(message);
        if (inferences == null) {
          inferences = Lists.newArrayListWithCapacity(4);
        }
        inferences.add(sb.toString());
      }
    }

    public int merge(Constraints other) {
      int added = 0;
      if (other.types != null) {
        if (types == null) {
          types = other.types;
          added++;
        }
        else {
          if (types.addAll(other.types)) {
            added++;
          }
        }
      }

      if (other.permissionReferences != null) {
        if (permissionReferences == null) {
          permissionReferences = other.permissionReferences;
          added++;
        }
        else {
          if (permissionReferences.addAll(other.permissionReferences)) {
            added++;
          }
        }
        if (other.permissionReferences.size() > 1) {
          requireAllPermissions = other.requireAllPermissions;
        }
      }

      if (!keep && other.keep) {
        keep = true;
        added++;
      }

      if (other.inferences != null) {
        if (inferences == null) {
          inferences = other.inferences;
        } else {
          for (String inference : other.inferences) {
            if (!inferences.contains(inference)) {
              inferences.add(inference);
            }
          }
        }
      }

      return added;
    }

    @NotNull
    public List<String> getResourceTypeAnnotations() {
      if (types != null && !types.isEmpty()) {
        List<String> annotations = Lists.newArrayList();
        for (ResourceType type : types) {
          StringBuilder sb = new StringBuilder();
          sb.append('@');
          if (type == COLOR_INT_MARKER_TYPE) {
            sb.append(COLOR_INT_ANNOTATION);
          }
          else if (type == PX_MARKER_TYPE) {
            sb.append(PX_ANNOTATION);
          }
          else {
            if (type == ResourceType.MIPMAP) {
              type = ResourceType.DRAWABLE;
            } else if (type == ResourceType.DECLARE_STYLEABLE) {
              continue;
            }
            sb.append(SUPPORT_ANNOTATIONS_PREFIX);
            sb.append(StringUtil.capitalize(type.getName()));
            sb.append(ResourceEvaluator.RES_SUFFIX);
          }
          annotations.add(sb.toString());
        }

        return annotations;
      }

      return Collections.emptyList();
    }

    @NotNull
    public List<String> getPermissionAnnotations() {
      if (permissionReferences != null && !permissionReferences.isEmpty()) {
        if (permissionReferences.size() == 1) {
          Object permission = permissionReferences.iterator().next();
          StringBuilder sb = new StringBuilder();
          sb.append('@').append(PERMISSION_ANNOTATION).append('(');
          if (permission instanceof String) {
            sb.append('"');
            sb.append(permission);
            sb.append('"');
          }
          else if (permission instanceof PsiField) {
            PsiField field = (PsiField)permission;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null) {
              String qualifiedName = containingClass.getQualifiedName();
              if (qualifiedName != null) {
                sb.append(qualifiedName);
                sb.append('.');
              }
            }
            sb.append(field.getName());
          }
          sb.append(')');
          return Collections.singletonList(sb.toString());
        }
        else {
          StringBuilder sb = new StringBuilder();
          sb.append('@').append(PERMISSION_ANNOTATION).append('(');
          if (requireAllPermissions) {
            sb.append(ATTR_ALL_OF);
          }
          else {
            sb.append(ATTR_ANY_OF);
          }
          sb.append("={");

          boolean first = true;
          for (Object permission : permissionReferences) {
            if (first) {
              first = false;
            }
            else {
              sb.append(',');
            }
            if (permission instanceof String) {
              sb.append('"');
              sb.append(permission);
              sb.append('"');
            }
            else if (permission instanceof PsiField) {
              PsiField field = (PsiField)permission;
              PsiClass containingClass = field.getContainingClass();
              if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if (qualifiedName != null) {
                  sb.append(qualifiedName);
                  sb.append('.');
                }
              }
              sb.append(field.getName());
            }
          }
          sb.append("}");
          sb.append(')');
          return Collections.singletonList(sb.toString());
        }
      }

      return Collections.emptyList();
    }

    @NotNull
    public String getResourceTypeAnnotationsString() {
      List<String> annotations = getResourceTypeAnnotations();
      if (!annotations.isEmpty()) {
        return Joiner.on('\n').join(annotations).replace(SUPPORT_ANNOTATIONS_PREFIX, "");
      }

      return "";
    }

    @NotNull
    public String getPermissionAnnotationsString() {
      List<String> annotations = getPermissionAnnotations();
      if (!annotations.isEmpty()) {
        return Joiner.on('\n').join(annotations).replace(SUPPORT_ANNOTATIONS_PREFIX, "").replace("android.Manifest", "Manifest");
      }

      return "";
    }

    @NotNull
    public String getKeepAnnotationsString() {
      if (keep) {
        return "@" + KEEP_ANNOTATION;
      }

      return "";
    }

    //public boolean callSuper;
    //public boolean checkResult;
    // TODO ranges and sizes
    // TODO typedefs
    // TODO threads
  }

  static class ConstraintUsageInfo extends UsageInfo {
    private final Constraints myConstraints;

    private ConstraintUsageInfo(@NotNull PsiElement element, @NotNull Constraints constraints) {
      super(element);
      myConstraints = constraints;
    }

    public Constraints getConstraints() {
      return myConstraints;
    }

    public void addInferenceExplanations(List<String> list) {
      if (CREATE_INFERENCE_REPORT && myConstraints.inferences != null) {
        list.addAll(myConstraints.inferences);
      }
    }
  }

  public InferSupportAnnotations(boolean annotateLocalVariables, Project project) {
    myProject = project;
    myAnnotateLocalVariables = annotateLocalVariables;
    myPointerManager = SmartPointerManager.getInstance(project);
  }

  public static void nothingFoundMessage(final Project project) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Messages.showInfoMessage(project, "Did not infer any new annotations", "Infer Support Annotation Results");
      }
    });
  }

  @TestOnly
  public void apply(final Project project) {
    for (Map.Entry<SmartPsiElementPointer<? extends PsiModifierListOwner>, Constraints> entry : myConstraints.entrySet()) {
      SmartPsiElementPointer<? extends PsiModifierListOwner> owner = entry.getKey();
      Constraints value = entry.getValue();
      PsiModifierListOwner element = owner.getElement();
      if (element != null) {
        annotateConstraints(project, value, element);
      }
    }

    if (myConstraints.isEmpty()) {
      throw new RuntimeException("Nothing found to infer");
    }
  }

  public static void apply(Project project, UsageInfo info) {
    if (info instanceof ConstraintUsageInfo) {
      annotateConstraints(project, ((ConstraintUsageInfo)info).getConstraints(), (PsiModifierListOwner)info.getElement());
    }
  }

  private static void annotateConstraints(Project project,
                                          Constraints constraints,
                                          PsiModifierListOwner element) {
    // TODO: Add some option for only annotating public/protected API methods, not private etc
    if (element == null) {
      return;
    }

    if (constraints.readOnly || ModuleUtilCore.findModuleForPsiElement(element) == null) {
      return;
    }

    for (String code : constraints.getResourceTypeAnnotations()) {
      insertAnnotation(project, element, code);
    }

    for (String code : constraints.getPermissionAnnotations()) {
      insertAnnotation(project, element, code);
    }

    if (constraints.keep) {
      insertAnnotation(project, element, constraints.getKeepAnnotationsString());
    }
  }

  private static void insertAnnotation(@NotNull final Project project,
                                       @NotNull final PsiModifierListOwner element,
                                       @NotNull final String code) {
    PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiAnnotation newAnnotation = elementFactory.createAnnotationFromText(code, element);
    PsiNameValuePair[] attributes = newAnnotation.getParameterList().getAttributes();
    int end = code.indexOf('(');
    if (end == -1) {
      end = code.length();
    }
    assert code.startsWith("@") : code;
    String fqn = code.substring(1, end);
    insertAnnotation(project, element, fqn, null, attributes);
  }

  private static void insertAnnotation(@NotNull final Project project,
                                       @NotNull final PsiModifierListOwner element,
                                       @NotNull final String fqn,
                                       @Nullable final String toRemove,
                                       @NotNull final PsiNameValuePair[] values) {
    WriteCommandAction.runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        String[] toRemoveArray = toRemove != null ? new String[]{toRemove} : ArrayUtil.EMPTY_STRING_ARRAY;
        new AddAnnotationFix(fqn, element, values, toRemoveArray).invoke(project, null, element.getContainingFile());
      }
    });
  }

  public void collect(List<UsageInfo> usages, AnalysisScope scope) {
    for (Map.Entry<SmartPsiElementPointer<? extends PsiModifierListOwner>, Constraints> entry : myConstraints.entrySet()) {
      SmartPsiElementPointer<? extends PsiModifierListOwner> pointer = entry.getKey();
      PsiModifierListOwner element = pointer.getElement();
      if (element != null && scope.contains(element) && !shouldIgnore(element)) {
        Constraints constraints = entry.getValue();
        usages.add(new ConstraintUsageInfo(element, constraints));
      }
    }
  }

  private boolean shouldIgnore(PsiModifierListOwner element) {
    if (!myAnnotateLocalVariables) {
      if (element instanceof PsiLocalVariable) return true;
      if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiForeachStatement) return true;
    }
    return false;
  }

  @Nullable
  private Constraints registerPermissionRequirement(@NotNull PsiModifierListOwner owner, boolean all, Object... permissions) {
    final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createSmartPsiElementPointer(owner);

    Constraints constraints = myConstraints.get(pointer);
    if (constraints == null) {
      constraints = new Constraints();
      constraints.permissionReferences = Sets.newHashSet(permissions);
      constraints.requireAllPermissions = all;
      storeConstraint(owner, pointer, constraints);
      numAnnotationsAdded++;
    }
    else if (constraints.permissionReferences == null) {
      constraints.permissionReferences = Sets.newHashSet(permissions);
      constraints.requireAllPermissions = all;
      numAnnotationsAdded++;
    }
    else {
      Set<Object> set = constraints.permissionReferences;
      if (Collections.addAll(set, permissions)) {
        if (set.size() > 1) {
          constraints.requireAllPermissions = all;
        }
        numAnnotationsAdded++;
      }
      else {
        return null;
      }
    }
    return constraints;
  }

  private void storeConstraint(@NotNull PsiModifierListOwner owner,
                               SmartPsiElementPointer<PsiModifierListOwner> pointer,
                               Constraints constraints) {
    constraints.readOnly = ModuleUtilCore.findModuleForPsiElement(owner) == null;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      constraints.readOnly = false;
    }
    myConstraints.put(pointer, constraints);
  }

  public void collect(@NotNull PsiFile file) {
    // This isn't quite right; this does iteration for a single file, but
    // really newly added annotations can change previously visited files'
    // inferred data too. We should do it in a more global way.
    int prevNumAnnotationsAdded;
    int pass = 0;
    do {
      final InferenceVisitor visitor = new InferenceVisitor();
      prevNumAnnotationsAdded = numAnnotationsAdded;
      file.accept(visitor);
      pass++;
    }
    while (prevNumAnnotationsAdded < numAnnotationsAdded && pass < MAX_PASSES);
  }

  @Nullable
  private Constraints getResourceTypeConstraints(PsiModifierListOwner owner, boolean inHierarchy) {
    Constraints constraints = null;
    for (PsiAnnotation annotation : AnnotationUtil.getAllAnnotations(owner, inHierarchy, null)) {
      String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName == null) {
        continue;
      }
      ResourceType type = null;
      if (qualifiedName.startsWith(SUPPORT_ANNOTATIONS_PREFIX) && qualifiedName.endsWith(RES_SUFFIX)) {
        String name = qualifiedName.substring(SUPPORT_ANNOTATIONS_PREFIX.length(), qualifiedName.length() - RES_SUFFIX.length());
        type = ResourceType.getEnum(name.toLowerCase(Locale.US));
      }
      else if (qualifiedName.equals(COLOR_INT_ANNOTATION)) {
        type = COLOR_INT_MARKER_TYPE;
      }
      else if (qualifiedName.equals(PX_ANNOTATION)) {
        type = PX_MARKER_TYPE;
      }
      if (type != null) {
        if (constraints == null) {
          constraints = new Constraints();
        }
        constraints.addResourceType(type);
      }
    }

    final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createSmartPsiElementPointer(owner);
    Constraints existing = myConstraints.get(pointer);
    if (existing != null) {
      if (constraints != null) {
        constraints.merge(existing);
        return constraints;
      }
      return existing;
    }

    return constraints;
  }

  @Nullable
  private PsiModifierListOwner findReflectiveReference(PsiMethodCallExpression call) {
    PsiReferenceExpression methodExpression = call.getMethodExpression();
    if (!"invoke".equals(methodExpression.getReferenceName())) {
      return null;
    }

    PsiElement qualifier = methodExpression.getQualifier();

    PsiMethodCallExpression methodCall = null;
    if (qualifier instanceof PsiMethodCallExpression) {
      methodCall = (PsiMethodCallExpression) qualifier;
    } else if (qualifier instanceof PsiReferenceExpression) {
      PsiElement methodVar = ((PsiReferenceExpression)qualifier).resolve();
      if (methodVar == null) {
        return null;
      }
      // Now find the assignment of the method --
      // TODO: make this smarter to handle assignment separate from declaration of variable etc
      if (methodVar instanceof PsiLocalVariable) {
        PsiLocalVariable var = (PsiLocalVariable)methodVar;
        PsiExpression initializer = var.getInitializer();
        if (initializer instanceof PsiMethodCallExpression) {
          methodCall = (PsiMethodCallExpression)initializer;
        }
      }
    } else {
      return null;
    }

    if (methodCall != null) {
      PsiReferenceExpression methodCallMethodExpression = methodCall.getMethodExpression();
      String declarationName = methodCallMethodExpression.getReferenceName();
      if (!"getDeclaredMethod".equals(declarationName) && !"getMethod".equals(declarationName)) {
        return null;
      }

      PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
      if (arguments.length < 1) {
        return null;
      }
      Object o = JavaConstantExpressionEvaluator.computeConstantExpression(arguments[0], false);
      if (!(o instanceof String)) {
        return null;
      }
      String methodName = (String)o;
      String className = null;

      qualifier = methodCallMethodExpression.getQualifier();

      if (qualifier instanceof PsiReferenceExpression) {
        PsiElement clsVar = ((PsiReferenceExpression)qualifier).resolve();
        if (clsVar == null) {
          return null;
        }

        if (clsVar instanceof PsiLocalVariable) {
          PsiLocalVariable var = (PsiLocalVariable)clsVar;
          qualifier = var.getInitializer();
        }
      }

      if (qualifier instanceof PsiMethodCallExpression) {
        methodCall = (PsiMethodCallExpression)qualifier;
        methodCallMethodExpression = methodCall.getMethodExpression();
        declarationName = methodCallMethodExpression.getReferenceName();
        if (!"loadClass".equals(declarationName) && !"forName".equals(declarationName)) {
          return null;
        }

        PsiExpression[] arguments2 = methodCall.getArgumentList().getExpressions();
        if (arguments2.length < 1) {
          return null;
        }
        o = JavaConstantExpressionEvaluator.computeConstantExpression(arguments2[0], false);
        if (!(o instanceof String)) {
          return null;
        }

        className = (String)o;
      }
      else if (qualifier instanceof PsiClassObjectAccessExpression) {
        PsiClassObjectAccessExpression accessExpression = (PsiClassObjectAccessExpression)qualifier;
        PsiTypeElement operand = accessExpression.getOperand();
        if (operand != null) {
          className = operand.getType().getCanonicalText();
        }
      }
      else {
        return null;
      }

      if (className != null) {
        PsiClass psiClass = JavaPsiFacade.getInstance(myProject).findClass(className, GlobalSearchScope.allScope(myProject));
        PsiMethod[] methods = psiClass.findMethodsByName(methodName, true);
        if (methods.length == 1) {
          return methods[0];
        } else if (methods.length == 0) {
          return null;
        }

        for (PsiMethod method : methods) {
          // Try to match parameters
          PsiParameter[] parameters = method.getParameterList().getParameters();
          if (arguments.length == parameters.length + 1) {
            boolean allMatch = true;
            for (int i = 0; i < parameters.length; i++) {
              PsiParameter parameter = parameters[i];
              PsiExpression argument = arguments[i + 1];
              PsiType parameterType = parameter.getType();
              PsiType argumentType = argument.getType();
              if (!typesMatch(argumentType, parameterType)) {
                allMatch = false;
                break;
              }
            }
            if (allMatch) {
              return method;
            }
          }
        }

        return null;
      }
    }

    // Also consider reflection libraries
    return null;
  }

  // Checks that a class type matches a given parameter type, e.g.
  //     Class<Integer> matches int
  private static boolean typesMatch(PsiType argumentType, PsiType parameterType) {
    if (argumentType instanceof PsiClassType) {
      PsiClassType type = (PsiClassType)argumentType;
      PsiType[] typeParameters = type.getParameters();
      if (typeParameters.length != 1) {
        return false;
      }
      PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(parameterType);
      if (unboxed != null) {
        parameterType = unboxed;
      }

      argumentType = typeParameters[0];
      unboxed = PsiPrimitiveType.getUnboxedType(argumentType);
      if (unboxed != null) {
        argumentType = unboxed;
      }

      return parameterType.equals(argumentType);
    } else {
      return false;
    }
  }

  @Nullable
  private Constraints computeRequiredPermissions(PsiModifierListOwner owner) {
    Constraints constraints = null;
    for (PsiAnnotation annotation : AnnotationUtil.getAllAnnotations(owner, true, null)) {
      String qualifiedName = annotation.getQualifiedName();
      if (qualifiedName == null) {
        continue;
      }
      if (qualifiedName.startsWith(PERMISSION_ANNOTATION)) {
        if (constraints == null) {
          constraints = new Constraints();
        }
        List<Object> permissions = Lists.newArrayList();

        PsiAnnotationMemberValue value = annotation.findAttributeValue(null); // TODO: Or "value" ?
        addPermissions(value, permissions);
        if (!permissions.isEmpty()) {
          constraints.permissionReferences = Sets.<Object>newHashSet(permissions);
        }
        else {
          PsiAnnotationMemberValue anyOf = annotation.findAttributeValue(ATTR_ANY_OF);
          addPermissions(anyOf, permissions);
          if (!permissions.isEmpty()) {
            constraints.permissionReferences = Sets.<Object>newHashSet(permissions);
          }
          else {
            PsiAnnotationMemberValue allOf = annotation.findAttributeValue(ATTR_ALL_OF);
            addPermissions(allOf, permissions);
            if (!permissions.isEmpty()) {
              constraints.permissionReferences = Sets.<Object>newHashSet(permissions);
              constraints.requireAllPermissions = true;
            }
          }
        }
      }
      else if (qualifiedName.equals(UI_THREAD_ANNOTATION)
               || qualifiedName.equals(MAIN_THREAD_ANNOTATION)
               || qualifiedName.equals(BINDER_THREAD_ANNOTATION)
               || qualifiedName.equals(WORKER_THREAD_ANNOTATION)) {
        // TODO: Record thread here to pass to caller, BUT ONLY IF CONDITIONAL
      }
    }

    final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createSmartPsiElementPointer(owner);
    Constraints existing = myConstraints.get(pointer);
    if (existing != null) {
      if (constraints != null) {
        constraints.merge(existing);
        return constraints;
      }
      return existing;
    }

    return constraints;
  }

  private static void addPermissions(@Nullable PsiAnnotationMemberValue value, @NotNull List<Object> names) {
    if (value == null) {
      return;
    }
    if (value instanceof PsiLiteral) {
      String name = (String)((PsiLiteral)value).getValue();
      if (name != null && !name.isEmpty()) {
        names.add(name);
      }
      // empty is just the default: means not specified
    }
    else if (value instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression)value;
      PsiElement resolved = referenceExpression.resolve();
      if (resolved instanceof PsiField) {
        names.add(resolved);
      }
    }
    else if (value instanceof PsiArrayInitializerMemberValue) {
      PsiArrayInitializerMemberValue array = (PsiArrayInitializerMemberValue)value;
      for (PsiAnnotationMemberValue memberValue : array.getInitializers()) {
        addPermissions(memberValue, names);
      }
    }
  }

  @Nullable
  private Constraints storeConstraints(@NotNull PsiModifierListOwner owner, @NotNull Constraints constraints) {
    final SmartPsiElementPointer<PsiModifierListOwner> pointer = myPointerManager.createSmartPsiElementPointer(owner);

    Constraints existing = myConstraints.get(pointer);
    if (existing == null) {
      existing = getResourceTypeConstraints(owner, false);
      if (existing != null) {
        storeConstraint(owner, pointer, existing);
      }
    }

    if (existing == null) {
      storeConstraint(owner, pointer, constraints);
      numAnnotationsAdded++;
      return constraints;
    }
    else {
      // Merge
      int added = existing.merge(constraints);
      numAnnotationsAdded += added;
      return added > 0 ? existing : null;
    }
  }

  private class InferenceVisitor extends JavaRecursiveElementWalkingVisitor {

    @Override
    public void visitMethod(@NotNull final PsiMethod method) {
      super.visitMethod(method);

      Constraints constraints = getResourceTypeConstraints(method, true);
      Collection<PsiMethod> overridingMethods = OverridingMethodsSearch.search(method).findAll();
      for (final PsiMethod overridingMethod : overridingMethods) {
        Constraints additional = getResourceTypeConstraints(overridingMethod, true);
        if (additional != null) {
          if (constraints == null) {
            constraints = additional;
          }
          else {
            constraints.addResourceTypes(additional.types);
          }
        }
      }
      if (constraints != null) {
        constraints = storeConstraints(method, constraints);
        if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
          constraints.addReport(method, constraints.getResourceTypeAnnotationsString() +
                                           " because it extends or is overridden by an annotated method");
        }
      }

      final PsiCodeBlock body = method.getBody();
      if (body != null) {
        body.accept(new JavaRecursiveElementWalkingVisitor() {
          private boolean myReturnedFromMethod = false;

          @Override
          public void visitClass(PsiClass aClass) {
          }

          @Override
          public void visitThrowStatement(PsiThrowStatement statement) {
            myReturnedFromMethod = true;
            super.visitThrowStatement(statement);
          }

          @Override
          public void visitLambdaExpression(PsiLambdaExpression expression) {
          }

          @Override
          public void visitReturnStatement(PsiReturnStatement statement) {
            PsiExpression expression = statement.getReturnValue();
            if (expression instanceof PsiReferenceExpression) {
              PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
              if (resolved instanceof PsiModifierListOwner) {
                // TODO: Look up annotations on this method; here we're for example
                // returning a value that must have the same type as this method
                // e.g.
                //   int unknownReturnType() {
                //       return getKnownReturnType();
                //   }
                //   @DimenRes int getKnownReturnType() { ... }
              }
            }

            // TODO: Resolve expression: if's a resource type, use that
            myReturnedFromMethod = true;
            super.visitReturnStatement(statement);
          }

          @Override
          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            PsiMethod calledMethod = expression.resolveMethod();
            if (calledMethod != null) {
              Constraints constraints = computeRequiredPermissions(calledMethod);
              if (constraints != null
                  && constraints.permissionReferences != null
                  && isUnconditionallyReachable(method, expression)) {

                Constraints inferred = constraints;
                constraints = storeConstraints(method, constraints);
                if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
                  PsiClass containingClass = calledMethod.getContainingClass();
                  String signature = (containingClass != null ? (containingClass.getName() + "#") : "")
                                     + calledMethod.getName();
                  String message = inferred.getPermissionAnnotationsString() + " because it calls " + signature;
                  constraints.addReport(method, message);
                }
              }
            }

            PsiModifierListOwner reflectiveReference = findReflectiveReference(expression);
            if (reflectiveReference != null) {
              Constraints constraints = new Constraints();
              constraints.keep = true;
              constraints = storeConstraints(reflectiveReference, constraints);
              if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
                PsiClass containingClass = method.getContainingClass();
                String signature = (containingClass != null ? (containingClass.getName() + "#") : "")
                                   + method.getName();
                String message = constraints.getKeepAnnotationsString() + " because it is called reflectively from " + signature;
                constraints.addReport(reflectiveReference, message);
              }
            }

            String name = expression.getMethodExpression().getReferenceName();
            if (name != null && name.startsWith("enforce")
                && ("enforceCallingOrSelfPermission".equals(name)
                    || "enforceCallingOrSelfUriPermission".equals(name)
                    || "enforceCallingPermission".equals(name)
                    || "enforceCallingUriPermission".equals(name)
                    || "enforcePermission".equals(name)
                    || "enforceUriPermission".equals(name))) {
              // TODO: Determine whether this method is reached *unconditionally*
              // and use that to merge multiple requirements in the method as well as
              // the permission conditional flag
              PsiExpression[] args = expression.getArgumentList().getExpressions();
              if (args.length > 0) {
                PsiExpression first = args[0];
                PsiReference reference = first.getReference();
                if (reference != null) {
                  PsiElement resolved = reference.resolve();
                  if (resolved instanceof PsiField) {
                    PsiField field = (PsiField)resolved;
                    if (field.hasModifierProperty(PsiModifier.FINAL)) {
                      Constraints constraints = registerPermissionRequirement(method, true, field);
                      if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
                        constraints.addReport(method, constraints.getPermissionAnnotationsString() + " because it calls " + name);
                      }
                      return;
                    }
                  }
                }
                Object v = JavaConstantExpressionEvaluator.computeConstantExpression(first, false);
                if (v instanceof String) {
                  String permission = (String)v;
                  Constraints constraints = registerPermissionRequirement(method, true, permission);
                  if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
                    constraints.addReport(method, constraints.getPermissionAnnotationsString() + " because it calls " + name);
                  }
                }
              }
            }
          }

          private boolean isUnconditionallyReachable(PsiMethod method, PsiElement expression) {
            if (myReturnedFromMethod) {
              return false;
            }

            PsiElement curr = expression.getParent();
            PsiElement prev = curr;
            while (curr != null) {
              if (curr == method) {
                return true;
              }
              if (curr instanceof PsiIfStatement || curr instanceof PsiConditionalExpression || curr instanceof PsiSwitchStatement) {
                return false;
              }
              if (curr instanceof PsiBinaryExpression) {
                // Check for short circuit evaluation:  A && B && C -- here A is unconditional, B and C is not
                PsiBinaryExpression binaryExpression = (PsiBinaryExpression)curr;
                if (prev != binaryExpression.getLOperand() && binaryExpression.getOperationTokenType() == JavaTokenType.ANDAND) {
                  return false;
                }
              }

              prev = curr;
              curr = curr.getParent();
            }

            return true;
          }
        });
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiMethod method = expression.resolveMethod();
      if (method != null) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        PsiExpression[] arguments = expression.getArgumentList().getExpressions();
        if (parameters.length > 0 && arguments.length >= parameters.length) { // >: varargs
          for (int i = 0; i < arguments.length; i++) {
            PsiExpression argument = arguments[i];
            ResourceType resourceType = AndroidPsiUtils.getResourceType(argument);
            if (resourceType != null) {
              Constraints newConstraint = new Constraints();
              newConstraint.addResourceType(resourceType);
              PsiParameter parameter = parameters[i];
              Constraints constraints = storeConstraints(parameter, newConstraint);
              if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
                constraints.addReport(parameter, newConstraint.getResourceTypeAnnotationsString() +
                                                 " because it's passed " + argument.getText() + " in a call");
              }
            }
          }
        }
      }
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
      super.visitReturnStatement(statement);

      PsiExpression returnValue = statement.getReturnValue();
      if (returnValue == null) {
        return;
      }

      ResourceType resourceType = AndroidPsiUtils.getResourceType(returnValue);
      if (resourceType != null) {
        Constraints newConstraint = new Constraints();
        newConstraint.addResourceType(resourceType);
        PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
        Constraints constraints = storeConstraints(method, newConstraint);
        if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
          constraints.addReport(method, newConstraint.getResourceTypeAnnotationsString() +
                                           " because it returns " + returnValue.getText());
        }
      } else if (returnValue instanceof PsiReferenceExpression) {
        PsiElement resolved = ((PsiReferenceExpression)returnValue).resolve();
        if (resolved instanceof PsiModifierListOwner) {
          PsiModifierListOwner owner = (PsiModifierListOwner)resolved;
          Constraints newConstraint = getResourceTypeConstraints(owner, true);
          if (newConstraint != null) {
            PsiMethod method = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
            Constraints constraints = storeConstraints(method, newConstraint);
            if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
              constraints.addReport(method, newConstraint.getResourceTypeAnnotationsString() +
                                            " because it returns " + returnValue.getText());
            }
          }
        }
      }
    }

    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      super.visitParameter(parameter);

      Constraints resourceTypeConstraints = getResourceTypeConstraints(parameter, true);

      if (resourceTypeConstraints != null && resourceTypeConstraints.types != null &&
          !resourceTypeConstraints.types.isEmpty()) {
        Constraints constraints = storeConstraints(parameter, resourceTypeConstraints);
        if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
          constraints.addReport(parameter, constraints.getResourceTypeAnnotationsString() +
                                           " because it extends a method with that parameter annotated or inferred");
        }
      }

      PsiElement grandParent = parameter.getDeclarationScope();
      if (grandParent instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)grandParent;
        if (method.getBody() != null) {

          for (PsiReference reference : ReferencesSearch.search(parameter, new LocalSearchScope(method))) {
            final PsiElement place = reference.getElement();
            if (place instanceof PsiReferenceExpression) {
              final PsiReferenceExpression expr = (PsiReferenceExpression)place;
              final PsiElement parent =
                PsiTreeUtil.skipParentsOfType(expr, PsiParenthesizedExpression.class, PsiTypeCastExpression.class);

              if (processParameter(parameter, expr, parent)) {
                return; //  TODO: return? Shouldn't it be break?
              }
            }
          }
        }
      }
    }

    private boolean processParameter(PsiParameter parameter, PsiReferenceExpression expr, PsiElement parent) {
      if (PsiUtil.isAccessedForWriting(expr)) {
        return true; // TODO: Move into super class
      }

      PsiCall call = PsiTreeUtil.getParentOfType(expr, PsiCall.class);
      if (call != null) {
        final PsiExpressionList argumentList = call.getArgumentList();
        if (argumentList != null) {
          final PsiExpression[] args = argumentList.getExpressions();
          int idx = ArrayUtil.find(args, expr);
          if (idx >= 0) {
            final PsiMethod resolvedMethod = call.resolveMethod();
            if (resolvedMethod != null) {
              final PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
              if (idx < parameters.length) { //not vararg
                final PsiParameter resolvedToParam = parameters[idx];
                Constraints constraints = getResourceTypeConstraints(resolvedToParam, true);
                if (constraints != null && constraints.types != null && !constraints.types.isEmpty()
                    && !resolvedToParam.isVarArgs()) {
                  constraints = storeConstraints(parameter, constraints);
                  if (CREATE_INFERENCE_REPORT && constraints != null && !constraints.readOnly) {
                    constraints.addReport(parameter, constraints.getResourceTypeAnnotationsString() +
                                                     " because it calls "
                                                     +
                                                     (resolvedMethod.getContainingClass() != null ? (resolvedMethod.getContainingClass()
                                                                                                       .getName()
                                                                                                     + "#") : "") +
                                                     resolvedMethod.getName());
                  }
                  return true;
                }
              }
            }
          }
        }
      }
      return false;
    }
  }

  @NotNull
  public static String generateReport(@NotNull UsageInfo[] infos) {
    if (CREATE_INFERENCE_REPORT) {
      StringBuilder sb = new StringBuilder(1000);
      sb.append("INFER SUPPORT ANNOTATIONS REPORT\n");
      sb.append("================================\n\n");

      List<String> list = Lists.newArrayList();
      for (UsageInfo info : infos) {
        ((InferSupportAnnotations.ConstraintUsageInfo)info).addInferenceExplanations(list);
      }
      Collections.sort(list);

      String lastClass = null;
      String lastMethod = null;
      String lastLine = null;
      for (String s : list) {
        if (s.equals(lastLine)) {
          // Some inferences are duplicated
          continue;
        }
        lastLine = s;

        String cls = null;
        String method = null;
        String field = null;
        String parameter = null;
        int index;

        index = s.indexOf("Class{");
        if (index != -1) {
          cls = s.substring(index + "Class{".length(), s.indexOf('}', index));
        }

        index = s.indexOf("Method{");
        if (index != -1) {
          method = s.substring(index + "Method{".length(), s.indexOf('}', index));
          index = s.indexOf("Parameter{");
          if (index != -1) {
            parameter = s.substring(index + "Parameter{".length(), s.indexOf('}', index));
          }
        }
        else {
          index = s.indexOf("Field{");
          if (index != -1) {
            field = s.substring(index + "Field{".length(), s.indexOf('}', index));
          }
        }

        boolean printedMethod = false;
        if (cls != null && !cls.equals(lastClass)) {
          lastClass = cls;
          lastMethod = null;
          sb.append("\n");
          sb.append("Class ").append(cls).append(":\n");
        }

        if (method != null && !method.equals(lastMethod)) {
          lastMethod = method;
          sb.append("  Method ").append(method).append(":\n");
          printedMethod = true;
        }
        else if (field != null) {
          sb.append("  Field ").append(field).append(":\n");
        }

        if (parameter != null) {
          if (!printedMethod) {
            sb.append("  Method ").append(method).append(":\n");
          }
          sb.append("    Parameter ");
          sb.append(parameter).append(":\n");
        }

        String message = s.substring(s.indexOf(':') + 1);
        sb.append("      ").append(message).append("\n");
      }

      if (list.isEmpty()) {
        sb.append("Nothing found.");
      }
      return sb.toString();
    } else {
      return "";
    }
  }
}
