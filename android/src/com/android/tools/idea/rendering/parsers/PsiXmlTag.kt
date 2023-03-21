package com.android.tools.idea.rendering.parsers

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.idea.res.resourceNamespace
import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlTag

/** Studio specific [XmlTag]-based implementation of [RenderXmlTag]. */
class PsiXmlTag(private val tag: XmlTag) : RenderXmlTag {
  override val localNamespaceDeclarations: Map<String, String>
    get() = tag.localNamespaceDeclarations

  override fun getAttribute(name: String, namespace: String): RenderXmlAttribute? =
    tag.getAttribute(name, namespace)?.let { PsiXmlAttribute(it) }

  override fun getAttribute(name: String): RenderXmlAttribute? =
    tag.getAttribute(name)?.let { PsiXmlAttribute(it) }

  override val name: String
    get() = tag.name

  override val subTags: List<RenderXmlTag>
    get() = tag.subTags.map { PsiXmlTag(it) }

  override val namespace: String
    get() = tag.namespace

  override val resourceNamespace: ResourceNamespace?
    get() = tag.resourceNamespace

  override val localName: String
    get() = tag.localName

  override val isValid: Boolean
    get() = tag.isValid

  override val attributes: List<RenderXmlAttribute>
    get() = tag.attributes.map { PsiXmlAttribute(it) }

  override val namespacePrefix: String
    get() = tag.namespacePrefix

  override val parentTag: RenderXmlTag?
    get() = tag.parentTag?.let { PsiXmlTag(it) }

  override fun getAttributeValue(name: String): String? = tag.getAttributeValue(name)

  override fun getAttributeValue(name: String, namespace: String): String? = tag.getAttributeValue(name, namespace)

  override fun getNamespaceByPrefix(prefix: String): String = tag.getNamespaceByPrefix(prefix)

  override fun getPrefixByNamespace(namespace: String): String? = tag.getPrefixByNamespace(namespace)

  override val project: Project
    get() = tag.project

  override val containingFileNameWithoutExtension: String
    get() = tag.containingFile.virtualFile.nameWithoutExtension

  override val isEmpty: Boolean
    get() = tag.isEmpty

  override fun hashCode(): Int {
    return tag.hashCode()
  }

  override fun equals(other: Any?): Boolean = when (other) {
    is PsiXmlTag -> this.tag == other.tag
    else -> false
  }

  val psiXmlTag: XmlTag
    get() = tag

  companion object {
    @JvmStatic
    fun create(xmlTag: XmlTag?): PsiXmlTag? = xmlTag?.let { PsiXmlTag(it) }
  }
}