/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.util.androidFacet
import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.navigation.GotoRelatedItem
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.awt.RelativePoint
import com.intellij.xml.util.XmlTagUtil
import org.jetbrains.android.dom.AndroidAttributeValue
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.util.AndroidBuildCommonUtils
import org.jetbrains.android.util.AndroidResourceUtil
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import java.util.ArrayList
import java.util.HashSet
import javax.swing.Icon

/**
 * Implementation of [RelatedItemLineMarkerProvider] for Android.
 *
 * This class provides related items for Kotlin/Java activities and fragments. These include related xml layouts and manifest declarations.
 * It also provides the corresponding activities and fragments for layout files. These items are displayed as line markers in the gutter of
 * a file, as well as accessible using the GotoRelated action.
 */
class AndroidGotoRelatedLineMarkerProvider : RelatedItemLineMarkerProvider() {

  override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<PsiElement>>) {
    val facet = element.androidFacet ?: return
    when (element) {
      is PsiClass -> {
        val gotoList = getItemsForClass(element, facet) ?: return
        result.add(createRelatedItemLineMarkerInfo(element.nameIdentifier as PsiElement, gotoList, XmlFileType.INSTANCE.icon!!,
                                                   "Related XML file"))
      }
      is KtClass -> {
        val gotoList = element.toLightClass()?.let { getItemsForClass(it, facet) } ?: return
        result.add(createRelatedItemLineMarkerInfo(element.nameIdentifier as PsiElement, gotoList, XmlFileType.INSTANCE.icon!!,
                                                   "Related XML file"))
      }
      is XmlFile -> {
        val gotoList = getItemsForXmlFile(element, facet) ?: return
        val rootTag = element.rootTag ?: return
        val anchor = XmlTagUtil.getStartTagNameElement(rootTag) ?: return
        result.add(
          createRelatedItemLineMarkerInfo(anchor as PsiElement, gotoList, AllIcons.Nodes.Class,"Related context Java file"))
      }
    }
  }

  private fun createRelatedItemLineMarkerInfo(
    anchor: PsiElement,
    gotoList: List<GotoRelatedItem>,
    icon: Icon,
    tooltip: String
  ): RelatedItemLineMarkerInfo<PsiElement> {
    return RelatedItemLineMarkerInfo(
      anchor,
      anchor.textRange,
      icon,
      Pass.LINE_MARKERS,
      { tooltip },
      { mouseEvent, _ ->
        if (gotoList.size == 1) {
          gotoList.first().navigate()
        }
        else {
          NavigationUtil.getRelatedItemsPopup(gotoList, "Go to Related Files").show(RelativePoint(mouseEvent))
        }
      },
      GutterIconRenderer.Alignment.RIGHT,
      gotoList)
  }

  companion object {

    /**
     * Related classes and fragments must inherit from one of the following classes.
     */
    private val CONTEXT_CLASSES = listOf(SdkConstants.CLASS_ACTIVITY,
                                         SdkConstants.CLASS_FRAGMENT,
                                         SdkConstants.CLASS_V4_FRAGMENT.oldName(),
                                         SdkConstants.CLASS_V4_FRAGMENT.newName(),
                                         SdkConstants.CLASS_ADAPTER)

    private fun PsiClass.findComponentDeclarationInManifest(): AndroidAttributeValue<PsiClass>? {
      val manifest = androidFacet?.manifest ?: return null
      val application = manifest.application ?: return null
      return when {
        InheritanceUtil.isInheritor(this, AndroidUtils.ACTIVITY_BASE_CLASS_NAME) ->
          application.activities?.find { it.activityClass.value == this }?.activityClass
        InheritanceUtil.isInheritor(this, AndroidUtils.SERVICE_CLASS_NAME) ->
          application.services?.find { it.serviceClass.value == this }?.serviceClass
        InheritanceUtil.isInheritor(this, AndroidUtils.RECEIVER_CLASS_NAME) ->
          application.receivers?.find { it.receiverClass.value == this }?.receiverClass
        InheritanceUtil.isInheritor(this, AndroidUtils.PROVIDER_CLASS_NAME) ->
          application.providers?.find { it.providerClass.value == this }?.providerClass
        else -> null
      }
    }

    @JvmStatic
    fun getItemsForClass(psiClass: PsiClass, facet: AndroidFacet): List<GotoRelatedItem>? {
      val items = ArrayList<GotoRelatedItem>()
      psiClass.findComponentDeclarationInManifest()?.xmlAttributeValue?.let { items.add(MyGotoManifestItem(it)) }

      if (psiClass.isInheritorOfOne(CONTEXT_CLASSES)) {
        if (psiClass.language == KotlinLanguage.INSTANCE) {
          val element = (psiClass as KtLightClass).kotlinOrigin as KtClass
          collectRelatedLayoutFilesForKotlinClass(element, facet).map { MyGotoLayoutItem(it) }.let { items.addAll(it) }
        } else {
          collectRelatedLayoutFilesForJavaClass(psiClass, facet).map { MyGotoLayoutItem(it) }.let { items.addAll(it) }
        }
      }
      if (items.isEmpty()) {
        return null
      }
      return items
    }

    @JvmStatic
    fun getItemsForXmlFile(file: XmlFile, facet: AndroidFacet): List<GotoRelatedItem>? {
      return when (ModuleResourceManagers.getInstance(facet).localResourceManager.getFileResourceFolderType(file)) {
        ResourceFolderType.LAYOUT -> collectRelatedClasses(file, facet)
        // TODO: Handle menus as well!
        else -> null
      }
    }

    private fun collectRelatedLayoutFilesForJavaClass(psiClass: PsiClass, facet: AndroidFacet): List<PsiFile> {
      val files = HashSet<PsiFile>()
      psiClass.accept(object : JavaRecursiveElementWalkingVisitor() {
        override fun visitReferenceExpression(expression: PsiReferenceExpression) {
          super.visitReferenceExpression(expression)

          val resClassName = ResourceType.LAYOUT.getName()
          val info = AndroidResourceUtil.getReferredResourceOrManifestField(facet, expression, resClassName, true) ?: return
          if (info.isFromManifest) {
            return
          }
          files.addAll(ModuleResourceManagers
                         .getInstance(facet)
                         .localResourceManager
                         .findResourcesByFieldName(ResourceNamespace.TODO(), resClassName, info.fieldName)
                         .filterIsInstance<PsiFile>())
        }
      })
      return files.toList()
    }

    private fun collectRelatedLayoutFilesForKotlinClass(ktClass: KtClass, facet: AndroidFacet): List<PsiFile> {
      val files = HashSet<PsiFile>()
      ktClass.accept(object : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          super.visitReferenceExpression(expression)
          val resClassName = ResourceType.LAYOUT.getName()
          val info = (expression as? KtSimpleNameExpression)?.let {
            AndroidResourceUtil.getReferredResourceOrManifestField(facet, it, resClassName, true)
          } ?: return
          if (info.isFromManifest) {
            return
          }

          files.addAll(ModuleResourceManagers
                         .getInstance(facet)
                         .localResourceManager
                         .findResourcesByFieldName(ResourceNamespace.TODO(), resClassName, info.fieldName)
                         .filterIsInstance<PsiFile>())
        }
      })
      return files.toList()
    }

    private fun collectRelatedClasses(file: XmlFile, facet: AndroidFacet): List<GotoRelatedItem>? {
      val resourceName = AndroidBuildCommonUtils.getResourceName(ResourceType.LAYOUT.getName(), file.name)
      val fields = AndroidResourceUtil.findResourceFields(facet, ResourceType.LAYOUT.getName(), resourceName, true)
      val field = fields.firstOrNull() ?: return null
      val module = facet.module
      // Explicitly chosen in the layout/menu file with a tools:context attribute?
      val declared = AndroidUtils.getContextClass(module, file)
      if (declared != null) {
        return listOf(GotoRelatedItem(declared))
      }

      return ReferencesSearch.search(field, module.getModuleScope(false)).mapNotNull { reference ->
        val element = reference.element
        when (element.language) {
          KotlinLanguage.INSTANCE -> checkKotlinReference(element)
          JavaLanguage.INSTANCE -> checkJavaReference(element)
          else -> null
        }
      }.toList()
    }

    /**
     * @param element Reference to a layout in a Java class.
     * @return The [GotoRelatedItem] for an element if it passes all the checks.
     */
    private fun checkJavaReference(element: PsiElement): GotoRelatedItem? {
      val referenceExpression = element as? PsiReferenceExpression ?: return null
      val method = referenceExpression.parentOfType(PsiMethodCallExpression::class) ?: return null
      val methodName = method.methodExpression.referenceName
      if (SdkConstants.SET_CONTENT_VIEW_METHOD == methodName || SdkConstants.INFLATE_METHOD == methodName) {
        val relatedClass = method.parentOfType(PsiClass::class) ?: return null
        if (relatedClass.isInheritorOfOne(CONTEXT_CLASSES)) {
          return GotoRelatedItem(relatedClass)
        }
      }
      return null
    }

    /**
     * @param element Reference to a layout in a Kotlin class.
     * @return The [GotoRelatedItem] for an element if it passes all the checks.
     */
    private fun checkKotlinReference(element: PsiElement): GotoRelatedItem? {
      val callExpression = element.parentOfType(KtCallExpression::class) ?: return null
      val calleeExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return null
      val methodName = calleeExpression.getReferencedName()
      if (SdkConstants.SET_CONTENT_VIEW_METHOD == methodName || SdkConstants.INFLATE_METHOD == methodName) {
        val relatedClass = callExpression.containingClass() ?: return null
        val lightClass = relatedClass.toLightClass() ?: return null
        //TODO(lukeegan): Replace use of isInheritorOfOne with a implementation using ClassDescriptors to avoid KotlinLight layer.
        if (lightClass.isInheritorOfOne(CONTEXT_CLASSES)) {
          return GotoRelatedItem(relatedClass)
        }
      }
      return null
    }

    /**
     * @param possibleBaseClasses List of fully qualified class names
     * Checks whether the PsiClass inherits from one of the possibleBaseClasses if they are available in the PsiClass scope.
     */
    private fun PsiClass.isInheritorOfOne(possibleBaseClasses: Collection<String>): Boolean {
      return possibleBaseClasses.any { InheritanceUtil.isInheritor(this, it) }
    }
  }

  /**
   * Subclass of [GotoRelatedItem] specifically for showing links to Layout files.
   */
  private class MyGotoLayoutItem(private val myFile: PsiFile) : GotoRelatedItem(myFile, "Layout Files") {

    override fun getCustomContainerName(): String? {
      val directory = myFile.containingDirectory
      return if (directory != null) "(" + directory.name + ")" else null
    }
  }

  /**
   * Subclass of [GotoRelatedItem] specifically for showing links to manifest.xml files.
   */
  private class MyGotoManifestItem(attributeValue: XmlAttributeValue) : GotoRelatedItem(attributeValue) {

    override fun getCustomName(): String? {
      return "AndroidManifest.xml"
    }

    override fun getCustomContainerName(): String? {
      return ""
    }

    override fun getCustomIcon(): Icon? {
      return XmlFileType.INSTANCE.icon
    }
  }
}
