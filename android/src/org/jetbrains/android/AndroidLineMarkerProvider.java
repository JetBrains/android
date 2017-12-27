package org.jetbrains.android;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.icons.AllIcons;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ConstantFunction;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLineMarkerProvider implements LineMarkerProvider {

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
    for (PsiElement element : elements) {
      final LineMarkerInfo info = doGetLineMarkerInfo(element);

      if (info != null) {
        result.add(info);
      }
    }
  }

  @Nullable
  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  private static LineMarkerInfo doGetLineMarkerInfo(PsiElement element) {
    final MyMarkerInfo info = getMarkerInfo(element);

    if (info == null) {
      return null;
    }
    final PsiElement anchor = info.myElement;
    final String tooltip = info.myTooltip;

    return new LineMarkerInfo<>(
      anchor, anchor.getTextOffset(), info.myIcon, Pass.LINE_MARKERS,
      new ConstantFunction<>(tooltip), new MyNavigationHandler(info));
  }

  @Nullable
  private static MyMarkerInfo getMarkerInfo(@NotNull PsiElement element) {
    if (!(element instanceof XmlFile) && !(element instanceof PsiJavaFile)) {
      return null;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(element);

    if (facet == null) {
      return null;
    }

    if (element instanceof PsiJavaFile) {
      final PsiClass[] classes = ((PsiJavaFile)element).getClasses();

      if (classes.length == 1) {
        final PsiClass aClass = classes[0];
        final PsiIdentifier nameIdentifier = aClass.getNameIdentifier();

        if (nameIdentifier != null) {
          final Computable<List<GotoRelatedItem>> computable = AndroidGotoRelatedProvider.getLazyItemsForClass(aClass, facet, true);
          return computable != null ? new MyMarkerInfo(nameIdentifier, computable, "Related XML file", AllIcons.FileTypes.Xml) : null;
        }
      }
    }
    else {
      final XmlTag rootTag = ((XmlFile)element).getRootTag();
      final Computable<List<GotoRelatedItem>> computable = AndroidGotoRelatedProvider.getLazyItemsForXmlFile((XmlFile)element, facet);
      PsiElement anchor = rootTag != null ? XmlTagUtil.getStartTagNameElement(rootTag) : element;
      if (anchor != null) {
        return computable != null ? new MyMarkerInfo(anchor, computable,
                                                     "Related context Java file", AllIcons.Nodes.Class) : null;
      }
    }
    return null;
  }

  private static class MyMarkerInfo {
    final PsiElement myElement;
    final String myTooltip;
    final Icon myIcon;
    private final Computable<List<GotoRelatedItem>> myComputable;

    private MyMarkerInfo(@NotNull PsiElement element,
                         @NotNull Computable<List<GotoRelatedItem>> computable,
                         @NotNull String tooltip,
                         @NotNull Icon icon) {
      myElement = element;
      myComputable = computable;
      myTooltip = tooltip;
      myIcon = icon;
    }

    public List<GotoRelatedItem> compute() {
      return myComputable.compute();
    }
  }

  public static class MyNavigationHandler implements GutterIconNavigationHandler<PsiElement> {
    private final MyMarkerInfo myInfo;

    private MyNavigationHandler(MyMarkerInfo info) {
      myInfo = info;
    }

    @Override
    public void navigate(MouseEvent e, PsiElement elt) {
      final List<GotoRelatedItem> items = doComputeItems();

      if (items.size() == 1) {
        items.get(0).navigate();
      }
      else {
        NavigationUtil.getRelatedItemsPopup(items, "Go to Related Files").show(new RelativePoint(e));
      }
    }

    @NotNull
    public List<GotoRelatedItem> doComputeItems() {
      return myInfo.compute();
    }
  }
}
