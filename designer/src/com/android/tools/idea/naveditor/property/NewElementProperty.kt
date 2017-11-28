package com.android.tools.idea.naveditor.property

import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Computable
import com.intellij.psi.xml.XmlTag
import com.intellij.util.xml.XmlName
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.attrs.AttributeDefinitions

open class NewElementProperty(private val myParent: NlComponent, private val myTagName: String, private val myAttrName: String,
                              private val myNamespace: String?, private val myAttrDefs: AttributeDefinitions,
                              private val myPropertiesManager: NavPropertiesManager) : NlProperty {

  private var delegate: NlProperty? = null

  override fun getName(): String = delegate?.name ?: ""

  override fun getNamespace(): String? = delegate?.namespace

  override fun getValue(): String? = delegate?.value

  override fun getResolvedValue(): String? = delegate?.resolvedValue

  override fun isDefaultValue(value: String?): Boolean = delegate?.isDefaultValue(value) != false

  override fun resolveValue(value: String?): String? = delegate?.resolveValue(value)

  override fun setValue(value: Any?) {
    delegate?.let {
      it.setValue(value)
      return
    }
    if (value is String? && value.isNullOrEmpty()) {
      return
    }
    val newComponent = WriteCommandAction.runWriteCommandAction(null, Computable<NlComponent> {
      val tag = myParent.tag.createChildTag(myTagName, null, null, false)
      val result = myParent.model.createComponent(tag, myParent, null)
      result.setAttribute(myNamespace, myAttrName, value as String)
      result
    })
    delegate = NlPropertyItem.create(XmlName(myAttrName, myNamespace), definition, listOf(newComponent), myPropertiesManager)
  }

  override fun getTooltipText(): String = delegate?.tooltipText ?: ""

  override fun getDefinition(): AttributeDefinition? = myAttrDefs.getAttrDefByName(myAttrName)

  override fun getComponents(): List<NlComponent> = delegate?.components ?: listOf()

  override fun getResolver(): ResourceResolver? = model.configuration.resourceResolver

  override fun getModel(): NlModel = myParent.model

  override fun getTag(): XmlTag? = delegate?.tag

  override fun getTagName(): String? = delegate?.tagName

  override fun getChildProperty(name: String): NlProperty = throw UnsupportedOperationException(myAttrName)

  override fun getDesignTimeProperty(): NlProperty = throw UnsupportedOperationException(myAttrName)

}