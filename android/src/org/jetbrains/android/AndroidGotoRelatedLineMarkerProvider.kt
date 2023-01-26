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

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.res.findResourceFields
import com.android.tools.idea.res.psi.AndroidResourceToPsiResolver
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.util.androidFacet
import com.android.utils.SdkUtils
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
import org.jetbrains.android.augment.ResourceLightField
import org.jetbrains.android.dom.AndroidAttributeValue
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import javax.swing.Icon

/**
 * Implementation of [RelatedItemLineMarkerProvider] for Android.
 *
 * This class provides related items for Kotlin/Java activities and fragments. These include related xml layouts, menus and manifest
 * declarations. It also provides the corresponding activities and fragments for layout and menu files. These items are displayed as line
 * markers in the gutter of a file, as well as accessible via "Go to related symbol" action.
 */
class AndroidGotoRelatedLineMarkerProvider : RelatedItemLineMarkerProvider() {

  override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
    val facet = element.androidFacet ?: return
    when (element) {
      is PsiClass -> {
        val gotoList = Handler.getItemsForClass(element) ?: return
        val nameIdentifier = element.nameIdentifier ?: return
        result.add(createRelatedItemLineMarkerInfo(nameIdentifier, gotoList, XmlFileType.INSTANCE.icon!!, "Related XML file"))
      }
      is KtClass -> {
        val gotoList = element.toLightClass()?.let { Handler.getItemsForClass(it) } ?: return
        val nameIdentifier = element.nameIdentifier ?: return
        result.add(createRelatedItemLineMarkerInfo(nameIdentifier, gotoList, XmlFileType.INSTANCE.icon!!, "Related XML file"))
      }
      is XmlFile -> {
        val gotoList = Handler.getItemsForXmlFile(element, facet) ?: return
        val rootTag = element.rootTag ?: return
        val anchor = XmlTagUtil.getStartTagNameElement(rootTag) ?: return
        if (gotoList.any { it.element?.language == KotlinLanguage.INSTANCE }) {
          result.add(
            createRelatedItemLineMarkerInfo(anchor as PsiElement, gotoList, KotlinIcons.CLASS, "Related Kotlin class"))
        } else {
          result.add(
            createRelatedItemLineMarkerInfo(anchor as PsiElement, gotoList, AllIcons.Nodes.Class, "Related Java class"))
        }
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
      { gotoList })
  }

  internal object Handler {

    private val REQUIRED_RESOURCE_TYPES = listOf(ResourceType.LAYOUT, ResourceType.MENU)

    /**
     * Related classes and fragments must inherit from one of the following classes.
     */
    private val CONTEXT_CLASSES = listOf(SdkConstants.CLASS_ACTIVITY,
                                         SdkConstants.CLASS_FRAGMENT,
                                         AndroidXConstants.CLASS_V4_FRAGMENT.oldName(),
                                         AndroidXConstants.CLASS_V4_FRAGMENT.newName(),
                                         SdkConstants.CLASS_ADAPTER)

    private fun PsiClass.findComponentDeclarationInManifest(): AndroidAttributeValue<PsiClass>? {
      val manifest = Manifest.getMainManifest(androidFacet) ?: return null
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

    fun getItemsForClass(psiClass: PsiClass): List<GotoRelatedItem>? {
      val items = ArrayList<GotoRelatedItem>()
      psiClass.findComponentDeclarationInManifest()?.xmlAttributeValue?.let { items.add(MyGotoManifestItem(it)) }

      if (psiClass.isInheritorOfOne(CONTEXT_CLASSES)) {
        if (psiClass.language == KotlinLanguage.INSTANCE) {
          val element = (psiClass as KtLightClass).kotlinOrigin as KtClass
          collectRelatedResourceFilesForKotlinClass(element).let { items.addAll(it) }
        } else {
          collectRelatedResourceFilesForJavaClass(psiClass).let { items.addAll(it) }
        }
      }
      if (items.isEmpty()) {
        return null
      }
      return items
    }

    @JvmStatic
    fun getItemsForXmlFile(file: XmlFile, facet: AndroidFacet): List<GotoRelatedItem>? {
      val folderName = file.containingDirectory?.name ?: return null
      return when (ResourceFolderType.getFolderType(folderName)) {
        ResourceFolderType.LAYOUT -> collectRelatedClasses(file, facet, ResourceType.LAYOUT)
        ResourceFolderType.MENU -> collectRelatedClasses(file, facet, ResourceType.MENU)
        else -> null
      }
    }

    private fun collectRelatedResourceFilesForJavaClass(psiClass: PsiClass): List<GotoRelatedItem> {
      val gotoRelatedItems = mutableListOf<GotoRelatedItem>()
      psiClass.accept(object : JavaRecursiveElementWalkingVisitor() {
        override fun visitReferenceExpression(expression: PsiReferenceExpression) {
          super.visitReferenceExpression(expression)
          val resolvedElement = expression.resolve() ?: return
          if (resolvedElement is ResourceLightField && resolvedElement.resourceType in REQUIRED_RESOURCE_TYPES) {
            gotoRelatedItems.addAll(getGotoRelatedFilesForResourceField(resolvedElement, expression))
          }
        }
      })
      return gotoRelatedItems
    }

    private fun collectRelatedResourceFilesForKotlinClass(ktClass: KtClass): List<GotoRelatedItem> {
      val gotoRelatedItems = mutableListOf<GotoRelatedItem>()
      ktClass.accept(object : KtTreeVisitorVoid() {
        override fun visitReferenceExpression(expression: KtReferenceExpression) {
          super.visitReferenceExpression(expression)
          val resolvedElement: PsiElement = expression.mainReference.resolve() ?: return
          if (resolvedElement is ResourceLightField && resolvedElement.resourceType in REQUIRED_RESOURCE_TYPES) {
            gotoRelatedItems.addAll(getGotoRelatedFilesForResourceField(resolvedElement, expression))
          }
        }
      })
      return gotoRelatedItems
    }

    private fun getGotoRelatedFilesForResourceField(field: ResourceLightField, context: PsiElement): List<GotoRelatedItem> {
      val resourceReferencePsiElement = ResourceReferencePsiElement.create(field) ?: return emptyList()
      val resourceReference = resourceReferencePsiElement.resourceReference
      val gotoDeclarationTargets = AndroidResourceToPsiResolver.getInstance()
        .getGotoDeclarationTargets(resourceReference, context).filterIsInstance<PsiFile>().toSet()
      return gotoDeclarationTargets.map { MyGotoRelatedItem(it, resourceReference.resourceType) }.toList()
    }

    private fun collectRelatedClasses(file: XmlFile,
                                      facet: AndroidFacet,
                                      resourceType: ResourceType): List<GotoRelatedItem>? {
      val resourceName = SdkUtils.fileNameToResourceName(file.name)
      val fields = findResourceFields(facet, resourceType.getName(), resourceName, true)
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
      }.toList().ifEmpty { null }
    }

    /**
     * @param element Reference to a layout in a Java class.
     * @return The [GotoRelatedItem] for an element if it passes all the checks.
     */
    private fun checkJavaReference(element: PsiElement): GotoRelatedItem? {
      val referenceExpression = element as? PsiReferenceExpression ?: return null
      val method = referenceExpression.parentOfType<PsiMethodCallExpression>() ?: return null
      val methodName = method.methodExpression.referenceName
      if (SdkConstants.SET_CONTENT_VIEW_METHOD == methodName || SdkConstants.INFLATE_METHOD == methodName) {
        val relatedClass = method.parentOfType<PsiClass>() ?: return null
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
      val callExpression = element.parentOfType<KtCallExpression>() ?: return null
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
  private class MyGotoRelatedItem(
    private val myFile: PsiFile,
    resourceType: ResourceType
  ): GotoRelatedItem(myFile, "${resourceType.displayName} Files") {

    override fun getCustomContainerName(): String? {
      val directory = myFile.containingDirectory
      return if (directory != null) "(" + directory.name + ")" else null
    }
  }

  /**
   * Subclass of [GotoRelatedItem] specifically for showing links to manifest.xml files.
   */
  private class MyGotoManifestItem(attributeValue: XmlAttributeValue) : GotoRelatedItem(attributeValue) {

    override fun getCustomName(): String {
      return "AndroidManifest.xml"
    }

    override fun getCustomContainerName(): String {
      return ""
    }

    override fun getCustomIcon(): Icon? {
      return XmlFileType.INSTANCE.icon
    }
  }
}
