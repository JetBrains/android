package org.jetbrains.android.dom.inspections

import com.android.SdkConstants.VIEW_FRAGMENT
import com.android.SdkConstants.VIEW_INCLUDE
import com.android.SdkConstants.VIEW_MERGE
import com.android.SdkConstants.VIEW_TAG
import com.android.resources.ResourceFolderType
import com.android.tools.idea.imports.MavenClassRegistryManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.Nls

private val builtinTags = listOf(VIEW_INCLUDE, VIEW_MERGE, VIEW_FRAGMENT, VIEW_TAG)

/**
 * Looks for unknown tags, such as unresolved custom layouts
 *
 * TODO: Check other resource types that allows this syntax (check the inflaters)
 * TODO: Enforce built-ins (non custom views) as well. E.g. if you have <NoSuchView/> it should
 *   light up red.
 */
class AndroidUnresolvableTagInspection : LocalInspectionTool() {

  @Nls
  override fun getGroupDisplayName(): String =
    AndroidBundle.message("android.inspections.group.name")

  @Nls
  override fun getDisplayName(): String =
    AndroidBundle.message("android.inspections.unresolvable.tag")

  override fun getShortName(): String = "AndroidUnresolvableTag"

  override fun checkFile(
    file: PsiFile,
    manager: InspectionManager,
    isOnTheFly: Boolean,
  ): Array<ProblemDescriptor>? {
    if (file !is XmlFile) {
      return ProblemDescriptor.EMPTY_ARRAY
    }
    val facet = AndroidFacet.getInstance(file) ?: return ProblemDescriptor.EMPTY_ARRAY
    if (!facet.module.getModuleSystem().canRegisterDependency().isSupported())
      return ProblemDescriptor.EMPTY_ARRAY

    if (isRelevantFile(facet, file)) {
      val visitor = MyVisitor(manager, isOnTheFly)
      file.accept(visitor)
      return visitor.myResult.toTypedArray()
    }
    return ProblemDescriptor.EMPTY_ARRAY
  }

  private fun isRelevantFile(facet: AndroidFacet, file: XmlFile): Boolean {
    val resourceType =
      ModuleResourceManagers.getInstance(facet).localResourceManager.getFileResourceFolderType(file)
    return if (resourceType != null) {
      resourceType == ResourceFolderType.LAYOUT || resourceType == ResourceFolderType.MENU
    } else false
  }

  private class MyVisitor(
    private val myInspectionManager: InspectionManager,
    private val myOnTheFly: Boolean,
  ) : XmlRecursiveElementVisitor() {
    val myResult: MutableList<ProblemDescriptor> = ArrayList()
    val mavenClassRegistryManager = MavenClassRegistryManager.getInstance()

    override fun visitXmlTag(tag: XmlTag) {
      super.visitXmlTag(tag)

      if (tag.namespace.isNotEmpty()) {
        return
      }

      // Make sure the class exists; check only the last reference; that's the class name tag (the
      // rest are for the
      // package segments).
      val reference = tag.references.lastOrNull() ?: return

      if (reference.resolve() == null && !builtinTags.contains(tag.name)) {
        val className: String = tag.name
        val fixes =
          mavenClassRegistryManager.collectFixesFromMavenClassRegistry(
            className,
            tag.project,
            tag.containingFile?.fileType,
          )
        getTagNameRange(tag)?.let {
          myResult.add(
            myInspectionManager.createProblemDescriptor(
              it,
              AndroidBundle.message("element.cannot.resolve", className),
              myOnTheFly,
              fixes.toTypedArray(),
              ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            )
          )
        }
      }
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
