package com.android.navigation;

import junit.framework.TestCase;
import com.android.tools.idea.editors.navigation.Utilities;

public class UtilitiesTest extends TestCase {

  public void testFileNameTranslation(){
    String correctJavaFileNameA = "FooBar.java";
    String correctJavaFileNameB = "IBinder.java";
    String correctXmlFileNameA = "foo_bar.xml";
    String correctXmlFileNameB = "i_binder.xml";


    String xmlFileNameA = Utilities.getXmlFileNameFromJavaFileName(correctJavaFileNameA);
    String xmlFileNameB = Utilities.getXmlFileNameFromJavaFileName(correctJavaFileNameB);

    String javaFileNameA = Utilities.getJavaFileNameFromXmlFileName(correctXmlFileNameA);
    String javaFileNameB = Utilities.getJavaFileNameFromXmlFileName(correctXmlFileNameB);

    assertEquals(correctXmlFileNameA, xmlFileNameA);
    assertEquals(correctXmlFileNameB, xmlFileNameB);

    assertEquals(correctJavaFileNameA, javaFileNameA);
    assertEquals(correctJavaFileNameB, javaFileNameB);
  }

}
