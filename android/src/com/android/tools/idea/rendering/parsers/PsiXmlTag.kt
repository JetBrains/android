package com.android.tools.idea.rendering.parsers

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.res.resourceNamespace
import com.android.tools.rendering.parsers.RenderXmlAttribute
import com.android.tools.rendering.parsers.RenderXmlTag
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag

/** Studio specific [XmlTag]-based implementation of [RenderXmlTag]. */
class PsiXmlTag private constructor(private val tagPointer: SmartPsiElementPointer<XmlTag>) : RenderXmlTag {
  constructor(
    tag: XmlTag
  ) : this(
    ApplicationManager.getApplication().runReadAction<SmartPsiElementPointer<XmlTag>> {
      SmartPointerManager.getInstance(tag.project).createSmartPsiElementPointer(tag)
    }
  )

  override val localNamespaceDeclarations: Map<String, String>
    get() = psiXmlTag?.localNamespaceDeclarations ?: emptyMap()

  override fun getAttribute(name: String, namespace: String): RenderXmlAttribute? =
    psiXmlTag?.getAttribute(name, namespace)?.let { PsiXmlAttribute(it) }

  override fun getAttribute(name: String): RenderXmlAttribute? = psiXmlTag?.getAttribute(name)?.let { PsiXmlAttribute(it) }

  override val name: String
    get() = psiXmlTag?.name ?: ""

  override val subTags: List<RenderXmlTag>
    get() = psiXmlTag?.subTags?.map { PsiXmlTag(it) } ?: emptyList()

  override val namespace: String
    get() = psiXmlTag?.namespace ?: ""

  override val resourceNamespace: ResourceNamespace?
    get() = psiXmlTag?.resourceNamespace

  override val localName: String
    get() = psiXmlTag?.localName ?: ""

  override val isValid: Boolean
    get() = psiXmlTag?.isValid ?: false

  override val attributes: List<RenderXmlAttribute>
    get() = psiXmlTag?.attributes?.map { PsiXmlAttribute(it) } ?: emptyList()

  override val namespacePrefix: String
    get() = psiXmlTag?.namespacePrefix ?: ""

  override val parentTag: RenderXmlTag?
    get() = psiXmlTag?.parentTag?.let { PsiXmlTag(it) }

  override fun getAttributeValue(name: String): String? = psiXmlTag?.getAttributeValue(name)

  override fun getAttributeValue(name: String, namespace: String): String? = psiXmlTag?.getAttributeValue(name, namespace)

  override fun getNamespaceByPrefix(prefix: String): String = psiXmlTag?.getNamespaceByPrefix(prefix) ?: ""

  override fun getPrefixByNamespace(namespace: String): String? = psiXmlTag?.getPrefixByNamespace(namespace)

  override val containingFileNameWithoutExtension: String
    get() = psiXmlTag?.containingFile?.virtualFile?.nameWithoutExtension ?: ""

  override val isEmpty: Boolean
    get() = psiXmlTag?.isEmpty ?: true

  override fun hashCode(): Int {
    return tagPointer.hashCode()
  }

  override fun equals(other: Any?): Boolean =
    when (other) {
      is PsiXmlTag -> this.tagPointer == other.tagPointer || (this.psiXmlTag != null && this.psiXmlTag == other.psiXmlTag)
      else -> false
    }

  val psiXmlTag: XmlTag?
    get() = ApplicationManager.getApplication().runReadAction<XmlTag> { tagPointer.element }

  companion object {
    @JvmStatic fun create(xmlTag: XmlTag?): PsiXmlTag? = xmlTag?.let { PsiXmlTag(it) }
  }
}
