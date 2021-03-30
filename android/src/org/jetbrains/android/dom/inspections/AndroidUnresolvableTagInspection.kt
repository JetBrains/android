package org.jetbrains.android.dom.inspections

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.VIEW_FRAGMENT
import com.android.SdkConstants.VIEW_TAG
import com.android.resources.ResourceFolderType
import com.android.support.AndroidxNameUtils
import com.android.tools.idea.imports.AndroidMavenImportFix
import com.android.tools.idea.imports.MavenClassRegistryManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls
import java.util.ArrayList

/**
 * Looks for unknown tags, such as unresolved custom layouts
 *
 * TODO: Check other resource types that allows this syntax (check the inflaters)
 * TODO: Enforce built-ins (non custom views) as well. E.g. if you have <NoSuchView/> it should light up red.
 */
class AndroidUnresolvableTagInspection : LocalInspectionTool() {

  @Nls
  override fun getGroupDisplayName(): String = AndroidBundle.message("android.inspections.group.name")

  @Nls
  override fun getDisplayName(): String = AndroidBundle.message("android.inspections.unresolvable.tag")

  override fun getShortName(): String = "AndroidUnresolvableTag"

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is XmlFile) {
      return ProblemDescriptor.EMPTY_ARRAY
    }
    val facet = AndroidFacet.getInstance(file) ?: return ProblemDescriptor.EMPTY_ARRAY
    if (!facet.module.getModuleSystem().canRegisterDependency().isSupported()) return ProblemDescriptor.EMPTY_ARRAY

    if (isRelevantFile(facet, file)) {
      val visitor = MyVisitor(manager, isOnTheFly)
      file.accept(visitor)
      return visitor.myResult.toTypedArray()
    }
    return ProblemDescriptor.EMPTY_ARRAY
  }

  private fun isRelevantFile(facet: AndroidFacet, file: XmlFile): Boolean {
    val resourceType = ModuleResourceManagers.getInstance(facet).localResourceManager.getFileResourceFolderType(file)
    return if (resourceType != null) {
      resourceType == ResourceFolderType.LAYOUT || resourceType == ResourceFolderType.MENU
    }
    else false
  }

  private class MyVisitor(private val myInspectionManager: InspectionManager,
                          private val myOnTheFly: Boolean) : XmlRecursiveElementVisitor() {
    val myResult: MutableList<ProblemDescriptor> = ArrayList()
    val mavenClassRegistryManager = MavenClassRegistryManager.getInstance()

    override fun visitXmlTag(tag: XmlTag) {
      super.visitXmlTag(tag)

      if (tag.namespace.isNotEmpty()) {
        return
      }

      val resolvedXmlElement: XmlElement?
      val className: String?
      when (val tagName = tag.name) {
        VIEW_TAG -> {
          // E.g. <view class="fqn"/>.
          resolvedXmlElement = tag.getAttribute(ATTR_CLASS)?.valueElement
          className = resolvedXmlElement?.value ?: return
        }
        VIEW_FRAGMENT -> {
          // E.g. <fragment android:name="fqn"/> or <fragment class="fqn"/>.
          val attribute: XmlAttribute? = tag.getAttribute(ATTR_NAME, ANDROID_URI) ?: tag.getAttribute(ATTR_CLASS)
          resolvedXmlElement = attribute?.valueElement
          className = resolvedXmlElement?.value ?: return
        }
        else -> {
          resolvedXmlElement = tag
          className = tagName
        }
      }

      // Make sure the class exists; Check only the last reference; That's the class name tag (the rest are for the package segments).
      val reference = resolvedXmlElement?.references?.lastOrNull() ?: return

      if (reference.resolve() == null) {
        val fixes = mutableListOf<LocalQuickFix>()
        val useAndroidX = tag.project.isAndroidx()
        mavenClassRegistryManager.getMavenClassRegistry()
          .findLibraryData(className, useAndroidX)
          .asSequence()
          .map {
            val resolvedArtifact = if (useAndroidX) {
              AndroidxNameUtils.getCoordinateMapping(it.artifact)
            }
            else {
              it.artifact
            }

            fixes.add(AndroidMavenImportFix(className, resolvedArtifact, it.version))
          }
          .toList()

        for (range in resolveRanges(tag, resolvedXmlElement)) {
          myResult.add(
            myInspectionManager.createProblemDescriptor(
              range,
              AndroidBundle.message("element.cannot.resolve", className),
              myOnTheFly,
              fixes.toTypedArray(),
              ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
            )
          )
        }
      }
    }

    private fun resolveRanges(tag: XmlTag, resolvedXmlElement: XmlElement): List<PsiElement> {
      return listOfNotNull(
        getTagNameRange(tag),
        // If the class is declared via `class` or `android:name`(fragment only) attributes, we also highlight this
        // attribute value and provide a fix suggestion, in addition to the unresolved tag name.
        if (resolvedXmlElement is XmlAttributeValue) getAttributeValueRange(resolvedXmlElement) else null
      )
    }

    private fun getAttributeValueRange(attributeValue: XmlAttributeValue): PsiElement {
      return attributeValue.findChildToken(XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) ?: attributeValue
    }

    private fun getTagNameRange(tag: XmlTag): PsiElement? {
      // Normal position of the tag name, but unusual spaces can mess with this...
      val range: PsiElement = tag.firstChild?.nextSibling ?: return null
      // ...so search properly:
      return range.findChildToken(XmlTokenType.XML_NAME) ?: range
    }

    private fun PsiElement.findChildToken(targetType: IElementType): PsiElement? {
      var curr: PsiElement? = firstChild
      while (curr != null && !(curr is XmlToken && curr.tokenType == targetType)) {
        curr = curr.nextSibling
      }

      return curr
    }
  }
}
