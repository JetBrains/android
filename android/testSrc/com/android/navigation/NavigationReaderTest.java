/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.navigation;

import junit.framework.TestCase;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class NavigationReaderTest extends TestCase {
  public void test1() throws FileNotFoundException {
    FileInputStream stream = new FileInputStream("../adt/idea/android/testData/resNavigation/res/layout/main.nav");
    XMLReader reader = new XMLReader(stream);
    Object result = reader.read();
    System.out.println("result = " + result);

    XMLWriter writer = new XMLWriter(System.out);
    writer.write(result);
  }

  /*
  public void test2() throws FileNotFoundException {
    FileInputStream stream = new FileInputStream("../adt/idea/android/testData/resNavigation/res/xml/test.xml");
    XMLReader reader = new XMLReader(stream);
    Object result = reader.read();
    System.out.println("result = " + result);
    assertTrue(result != null);
  }
  */

  //public static void main(String[] args) throws FileNotFoundException {
  //  new NavigationReaderTest().test1();
  //}
}
