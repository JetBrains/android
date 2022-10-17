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

import static com.android.tools.idea.mlkit.MlProjectTestUtil.setupTestMlProject;
import static com.android.tools.idea.projectsystem.ModuleSystemUtil.getMainModule;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.containers.ContainerUtil.map;

import com.android.testutils.TestUtils;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.android.tools.idea.mlkit.viewer.TfliteModelFileType;
import com.android.tools.idea.project.DefaultModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileTypes.BinaryFileDecompiler;
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.PersistentFSConstants;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.containers.ContainerUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class MlLightClassTest extends AndroidTestCase {

  private final static String AGP_VERSION_SUPPORTING_ML = "4.2.0-alpha8";
  private final static String AGP_VERSION_NOT_SUPPORTING_ML = "4.2.0-alpha7";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    StudioFlags.ML_MODEL_BINDING.override(true);

    // ML model size is over default 2.5 MiB
    PersistentFSConstants.setMaxIntellisenseFileSize(100_000_000);

    ((DefaultModuleSystem)ProjectSystemUtil.getModuleSystem(myModule)).setMlModelBindingEnabled(true);

    // Pull in tflite model, which has image(i.e. name: image1) as input tensor and labels as output tensor
    myFixture.setTestDataPath(TestUtils.resolveWorkspacePath("prebuilts/tools/common/mlkit/testData/models").toString());

    // Mock TensorImage, TensorBuffer and Category
    myFixture.addFileToProject("src/org/tensorflow/lite/support/image/TensorImage.java",
                               "package org.tensorflow.lite.support.image; public class TensorImage {}");
    myFixture.addFileToProject("src/org/tensorflow/lite/support/tensorbuffer/TensorBuffer.java",
                               "package org.tensorflow.lite.support.tensorbuffer; public class TensorBuffer {}");
    myFixture.addFileToProject("src/org/tensorflow/lite/support/model/Model.java",
                               "package org.tensorflow.lite.support.model; public class Model { public static class Options {} }");
    myFixture.addFileToProject("src/org/tensorflow/lite/support/label/Category.java",
                               "package org.tensorflow.lite.support.label; public class Category {}");
  }

  private void setupProject(String version) {
    myFixture = setupTestMlProject(myFixture, version, 28);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.ML_MODEL_BINDING.clearOverride();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testHighlighting_java() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/mobilenet_model.tflite");
    myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/sub/mobilenet_model.tflite");
    myFixture.copyFileToProject("style_transfer_quant_metadata.tflite", "ml/style_transfer_model.tflite");
    myFixture.copyFileToProject("ssd_mobilenet_odt_metadata_v1.2.tflite", "ml/ssd_model_v2.tflite");
    VirtualFile ssdModelFile = myFixture.copyFileToProject("ssd_mobilenet_odt_metadata.tflite", "ml/ssd_model.tflite");


    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import android.graphics.RectF;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import java.util.List;\n" +
      "import org.tensorflow.lite.support.image.TensorImage;\n" +
      "import org.tensorflow.lite.support.label.Category;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "import org.tensorflow.lite.support.model.Model;\n" +
      "import java.io.IOException;\n" +
      "import p1.p2.ml.MobilenetModel;\n" +
      "import p1.p2.ml.MobilenetModel219;\n" +
      "import p1.p2.ml.SsdModel;\n" +
      "import p1.p2.ml.SsdModelV2;\n" +
      "import p1.p2.ml.StyleTransferModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            Model.Options options = new Model.Options();\n" +
      "            MobilenetModel mobilenetModel = MobilenetModel.newInstance(this);\n" +
      "            TensorImage image = new TensorImage();\n" +
      "            TensorBuffer buffer = new TensorBuffer();\n" +
      "            MobilenetModel.Outputs mobilenetOutputs = mobilenetModel.process(image);\n" +
      "            mobilenetOutputs = mobilenetModel.process(buffer);\n" +
      "            List<Category> categoryList = mobilenetOutputs.getProbabilityAsCategoryList();\n" +
      "            TensorBuffer categoryBuffer = mobilenetOutputs.getProbabilityAsTensorBuffer();\n" +
      "            mobilenetModel.close();\n" +
      "\n" +
      "            MobilenetModel219 mobilenetModel219 = MobilenetModel219.newInstance(this, options);\n" +
      "            MobilenetModel219.Outputs mobilenetOutputs2 = mobilenetModel219.process(image);\n" +
      "            mobilenetOutputs2 = mobilenetModel219.process(buffer);\n" +
      "            List<Category> categoryList2 = mobilenetOutputs2.getProbabilityAsCategoryList();\n" +
      "            TensorBuffer categoryBuffer2 = mobilenetOutputs2.getProbabilityAsTensorBuffer();\n" +
      "            mobilenetModel219.close();\n" +
      "\n" +
      "            SsdModel ssdModel = SsdModel.newInstance(this);\n" +
      "            SsdModel.Outputs ssdOutputs = ssdModel.process(image);\n" +
      "            ssdOutputs = ssdModel.process(buffer);\n" +
      "            TensorBuffer locations = ssdOutputs.getLocationsAsTensorBuffer();\n" +
      "            TensorBuffer classes = ssdOutputs.getClassesAsTensorBuffer();\n" +
      "            TensorBuffer scores = ssdOutputs.getScoresAsTensorBuffer();\n" +
      "            TensorBuffer numberofdetections = ssdOutputs.getNumberOfDetectionsAsTensorBuffer();\n" +
      "            ssdModel.close();\n" +
      "\n" +
      "            SsdModelV2 ssdModelV2 = SsdModelV2.newInstance(this);\n" +
      "            SsdModelV2.Outputs ssdOutputsV2 = ssdModelV2.process(image);\n" +
      "            ssdOutputsV2 = ssdModelV2.process(buffer);\n" +
      "            TensorBuffer locationsV2 = ssdOutputsV2.getLocationsAsTensorBuffer();\n" +
      "            TensorBuffer classesV2 = ssdOutputsV2.getClassesAsTensorBuffer();\n" +
      "            TensorBuffer scoresV2 = ssdOutputsV2.getScoresAsTensorBuffer();\n" +
      "            TensorBuffer numberofdetectionsV2 = ssdOutputsV2.getNumberOfDetectionsAsTensorBuffer();\n" +
      "            List<SsdModelV2.DetectionResult> results = ssdOutputsV2.getDetectionResultList();\n" +
      "            SsdModelV2.DetectionResult result = results.get(0);\n" +
      "            String label = result.getClassesAsString();\n" +
      "            RectF boundingBox = result.getLocationsAsRectF();\n" +
      "            float score = result.getScoresAsFloat();\n" +
      "            ssdModel.close();\n" +
      "\n" +
      "            TensorBuffer stylearray = null;\n" +
      "            StyleTransferModel styleTransferModel = StyleTransferModel.newInstance(this, options);\n" +
      "            StyleTransferModel.Outputs outputs = styleTransferModel.process(image, stylearray);\n" +
      "            outputs = styleTransferModel.process(buffer, stylearray);\n" +
      "            TensorImage styledimage = outputs.getStyledImageAsTensorImage();" +
      "            TensorBuffer styledimageBuffer = outputs.getStyledImageAsTensorBuffer();" +
      "            styleTransferModel.close();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_newAPINotExistInLowAGP_java() {
    setupProject(AGP_VERSION_NOT_SUPPORTING_ML);
    VirtualFile ssdModelFile = myFixture.copyFileToProject("ssd_mobilenet_odt_metadata_v1.2.tflite", "ml/ssd_model_v2.tflite");

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import android.graphics.RectF;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import java.util.List;\n" +
      "import org.tensorflow.lite.support.image.TensorImage;\n" +
      "import org.tensorflow.lite.support.label.Category;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "import org.tensorflow.lite.support.model.Model;\n" +
      "import java.io.IOException;\n" +
      "import p1.p2.ml.SsdModelV2;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            Model.Options options = new Model.Options();\n" +
      "            TensorImage image = new TensorImage();\n" +
      "            TensorBuffer buffer = new TensorBuffer();\n" +
      "\n" +
      "            SsdModelV2 ssdModelV2 = SsdModelV2.newInstance(this);\n" +
      "            SsdModelV2.Outputs ssdOutputsV2 = ssdModelV2.process<error descr=\"'process(org.tensorflow.lite.support.tensorbuffer.TensorBuffer)' in 'p1.p2.ml.SsdModelV2' cannot be applied to '(org.tensorflow.lite.support.image.TensorImage)'\">(image)</error>;\n" +
      "            ssdOutputsV2 = ssdModelV2.process(buffer);\n" +
      "            TensorBuffer locationsV2 = ssdOutputsV2.getLocationsAsTensorBuffer();\n" +
      "            TensorBuffer classesV2 = ssdOutputsV2.getClassesAsTensorBuffer();\n" +
      "            TensorBuffer scoresV2 = ssdOutputsV2.getScoresAsTensorBuffer();\n" +
      "            TensorBuffer numberofdetectionsV2 = ssdOutputsV2.getNumberOfDetectionsAsTensorBuffer();\n" +
      "            List<SsdModelV2.<error descr=\"Cannot resolve symbol 'DetectionResult'\">DetectionResult</error>> results = ssdOutputsV2.<error descr=\"Cannot resolve method 'getDetectionResultList' in 'Outputs'\">getDetectionResultList</error>();\n" +
      "            ssdModelV2.close();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_modelWithoutMetadata_java() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "ml/my_plain_model.tflite");
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyPlainModel;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import android.graphics.Bitmap;\n" +
      "import java.nio.ByteBuffer;\n" +
      "import java.io.IOException;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MyPlainModel myModel = MyPlainModel.newInstance(this);\n" +
      "            TensorBuffer tensorBuffer = null;\n" +
      "            MyPlainModel.Outputs output = myModel.process(tensorBuffer);\n" +
      "            TensorBuffer data0 = output.getOutputFeature0AsTensorBuffer();\n" +
      "            myModel.close();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_modelWithV2Metadata_java() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata_v2.tflite", "ml/my_model_v2.tflite");
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyModelV2;\n" +
      "import java.io.IOException;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MyModelV2 myModel = MyModelV2.newInstance(this);\n" +
      "            TensorBuffer tensorBuffer = null;\n" +
      "            MyModelV2.Outputs output = myModel.process(tensorBuffer);\n" +
      "            TensorBuffer data0 = output.getProbabilityAsTensorBuffer();\n" +
      "            myModel.close();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_invokeConstructorThrowError_java() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "ml/my_plain_model.tflite");
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyPlainModel;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import android.graphics.Bitmap;\n" +
      "import java.nio.ByteBuffer;\n" +
      "import java.io.IOException;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MyPlainModel myModel = new <error descr=\"'MyPlainModel(android.content.Context)' has private access in 'p1.p2.ml.MyPlainModel'\">MyPlainModel</error>();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_invokeConstructorWithContextThrowError_java() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "ml/my_plain_model.tflite");
    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import p1.p2.ml.MyPlainModel;\n" +
      "import java.lang.String;\n" +
      "import java.lang.Float;\n" +
      "import java.util.Map;\n" +
      "import android.graphics.Bitmap;\n" +
      "import java.nio.ByteBuffer;\n" +
      "import java.io.IOException;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MyPlainModel myModel = new <error descr=\"'MyPlainModel(android.content.Context)' has private access in 'p1.p2.ml.MyPlainModel'\">MyPlainModel</error>(this);\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_kotlin() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/mobilenet_model.tflite");
    myFixture.copyFileToProject("style_transfer_quant_metadata.tflite", "ml/style_transfer_model.tflite");
    myFixture.copyFileToProject("ssd_mobilenet_odt_metadata_v1.2.tflite", "ml/ssd_model_v2.tflite");
    VirtualFile ssdModelFile = myFixture.copyFileToProject("ssd_mobilenet_odt_metadata.tflite", "ml/ssd_model.tflite");

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.kt",
      // language=kotlin
      "package p1.p2\n" +
      "\n" +
      "import android.app.Activity\n" +
      "import android.os.Bundle\n" +
      "import android.graphics.RectF\n" +
      "import android.util.Log\n" +
      "import kotlin.collections.List\n" +
      "import org.tensorflow.lite.support.image.TensorImage\n" +
      "import org.tensorflow.lite.support.label.Category\n" +
      "import org.tensorflow.lite.support.model.Model\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer\n" +
      "import p1.p2.ml.MobilenetModel\n" +
      "import p1.p2.ml.SsdModel\n" +
      "import p1.p2.ml.SsdModelV2\n" +
      "import p1.p2.ml.StyleTransferModel\n" +
      "\n" +
      "class MainActivity : Activity() {\n" +
      "    @Suppress(\"DEPRECATION\", \"UNUSED_VARIABLE\")" +
      "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
      "        super.onCreate(savedInstanceState)\n" +
      "        val options = Model.Options()\n" +
      "        val tensorImage = TensorImage()\n" +
      "        val tensorBuffer = TensorBuffer()\n" +
      "\n" +
      "        val mobilenetModel : MobilenetModel = MobilenetModel.newInstance(this)\n" +
      "        val mobilenetOutputs : MobilenetModel.Outputs = mobilenetModel.process(tensorImage)\n" +
      "        val mobilenetOutputs2 : MobilenetModel.Outputs = mobilenetModel.process(tensorBuffer)\n" +
      "        val probability : List<Category> = mobilenetOutputs.probabilityAsCategoryList\n" +
      "        val probabilityBuffer : TensorBuffer = mobilenetOutputs.probabilityAsTensorBuffer\n" +
      "        mobilenetModel.close()\n" +
      "\n" +
      "        val ssdModel : SsdModel = SsdModel.newInstance(this, options)\n" +
      "        val ssdOutputs : SsdModel.Outputs = ssdModel.process(tensorImage)\n" +
      "        val ssdOutputs2 : SsdModel.Outputs = ssdModel.process(tensorBuffer)\n" +
      "        val locations : TensorBuffer = ssdOutputs.locationsAsTensorBuffer\n" +
      "        val classes : TensorBuffer = ssdOutputs.classesAsTensorBuffer\n" +
      "        val scores : TensorBuffer = ssdOutputs.scoresAsTensorBuffer\n" +
      "        val numberofdetections : TensorBuffer = ssdOutputs.numberOfDetectionsAsTensorBuffer\n" +
      "        ssdModel.close()\n" +
      "\n" +
      "        val ssdModelV2: SsdModelV2 = SsdModelV2.newInstance(this);\n" +
      "        val ssdOutputsV2: SsdModelV2.Outputs = ssdModelV2.process(tensorImage);\n" +
      "        val ssdOutputsFromBuffer: SsdModelV2.Outputs = ssdModelV2.process(tensorBuffer);\n" +
      "        val locationsV2: TensorBuffer  = ssdOutputsV2.getLocationsAsTensorBuffer();\n" +
      "        val classesV2: TensorBuffer = ssdOutputsV2.getClassesAsTensorBuffer();\n" +
      "        val scoresV2: TensorBuffer = ssdOutputsV2.getScoresAsTensorBuffer();\n" +
      "        val numberofdetectionsV2: TensorBuffer  = ssdOutputsV2.getNumberOfDetectionsAsTensorBuffer();\n" +
      "        val results:List<SsdModelV2.DetectionResult> = ssdOutputsV2.getDetectionResultList();\n" +
      "        val result: SsdModelV2.DetectionResult = results.get(0);\n" +
      "        val label: String = result.getClassesAsString();\n" +
      "        val boundingBox: RectF = result.getLocationsAsRectF();\n" +
      "        val score = result.getScoresAsFloat();\n" +
      "        ssdModel.close();\n" +
      "\n" +
      "        val styleTransferModel : StyleTransferModel = StyleTransferModel.newInstance(this, options)\n" +
      "        val styleTransferOutputs : StyleTransferModel.Outputs = styleTransferModel.process(tensorImage, tensorBuffer)\n" +
      "        val styleTransferOutputs2 : StyleTransferModel.Outputs = styleTransferModel.process(tensorBuffer, tensorBuffer)\n" +
      "        val styledImage : TensorImage = styleTransferOutputs.styledImageAsTensorImage\n" +
      "        val styledImageBuffer : TensorBuffer = styleTransferOutputs.styledImageAsTensorBuffer\n" +
      "        styleTransferModel.close()\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_modelWithoutMetadata_kotlin() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "ml/my_plain_model.tflite");

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.kt",
      // language=kotlin
      "package p1.p2\n" +
      "\n" +
      "import android.app.Activity\n" +
      "import android.os.Bundle\n" +
      "import p1.p2.ml.MyPlainModel\n" +
      "import android.util.Log\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "\n" +
      "class MainActivity : Activity() {\n" +
      "    override fun onCreate(savedInstanceState: Bundle?) {\n" +
      "        super.onCreate(savedInstanceState)\n" +
      "        val inputFeature = TensorBuffer()\n" +
      "        val mymodel = MyPlainModel.newInstance(this)\n" +
      "        val outputs = mymodel.process(inputFeature)\n" +
      "        val outputFeature = outputs.outputFeature0AsTensorBuffer\n" +
      "        Log.d(\"TAG\", outputFeature.toString())\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testHighlighting_modelFileOverwriting() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    String targetModelFilePath = "ml/my_model.tflite";
    VirtualFile modelFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", targetModelFilePath);

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import java.lang.String;\n" +
      "import java.util.Map;\n" +
      "import java.util.List;\n" +
      "import org.tensorflow.lite.support.image.TensorImage;\n" +
      "import org.tensorflow.lite.support.label.Category;\n" +
      "import java.io.IOException;\n" +
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MyModel myModel = MyModel.newInstance(this);\n" +
      "            TensorImage image = null;\n" +
      "            MyModel.Outputs outputs = myModel.process(image);\n" +
      "            List<Category> categoryList = outputs.getProbabilityAsCategoryList();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();

    // Overwrites the target model file and then verify the light class gets updated.
    modelFile = myFixture.copyFileToProject("style_transfer_quant_metadata.tflite", targetModelFilePath);

    activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity2.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import java.lang.String;\n" +
      "import java.util.Map;\n" +
      "import org.tensorflow.lite.support.image.TensorImage;\n" +
      "import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;\n" +
      "import java.io.IOException;\n" +
      "import p1.p2.ml.MyModel;\n" +
      "\n" +
      "public class MainActivity2 extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            TensorImage image = null;\n" +
      "            TensorBuffer stylearray = null;\n" +
      "            MyModel myModel = MyModel.newInstance(this);\n" +
      "            MyModel.Outputs outputs = myModel.process(image, stylearray);\n" +
      "            TensorImage styledimage = outputs.getStyledImageAsTensorImage();\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();
  }

  public void testLightModelClassNavigation() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");

    // Below is the workaround to make MockFileDocumentManagerImpl#getDocument return a non-null value for a model file, so the non-null
    // document assertion in TestEditorManagerImpl#doOpenTextEditor could pass.
    FileTypeExtensionPoint<BinaryFileDecompiler> extension =
      new FileTypeExtensionPoint<>(TfliteModelFileType.INSTANCE.getName(), new BinaryFileDecompiler() {
        @NotNull
        @Override
        public CharSequence decompile(@NotNull VirtualFile file) {
          return "Model summary info.";
        }
      });
    extension.setPluginDescriptor(new DefaultPluginDescriptor("test"));
    BinaryFileTypeDecompilers.getInstance().getPoint().registerExtension(extension, getProject());

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
    assertThat(FileEditorManagerEx.getInstanceEx(myFixture.getProject()).getCurrentFile()).isEqualTo(modelVirtualFile);
  }

  public void testCompleteProcessMethod() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");

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
      "        MyModel myModel = MyModel.newInstance(this);\n" +
      "        myModel.pro<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.complete(CompletionType.SMART);
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
      "        MyModel myModel = MyModel.newInstance(this);\n" +
      "        myModel.process();\n" +
      "    }\n" +
      "}");
  }

  public void testCompleteNewInstanceMethod() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");

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
      "        MyModel.newI<caret>;\n" +
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
      "        MyModel.newInstance();\n" +
      "    }\n" +
      "}");
  }

  public void testCompleteInnerClass() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");

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
    assertThat(elements).hasLength(3);
    Optional<LookupElement> element = Arrays.asList(elements).stream()
      .filter(element1 -> element1.toString().equals("MyModel.Outputs")).findFirst();
    assertThat(element.isPresent()).isTrue();;

    myFixture.getLookup().setCurrentItem(element.get());
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
                          "        MyModel.Outputs;\n" +
                          "    }\n" +
                          "}");
  }

  public void testCompleteInnerInputClassWithoutOuterClass() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");

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
      "        Outpu<caret>;\n" +
      "    }\n" +
      "}"
    );

    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);

    // Find position of "Outputs"
    int lookupPosition = -1;
    for (int i = 0; i < elements.length; i++) {
      if (elements[i].toString().equals("Outputs")) {
        lookupPosition = i;
        break;
      }
    }
    assertThat(lookupPosition).isGreaterThan(-1);

    myFixture.getLookup().setCurrentItem(elements[lookupPosition]);
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
                          "        MyModel.Outputs;\n" +
                          "    }\n" +
                          "}");
  }


  public void testCompleteModelClass() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");

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
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");
    myFixture.copyFileToProject("mobilenet_quant_no_metadata.tflite", "ml/my_plain_model.tflite");

    MlModuleService mlkitService = MlModuleService.getInstance(getMainModule(myModule));
    List<LightModelClass> lightClasses = mlkitService.getLightModelClassList();
    List<String> classNameList = map(lightClasses, psiClass -> psiClass.getName());
    assertThat(classNameList).containsExactly("MyModel", "MyPlainModel");
    assertThat(ModuleUtilCore.findModuleForPsiElement(lightClasses.get(0))).isEqualTo(getMainModule(myModule));
  }

  public void testFallbackApisAreDeprecated() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");

    MlModuleService mlkitService = MlModuleService.getInstance(getMainModule(myModule));
    List<LightModelClass> lightClasses = mlkitService.getLightModelClassList();
    assertThat(lightClasses).hasSize(1);

    LightModelClass lightModelClass = lightClasses.get(0);
    List<PsiMethod> deprecatedProcessMethods =
      ContainerUtil.filter(Arrays.asList(lightModelClass.getMethods()), method -> method.isDeprecated());
    assertThat(deprecatedProcessMethods).hasSize(1);
    assertThat(deprecatedProcessMethods.get(0).getName()).isEqualTo("process");
    List<PsiMethod> deprecatedGetMethods =
      ContainerUtil.filter(Arrays.asList(lightModelClass.getInnerClasses()[0].getMethods()), method -> method.isDeprecated());
    assertThat(deprecatedGetMethods).hasSize(1);
    assertThat(deprecatedGetMethods.get(0).getName()).isEqualTo("getProbabilityAsTensorBuffer");
  }

  public void testBrokenFiles() {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    VirtualFile modelVirtualFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/my_model.tflite");
    VfsTestUtil.createFile(ProjectUtil.guessModuleDir(myModule), "ml/broken.tflite", new byte[]{1, 2, 3});

    GlobalSearchScope searchScope = myFixture.addFileToProject("src/MainActivity.java", "public class MainActivity {}").getResolveScope();
    assertThat(myFixture.getJavaFacade().findClass("p1.p2.ml.MyModel", searchScope))
      .named("Class for valid model")
      .isNotNull();
    assertThat(myFixture.getJavaFacade().findClass("p1.p2.ml.Broken", searchScope))
      .named("Class for invalid model")
      .isNull();
  }

  public void testModelApiGenEventIsLogged() throws Exception {
    setupProject(AGP_VERSION_SUPPORTING_ML);
    TestUsageTracker usageTracker = new TestUsageTracker(new VirtualTimeScheduler());
    UsageTracker.setWriterForTest(usageTracker);

    VirtualFile mobilenetModelFile = myFixture.copyFileToProject("mobilenet_quant_metadata.tflite", "ml/mobilenet_model.tflite");

    PsiFile activityFile = myFixture.addFileToProject(
      "/src/p1/p2/MainActivity.java",
      // language=java
      "package p1.p2;\n" +
      "\n" +
      "import android.app.Activity;\n" +
      "import android.os.Bundle;\n" +
      "import java.io.IOException;\n" +
      "import p1.p2.ml.MobilenetModel;\n" +
      "\n" +
      "public class MainActivity extends Activity {\n" +
      "    @Override\n" +
      "    protected void onCreate(Bundle savedInstanceState) {\n" +
      "        super.onCreate(savedInstanceState);\n" +
      "        try {\n" +
      "            MobilenetModel mobilenetModel = MobilenetModel.newInstance(this);\n" +
      "        } catch (IOException e) {};\n" +
      "    }\n" +
      "}"
    );
    myFixture.configureFromExistingVirtualFile(activityFile.getVirtualFile());
    myFixture.checkHighlighting();

    List<MlModelBindingEvent> loggedUsageList =
      usageTracker.getUsages().stream()
        .filter(it -> it.getStudioEvent().getKind() == AndroidStudioEvent.EventKind.ML_MODEL_BINDING)
        .map(usage -> usage.getStudioEvent().getMlModelBindingEvent())
        .filter(it -> it.getEventType() == MlModelBindingEvent.EventType.MODEL_API_GEN)
        .collect(Collectors.toList());
    assertThat(loggedUsageList.size()).isEqualTo(1);

    UsageTracker.cleanAfterTesting();
    usageTracker.close();
  }
}
