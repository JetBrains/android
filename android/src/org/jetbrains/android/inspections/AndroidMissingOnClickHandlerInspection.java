package org.jetbrains.android.inspections;

import static org.jetbrains.android.dom.AndroidResourceDomFileDescription.isFileInResourceFolderType;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.utils.SdkUtils;
import com.intellij.codeInsight.intention.AbstractIntentionAction;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.AndroidGotoRelatedLineMarkerProvider;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.android.dom.menu.MenuDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.intentions.AndroidCreateOnClickHandlerAction;
import org.jetbrains.android.util.AndroidBundle;
import com.android.tools.idea.res.IdeResourcesUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClass;

public class AndroidMissingOnClickHandlerInspection extends LocalInspectionTool {
  @NotNull
  private static Collection<PsiClass> findRelatedActivities(@NotNull XmlFile file,
                                                            @NotNull AndroidFacet facet,
                                                            @NotNull DomFileDescription<?> description) {
    if (isFileInResourceFolderType(file, ResourceFolderType.LAYOUT)) {
      final List<GotoRelatedItem> items = AndroidGotoRelatedLineMarkerProvider.getItemsForXmlFile(file, facet);
      if (items == null || items.isEmpty()) {
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
        } else if (element instanceof KtClass) {
          final PsiClass aClass = LightClassUtilsKt.toLightClass((KtClass)element);

          if (aClass != null && aClass.isInheritor(activityClass, true)) {
            result.add(aClass);
          }
        }
      }
      return result;
    }
    else {
      return findRelatedActivitiesForMenu(file, facet);
    }
  }

  @NotNull
  private static Set<PsiClass> findRelatedActivitiesForMenu(@NotNull XmlFile file, @NotNull AndroidFacet facet) {
    final String resType = ResourceType.MENU.getName();
    final String resourceName = SdkUtils.fileNameToResourceName(file.getName());
    final PsiField[] fields = IdeResourcesUtil.findResourceFields(facet, resType, resourceName, true);

    if (fields.length == 0) {
      return Collections.emptySet();
    }
    final Module module = facet.getModule();
    final GlobalSearchScope scope = module.getModuleScope(false);
    final PsiClass activityClass = findActivityClass(module);
    if (activityClass == null) {
      return Collections.emptySet();
    }
    final Set<PsiClass> result = new HashSet<PsiClass>();

    ReferencesSearch.search(fields[0], scope).forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
        final PsiElement element = reference.getElement();

        if (element == null) {
          return true;
        }
        final PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);

        if (aClass != null && !result.contains(aClass) && aClass.isInheritor(activityClass, true)) {
          result.add(aClass);
        }
        return true;
      }
    });
    return result;
  }

  @Nullable
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

    if (!isFileInResourceFolderType((XmlFile)file, ResourceFolderType.LAYOUT) &&
        !(description instanceof MenuDomFileDescription)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    final Collection<PsiClass> activities = findRelatedActivities((XmlFile)file, facet, description);
    final MyVisitor visitor = new MyVisitor(manager, isOnTheFly, activities);
    file.accept(visitor);
    return visitor.myResult.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static class MyVisitor extends XmlRecursiveElementVisitor {
    private final InspectionManager myInspectionManager;
    private final boolean myOnTheFly;
    private final Collection<PsiClass> myRelatedActivities;

    final List<ProblemDescriptor> myResult = new ArrayList<ProblemDescriptor>();

    private MyVisitor(@NotNull InspectionManager inspectionManager, boolean onTheFly, @NotNull Collection<PsiClass> relatedActivities) {
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
        final OnClickConverter.MyReference ref = (OnClickConverter.MyReference)reference;
        final String methodName = ref.getValue();

        if (methodName.isEmpty()) {
          continue;
        }
        final ResolveResult[] results = ref.multiResolve(false);
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
          if (!containsOrExtends(resolvedClasses, relatedActivity)) {
            activity = relatedActivity;
            break;
          }
          else if (activity == null && containsOrExtends(resolvedClassesWithMistake, relatedActivity)) {
            activity = relatedActivity;
          }
        }

        if (activity != null) {
          reportMissingOnClickProblem(ref, activity, methodName, resolvedClassesWithMistake.contains(activity));
        }
        else if (results.length == 0) {
          myResult.add(myInspectionManager.createProblemDescriptor(
            value, reference.getRangeInElement(), ProblemsHolder.unresolvedReferenceMessage(reference),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly));
        }
        else if (!resolvedClassesWithMistake.isEmpty()) {
          reportMissingOnClickProblem(ref, resolvedClassesWithMistake.iterator().next(), methodName, true);
        }
      }
    }

    /**
     * Returns true if the given associated activity class is either found in the given set of
     * classes, or (less likely) extends any of the classes in that set
     */
    private static boolean containsOrExtends(@NotNull Set<PsiClass> resolvedClasses, @NotNull PsiClass relatedActivity) {
      if (resolvedClasses.contains(relatedActivity)) {
        return true;
      }
      for (PsiClass resolvedClass : resolvedClasses) {
        if (relatedActivity.isInheritor(resolvedClass, false)) {
          return true;
        }
      }
      return false;
    }

    private void reportMissingOnClickProblem(OnClickConverter.MyReference reference,
                                             PsiClass activity,
                                             String methodName,
                                             boolean incorrectSignature) {
      String activityName = activity.getName();

      if (activityName == null) {
        activityName = "";
      }
      final String message =
        incorrectSignature
        ? AndroidBundle.message("android.inspections.on.click.missing.incorrect.signature", methodName, activityName)
        : AndroidBundle.message("android.inspections.on.click.missing.problem", methodName, activityName);

      final LocalQuickFix[] fixes =
        StringUtil.isJavaIdentifier(methodName)
        ? new LocalQuickFix[]{new MyQuickFix(methodName, reference.getConverter(), activity)}
        : LocalQuickFix.EMPTY_ARRAY;

      myResult.add(myInspectionManager.createProblemDescriptor(
        reference.getElement(), reference.getRangeInElement(), message,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly, fixes));
    }
  }

  public static class MyQuickFix extends AbstractIntentionAction implements LocalQuickFix {
    private final String myMethodName;
    private final OnClickConverter myConverter;
    private final PsiClass myClass;

    private MyQuickFix(@NotNull String methodName, @NotNull OnClickConverter converter, @NotNull PsiClass aClass) {
      myMethodName = methodName;
      myConverter = converter;
      myClass = aClass;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Create '" + myMethodName + "(" + myConverter.getShortParameterName() + ")' in '" + myClass.getName() + "'";
    }

    @NotNull
    @Override
    public String getText() {
      return getName();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      final String paramType = myConverter.getDefaultMethodParameterType(myClass);
      AndroidCreateOnClickHandlerAction.addHandlerMethodAndNavigate(project, myClass, myMethodName, paramType);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      // it is called from inspection view or "fix all problems" action (for example) instead of invoke()
      doApplyFix(project);
    }

    public void doApplyFix(@NotNull Project project) {
      final String paramType = myConverter.getDefaultMethodParameterType(myClass);
      AndroidCreateOnClickHandlerAction.addHandlerMethod(project, myClass, myMethodName, paramType);
    }
  }
}

