package org.jetbrains.android;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.ide.actions.GotoRelatedFileAction;
import com.intellij.navigation.GotoRelatedItem;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.util.containers.HashSet;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings("ConstantConditions")
public class AndroidGotoRelatedTest extends AndroidTestCase {
  private static final String BASE_PATH = "/gotoRelated/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
  }

  public void testActivityToLayout() throws Exception {
    final VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    final VirtualFile layout1 = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    final VirtualFile layout2 = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout2.xml");
    final VirtualFile layoutLand = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml");
    final VirtualFile activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java");
    final List<VirtualFile> expectedTargetFiles = Arrays.asList(layout, layout1, layout2, layoutLand);
    doTestGotoRelatedFile(activityFile, expectedTargetFiles, PsiFile.class);
    doCheckLineMarkers(expectedTargetFiles, PsiFile.class);
  }

  public void testSimpleClassToLayout() throws Exception {
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    final VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "Class1.java", "src/p1/p2/Class1.java");
    doTestGotoRelatedFile(file, Collections.<VirtualFile>emptyList(), PsiFile.class);
    final List<LineMarkerInfo> markerInfos = doGetRelatedLineMarkers();
    assertEmpty(markerInfos);
  }

  public void testFragmentToLayout() throws Exception {
    final VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    final VirtualFile layoutLand = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml");
    final VirtualFile fragmentFile = myFixture.copyFileToProject(BASE_PATH + "Fragment1.java", "src/p1/p2/MyFragment.java");
    final List<VirtualFile> expectedTargetFiles = Arrays.asList(layout, layoutLand);
    doTestGotoRelatedFile(fragmentFile, expectedTargetFiles, PsiFile.class);
    doCheckLineMarkers(expectedTargetFiles, PsiFile.class);
  }

  public void testLayoutToContext() throws Exception {
    final VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout2.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml");
    final VirtualFile activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java");
    myFixture.copyFileToProject(BASE_PATH + "Class1.java", "src/p1/p2/Class1.java");
    myFixture.copyFileToProject(BASE_PATH + "Activity2.java", "src/p1/p2/Activity2.java");
    final VirtualFile fragmentFile = myFixture.copyFileToProject(BASE_PATH + "Fragment1.java", "src/p1/p2/MyFragment.java");
    myFixture.copyFileToProject(BASE_PATH + "Fragment2.java", "src/p1/p2/Fragment2.java");
    final List<VirtualFile> expectedTargetFiles = Arrays.asList(activityFile, fragmentFile);
    doTestGotoRelatedFile(layout, expectedTargetFiles, PsiClass.class);
    doCheckLineMarkers(expectedTargetFiles, PsiClass.class);
  }

  public void testNestedActivity() throws Exception {
    final VirtualFile layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout2.xml");
    final VirtualFile layoutLand = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml");
    final VirtualFile activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity3.java", "src/p1/p2/MyActivity.java");
    doTestGotoRelatedFile(activityFile, Arrays.asList(layout, layoutLand), PsiFile.class);
  }

  private void doTestGotoRelatedFile(VirtualFile file, List<VirtualFile> expectedTargetFiles, Class<?> targetElementClass) {
    myFixture.configureFromExistingVirtualFile(file);

    final GotoRelatedFileAction action = new GotoRelatedFileAction();
    final TestActionEvent e = new TestActionEvent(action);
    action.beforeActionPerformedUpdate(e);
    final Presentation presentation = e.getPresentation();
    assertTrue(presentation.isEnabled() && presentation.isVisible());

    doCheckItems(expectedTargetFiles, GotoRelatedFileAction.getItems(myFixture.getFile(), myFixture.getEditor(), null), targetElementClass);
  }

  private void doCheckLineMarkers(List<VirtualFile> expectedTargetFiles, Class<?> targetElementClass) {
    final List<LineMarkerInfo> relatedMarkers = doGetRelatedLineMarkers();
    assertEquals(relatedMarkers.toString(), 1, relatedMarkers.size());
    final LineMarkerInfo marker = relatedMarkers.get(0);
    doCheckItems(expectedTargetFiles, ((AndroidLineMarkerProvider.MyNavigationHandler)marker.
      getNavigationHandler()).doComputeItems(), targetElementClass);
  }

  private List<LineMarkerInfo> doGetRelatedLineMarkers() {
    myFixture.doHighlighting();

    final List<LineMarkerInfo> markers = DaemonCodeAnalyzerImpl.getLineMarkers(
      myFixture.getEditor().getDocument(), myFixture.getProject());
    final List<LineMarkerInfo> relatedMarkers = new ArrayList<LineMarkerInfo>();

    for (LineMarkerInfo marker : markers) {
      if (marker.getNavigationHandler() instanceof AndroidLineMarkerProvider.MyNavigationHandler) {
        relatedMarkers.add(marker);
      }
    }
    return relatedMarkers;
  }

  private static void doCheckItems(List<VirtualFile> expectedTargetFiles, List<GotoRelatedItem> items, Class<?> targetElementClass) {
    final Set<VirtualFile> targetFiles = new HashSet<VirtualFile>();

    for (GotoRelatedItem item : items) {
      final PsiElement element = item.getElement();
      assertInstanceOf(element, targetElementClass);
      final VirtualFile targetFile = element.getContainingFile().getVirtualFile();
      assertNotNull(targetFile);
      targetFiles.add(targetFile);
    }
    assertEquals(new HashSet<VirtualFile>(expectedTargetFiles), targetFiles);
  }
}
