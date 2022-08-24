package org.jetbrains.android.dom.inspections;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.util.XmlTagUtil;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.dom.AndroidAnyTagDescriptor;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.TagFromClassDescriptor;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AndroidElementNotAllowedInspection extends LocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.inspections.group.name");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.inspections.element.not.allowed.name");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "AndroidElementNotAllowed";
  }

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof XmlFile)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    if (AndroidUnknownAttributeInspection.isMyFile(facet, (XmlFile)file)) {
      MyVisitor visitor = new MyVisitor(manager, isOnTheFly);
      file.accept(visitor);
      return visitor.myResult.toArray(ProblemDescriptor.EMPTY_ARRAY);
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }

  private static class MyVisitor extends XmlRecursiveElementVisitor {
    private final InspectionManager myInspectionManager;
    private final boolean myOnTheFly;
    final List<ProblemDescriptor> myResult = new ArrayList<ProblemDescriptor>();

    private MyVisitor(InspectionManager inspectionManager, boolean onTheFly) {
      myInspectionManager = inspectionManager;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitXmlTag(@NotNull XmlTag tag) {
      super.visitXmlTag(tag);

      if (tag.getNamespace().isEmpty()) {
        final XmlElementDescriptor descriptor = tag.getDescriptor();

        if (descriptor instanceof AndroidAnyTagDescriptor ||
            descriptor instanceof TagFromClassDescriptor && ((TagFromClassDescriptor)descriptor).getClazz() == null) {
          final XmlToken startTagNameElement = XmlTagUtil.getStartTagNameElement(tag);
          if (startTagNameElement != null && !isUnknownCustomView(tag)) {
            myResult.add(myInspectionManager.createProblemDescriptor(startTagNameElement, XmlAnalysisBundle.message(
              "xml.inspections.element.is.not.allowed.here", tag.getName()), myOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }

          final XmlToken endTagNameElement = XmlTagUtil.getEndTagNameElement(tag);
          if (endTagNameElement != null && !isUnknownCustomView(tag)) {
            myResult.add(myInspectionManager.createProblemDescriptor(endTagNameElement, XmlAnalysisBundle.message(
              "xml.inspections.element.is.not.allowed.here", tag.getName()), myOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
          }
        }
      }
    }
  }

  private static boolean isUnknownCustomView(XmlTag tag) {
    PsiFile file = tag.getContainingFile();
    if (file != null) {
      ResourceFolderType type = IdeResourcesUtil.getFolderType(file);
      if (type == ResourceFolderType.LAYOUT && tag.getName().indexOf('.') != -1) {
        return true;
      }
    }

    return false;
  }
}
