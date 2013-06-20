package com.android.navigation;

import junit.framework.TestCase;
import com.android.tools.idea.editors.navigation.Utilities;

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


    String xmlFileNameA = Utilities.getXmlFileNameFromJavaFileName(correctJavaFileNameA);
    String xmlFileNameB = Utilities.getXmlFileNameFromJavaFileName(correctJavaFileNameB);

    String javaFileNameA = Utilities.getJavaFileNameFromXmlFileName(correctXmlFileNameA);
    String javaFileNameB = Utilities.getJavaFileNameFromXmlFileName(correctXmlFileNameB);

    assertEquals(correctJavaFileNameATranslation, xmlFileNameA);
    assertEquals(correctJavaFileNameBTranslation, xmlFileNameB);

    assertEquals(correctXmlFileNameATranslation, javaFileNameA);
    assertEquals(correctXmlFileNameBTranslation, javaFileNameB);
  }

}
