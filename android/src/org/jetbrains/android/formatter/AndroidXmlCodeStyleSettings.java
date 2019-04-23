// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.formatter;

import com.intellij.configurationStore.XmlSerializer;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomManager;
import org.jdom.Element;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.*;

public class AndroidXmlCodeStyleSettings extends CustomCodeStyleSettings {
  public boolean USE_CUSTOM_SETTINGS = false;

  public LayoutSettings LAYOUT_SETTINGS = new LayoutSettings();
  public ManifestSettings MANIFEST_SETTINGS = new ManifestSettings();
  public ValueResourceFileSettings VALUE_RESOURCE_FILE_SETTINGS = new ValueResourceFileSettings();
  public OtherSettings OTHER_SETTINGS = new OtherSettings();

  public AndroidXmlCodeStyleSettings(CodeStyleSettings container) {
    super("AndroidXmlCodeStyleSettings", container);
  }

  public static AndroidXmlCodeStyleSettings getInstance(CodeStyleSettings settings) {
    return settings.getCustomSettings(AndroidXmlCodeStyleSettings.class);
  }

  @Override
  public Object clone() {
    final AndroidXmlCodeStyleSettings cloned = (AndroidXmlCodeStyleSettings)super.clone();
    cloned.LAYOUT_SETTINGS = (LayoutSettings)LAYOUT_SETTINGS.clone();
    cloned.MANIFEST_SETTINGS = (ManifestSettings)MANIFEST_SETTINGS.clone();
    cloned.VALUE_RESOURCE_FILE_SETTINGS = (ValueResourceFileSettings)VALUE_RESOURCE_FILE_SETTINGS.clone();
    cloned.OTHER_SETTINGS = (OtherSettings)OTHER_SETTINGS.clone();
    return cloned;
  }

  public static class MySettings implements JDOMExternalizable, Cloneable {
    public int WRAP_ATTRIBUTES;
    public boolean INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE;
    public boolean INSERT_LINE_BREAK_BEFORE_NAMESPACE_DECLARATION;
    public boolean INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE;

    @Override
    public void readExternal(@NotNull Element element) {
      XmlSerializer.deserializeInto(element, this);
    }

    @Override
    public void writeExternal(@NotNull Element element) {
      XmlSerializer.serializeObjectInto(this, element);
    }

    public XmlPolicy createXmlPolicy(CodeStyleSettings settings, FormattingDocumentModel documentModel) {
      return new AndroidXmlPolicy(settings, this, documentModel);
    }

    @Override
    protected MySettings clone() {
      try {
        return (MySettings)super.clone();
      }
      catch (CloneNotSupportedException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      final MySettings s = (MySettings)o;

      return INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE == s.INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE &&
             INSERT_LINE_BREAK_BEFORE_NAMESPACE_DECLARATION == s.INSERT_LINE_BREAK_BEFORE_NAMESPACE_DECLARATION &&
             INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE == s.INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE &&
             WRAP_ATTRIBUTES == s.WRAP_ATTRIBUTES;
    }

    @Override
    public int hashCode() {
      int result = WRAP_ATTRIBUTES;
      result = 31 * result + (INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE ? 1 : 0);
      result = 31 * result + (INSERT_LINE_BREAK_BEFORE_NAMESPACE_DECLARATION ? 1 : 0);
      result = 31 * result + (INSERT_LINE_BREAK_AFTER_LAST_ATTRIBUTE ? 1 : 0);
      return result;
    }
  }

  public static class LayoutSettings extends MySettings {
    public boolean INSERT_BLANK_LINE_BEFORE_TAG = true;

    {
      WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
      INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = true;
    }

    @Override
    public XmlPolicy createXmlPolicy(CodeStyleSettings settings, FormattingDocumentModel documentModel) {
      return new AndroidXmlPolicy(settings, this, documentModel) {
        @Override
        public boolean insertLineBreakBeforeTag(XmlTag xmlTag) {
          return INSERT_BLANK_LINE_BEFORE_TAG || super.insertLineBreakBeforeTag(xmlTag);
        }

        @Override
        public boolean insertLineBreakAfterTagBegin(XmlTag tag) {
          return INSERT_BLANK_LINE_BEFORE_TAG;
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      final LayoutSettings settings = (LayoutSettings)o;

      return INSERT_BLANK_LINE_BEFORE_TAG == settings.INSERT_BLANK_LINE_BEFORE_TAG;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (INSERT_BLANK_LINE_BEFORE_TAG ? 1 : 0);
      return result;
    }
  }

  public static class ManifestSettings extends MySettings {
    public boolean GROUP_TAGS_WITH_SAME_NAME = true;

    {
      WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
      INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = true;
    }

    @Override
    public XmlPolicy createXmlPolicy(CodeStyleSettings settings, FormattingDocumentModel documentModel) {
      return new AndroidXmlPolicy(settings, this, documentModel) {
        @Override
        public boolean insertLineBreakBeforeTag(XmlTag xmlTag) {
          if (GROUP_TAGS_WITH_SAME_NAME) {
            PsiElement element = getPrevSiblingElement(xmlTag);

            if (element instanceof XmlTag) {
              final String name1 = ((XmlTag)element).getName();
              final String name2 = xmlTag.getName();

              if (!name1.equals(name2)) {
                element = getPrevSiblingElement(element);

                if (element instanceof XmlTag && ((XmlTag)element).getName().equals(name1)) {
                  return true;
                }
                element = getNextSiblingElement(xmlTag);
                return element instanceof XmlTag && ((XmlTag)element).getName().equals(name2);
              }
            }
          }
          return false;
        }

        @Override
        public boolean insertLineBreakAfterTagBegin(XmlTag tag) {
          return GROUP_TAGS_WITH_SAME_NAME && tag.getParentTag() == null;
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      final ManifestSettings settings = (ManifestSettings)o;

      return GROUP_TAGS_WITH_SAME_NAME == settings.GROUP_TAGS_WITH_SAME_NAME;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (GROUP_TAGS_WITH_SAME_NAME ? 1 : 0);
      return result;
    }
  }

  public static class ValueResourceFileSettings extends MySettings {
    public boolean INSERT_LINE_BREAKS_AROUND_STYLE = true;

    {
      WRAP_ATTRIBUTES = CommonCodeStyleSettings.DO_NOT_WRAP;
      INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = false;
    }

    @Override
    public XmlPolicy createXmlPolicy(CodeStyleSettings settings, FormattingDocumentModel documentModel) {
      return new AndroidXmlPolicy(settings, this, documentModel) {
        @Override
        public boolean insertLineBreakAfterTagBegin(XmlTag tag) {
          if (!INSERT_LINE_BREAKS_AROUND_STYLE) {
            return false;
          }
          final XmlTag[] subTags = tag.getSubTags();
          return subTags.length != 0 && isStyleTag(subTags[0]);
        }

        @Override
        public boolean keepWhiteSpacesInsideTag(XmlTag tag) {
          if (super.keepWhiteSpacesInsideTag(tag)) {
            return true;
          }

          boolean inItem = false;
          while (tag != null) {
            String tagName = tag.getName();
            if (TAG_ITEM.equals(tagName)) {
              inItem = true;
            } else if (TAG_STRING.equals(tagName)) {
              return true;
            } else if (TAG_STRING_ARRAY.equals(tagName) || TAG_PLURALS.equals(tagName)) {
              return inItem;
            }
            tag = tag.getParentTag();
          }
          return false;
        }

        @Override
        public boolean insertLineBreakBeforeTag(XmlTag xmlTag) {
          if (!INSERT_LINE_BREAKS_AROUND_STYLE) {
            return false;
          }
          if (isStyleTag(xmlTag)) {
            return true;
          }
          final PsiElement sibling = getPrevSiblingElement(xmlTag);
          return sibling instanceof XmlTag && isStyleTag((XmlTag)sibling);
        }

        private boolean isStyleTag(XmlTag tag) {
          return DomManager.getDomManager(tag.getProject()).
            getDomElement(tag) instanceof Style;
        }
      };
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      final ValueResourceFileSettings settings = (ValueResourceFileSettings)o;

      return INSERT_LINE_BREAKS_AROUND_STYLE == settings.INSERT_LINE_BREAKS_AROUND_STYLE;
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (INSERT_LINE_BREAKS_AROUND_STYLE ? 1 : 0);
      return result;
    }
  }

  public static class OtherSettings extends MySettings {
    {
      WRAP_ATTRIBUTES = CommonCodeStyleSettings.WRAP_ALWAYS;
      INSERT_LINE_BREAK_BEFORE_FIRST_ATTRIBUTE = true;
    }
  }
}
