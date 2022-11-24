package org.jetbrains.android

import com.android.SdkConstants
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.testing.caret
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.ide.actions.GotoRelatedSymbolAction
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil.findFileByIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.kotlin.psi.KtClass
import java.io.File

/**
 * Tests of [AndroidGotoRelatedLineMarkerProvider] for Android.
 *
 * This class tests related items for Kotlin/Java activities and fragments. These include related xml layouts, menus and manifest
 * declarations. It also tests the corresponding activities and fragments for layout and menu files. Also checks the LineMarkers are
 * present.
 */
class AndroidGotoRelatedLineMarkerTest : AndroidTestCase() {

  override fun providesCustomManifest(): Boolean {
    return true
  }

  fun testJavaAnonymousInnerClassToNothing() {
    myFixture.addFileToProject("res/layout/activity_main.xml", BASIC_LAYOUT).virtualFile
    val file = myFixture.addFileToProject(
      "src/p1/p2/Util.java",
      //language=Java
      """
        package p1.p2;
        import android.app.Activity;
        import android.os.Bundle;
        import android.support.annotation.Nullable;
        public class Util  {
            void foo() {
                Activity blank = new Activity() {
                    @Override
                    protected void onCreate(@Nullable Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        setContentView(R.layout.activity_main);
                    }
                };
            }
        }
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(file)
    assertThat(doGotoRelatedFile(file)).isEmpty()
    doCheckNoLineMarkers()
  }

  fun testKotlinActivityToLayoutAndMenu() {
    createManifest()
    val layoutFile = myFixture.addFileToProject("res/layout/layout.xml", BASIC_LAYOUT).virtualFile
    val menuFile = myFixture.addFileToProject("res/menu/menu_main.xml", MENU).virtualFile
    val activityFile = myFixture.addFileToProject("src/p1/p2/MyActivity.kt", KOTLIN_ACTIVITY).virtualFile
    doTestGotoRelatedFile(activityFile, setOf(layoutFile, menuFile), PsiFile::class.java)
    doCheckLineMarkers(setOf(layoutFile, menuFile), PsiFile::class.java,"Related XML file")
  }

  fun testKotlinActivityToLayoutAndManifestAndMenu() {
    val manifestFile = myFixture.addFileToProject("AndroidManifest.xml", MANIFEST).virtualFile
    val layoutFile = myFixture.addFileToProject("res/layout/layout.xml", BASIC_LAYOUT).virtualFile
    val menuFile = myFixture.addFileToProject("res/menu/menu_main.xml", MENU).virtualFile
    val activityFile = myFixture.addFileToProject("src/p1/p2/MyActivity.kt", KOTLIN_ACTIVITY).virtualFile
    val items = doGotoRelatedFile(activityFile)
    assertThat(items).hasSize(3)

    val fileElements = items.map { it.element }.filterIsInstance<PsiFile>()
    assertThat(fileElements.map { it.virtualFile }).containsAllOf(menuFile, layoutFile)

    val manifestElements = items.map { it.element }.filterIsInstance<XmlAttributeValue>()
    assertThat(manifestElements.first().containingFile.virtualFile).isEqualTo(manifestFile)
  }

  fun testActivityToLayout() {
    createManifest()
    val layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml")
    val layout1 = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml")
    val layoutLand = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml")
    val layout2 = arrayOfNulls<VirtualFile>(1)
    addAarDependency(myModule, "myLibrary", "com.library") { dir ->
      val destination = File(dir, "res/layout/layout2.xml")
      FileUtil.copy(File(AndroidTestBase.getTestDataPath() + BASE_PATH + "layout1.xml"), destination)
      layout2[0] = findFileByIoFile(destination, true)
    }

    val activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java")
    val expectedTargetFiles = setOf(layout, layout1, layout2[0]!!, layoutLand)
    doTestGotoRelatedFile(activityFile, expectedTargetFiles, PsiFile::class.java)
    doCheckLineMarkers(expectedTargetFiles, PsiFile::class.java, "Related XML file")
  }

  fun testActivityToLayoutAndManifest() {
    val layoutFile = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml")
    val manifestFile = myFixture.copyFileToProject(BASE_PATH + "Manifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML)
    val activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java")

    val items = doGotoRelatedFile(activityFile)
    assertThat(items).hasSize(2)
    var manifestDeclarationTarget: XmlAttributeValue? = null
    var psiFileTarget: PsiFile? = null

    for (item in items) {
      when (val element = item.element) {
        is PsiFile -> psiFileTarget = element
        is XmlAttributeValue -> manifestDeclarationTarget = element
        else -> fail("Unexpected element: " + element!!)
      }
    }
    assertThat(psiFileTarget!!.virtualFile).isEqualTo(layoutFile)
    assertThat(manifestDeclarationTarget!!.containingFile.virtualFile).isEqualTo(manifestFile)
  }

  fun testActivityToAndManifest() {
    val manifestFile = myFixture.copyFileToProject(BASE_PATH + "Manifest.xml", SdkConstants.FN_ANDROID_MANIFEST_XML)
    val activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java")

    val items = doGotoRelatedFile(activityFile)
    assertThat(items).hasSize(1)
    val item = items[0]
    val element = item.element
    assertThat(element).isInstanceOf(XmlAttributeValue::class.java)
    assertThat(element!!.containingFile.virtualFile).isEqualTo(manifestFile)
  }

  fun testSimpleClassToLayout() {
    createManifest()
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml")
    val file = myFixture.copyFileToProject(BASE_PATH + "Class1.java", "src/p1/p2/Class1.java")
    doTestGotoRelatedFile(file, ImmutableSet.of(), PsiFile::class.java)
    val markerInfos = doGetRelatedLineMarkers()
    assertThat(markerInfos).isEmpty()
  }

  fun testFragmentToLayout() {
    createManifest()
    val layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml")
    val layoutLand = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml")
    val fragmentFile = myFixture.copyFileToProject(BASE_PATH + "Fragment1.java", "src/p1/p2/MyFragment.java")
    val expectedTargetFiles = ImmutableSet.of(layout, layoutLand)
    doTestGotoRelatedFile(fragmentFile, expectedTargetFiles, PsiFile::class.java)
    doCheckLineMarkers(expectedTargetFiles, PsiFile::class.java, "Related XML file")
  }

  fun testMenuToActivity() {
    createManifest()
    val menuFile = myFixture.addFileToProject("res/menu/menu_main.xml", MENU).virtualFile
    val activityFile = myFixture.addFileToProject("src/p1/p2/MyActivity.kt", KOTLIN_ACTIVITY).virtualFile
    doTestGotoRelatedFile(menuFile, setOf(activityFile), KtClass::class.java)
    doCheckLineMarkers(setOf(activityFile), KtClass::class.java,"Related Kotlin class")
  }

  fun testLayoutToJavaContext() {
    createManifest()
    val layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml")
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml")
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout2.xml")
    myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml")
    val activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.java", "src/p1/p2/MyActivity.java")
    myFixture.copyFileToProject(BASE_PATH + "Class1.java", "src/p1/p2/Class1.java")
    myFixture.copyFileToProject(BASE_PATH + "Activity2.java", "src/p1/p2/Activity2.java")
    val fragmentFile = myFixture.copyFileToProject(BASE_PATH + "Fragment1.java", "src/p1/p2/MyFragment.java")
    myFixture.copyFileToProject(BASE_PATH + "Fragment2.java", "src/p1/p2/Fragment2.java")
    val expectedTargetFiles = ImmutableSet.of(activityFile, fragmentFile)
    doTestGotoRelatedFile(layout, expectedTargetFiles, PsiClass::class.java)
    doCheckLineMarkers(expectedTargetFiles, PsiClass::class.java, "Related Java class")
  }

  fun testLayoutToKotlinContext() {
    createManifest()
    val layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml")
    val activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity1.kt", "src/p1/p2/MyActivity.kt")
    val expectedTargetFiles = ImmutableSet.of(activityFile)
    doTestGotoRelatedFile(layout, expectedTargetFiles, KtClass::class.java)
    doCheckLineMarkers(expectedTargetFiles, KtClass::class.java, "Related Kotlin class")
  }

  fun testLayoutDoNothing() {
    createManifest()
    val layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml")
    assertThat(doGotoRelatedFile(layout)).isEmpty()
    doCheckNoLineMarkers()
  }

  fun testNestedActivity() {
    createManifest()
    val layout = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout.xml")
    val layout1 = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout1.xml")
    val layout2 = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout/layout2.xml")
    val layoutLand = myFixture.copyFileToProject(BASE_PATH + "layout1.xml", "res/layout-land/layout.xml")
    val activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity3.java", "src/p1/p2/MyActivity.java")
    doTestGotoRelatedFile(activityFile, ImmutableSet.of(layout, layoutLand, layout1, layout2), PsiFile::class.java)
  }

  fun testSpecifiedWithAttribute() {
    createManifest()
    val layout = myFixture.copyFileToProject(BASE_PATH + "layout2.xml", "res/layout/layout.xml")
    val activityFile = myFixture.copyFileToProject(BASE_PATH + "Activity4.java", "src/p1/p2/MyActivity.java")
    doTestGotoRelatedFile(layout, ImmutableSet.of(activityFile), PsiClass::class.java)
  }

  private fun doTestGotoRelatedFile(file: VirtualFile, expectedTargetFiles: Set<VirtualFile>, targetElementClass: Class<*>) {
    val items = doGotoRelatedFile(file)
    doCheckItems(expectedTargetFiles, items, targetElementClass)
  }

  private fun doGotoRelatedFile(file: VirtualFile): List<GotoRelatedItem> {
    myFixture.configureFromExistingVirtualFile(file)

    val action = GotoRelatedSymbolAction()
    val e = TestActionEvent.createTestEvent(action)
    assertThat(ActionUtil.lastUpdateAndCheckDumb(action, e, true)).isTrue()
    return GotoRelatedSymbolAction.getItems(myFixture.file, myFixture.editor, null)
  }

  private fun doCheckLineMarkers(expectedTargetFiles: Set<VirtualFile>,
                                 targetElementClass: Class<*>,
                                 expectedTooltip: String) {
    val relatedMarkers = doGetRelatedLineMarkers()
    assertThat(relatedMarkers).hasSize(1)
    val marker = relatedMarkers[0] as RelatedItemLineMarkerInfo
    assertThat(marker.lineMarkerTooltip).isEqualTo(expectedTooltip)
    doCheckItems(expectedTargetFiles, marker.createGotoRelatedItems().toList(),
                 targetElementClass)
  }

  private fun doCheckNoLineMarkers() {
    val relatedMarkers = doGetRelatedLineMarkers()
    assertThat(relatedMarkers).hasSize(0)
  }

  private fun doGetRelatedLineMarkers(): List<LineMarkerInfo<*>> {
    myFixture.doHighlighting()

    val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, myFixture.project)
    val relatedMarkers = ArrayList<LineMarkerInfo<*>>()

    for (marker in markers) {
      if (marker is RelatedItemLineMarkerInfo) {
        relatedMarkers.add(marker)
      }
    }
    return relatedMarkers
  }

  companion object {
    private const val BASE_PATH = "/gotoRelated/"

    private fun doCheckItems(expectedTargetFiles: Set<VirtualFile>, items: List<GotoRelatedItem>, targetElementClass: Class<*>) {
      val targetFiles = HashSet<VirtualFile>()

      for (item in items) {
        val element = item.element
        assertThat(element).isInstanceOf(targetElementClass)
        val targetFile = element!!.containingFile.virtualFile
        assertThat(targetFile).isNotNull()
        targetFiles.add(targetFile)
      }
      assertThat(targetFiles).containsExactlyElementsIn(expectedTargetFiles)
    }

    private val BASIC_LAYOUT =
      //language=XML
      """
       <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                      android:orientation="vertical"
                      android:layout_width="match_parent"
                      android:layout_height="match_parent">
        </LinearLayout>
     """.trimIndent()

    private val KOTLIN_ACTIVITY =
      //language=Kotlin
      """
        package p1.p2

        import android.app.Activity
        import android.os.Bundle

        import p1.p2.R.layout.layout
        import p1.p2.R as AliasR

        class MyActivity : Activity() {
        ${caret}
          public override fun onCreate(state: Bundle?) {
            setContentView(layout)
          }

          override fun onCreateOptionsMenu(menu: Menu): Boolean {
              menuInflater.inflate(AliasR.menu.menu_main, menu)
              return true
          }
        }
      """.trimIndent()

    private val MANIFEST =
      //language=XML
      """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
          package="p1.p2">
          <application>
            <activity android:name="p1.p2.MyActivity"/>
          </application>
        </manifest>
      """.trimIndent()

    private val MENU =
      //language=XML
      """
        <menu xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:tools="http://schemas.android.com/tools"
            tools:context="com.example.myapplication.MainActivity">
            <item
                android:id="@+id/action_settings"
                android:orderInCategory="100"
                android:title="@string/action_settings"
                app:showAsAction="never" />
        </menu>
      """.trimIndent()
  }
}
