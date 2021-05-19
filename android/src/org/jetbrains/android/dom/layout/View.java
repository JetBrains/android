package org.jetbrains.android.dom.layout;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.android.dom.converters.ViewClassConverter;

/**
 * <view> tag </view>
 */
@DefinesXml
public interface View extends LayoutViewElement {
  @Required
  @Attribute("class")
  @Convert(ViewClassConverter.class)
  GenericAttributeValue<PsiClass> getViewClass();
}
