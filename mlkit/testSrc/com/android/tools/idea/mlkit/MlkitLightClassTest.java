/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.google.common.collect.Iterables;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import java.io.File;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class MlkitLightClassTest extends AndroidTestCase {
  private VirtualFile myModelVirtualFile;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.override(true);
    StudioFlags.MLKIT_LIGHT_CLASSES.override(true);

    // Pull in tflite model, which has image(i.e. name: image1) as input tensor and labels as output tensor
    myFixture.setTestDataPath(new File(getModulePath("mlkit"), "testData").getPath());
    myModelVirtualFile = myFixture.copyFileToProject("my_model.tflite", "/assets/my_model.tflite");
    PsiTestUtil.addSourceContentToRoots(myModule, myModelVirtualFile.getParent());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.MLKIT_TFLITE_MODEL_FILE_TYPE.clearOverride();
      StudioFlags.MLKIT_LIGHT_CLASSES.clearOverride();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testHighlighting_java() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyModel;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import android.graphics.Bitmap;\n" +
      "import java.io.IOException;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MyModel myModel = new MyModel(this);\n" +
      "            Bitmap image = null;\n" +
      "            MyModel.Inputs inputs = myModel.createInputs();\n" +
      "            inputs.loadImage1(image);\n" +
      "            MyModel.Outputs output = myModel.run(inputs);\n" +
      "            Map<String, Float> probability = output.getProbability();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_kotlin() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.kt",
      // language=kotlin
      "package p1.p2\n" +
      "\n" +
      "import android.app.Activity\n" +
      "import android.os.Bundle\n" +
      "import p1.p2.ml.MyModel\n" +
      "import android.util.Log\n" +
      "import android.graphics.Bitmap\n" +
      "\n" +
      "class MainActivity : Activity() {\n" +
      "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
      "        super.onCreate(savedInstanceState)\n" +
      "        val image: Bitmap? = null\n" +
      "        val mymodel = MyModel(this)\n" +
      "        val inputs = mymodel.createInputs()\n" +
      "        inputs.loadImage1(image)\n" +
      "        val outputs = mymodel.run(inputs)\n" +
      "        val probability = outputs.probability\n" +
      "        Log.d(\"TAG\", probability.toString())\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testLightModelClassNavigation() {
    // Below is the workaround to make MockFileDocumentManagerImpl#getDocument return a non-null value for a model file, so the non-null
    // document assertion in TestEditorManagerImpl#doOpenTextEditor could pass.
    BinaryFileTypeDecompilers.getInstance().getPoint().registerExtension(
      new FileTypeExtensionPoint<>(TfliteModelFileType.INSTANCE.getName(), new BinaryFileDecompiler() {
        @NotNull
        @Override
        public CharSequence decompile(@NotNull VirtualFile file) {
          return "Model summary info.";
        }
      }),
      getProject());

    AndroidTestUtils.loadNewFile(myFixture,
                                 "/src/p1/p2/MainActivity.java",
                                 "package p1.p2;\n" +
                                 "\n" +
                                 "import android.app.Activity;\n" +
                                 "import android.os.Bundle;\n" +
                                 "import p1.p2.ml.MyModel;\n" +
                                 "\n" +
                                 "public class MainActivity extends Activity {\n" +
                                 "    @Override\n" +
                                 "    protected void onCreate(Bundle savedInstanceState) {\n" +
                                 "        super.onCreate(savedInstanceState);\n" +
                                 "        My<caret>Model myModel = new MyModel(this);\n" +
                                 "    }\n" +
                                 "}"
    );

    AndroidTestUtils.goToElementAtCaret(myFixture);
    assertThat(FileEditorManagerEx.getInstanceEx(myFixture.getProject()).getCurrentFile()).isEqualTo(myModelVirtualFile);
  }


  public void testCompleteRunMethod() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = new MyModel(this);\n" +
      "        myModel.ru<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResult(
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = new MyModel(this);\n" +
      "        myModel.run();\n" +
      "    }\n" +
      "}");
  }

  public void testCompleteCreateInputsMethod() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = new MyModel(this);\n" +
      "        myModel.c<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResult(
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel myModel = new MyModel(this);\n" +
      "        myModel.createInputs();\n" +
      "    }\n" +
      "}");
  }

  public void testCompleteInnerClass() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyModel.<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    assertThat(elements).hasLength(2);
    assertThat(elements[0].toString()).isEqualTo("MyModel.Inputs");
    assertThat(elements[1].toString()).isEqualTo("MyModel.Outputs");

    myFixture.getLookup().setCurrentItem(elements[0]);
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult("package p1.p2;\n" +
                          "\n" +
                          "import android.app.Activity;\n" +
                          "import android.os.Bundle;\n" +
                          "\n" +
                          "import p1.p2.ml.MyModel;\n" +
                          "\n" +
                          "public class MainActivity extends Activity {\n" +
                          "    @Override\n" +
                          "    protected void onCreate(Bundle savedInstanceState) {\n" +
                          "        super.onCreate(savedInstanceState);\n" +
                          "        MyModel.Inputs;\n" +
                          "    }\n" +
                          "}");
  }

  public void testCompleteModelClass() {
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        MyMod<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    assertThat(elements).hasLength(1);
    assertThat(elements[0].getLookupString()).isEqualTo("MyModel");

    myFixture.getLookup().setCurrentItem(elements[0]);
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    myFixture.checkResult("package p1.p2;\n" +
                          "\n" +
                          "import android.app.Activity;\n" +
                          "import android.os.Bundle;\n" +
                          "\n" +
                          "import p1.p2.ml.MyModel;\n" +
                          "\n" +
                          "public class MainActivity extends Activity {\n" +
                          "    @Override\n" +
                          "    protected void onCreate(Bundle savedInstanceState) {\n" +
                          "        super.onCreate(savedInstanceState);\n" +
                          "        MyModel;\n" +
                          "    }\n" +
                          "}");
  }

  public void testModuleService() {
    MlkitModuleService mlkitService = MlkitModuleService.getInstance(myModule);
    List<PsiClass> lightClasses = mlkitService.getLightModelClassList();
    assertThat(lightClasses).hasSize(1);
    PsiClass lightClass = Iterables.getOnlyElement(lightClasses);
    assertThat(lightClass.getName()).isEqualTo("MyModel");
    assertThat(ModuleUtilCore.findModuleForPsiElement(lightClass)).isEqualTo(myModule);
  }
}
