package org.jetbrains.android.inspections;

import static org.jetbrains.android.dom.AndroidResourceDomFileDescription.isFileInResourceFolderType;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.IdeResourcesUtil;
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
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.AndroidGotoRelatedLineMarkerProvider;
import org.jetbrains.android.dom.converters.OnClickConverter;
import org.jetbrains.android.dom.layout.LayoutViewElementDomFileDescription;
import org.jetbrains.android.dom.menu.MenuDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.intentions.AndroidCreateOnClickHandlerAction;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.asJava.LightClassUtilsKt;
import org.jetbrains.kotlin.psi.KtClass;

/**
 * Inspection that runs over layout and menu resource files. Checks the views to see if they have onclick attributes. If so, makes tries
 * to find the relevant fragment or activity, and determines whether the onclick listener was implemented.
 * <p>
 * If no onClick listener is found, then a quick fix is provided that implements the listener.
 */
public class AndroidMissingOnClickHandlerInspection extends LocalInspectionTool {
  @NotNull
  private static Set<PsiClass> findRelatedActivities(@NotNull XmlFile file, @NotNull AndroidFacet facet) {
    if (isFileInResourceFolderType(file, ResourceFolderType.LAYOUT)) {
      final List<GotoRelatedItem> items = AndroidGotoRelatedLineMarkerProvider.getItemsForXmlFile(file, facet);
      if (items == null || items.isEmpty()) {
        return Collections.emptySet();
      }

      return items.stream()
        .map(item -> {
          PsiElement itemElement = item.getElement();
          if (itemElement instanceof PsiClass) {
            return (PsiClass)itemElement;
          }
          else if (itemElement instanceof KtClass) {
            return LightClassUtilsKt.toLightClass((KtClass)itemElement);
          }
          return null;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    }
    else {
      return findRelatedActivitiesForMenu(file, facet);
    }
  }

  @NotNull
  private static Set<PsiClass> findRelatedActivitiesForMenu(@NotNull XmlFile file, @NotNull AndroidFacet facet) {
    final String resourceName = SdkUtils.fileNameToResourceName(file.getName());
    final PsiField[] fields = IdeResourcesUtil.findResourceFields(facet, ResourceType.MENU.getName(), resourceName, true);

    if (fields.length == 0) {
      return Collections.emptySet();
    }
    final Module module = facet.getModule();
    final GlobalSearchScope scope = module.getModuleScope(false);
    final PsiClass activityClass = findActivityClass(module);
    if (activityClass == null) {
      return Collections.emptySet();
    }
    final Set<PsiClass> result = new HashSet<>();

    PsiField menuResourceField = fields[0];
    ReferencesSearch.search(menuResourceField, scope).forEach(reference -> {
      final PsiElement element = reference.getElement();
      final PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (aClass != null && !result.contains(aClass) && aClass.isInheritor(activityClass, true)) {
        result.add(aClass);
      }
      return true;
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

    if (!(description instanceof LayoutViewElementDomFileDescription) &&
        !(description instanceof MenuDomFileDescription)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    final MyVisitor visitor = new MyVisitor(manager, isOnTheFly);
    file.accept(visitor);
    return visitor.myResult.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static class MyVisitor extends XmlRecursiveElementVisitor {
    private final InspectionManager myInspectionManager;
    private final boolean myOnTheFly;

    final List<ProblemDescriptor> myResult = new ArrayList<>();

    private MyVisitor(@NotNull InspectionManager inspectionManager, boolean onTheFly) {
      myInspectionManager = inspectionManager;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitXmlAttributeValue(@NotNull XmlAttributeValue value) {
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
        final Set<PsiClass> resolvedClasses = new HashSet<>();
        final Set<PsiClass> resolvedClassesWithMistake = new HashSet<>();

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
        PsiFile containingFile = value.getContainingFile();
        AndroidFacet facet = AndroidFacet.getInstance(containingFile);
        if (facet == null) {
          return;
        }
        Set<PsiClass> activities = findRelatedActivities((XmlFile)containingFile, facet);

        for (PsiClass relatedActivity : activities) {
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

