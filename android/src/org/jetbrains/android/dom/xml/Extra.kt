package org.jetbrains.android.dom.xml

import com.intellij.util.xml.Required
import org.jetbrains.android.dom.AndroidAttributeValue

interface Extra : XmlResourceElement {
  @Required fun getName(): AndroidAttributeValue<String>

  @Required fun getValue(): AndroidAttributeValue<String>
}
