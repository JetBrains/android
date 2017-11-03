/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.property.NlProperty;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mock;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class OnClickEnumSupportTest extends AndroidTestCase {
  @Mock
  private NlProperty myProperty;
  @Mock
  private NlModel myModel;
  @Mock
  private Configuration myConfiguration;

  private OnClickEnumSupport mySupport;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(myProperty.resolveValue(anyString())).thenAnswer(invocation -> invocation.getArguments()[0]);
    when(myProperty.getModel()).thenReturn(myModel);
    when(myModel.getModule()).thenReturn(myModule);
    when(myModel.getConfiguration()).thenReturn(myConfiguration);
    myFixture.addFileToProject("src/com/example/MainActivity.java", MAIN_ACTIVITY_SOURCE);
    myFixture.addFileToProject("src/com/example/OtherActivity.java", OTHER_ACTIVITY_SOURCE);
    myFixture.addFileToProject("res/layout/main.xml", MAIN_ACTIVITY_LAYOUT_SOURCE);
    myFixture.addFileToProject("res/layout/other.xml", OTHER_ACTIVITY_LAYOUT_SOURCE);
    myFixture.addFileToProject("AndroidManifest.xml", MANIFEST_SOURCE);
    mySupport = new OnClickEnumSupport(myProperty);
  }

  @Override
  public boolean providesCustomManifest() {
    return true;
  }

  public void testFindPossibleValuesWithKnownActivityName() {
    when(myConfiguration.getActivity()).thenReturn("com.example.MainActivity");
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("onClick", "onClick", "MainActivity"),
      new ValueWithDisplayString("help", "help", "MainActivity")).inOrder();
  }

  public void testFindPossibleValuesWithKnownRelativeActivityName() {
    when(myConfiguration.getActivity()).thenReturn(".MainActivity");
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("onClick", "onClick", "MainActivity"),
      new ValueWithDisplayString("help", "help", "MainActivity")).inOrder();
  }

  public void testFindPossibleValuesWithWrongActivityName() {
    when(myConfiguration.getActivity()).thenReturn(".WrongActivityName");
    assertThat(mySupport.getAllValues()).isEmpty();
  }

  public void testFindPossibleValuesWithUnknownActivityName() {
    assertThat(mySupport.getAllValues()).containsExactly(
      new ValueWithDisplayString("onClick", "onClick", "MainActivity"),
      new ValueWithDisplayString("help", "help", "MainActivity"),
      new ValueWithDisplayString("startProcessing", "startProcessing", "OtherActivity")).inOrder();
  }

  public void testCreateDefaultValue() {
    assertThat(mySupport.createValue(""))
      .isEqualTo(ValueWithDisplayString.UNSET);
  }

  public void testCreateUnknownValue() {
    assertThat(mySupport.createValue("onAccept"))
      .isEqualTo(new ValueWithDisplayString("onAccept", "onAccept"));
  }

  @Language("XML")
  private static final String MANIFEST_SOURCE =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<manifest  \n" +
    "    package='com.example'>\n" +
    "</manifest>\n";

  @Language("XML")
  private static final String MAIN_ACTIVITY_LAYOUT_SOURCE =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
    "                tools:context=\"com.example.MainActivity\">" +
    "  <TextView android:id=\"@+id/textView\"/>" +
    "</RelativeLayout>";

  @Language("XML")
  private static final String OTHER_ACTIVITY_LAYOUT_SOURCE =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" >" +
    "  <TextView android:id=\"@+id/textView\"/>" +
    "</RelativeLayout>";

  @Language("JAVA")
  private static final String MAIN_ACTIVITY_SOURCE =
    "package com.example;\n" +
    "\n" +
    "import android.app.Activity;\n" +
    "import android.os.Bundle;\n" +
    "import android.view.View;\n" +
    "\n" +
    "public class MainActivity extends Activity {\n" +
    "\n" +
    "    @Override\n" +
    "    protected void onCreate(Bundle savedInstanceState) {\n" +
    "        super.onCreate(savedInstanceState);\n" +
    "        setContentView(R.layout.activity_main);\n" +
    "    }\n" +
    "\n" +
    "    public void onClick(View view) {\n" +
    "\n" +
    "    }\n" +
    "\n" +
    "    public void help(View view) {\n" +
    "\n" +
    "    }\n" +
    "\n" +
    "}";

  @Language("JAVA")
  private static final String OTHER_ACTIVITY_SOURCE =
    "package com.example;\n" +
    "\n" +
    "import android.app.Activity;\n" +
    "import android.os.Bundle;\n" +
    "import android.view.View;\n" +
    "\n" +
    "public class OtherActivity extends Activity {\n" +
    "\n" +
    "    @Override\n" +
    "    protected void onCreate(Bundle savedInstanceState) {\n" +
    "        super.onCreate(savedInstanceState);\n" +
    "        setContentView(R.layout.activity_main);\n" +
    "    }\n" +
    "\n" +
    "    public void onClick(View view) {\n" +
    "\n" +
    "    }\n" +
    "\n" +
    "    public void startProcessing(View view) {\n" +
    "\n" +
    "    }\n" +
    "\n" +
    "}";
}
