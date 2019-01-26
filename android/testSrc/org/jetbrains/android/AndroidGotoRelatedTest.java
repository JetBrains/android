package org.jetbrains.android;

import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.ide.actions.GotoRelatedSymbolAction;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.testFramework.TestActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings("ConstantConditions")
public class AndroidGotoRelatedTest extends AndroidTestCase {
  private static final String BASE_PATH = "/gotoRelated/";

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    copyRJavaToGeneratedSources();
  }

  public void testActivityToLayout() throws Exception {
    createManifest();
    VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    VirtualFile layout1 = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    VirtualFile layout2 = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout2.xml");
    VirtualFile layoutLand = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml");
    VirtualFile activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java");
    Set<VirtualFile> expectedTargetFiles = ImmutableSet.of(layout, layout1, layout2, layoutLand);
    doTestGotoRelatedFile(activityFile, expectedTargetFiles, PsiFile.class);
    doCheckLineMarkers(expectedTargetFiles, PsiFile.class);
  }

  public void testActivityToLayoutAndManifest() throws Exception {
    VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    VirtualFile manifestFile =
      myFixture.copyFileToProject(BASE_PATH + "Manifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    VirtualFile activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java");

    AndroidGotoRelatedProvider.ourAddDeclarationToManifest = true;
    List<GotoRelatedItem> items;
    try {
      items = doGotoRelatedFile(activityFile);
    }
    finally {
      AndroidGotoRelatedProvider.ourAddDeclarationToManifest = false;
    }
    assertEquals(2, items.size());
    XmlAttributeValue manifestDeclarationTarget = null;
    PsiFile psiFileTarget = null;

    for (GotoRelatedItem item : items) {
      PsiElement element = item.getElement();

      if (element instanceof PsiFile) {
        psiFileTarget = (PsiFile)element;
      }
      else if (element instanceof XmlAttributeValue) {
        manifestDeclarationTarget = (XmlAttributeValue)element;
      }
      else {
        fail("Unexpected element: " + element);
      }
    }
    assertEquals(layout, psiFileTarget.getVirtualFile());
    assertEquals(manifestFile, manifestDeclarationTarget.getContainingFile().getVirtualFile());
  }

  public void testActivityToAndManifest() throws Exception {
    VirtualFile manifestFile =
      myFixture.copyFileToProject(BASE_PATH + "Manifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
    VirtualFile activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java");

    AndroidGotoRelatedProvider.ourAddDeclarationToManifest = true;
    List<GotoRelatedItem> items;
    try {
      items = doGotoRelatedFile(activityFile);
    }
    finally {
      AndroidGotoRelatedProvider.ourAddDeclarationToManifest = false;
    }
    assertEquals(1, items.size());
    GotoRelatedItem item = items.get(0);
    PsiElement element = item.getElement();
    assertInstanceOf(element, XmlAttributeValue.class);
    assertEquals(manifestFile, element.getContainingFile().getVirtualFile());
  }

  public void testSimpleClassToLayout() throws Exception {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "Class1.java", "src/p1/p2/Class1.java");
    doTestGotoRelatedFile(file, ImmutableSet.of(), PsiFile.class);
    List<LineMarkerInfo> markerInfos = doGetRelatedLineMarkers();
    assertEmpty(markerInfos);
  }

  public void testFragmentToLayout() throws Exception {
    createManifest();
    VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    VirtualFile layoutLand = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml");
    VirtualFile fragmentFile = myFixture.copyFileToProject(BASE_PATH + "Fragment1.java", "src/p1/p2/MyFragment.java");
    Set<VirtualFile> expectedTargetFiles = ImmutableSet.of(layout, layoutLand);
    doTestGotoRelatedFile(fragmentFile, expectedTargetFiles, PsiFile.class);
    doCheckLineMarkers(expectedTargetFiles, PsiFile.class);
  }

  public void testLayoutToContext() throws Exception {
    createManifest();
    VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout2.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml");
    VirtualFile activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java");
    myFixture.copyFileToProject(BASE_PATH + "Class1.java", "src/p1/p2/Class1.java");
    myFixture.copyFileToProject(BASE_PATH + "Activity2.java", "src/p1/p2/Activity2.java");
    VirtualFile fragmentFile = myFixture.copyFileToProject(BASE_PATH + "Fragment1.java", "src/p1/p2/MyFragment.java");
    myFixture.copyFileToProject(BASE_PATH + "Fragment2.java", "src/p1/p2/Fragment2.java");
    Set<VirtualFile> expectedTargetFiles = ImmutableSet.of(activityFile, fragmentFile);
    doTestGotoRelatedFile(layout, expectedTargetFiles, PsiClass.class);
    doCheckLineMarkers(expectedTargetFiles, PsiClass.class);
  }

  public void testNestedActivity() throws Exception {
    createManifest();
    VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout2.xml");
    VirtualFile layoutLand = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml");
    VirtualFile activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity3.java", "src/p1/p2/MyActivity.java");
    doTestGotoRelatedFile(activityFile, ImmutableSet.of(layout, layoutLand), PsiFile.class);
  }

  public void testSpecifiedWithAttribute() throws Exception {
    createManifest();
    VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml");
    VirtualFile activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity4.java", "src/p1/p2/MyActivity.java");
    doTestGotoRelatedFile(layout, ImmutableSet.of(activityFile), PsiClass.class);
  }

  private void doTestGotoRelatedFile(VirtualFile file, Set<VirtualFile> expectedTargetFiles, Class<?> targetElementClass) {
    List<GotoRelatedItem> items = doGotoRelatedFile(file);
    doCheckItems(expectedTargetFiles, items, targetElementClass);
  }

  private List<GotoRelatedItem> doGotoRelatedFile(VirtualFile file) {
    myFixture.configureFromExistingVirtualFile(file);

    GotoRelatedSymbolAction action = new GotoRelatedSymbolAction();
    TestActionEvent e = new TestActionEvent(action);
    action.beforeActionPerformedUpdate(e);
    Presentation presentation = e.getPresentation();
    assertTrue(presentation.isEnabled() && presentation.isVisible());
    return GotoRelatedSymbolAction.getItems(myFixture.getFile(), myFixture.getEditor(), null);
  }

  private void doCheckLineMarkers(Set<VirtualFile> expectedTargetFiles, Class<?> targetElementClass) {
    List<LineMarkerInfo> relatedMarkers = doGetRelatedLineMarkers();
    assertEquals(relatedMarkers.toString(), 1, relatedMarkers.size());
    LineMarkerInfo marker = relatedMarkers.get(0);
    doCheckItems(expectedTargetFiles, ((AndroidLineMarkerProvider.MyNavigationHandler)marker.getNavigationHandler()).doComputeItems(),
                 targetElementClass);
  }

  private List<LineMarkerInfo> doGetRelatedLineMarkers() {
    myFixture.doHighlighting();

    List<LineMarkerInfo> markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.getEditor().getDocument(), myFixture.getProject());
    List<LineMarkerInfo> relatedMarkers = new ArrayList<>();

    for (LineMarkerInfo marker : markers) {
      if (marker.getNavigationHandler() instanceof AndroidLineMarkerProvider.MyNavigationHandler) {
        relatedMarkers.add(marker);
      }
    }
    return relatedMarkers;
  }

  private static void doCheckItems(Set<VirtualFile> expectedTargetFiles, List<GotoRelatedItem> items, Class<?> targetElementClass) {
    Set<VirtualFile> targetFiles = new HashSet<>();

    for (GotoRelatedItem item : items) {
      PsiElement element = item.getElement();
      assertThat(element).isInstanceOf(targetElementClass);
      VirtualFile targetFile = element.getContainingFile().getVirtualFile();
      assertThat(targetFile).isNotNull();
      targetFiles.add(targetFile);
    }
    assertThat(targetFiles).containsExactlyElementsIn(expectedTargetFiles);
  }
}
