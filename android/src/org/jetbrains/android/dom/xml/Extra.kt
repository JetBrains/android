package org.jetbrains.android.dom.xml

import com.intellij.util.xml.Required
import org.jetbrains.android.dom.AndroidAttributeValue

interface Extra : XmlResourceElement {
  @get:Required val name: AndroidAttributeValue<String?>?

  @get:Required val value: AndroidAttributeValue<String?>?
}
