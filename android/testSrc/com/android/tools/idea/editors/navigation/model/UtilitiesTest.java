package com.android.tools.idea.editors.navigation.model;

import junit.framework.TestCase;
import com.android.tools.idea.editors.navigation.NavigationEditorUtils;

public class UtilitiesTest extends TestCase {

  public void testFileNameTranslation(){
    String correctJavaFileNameA = "FooBar.java";
    String correctJavaFileNameATranslation = "foo_bar";
    String correctJavaFileNameB = "IBinder.java";
    String correctJavaFileNameBTranslation = "i_binder";
    String correctXmlFileNameA = "foo_bar.xml";
    String correctXmlFileNameATranslation = "FooBar";
    String correctXmlFileNameB = "i_binder.xml";
    String correctXmlFileNameBTranslation = "IBinder";


    String xmlFileNameA = NavigationEditorUtils.getXmlFileNameFromJavaFileName(correctJavaFileNameA);
    String xmlFileNameB = NavigationEditorUtils.getXmlFileNameFromJavaFileName(correctJavaFileNameB);

    String javaFileNameA = NavigationEditorUtils.getJavaFileNameFromXmlFileName(correctXmlFileNameA);
    String javaFileNameB = NavigationEditorUtils.getJavaFileNameFromXmlFileName(correctXmlFileNameB);

    assertEquals(correctJavaFileNameATranslation, xmlFileNameA);
    assertEquals(correctJavaFileNameBTranslation, xmlFileNameB);

    assertEquals(correctXmlFileNameATranslation, javaFileNameA);
    assertEquals(correctXmlFileNameBTranslation, javaFileNameB);
  }

}
