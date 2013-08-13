package org.jetbrains.android.inspections;

import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.AndroidGotoRelatedProvider;
import org.jetbrains.android.dom.AndroidCreateOnClickHandlerAction;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMissingOnClickHandlerInspection extends LocalInspectionTool {
  @NotNull
  public static List<PsiClass> findRelatedActivities(@NotNull XmlFile file, @NotNull AndroidFacet facet) {
    final Computable<List<GotoRelatedItem>> computable = AndroidGotoRelatedProvider.getLazyItemsForXmlFile(file, facet);

    if (computable == null) {
      return Collections.emptyList();
    }
    final List<GotoRelatedItem> items = computable.compute();

    if (items.isEmpty()) {
      return Collections.emptyList();
    }
    final PsiClass activityClass = findActivityClass(facet.getModule());

    if (activityClass == null) {
      return Collections.emptyList();
    }
    final List<PsiClass> result = new ArrayList<PsiClass>();

    for (GotoRelatedItem item : items) {
      final PsiElement element = item.getElement();

      if (element instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)element;

        if (aClass.isInheritor(activityClass, true)) {
          result.add(aClass);
        }
      }
    }
    return result;
  }

  public static PsiClass findActivityClass(@NotNull Module module) {
    return JavaPsiFacade.getInstance(module.getProject())
      .findClass(AndroidUtils.ACTIVITY_BASE_CLASS_NAME, module.getModuleWithDependenciesAndLibrariesScope(false));
  }

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof XmlFile)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(file);

    if (facet == null) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    final DomFileDescription<?> description = DomManager.getDomManager(file.getProject()).getDomFileDescription((XmlFile)file);

    if (!(description instanceof LayoutDomFileDescription)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    final List<PsiClass> activities = findRelatedActivities((XmlFile)file, facet);
    final MyVisitor visitor = new MyVisitor(manager, isOnTheFly, activities);
    file.accept(visitor);
    return visitor.myResult.toArray(new ProblemDescriptor[visitor.myResult.size()]);
  }

  private static class MyVisitor extends XmlRecursiveElementVisitor {
    private final InspectionManager myInspectionManager;
    private final boolean myOnTheFly;
    private final List<PsiClass> myRelatedActivities;

    final List<ProblemDescriptor> myResult = new ArrayList<ProblemDescriptor>();

    private MyVisitor(@NotNull InspectionManager inspectionManager, boolean onTheFly, @NotNull List<PsiClass> relatedActivities) {
      myInspectionManager = inspectionManager;
      myOnTheFly = onTheFly;
      myRelatedActivities = relatedActivities;
    }

    @Override
    public void visitXmlAttributeValue(XmlAttributeValue value) {
      for (PsiReference reference : value.getReferences()) {
        if (!(reference instanceof OnClickConverter.MyReference)) {
          continue;
        }
        final String methodName = ((OnClickConverter.MyReference)reference).getValue();

        if (methodName.isEmpty()) {
          continue;
        }
        final ResolveResult[] results = ((OnClickConverter.MyReference)reference).multiResolve(false);
        final Set<PsiClass> resolvedClasses = new HashSet<PsiClass>();
        final Set<PsiClass> resolvedClassesWithMistake = new HashSet<PsiClass>();

        for (ResolveResult result : results) {
          if (result instanceof OnClickConverter.MyResolveResult) {
            final PsiElement element = result.getElement();

            if (element != null) {
              final PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

              if (aClass != null) {
                resolvedClasses.add(aClass);

                if (!((OnClickConverter.MyResolveResult)result).hasCorrectSignature()) {
                  resolvedClassesWithMistake.add(aClass);
                }
              }
            }
          }
        }
        PsiClass activity = null;

        for (PsiClass relatedActivity : myRelatedActivities) {
          if (!resolvedClasses.contains(relatedActivity)) {
            activity = relatedActivity;
            break;
          }
          else if (activity == null && resolvedClassesWithMistake.contains(relatedActivity)) {
            activity = relatedActivity;
          }
        }

        if (activity != null) {
          String activityName = activity.getName();

          if (activityName == null) {
            activityName = "";
          }
          final String message =
            resolvedClassesWithMistake.contains(activity)
            ? AndroidBundle.message("android.inspections.on.click.missing.incorrect.signature", methodName, activityName)
            : AndroidBundle.message("android.inspections.on.click.missing.problem", methodName, activityName);

          myResult.add(myInspectionManager.createProblemDescriptor(
            value, reference.getRangeInElement(),
            message,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly, new MyQuickFix(
            methodName, activity)));
        }
        else if (results.length == 0) {
          myResult.add(myInspectionManager.createProblemDescriptor(
            value, reference.getRangeInElement(), ProblemsHolder.unresolvedReferenceMessage(reference),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly));
        }
      }
    }
  }

  public static class MyQuickFix extends AbstractIntentionAction implements LocalQuickFix {
    private final String myMethodName;
    private final PsiClass myClass;

    private MyQuickFix(@NotNull String methodName, @NotNull PsiClass aClass) {
      myMethodName = methodName;
      myClass = aClass;
    }

    @NotNull
    @Override
    public String getName() {
      return "Create '" + myMethodName + "(View)' in '" + myClass.getName() + "'";
    }

    @NotNull
    @Override
    public String getText() {
      return getName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      AndroidCreateOnClickHandlerAction.addHandlerMethodAndNavigate(project, myClass, myMethodName);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      // it is called from inspection view or "fix all problems" action (for example) instead of invoke()
      doApplyFix(project);
    }

    public void doApplyFix(@NotNull Project project) {
      AndroidCreateOnClickHandlerAction.addHandlerMethod(project, myClass, myMethodName);
    }
  }
}

