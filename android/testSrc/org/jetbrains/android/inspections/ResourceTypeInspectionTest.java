/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.inspections;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.tools.idea.startup.ExternalAnnotationsSupport;
import com.google.common.collect.Lists;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.LightInspectionTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("StatementWithEmptyBody")
public class ResourceTypeInspectionTest extends LightInspectionTestCase {

  private String myOldCharset;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    //noinspection StatementWithEmptyBody
    if (getName().equals("testNotAndroid")) {
      // Don't add an Android facet here; we're testing that we're a no-op outside of Android projects
      // since the inspection is registered at the .java source type level
      return;
    }

    // Module must have Android facet or resource type inspection will become a no-op
    if (AndroidFacet.getInstance(myModule) == null) {
      String sdkPath = AndroidTestBase.getDefaultTestSdkPath();
      String platform = AndroidTestBase.getDefaultPlatformDir();
      AndroidTestCase.addAndroidFacet(myModule, sdkPath, platform, true);
      Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
      assertNotNull(sdk);
      @SuppressWarnings("SpellCheckingInspection") SdkModificator sdkModificator = sdk.getSdkModificator();
      ExternalAnnotationsSupport.attachJdkAnnotations(sdkModificator);
      sdkModificator.commitChanges();
    }

    // Required by testLibraryRevocablePermissions (but placing it there leads to
    // test ordering issues)
    myFixture.addFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML,
                               "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                               "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                               "    package=\"test.pkg.permissiontest\">\n" +
                               "\n" +
                               "    <uses-sdk android:minSdkVersion=\"17\" android:targetSdkVersion=\"23\" />" +
                               "\n" +
                               "    <permission\n" +
                               "        android:name=\"my.normal.P1\"\n" +
                               "        android:protectionLevel=\"normal\" />\n" +
                               "\n" +
                               "    <permission\n" +
                               "        android:name=\"my.dangerous.P2\"\n" +
                               "        android:protectionLevel=\"dangerous\" />\n" +
                               "\n" +
                               "    <uses-permission android:name=\"my.normal.P1\" />\n" +
                               "    <uses-permission android:name=\"my.dangerous.P2\" />\n" +
                               "\n" +
                               "</manifest>\n");
    myOldCharset = EncodingProjectManager.getInstance(getProject()).getDefaultCharsetName();
    EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName("UTF-8");
  }

  @Override
  public void tearDown() throws Exception {
    try {
      EncodingProjectManager.getInstance(getProject()).setDefaultCharsetName(myOldCharset);
    }
    finally{
      super.tearDown();
    }
  }

  public void testTypes() {
    doCheck("import android.annotation.SuppressLint;\n" +
            "import android.annotation.TargetApi;\n" +
            "import android.app.Notification;\n" +
            "import android.content.Context;\n" +
            "import android.content.Intent;\n" +
            "import android.content.ServiceConnection;\n" +
            "import android.content.res.Resources;\n" +
            "import android.os.Build;\n" +
            "import android.support.annotation.DrawableRes;\n" +
            "import android.view.View;\n" +
            "\n" +
            "import static android.content.Context.CONNECTIVITY_SERVICE;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class X {\n" +
            "    public void testResourceTypeParameters(Context context, int unknown) {\n" +
            "        Resources resources = context.getResources();\n" +
            "        String ok1 = resources.getString(R.string.app_name);\n" +
            "        String ok2 = resources.getString(unknown);\n" +
            "        String ok3 = resources.getString(android.R.string.ok);\n" +
            "        int ok4 = resources.getColor(android.R.color.black);\n" +
            "        if (testResourceTypeReturnValues(context, true) == R.drawable.ic_launcher) { // ok\n" +
            "        }\n" +
            "\n" +
            "        //String ok2 = resources.getString(R.string.app_name, 1, 2, 3);\n" +
            "        float error1 = resources.getDimension(/*Expected resource of type dimen*/R.string.app_name/**/);\n" +
            "        boolean error2 = resources.getBoolean(/*Expected resource of type bool*/R.string.app_name/**/);\n" +
            "        boolean error3 = resources.getBoolean(/*Expected resource of type bool*/android.R.drawable.btn_star/**/);\n" +
            "        if (testResourceTypeReturnValues(context, true) == /*Expected resource of type drawable*/R.string.app_name/**/) {\n" +
            "        }\n" +
            "        @SuppressWarnings(\"UnnecessaryLocalVariable\")\n" +
            "        int flow = R.string.app_name;\n" +
            "        @SuppressWarnings(\"UnnecessaryLocalVariable\")\n" +
            "        int flow2 = flow;\n" +
            "        boolean error4 = resources.getBoolean(/*Expected resource of type bool*/flow2/**/);\n" +
            "    }\n" +
            "\n" +
            "    @android.support.annotation.DrawableRes\n" +
            "    public int testResourceTypeReturnValues(Context context, boolean useString) {\n" +
            "        if (useString) {\n" +
            "            return /*Expected resource of type drawable*/R.string.app_name/**/; // error\n" +
            "        } else {\n" +
            "            return R.drawable.ic_launcher; // ok\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static final class R {\n" +
            "        public static final class drawable {\n" +
            "            public static final int ic_launcher=0x7f020057;\n" +
            "        }\n" +
            "        public static final class string {\n" +
            "            public static final int app_name=0x7f0a000e;\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  public void testTypes2() {
        doCheck("package test.pkg;\n" +
                "\n" +
                "import android.support.annotation.DrawableRes;\n" +
                "import android.support.annotation.StringRes;\n" +
                "\n" +
                "enum X {\n" +
                "\n" +
                "    SKI(/*Expected resource of type drawable*/1/**/, /*Expected resource of type string*/2/**/),\n" +
                "    SNOWBOARD(/*Expected resource of type drawable*/3/**/, /*Expected resource of type string*/4/**/);\n" +
                "\n" +
                "    private final int mIconResId;\n" +
                "    private final int mLabelResId;\n" +
                "\n" +
                "    X(@DrawableRes int iconResId, @StringRes int labelResId) {\n" +
                "        mIconResId = iconResId;\n" +
                "        mLabelResId = labelResId;\n" +
                "    }\n" +
                "\n" +
                "}");
  }

  public void testIntDef() {
    doCheck("import android.annotation.SuppressLint;\n" +
            "import android.annotation.TargetApi;\n" +
            "import android.app.Notification;\n" +
            "import android.content.Context;\n" +
            "import android.content.Intent;\n" +
            "import android.content.ServiceConnection;\n" +
            "import android.content.res.Resources;\n" +
            "import android.os.Build;\n" +
            "import android.support.annotation.DrawableRes;\n" +
            "import android.view.View;\n" +
            "\n" +
            "import static android.content.Context.CONNECTIVITY_SERVICE;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class X {\n" +
            "\n" +
            "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
            "    public void testStringDef(Context context, String unknown) {\n" +
            "        Object ok1 = context.getSystemService(unknown);\n" +
            "        Object ok2 = context.getSystemService(Context.CLIPBOARD_SERVICE);\n" +
            "        Object ok3 = context.getSystemService(android.content.Context.WINDOW_SERVICE);\n" +
            "        Object ok4 = context.getSystemService(CONNECTIVITY_SERVICE);\n" +
            "    }\n" +
            "\n" +
            "    @SuppressLint(\"UseCheckPermission\")\n" +
            "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
            "    public void testIntDef(Context context, int unknown, View view) {\n" +
            "        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); // OK\n" +
            "        view.setLayoutDirection(/*Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE*/View.TEXT_ALIGNMENT_TEXT_START/**/); // Error\n" +
            "        view.setLayoutDirection(/*Must be one of: View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_INHERIT, View.LAYOUT_DIRECTION_LOCALE*/View.LAYOUT_DIRECTION_RTL | View.LAYOUT_DIRECTION_RTL/**/); // Error\n" +
            "\n" +
            "        // Regression test for http://b.android.com/197184\n" +
            "        view.setLayoutDirection/*'setLayoutDirection(int)' in 'android.view.View' cannot be applied to '(int, int)'*/(View.LAYOUT_DIRECTION_RTL, View.LAYOUT_DIRECTION_LTR)/**/; // ERROR\n" +
            "    }\n" +
            "\n" +
            "    @TargetApi(Build.VERSION_CODES.KITKAT)\n" +
            "    public void testIntDefFlags(Context context, int unknown, Intent intent,\n" +
            "                           ServiceConnection connection) {\n" +
            "        // Flags\n" +
            "        Object ok1 = context.bindService(intent, connection, 0);\n" +
            "        Object ok2 = context.bindService(intent, connection, -1);\n" +
            "        Object ok3 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT);\n" +
            "        Object ok4 = context.bindService(intent, connection, Context.BIND_ABOVE_CLIENT\n" +
            "                | Context.BIND_AUTO_CREATE);\n" +
            "        int flags1 = Context.BIND_ABOVE_CLIENT | Context.BIND_AUTO_CREATE;\n" +
            "        Object ok5 = context.bindService(intent, connection, flags1);\n" +
            "\n" +
            "        Object error1 = context.bindService(intent, connection,\n" +
            "                Context.BIND_ABOVE_CLIENT | /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY*/Context.CONTEXT_IGNORE_SECURITY/**/);\n" +
            "        int flags2 = Context.BIND_ABOVE_CLIENT | Context.CONTEXT_IGNORE_SECURITY;\n" +
            "        Object error2 = context.bindService(intent, connection, /*Must be one or more of: Context.BIND_AUTO_CREATE, Context.BIND_DEBUG_UNBIND, Context.BIND_NOT_FOREGROUND, Context.BIND_ABOVE_CLIENT, Context.BIND_ALLOW_OOM_MANAGEMENT, Context.BIND_WAIVE_PRIORITY, Context.BIND_IMPORTANT, Context.BIND_ADJUST_WITH_ACTIVITY*/flags2/**/);\n" +
            "    }\n" +
            "}\n");
  }

  /**
   * Test @IntDef when applied to multiple elements like arrays or varargs.
   */
  public void testIntDefMultiple() {
    doCheck("import android.support.annotation.IntDef;\n" +
            "\n" +
            "public class X {\n" +
            "    private static final int VALUE_A = 0;\n" +
            "    private static final int VALUE_B = 1;\n" +
            "\n" +
            "    private static int[] VALID_ARRAY = {VALUE_A, VALUE_B};\n" +
            "    private static int[] INVALID_ARRAY = {VALUE_A, 0, VALUE_B};\n" +
            "    private static int[] INVALID_ARRAY2 = {10};\n" +
            "\n" +
            "    @IntDef({VALUE_A, VALUE_B})\n" +
            "    public @interface MyIntDef {}\n" +
            "\n" +
            "    @MyIntDef\n" +
            "    public int a = 0;\n" +
            "\n" +
            "    @MyIntDef\n" +
            "    public int[] b;\n" +
            "\n" +
            "    public void testCall() {\n" +
            "        restrictedArray(new int[]{VALUE_A}); // OK\n" +
            "        restrictedArray(new int[]{VALUE_A, VALUE_B}); // OK\n" +
            "        restrictedArray(new int[]{VALUE_A, /*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/, VALUE_B}); // ERROR;\n" +
            "        restrictedArray(VALID_ARRAY); // OK\n" +
            "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY/**/); // ERROR\n" +
            "        restrictedArray(/*Must be one of: X.VALUE_A, X.VALUE_B*/INVALID_ARRAY2/**/); // ERROR\n" +
            "\n" +
            "        restrictedEllipsis(VALUE_A); // OK\n" +
            "        restrictedEllipsis(VALUE_A, VALUE_B); // OK\n" +
            "        restrictedEllipsis(VALUE_A, /*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/, VALUE_B); // ERROR\n" +
            "        restrictedEllipsis(/*Must be one of: X.VALUE_A, X.VALUE_B*/0/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    private void restrictedEllipsis(@MyIntDef int... test) {}\n" +
            "\n" +
            "    private void restrictedArray(@MyIntDef int[] test) {}\n" +
            "}");
  }

  public void testFlow() {
    doCheck("import android.content.res.Resources;\n" +
            "import android.support.annotation.DrawableRes;\n" +
            "import android.support.annotation.StringRes;\n" +
            "import android.support.annotation.StyleRes;\n" +
            "\n" +
            "import java.util.Random;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "public class X {\n" +
            "    public void testLiterals(Resources resources) {\n" +
            "        resources.getDrawable(0); // OK\n" +
            "        resources.getDrawable(-1); // OK\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/10/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testConstants(Resources resources) {\n" +
            "        resources.getDrawable(R.drawable.my_drawable); // OK\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/R.string.my_string/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testFields(String fileExt, Resources resources) {\n" +
            "        int mimeIconId = MimeTypes.styleAndDrawable;\n" +
            "        resources.getDrawable(mimeIconId); // OK\n" +
            "\n" +
            "        int s1 = MimeTypes.style;\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/s1/**/); // ERROR\n" +
            "        int s2 = MimeTypes.styleAndDrawable;\n" +
            "        resources.getDrawable(s2); // OK\n" +
            "        int w3 = MimeTypes.drawable;\n" +
            "        resources.getDrawable(w3); // OK\n" +
            "\n" +
            "        // Direct reference\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/MimeTypes.style/**/); // ERROR\n" +
            "        resources.getDrawable(MimeTypes.styleAndDrawable); // OK\n" +
            "        resources.getDrawable(MimeTypes.drawable); // OK\n" +
            "    }\n" +
            "\n" +
            "    public void testCalls(String fileExt, Resources resources) {\n" +
            "        int mimeIconId = MimeTypes.getIconForExt(fileExt);\n" +
            "        resources.getDrawable(mimeIconId); // OK\n" +
            "        resources.getDrawable(MimeTypes.getInferredString()); // OK (wrong but can't infer type)\n" +
            "        resources.getDrawable(MimeTypes.getInferredDrawable()); // OK\n" +
            "        resources.getDrawable(/*Expected resource of type drawable*/MimeTypes.getAnnotatedString()/**/); // Error\n" +
            "        resources.getDrawable(MimeTypes.getAnnotatedDrawable()); // OK\n" +
            "        resources.getDrawable(MimeTypes.getUnknownType()); // OK (unknown/uncertain)\n" +
            "    }\n" +
            "\n" +
            "    private static class MimeTypes {\n" +
            "        @android.support.annotation.StyleRes\n" +
            "        @android.support.annotation.DrawableRes\n" +
            "        public static int styleAndDrawable;\n" +
            "\n" +
            "        @android.support.annotation.StyleRes\n" +
            "        public static int style;\n" +
            "\n" +
            "        @android.support.annotation.DrawableRes\n" +
            "        public static int drawable;\n" +
            "\n" +
            "        @android.support.annotation.DrawableRes\n" +
            "        public static int getIconForExt(String ext) {\n" +
            "            return R.drawable.my_drawable;\n" +
            "        }\n" +
            "\n" +
            "        public static int getInferredString() {\n" +
            "            // Implied string - can we handle this?\n" +
            "            return R.string.my_string;\n" +
            "        }\n" +
            "\n" +
            "        public static int getInferredDrawable() {\n" +
            "            // Implied drawable - can we handle this?\n" +
            "            return R.drawable.my_drawable;\n" +
            "        }\n" +
            "\n" +
            "        @android.support.annotation.StringRes\n" +
            "        public static int getAnnotatedString() {\n" +
            "            return R.string.my_string;\n" +
            "        }\n" +
            "\n" +
            "        @android.support.annotation.DrawableRes\n" +
            "        public static int getAnnotatedDrawable() {\n" +
            "            return R.drawable.my_drawable;\n" +
            "        }\n" +
            "\n" +
            "        public static int getUnknownType() {\n" +
            "            return new Random(1000).nextInt();\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static final class R {\n" +
            "        public static final class drawable {\n" +
            "            public static final int my_drawable =0x7f020057;\n" +
            "        }\n" +
            "        public static final class string {\n" +
            "            public static final int my_string =0x7f0a000e;\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  public void testColorAsDrawable() {
    doCheck("package p1.p2;\n" +
            "\n" +
            "import android.content.Context;\n" +
            "import android.view.View;\n" +
            "\n" +
            "public class X {\n" +
            "    static void test(Context context) {\n" +
            "        View separator = new View(context);\n" +
            "        separator.setBackgroundResource(android.R.color.black);\n" +
            "    }\n" +
            "}\n");
  }

  public void testMipmap() {
    doCheck("package p1.p2;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "\n" +
            "public class X extends Activity {\n" +
            "  public void test() {\n" +
            "    Object o = getResources().getDrawable(R.mipmap.ic_launcher);\n" +
            "  }\n" +
            "\n" +
            "  public static final class R {\n" +
            "    public static final class drawable {\n" +
            "      public static int icon=0x7f020000;\n" +
            "    }\n" +
            "    public static final class mipmap {\n" +
            "      public static int ic_launcher=0x7f020001;\n" +
            "    }\n" +
            "  }\n" +
            "}");
  }

  public void testRanges() {
    doCheck("import android.support.annotation.FloatRange;\n" +
            "import android.support.annotation.IntRange;\n" +
            "import android.support.annotation.Size;\n" +
            "\n" +
            "@SuppressWarnings(\"UnusedDeclaration\")\n" +
            "class X {\n" +
            "    public void printExact(@Size(5) String arg) { System.out.println(arg); }\n" +
            "    public void printMin(@Size(min=5) String arg) { }\n" +
            "    public void printMax(@Size(max=8) String arg) { }\n" +
            "    public void printRange(@Size(min=4,max=6) String arg) { }\n" +
            "    public void printExact(@Size(5) int[] arg) { }\n" +
            "    public void printMin(@Size(min=5) int[] arg) { }\n" +
            "    public void printMax(@Size(max=8) int[] arg) { }\n" +
            "    public void printRange(@Size(min=4,max=6) int[] arg) { }\n" +
            "    public void printMultiple(@Size(multiple=3) int[] arg) { }\n" +
            "    public void printMinMultiple(@Size(min=4,multiple=3) int[] arg) { }\n" +
            "    public void printAtLeast(@IntRange(from=4) int arg) { }\n" +
            "    public void printAtMost(@IntRange(to=7) int arg) { }\n" +
            "    public void printBetween(@IntRange(from=4,to=7) int arg) { }\n" +
            "    public void printAtLeastInclusive(@FloatRange(from=2.5) float arg) { }\n" +
            "    public void printAtLeastExclusive(@FloatRange(from=2.5,fromInclusive=false) float arg) { }\n" +
            "    public void printAtMostInclusive(@FloatRange(to=7) double arg) { }\n" +
            "    public void printAtMostExclusive(@FloatRange(to=7,toInclusive=false) double arg) { }\n" +
            "    public void printBetweenFromInclusiveToInclusive(@FloatRange(from=2.5,to=5.0) float arg) { }\n" +
            "    public void printBetweenFromExclusiveToInclusive(@FloatRange(from=2.5,to=5.0,fromInclusive=false) float arg) { }\n" +
            "    public void printBetweenFromInclusiveToExclusive(@FloatRange(from=2.5,to=5.0,toInclusive=false) float arg) { }\n" +
            "    public void printBetweenFromExclusiveToExclusive(@FloatRange(from=2.5,to=5.0,fromInclusive=false,toInclusive=false) float arg) { }\n" +
            "    public static final int MINIMUM = -1;\n" +
            "    public static final int MAXIMUM = 42;\n" +
            "    public static final int SIZE = 5;\n" +
            "    public void printIndirect(@IntRange(from = MINIMUM, to = MAXIMUM) int arg) { }\n" +
            "    public void printIndirectSize(@Size(SIZE) String arg) { }\n" +
            "\n" +
            "    public void testLength() {\n" +
            "        String arg = \"1234\";\n" +
            "        printExact(/*Length must be exactly 5*/arg/**/); // ERROR\n" +
            "\n" +
            "\n" +
            "        printExact(/*Length must be exactly 5*/\"1234\"/**/); // ERROR\n" +
            "        printExact(\"12345\"); // OK\n" +
            "        printExact(/*Length must be exactly 5*/\"123456\"/**/); // ERROR\n" +
            "\n" +
            "        printMin(/*Length must be at least 5 (was 4)*/\"1234\"/**/); // ERROR\n" +
            "        printMin(\"12345\"); // OK\n" +
            "        printMin(\"123456\"); // OK\n" +
            "\n" +
            "        printMax(\"123456\"); // OK\n" +
            "        printMax(\"1234567\"); // OK\n" +
            "        printMax(\"12345678\"); // OK\n" +
            "        printMax(/*Length must be at most 8 (was 9)*/\"123456789\"/**/); // ERROR\n" +
            "        printAtMost(1 << 2); // OK\n" +
            "        printMax(\"123456\" + \"\"); //OK\n" +
            "        printAtMost(/*Value must be \u2264 7 (was 8)*/1 << 2 + 1/**/); // ERROR\n" +
            "        printAtMost(/*Value must be \u2264 7 (was 32)*/1 << 5/**/); // ERROR\n" +
            "        printMax(/*Length must be at most 8 (was 11)*/\"123456\" + \"45678\"/**/); //ERROR\n" +
            "\n" +
            "        printRange(/*Length must be at least 4 and at most 6 (was 3)*/\"123\"/**/); // ERROR\n" +
            "        printRange(\"1234\"); // OK\n" +
            "        printRange(\"12345\"); // OK\n" +
            "        printRange(\"123456\"); // OK\n" +
            "        printRange(/*Length must be at least 4 and at most 6 (was 7)*/\"1234567\"/**/); // ERROR\n" +
            "        printIndirectSize(/*Length must be exactly 5*/\"1234567\"/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testSize() {\n" +
            "        printExact(/*Size must be exactly 5*/new int[]{1, 2, 3, 4}/**/); // ERROR\n" +
            "        printExact(new int[]{1, 2, 3, 4, 5}); // OK\n" +
            "        printExact(/*Size must be exactly 5*/new int[]{1, 2, 3, 4, 5, 6}/**/); // ERROR\n" +
            "\n" +
            "        printMin(/*Size must be at least 5 (was 4)*/new int[]{1, 2, 3, 4}/**/); // ERROR\n" +
            "        printMin(new int[]{1, 2, 3, 4, 5}); // OK\n" +
            "        printMin(new int[]{1, 2, 3, 4, 5, 6}); // OK\n" +
            "\n" +
            "        printMax(new int[]{1, 2, 3, 4, 5, 6}); // OK\n" +
            "        printMax(new int[]{1, 2, 3, 4, 5, 6, 7}); // OK\n" +
            "        printMax(new int[]{1, 2, 3, 4, 5, 6, 7, 8}); // OK\n" +
            "        printMax(new int[]{1, 2, 3, 4, 5, 6, 7, 8}); // OK\n" +
            "        printMax(/*Size must be at most 8 (was 9)*/new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9}/**/); // ERROR\n" +
            "\n" +
            "        printRange(/*Size must be at least 4 and at most 6 (was 3)*/new int[] {1,2,3}/**/); // ERROR\n" +
            "        printRange(new int[] {1,2,3,4}); // OK\n" +
            "        printRange(new int[] {1,2,3,4,5}); // OK\n" +
            "        printRange(new int[] {1,2,3,4,5,6}); // OK\n" +
            "        printRange(/*Size must be at least 4 and at most 6 (was 7)*/new int[] {1,2,3,4,5,6,7}/**/); // ERROR\n" +
            "\n" +
            "        printMultiple(new int[] {1,2,3}); // OK\n" +
            "        printMultiple(/*Size must be a multiple of 3 (was 4)*/new int[] {1,2,3,4}/**/); // ERROR\n" +
            "        printMultiple(/*Size must be a multiple of 3 (was 5)*/new int[] {1,2,3,4,5}/**/); // ERROR\n" +
            "        printMultiple(new int[] {1,2,3,4,5,6}); // OK\n" +
            "        printMultiple(/*Size must be a multiple of 3 (was 7)*/new int[] {1,2,3,4,5,6,7}/**/); // ERROR\n" +
            "\n" +
            "        printMinMultiple(new int[] {1,2,3,4,5,6}); // OK\n" +
            "        printMinMultiple(/*Size must be at least 4 and a multiple of 3 (was 3)*/new int[]{1, 2, 3}/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void testSize2(int[] unknownSize) {\n" +
            "        int[] location1 = new int[5];\n" +
            "        printExact(location1);\n" +
            "        int[] location2 = new int[6];\n" +
            "        printExact(/*Size must be exactly 5*/location2/**/);\n" +
            "        printExact(unknownSize);\n" +
            "    }\n" +
            "\n" +
            "    public void testIntRange() {\n" +
            "        printAtLeast(/*Value must be \u2265 4 (was 3)*/3/**/); // ERROR\n" +
            "        printAtLeast(4); // OK\n" +
            "        printAtLeast(5); // OK\n" +
            "\n" +
            "        printAtMost(5); // OK\n" +
            "        printAtMost(6); // OK\n" +
            "        printAtMost(7); // OK\n" +
            "        printAtMost(/*Value must be \u2264 7 (was 8)*/8/**/); // ERROR\n" +
            "\n" +
            "        printBetween(/*Value must be \u2265 4 and \u2264 7 (was 3)*/3/**/); // ERROR\n" +
            "        printBetween(4); // OK\n" +
            "        printBetween(5); // OK\n" +
            "        printBetween(6); // OK\n" +
            "        printBetween(7); // OK\n" +
            "        printBetween(/*Value must be \u2265 4 and \u2264 7 (was 8)*/8/**/); // ERROR\n" +
            "        int value = 8;\n" +
            "        printBetween(/*Value must be \u2265 4 and \u2264 7 (was 8)*/value/**/); // ERROR\n" +
            "        printBetween(/*Value must be \u2265 4 and \u2264 7 (was -7)*/-7/**/);\n" +
            "        printIndirect(/*Value must be \u2265 -1 and \u2264 42 (was -2)*/-2/**/);\n" +
            "    }\n" +
            "\n" +
            "    public void testFloatRange() {\n" +
            "        printAtLeastInclusive(/*Value must be \u2265 2.5 (was 2.49f)*/2.49f/**/); // ERROR\n" +
            "        printAtLeastInclusive(2.5f); // OK\n" +
            "        printAtLeastInclusive(2.6f); // OK\n" +
            "\n" +
            "        printAtLeastExclusive(/*Value must be > 2.5 (was 2.49f)*/2.49f/**/); // ERROR\n" +
            "        printAtLeastExclusive(/*Value must be > 2.5 (was 2.5f)*/2.5f/**/); // ERROR\n" +
            "        printAtLeastExclusive(2.501f); // OK\n" +
            "        printAtLeastExclusive(/*Value must be > 2.5 (was -10.0)*/-10/**/);\n" +
            "\n" +
            "        printAtMostInclusive(6.8f); // OK\n" +
            "        printAtMostInclusive(6.9f); // OK\n" +
            "        printAtMostInclusive(7.0f); // OK\n" +
            "        printAtMostInclusive(/*Value must be \u2264 7.0 (was 7.1f)*/7.1f/**/); // ERROR\n" +
            "\n" +
            "        printAtMostExclusive(6.9f); // OK\n" +
            "        printAtMostExclusive(6.99f); // OK\n" +
            "        printAtMostExclusive(/*Value must be < 7.0 (was 7.0f)*/7.0f/**/); // ERROR\n" +
            "        printAtMostExclusive(/*Value must be < 7.0 (was 7.1f)*/7.1f/**/); // ERROR\n" +
            "\n" +
            "        printBetweenFromInclusiveToInclusive(/*Value must be \u2265 2.5 and \u2264 5.0 (was 2.4f)*/2.4f/**/); // ERROR\n" +
            "        printBetweenFromInclusiveToInclusive(2.5f); // OK\n" +
            "        printBetweenFromInclusiveToInclusive(3f); // OK\n" +
            "        printBetweenFromInclusiveToInclusive(5.0f); // OK\n" +
            "        printBetweenFromInclusiveToInclusive(/*Value must be \u2265 2.5 and \u2264 5.0 (was 5.1f)*/5.1f/**/); // ERROR\n" +
            "\n" +
            "        printBetweenFromExclusiveToInclusive(/*Value must be > 2.5 and \u2264 5.0 (was 2.4f)*/2.4f/**/); // ERROR\n" +
            "        printBetweenFromExclusiveToInclusive(/*Value must be > 2.5 and \u2264 5.0 (was 2.5f)*/2.5f/**/); // ERROR\n" +
            "        printBetweenFromExclusiveToInclusive(5.0f); // OK\n" +
            "        printBetweenFromExclusiveToInclusive(/*Value must be > 2.5 and \u2264 5.0 (was 5.1f)*/5.1f/**/); // ERROR\n" +
            "\n" +
            "        printBetweenFromInclusiveToExclusive(/*Value must be \u2265 2.5 and < 5.0 (was 2.4f)*/2.4f/**/); // ERROR\n" +
            "        printBetweenFromInclusiveToExclusive(2.5f); // OK\n" +
            "        printBetweenFromInclusiveToExclusive(3f); // OK\n" +
            "        printBetweenFromInclusiveToExclusive(4.99f); // OK\n" +
            "        printBetweenFromInclusiveToExclusive(/*Value must be \u2265 2.5 and < 5.0 (was 5.0f)*/5.0f/**/); // ERROR\n" +
            "\n" +
            "        printBetweenFromExclusiveToExclusive(/*Value must be > 2.5 and < 5.0 (was 2.4f)*/2.4f/**/); // ERROR\n" +
            "        printBetweenFromExclusiveToExclusive(/*Value must be > 2.5 and < 5.0 (was 2.5f)*/2.5f/**/); // ERROR\n" +
            "        printBetweenFromExclusiveToExclusive(2.51f); // OK\n" +
            "        printBetweenFromExclusiveToExclusive(4.99f); // OK\n" +
            "        printBetweenFromExclusiveToExclusive(/*Value must be > 2.5 and < 5.0 (was 5.0f)*/5.0f/**/); // ERROR\n" +
            "    }\n" +
            "}\n");
  }

  /**
   * Test @IntRange and @FloatRange support annotation applied to arrays and vargs.
   */
  public void testRangesMultiple() {
    doCheck("import android.support.annotation.FloatRange;\n" +
            "import android.support.annotation.IntRange;\n" +
            "\n" +
            "public class X {\n" +
            "    private static float[] VALID_FLOAT_ARRAY = new float[] {10.0f, 12.0f, 15.0f};\n" +
            "    private static float[] INVALID_FLOAT_ARRAY = new float[] {10.0f, 12.0f, 5.0f};\n" +
            "\n" +
            "    private static int[] VALID_INT_ARRAY = new int[] {15, 120, 500};\n" +
            "    private static int[] INVALID_INT_ARRAY = new int[] {15, 120, 5};\n" +
            "\n" +
            "    @FloatRange(from = 10.0, to = 15.0)\n" +
            "    public float[] a;\n" +
            "\n" +
            "    @IntRange(from = 10, to = 500)\n" +
            "    public int[] b;\n" +
            "\n" +
            "    public void testCall() {\n" +
            "        a = new float[2];\n" +
            "        a[0] = /*Value must be \u2265 10.0 and \u2264 15.0 (was 5f)*/5f/**/; // ERROR\n" +
            "        a[1] = 14f; // OK\n" +
            "        varargsFloat(15.0f, 10.0f, /*Value must be \u2265 10.0 and \u2264 15.0 (was 5.0f)*/5.0f/**/); // ERROR\n" +
            "        restrictedFloatArray(VALID_FLOAT_ARRAY); // OK\n" +
            "        restrictedFloatArray(/*Value must be \u2265 10.0 and \u2264 15.0*/INVALID_FLOAT_ARRAY/**/); // ERROR\n" +
            "        restrictedFloatArray(new float[]{10.5f, 14.5f}); // OK\n" +
            "        restrictedFloatArray(/*Value must be \u2265 10.0 and \u2264 15.0*/new float[]{12.0f, 500.0f}/**/); // ERROR\n" +
            "\n" +
            "\n" +
            "        b = new int[2];\n" +
            "        b[0] = /*Value must be \u2265 10 and \u2264 500 (was 5)*/5/**/; // ERROR\n" +
            "        b[1] = 100; // OK\n" +
            "        varargsInt(15, 10, /*Value must be \u2265 10 and \u2264 500 (was 510)*/510/**/); // ERROR\n" +
            "        restrictedIntArray(VALID_INT_ARRAY); // OK\n" +
            "        restrictedIntArray(/*Value must be \u2265 10 and \u2264 500*/INVALID_INT_ARRAY/**/); // ERROR\n" +
            "        restrictedIntArray(new int[]{50, 500}); // OK\n" +
            "        restrictedIntArray(/*Value must be \u2265 10 and \u2264 500*/new int[]{0, 500}/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public void restrictedIntArray(@IntRange(from = 10, to = 500) int[] a) {\n" +
            "    }\n" +
            "\n" +
            "    public void varargsInt(@IntRange(from = 10, to = 500) int... a) {\n" +
            "    }\n" +
            "\n" +
            "    public void varargsFloat(@FloatRange(from = 10.0, to = 15.0) float... a) {\n" +
            "    }\n" +
            "\n" +
            "    public void restrictedFloatArray(@FloatRange(from = 10.0, to = 15.0) float[] a) {\n" +
            "    }\n" +
            "}\n" +
            "\n");
  }

  public void testColorInt() {
    doCheck("import android.app.Activity;\n" +
            "import android.graphics.Paint;\n" +
            "import android.widget.TextView;\n" +
            "\n" +
            "public class X extends Activity {\n" +
            "    public void foo(TextView textView, int foo) {\n" +
            "        Paint paint2 = new Paint();\n" +
            "        paint2.setColor(/*Should pass resolved color instead of resource id here: `getResources().getColor(R.color.blue)`*/R.color.blue/**/);\n" +
            "        // Wrong\n" +
            "        textView.setTextColor(/*Should pass resolved color instead of resource id here: `getResources().getColor(R.color.red)`*/R.color.red/**/);\n" +
            "        textView.setTextColor(/*Should pass resolved color instead of resource id here: `getResources().getColor(android.R.color.black)`*/android.R.color.black/**/);\n" +
            "        textView.setTextColor(/*Should pass resolved color instead of resource id here: `getResources().getColor(foo > 0 ? R.color.green : R.color.blue)`*/foo > 0 ? R.color.green : R.color.blue/**/);\n" +
            "        // OK\n" +
            "        textView.setTextColor(getResources().getColor(R.color.red));\n" +
            "        // OK\n" +
            "        foo1(R.color.blue);\n" +
            "        foo2(0xffff0000);\n" +
            "        // Wrong\n" +
            "        foo1(/*Expected resource of type color*/0xffff0000/**/);\n" +
            "        foo2(/*Should pass resolved color instead of resource id here: `getResources().getColor(R.color.blue)`*/R.color.blue/**/);\n" +
            "    }\n" +
            "\n" +
            "    private void foo1(@android.support.annotation.ColorRes int c) {\n" +
            "    }\n" +
            "\n" +
            "    private void foo2(@android.support.annotation.ColorInt int c) {\n" +
            "    }\n" +
            "\n" +
            "    private static class R {\n" +
            "        private static class color {\n" +
            "            public static final int red=0x7f060000;\n" +
            "            public static final int green=0x7f060001;\n" +
            "            public static final int blue=0x7f060002;\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  public void testColorInt2() {
    doCheck("package test.pkg;\n" +
            "import android.content.Context;\n" +
            "import android.content.res.Resources;\n" +
            "import android.support.annotation.ColorInt;\n" +
            "import android.support.annotation.ColorRes;\n" +
            "\n" +
            "public abstract class X {\n" +
            "    @ColorInt\n" +
            "    public abstract int getColor1();\n" +
            "    public abstract void setColor1(@ColorRes int color);\n" +
            "    @ColorRes\n" +
            "    public abstract int getColor2();\n" +
            "    public abstract void setColor2(@ColorInt int color);\n" +
            "\n" +
            "    public void test1(Context context) {\n" +
            "        int actualColor = getColor1();\n" +
            "        setColor1(/*Expected resource of type color*/actualColor/**/); // ERROR\n" +
            "        setColor1(/*Expected resource of type color*/getColor1()/**/); // ERROR\n" +
            "        setColor1(getColor2()); // OK\n" +
            "    }\n" +
            "    public void test2(Context context) {\n" +
            "        int actualColor = getColor2();\n" +
            "        setColor2(/*Should pass resolved color instead of resource id here: `getResources().getColor(actualColor)`*/actualColor/**/); // ERROR\n" +
            "        setColor2(/*Should pass resolved color instead of resource id here: `getResources().getColor(getColor2())`*/getColor2()/**/); // ERROR\n" +
            "        setColor2(getColor1()); // OK\n" +
            "    }\n" +
            "}\n");
  }

  public void testCheckResult() {
    doCheck("import android.Manifest;\n" +
            "import android.content.Context;\n" +
            "import android.content.pm.PackageManager;\n" +
            "import android.graphics.Bitmap;\n" +
            "\n" +
            "public class X {\n" +
            "    private void foo(Context context) {\n" +
            "        /*The result of 'checkCallingOrSelfPermission' is not used; did you mean to call 'enforceCallingOrSelfPermission(String,String)'?*/context.checkCallingOrSelfPermission(Manifest.permission.INTERNET)/**/; // WRONG\n" +
            "        /*The result of 'checkPermission' is not used; did you mean to call 'enforcePermission(String,int,int,String)'?*/context.checkPermission(Manifest.permission.INTERNET, 1, 1)/**/;\n" +
            "        check(context.checkCallingOrSelfPermission(Manifest.permission.INTERNET)); // OK\n" +
            "        int check = context.checkCallingOrSelfPermission(Manifest.permission.INTERNET); // OK\n" +
            "        if (context.checkCallingOrSelfPermission(Manifest.permission.INTERNET) // OK\n" +
            "                != PackageManager.PERMISSION_GRANTED) {\n" +
            "            showAlert(context, \"Error\",\n" +
            "                    \"Application requires permission to access the Internet\");\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    private Bitmap checkResult(Bitmap bitmap) {\n" +
            "        /*The result of 'extractAlpha' is not used*/bitmap.extractAlpha()/**/; // WARNING\n" +
            "        Bitmap bitmap2 = bitmap.extractAlpha(); // OK\n" +
            "        call(bitmap.extractAlpha()); // OK\n" +
            "        return bitmap.extractAlpha(); // OK\n" +
            "    }\n" +
            "\n" +
            "    private void showAlert(Context context, String error, String s) {\n" +
            "    }\n" +
            "\n" +
            "    private void check(int i) {\n" +
            "    }\n" +
            "    private void call(Bitmap bitmap) {\n" +
            "    }\n" +
            "}");
  }

  public void testMissingPermission() {
    doCheck("import android.Manifest;\n" +
            "import android.content.Context;\n" +
            "import android.content.pm.PackageManager;\n" +
            "import android.graphics.Bitmap;\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "\n" +
            "import static android.Manifest.permission.ACCESS_COARSE_LOCATION;\n" +
            "import static android.Manifest.permission.ACCESS_FINE_LOCATION;\n" +
            "\n" +
            "public class X {\n" +
            "    private static void foo(Context context, LocationManager manager) {\n" +
            "        /*Missing permissions required by LocationManager.myMethod: android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION*/manager.myMethod(\"myprovider\")/**/;\n" +
            "    }\n" +
            "\n" +
            "    @SuppressWarnings(\"UnusedDeclaration\")\n" +
            "    public abstract class LocationManager {\n" +
            "        @RequiresPermission(anyOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})\n" +
            "        public abstract Location myMethod(String provider);\n" +
            "        public class Location {\n" +
            "        }\n" +
            "    }\n" +
            "}");
  }

  public void testImpliedPermissions() {
    // Regression test for
    //   https://code.google.com/p/android/issues/detail?id=177381
    doCheck("package test.pkg;\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "\n" +
            "public class X {\n" +
            "    @RequiresPermission(allOf = {\"my.permission.PERM1\",\"my.permission.PERM2\"})\n" +
            "    public void method1() {\n" +
            "    }\n" +
            "\n" +
            "    @RequiresPermission(\"my.permission.PERM1\")\n" +
            "    public void method2() {\n" +
            "        /*Missing permissions required by X.method1: my.permission.PERM2*/method1()/**/;\n" +
            "    }\n" +
            "\n" +
            "    @RequiresPermission(allOf = {\"my.permission.PERM1\",\"my.permission.PERM2\"})\n" +
            "    public void method3() {\n" +
            "        // The above @RequiresPermission implies that we are holding these\n" +
            "        // permissions here, so the call to method1() should not be flagged as\n" +
            "        // missing a permission!\n" +
            "        method1();\n" +
            "    }\n" +
            "}\n");
  }

  public void testLibraryRevocablePermission() {
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "\n" +
            "public class X {\n" +
            "    public void something() {\n" +
            "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or explicitly handle a potential `SecurityException`*/methodRequiresDangerous()/**/;\n" +
            "        methodRequiresNormal();\n" +
            "    }\n" +
            "\n" +
            "    @RequiresPermission(\"my.normal.P1\")\n" +
            "    public void methodRequiresNormal() {\n" +
            "    }\n" +
            "\n" +
            "    @RequiresPermission(\"my.dangerous.P2\")\n" +
            "    public void methodRequiresDangerous() {\n" +
            "    }\n" +
            "}\n");
  }

  public void testHandledPermission() {
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.content.Context;\n" +
            "import android.content.pm.PackageManager;\n" +
            "import android.location.LocationManager;\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "\n" +
            "import java.io.IOException;\n" +
            "import java.security.AccessControlException;\n" +
            "\n" +
            "public class X {\n" +
            "    public static void test1() {\n" +
            "        try {\n" +
            "            // Ok: Security exception caught in one of the branches\n" +
            "            methodRequiresDangerous(); // OK\n" +
            "        } catch (IllegalArgumentException ignored) {\n" +
            "        } catch (SecurityException ignored) {\n" +
            "        }\n" +
            "\n" +
            "        try {\n" +
            "            // You have to catch SecurityException explicitly, not parent\n" +
            "            /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or explicitly handle a potential `SecurityException`*/methodRequiresDangerous()/**/; // ERROR\n" +
            "        } catch (RuntimeException e) { // includes Security Exception\n" +
            "        }\n" +
            "\n" +
            "        try {\n" +
            "            // Ok: Caught in outer statement\n" +
            "            try {\n" +
            "                methodRequiresDangerous(); // OK\n" +
            "            } catch (IllegalArgumentException e) {\n" +
            "                // inner\n" +
            "            }\n" +
            "        } catch (SecurityException ignored) {\n" +
            "        }\n" +
            "\n" +
            "        try {\n" +
            "            // You have to catch SecurityException explicitly, not parent\n" +
            "            /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or explicitly handle a potential `SecurityException`*/methodRequiresDangerous()/**/; // ERROR\n" +
            "        } catch (Exception e) { // includes Security Exception\n" +
            "        }\n" +
            "\n" +
            "        // NOT OK: Catching security exception subclass (except for dedicated ones?)\n" +
            "\n" +
            "        try {\n" +
            "            // Error: catching security exception, but not all of them\n" +
            "            /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or explicitly handle a potential `SecurityException`*/methodRequiresDangerous()/**/; // ERROR\n" +
            "        } catch (AccessControlException e) { // security exception but specific one\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static void test2() {\n" +
            "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or explicitly handle a potential `SecurityException`*/methodRequiresDangerous()/**/; // ERROR: not caught\n" +
            "    }\n" +
            "\n" +
            "    public static void test3()\n" +
            "            throws IllegalArgumentException {\n" +
            "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or explicitly handle a potential `SecurityException`*/methodRequiresDangerous()/**/; // ERROR: not caught by right type\n" +
            "    }\n" +
            "\n" +
            "    public static void test4()\n" +
            "            throws AccessControlException {  // Security exception but specific one\n" +
            "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or explicitly handle a potential `SecurityException`*/methodRequiresDangerous()/**/; // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public static void test5()\n" +
            "            throws SecurityException {\n" +
            "        methodRequiresDangerous(); // OK\n" +
            "    }\n" +
            "\n" +
            "    public static void test6()\n" +
            "            throws Exception { // includes Security Exception\n" +
            "        // You have to throw SecurityException explicitly, not parent\n" +
            "        /*Call requires permission which may be rejected by user: code should explicitly check to see if permission is available (with `checkPermission`) or explicitly handle a potential `SecurityException`*/methodRequiresDangerous()/**/; // ERROR\n" +
            "    }\n" +
            "\n" +
            "    public static void test7(Context context)\n" +
            "            throws IllegalArgumentException {\n" +
            "        if (context.getPackageManager().checkPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, context.getPackageName()) != PackageManager.PERMISSION_GRANTED) {\n" +
            "            return;\n" +
            "        }\n" +
            "        methodRequiresDangerous(); // OK: permission checked\n" +
            "    }\n" +
            "\n" +
            "    @RequiresPermission(\"my.dangerous.P2\")\n" +
            "    public static void methodRequiresDangerous() {\n" +
            "    }\n" +
            "\n" +
            "    public void test8() { // Regression test for http://b.android.com/187204\n" +
            "        try {\n" +
            "            methodRequiresDangerous();\n" +
            "            mightThrow();\n" +
            "        } catch (SecurityException | IOException se) { // OK: Checked in multi catch\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public void mightThrow() throws IOException {\n" +
            "    }\n" +
            "\n" +
            "}\n");
  }

  public void testIntentsAndContentResolvers() {
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.Manifest;\n" +
            "import android.app.Activity;\n" +
            "import android.content.ContentResolver;\n" +
            "import android.content.Context;\n" +
            "import android.content.Intent;\n" +
            "import android.net.Uri;\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "\n" +
            "import static android.Manifest.permission.READ_HISTORY_BOOKMARKS;\n" +
            "import static android.Manifest.permission.WRITE_HISTORY_BOOKMARKS;\n" +
            "\n" +
            "@SuppressWarnings({\"deprecation\", \"unused\"})\n" +
            "public class X {\n" +
            "    @RequiresPermission(Manifest.permission.CALL_PHONE)\n" +
            "    public static final String ACTION_CALL = \"android.intent.action.CALL\";\n" +
            "\n" +
            "    @RequiresPermission.Read(@RequiresPermission(READ_HISTORY_BOOKMARKS))\n" +
            "    @RequiresPermission.Write(@RequiresPermission(WRITE_HISTORY_BOOKMARKS))\n" +
            "    public static final Uri BOOKMARKS_URI = Uri.parse(\"content://browser/bookmarks\");\n" +
            "\n" +
            "    public static final Uri COMBINED_URI = Uri.withAppendedPath(BOOKMARKS_URI, \"bookmarks\");\n" +
            "\n" +
            "    public static void activities1(Activity activity) {\n" +
            "        Intent intent = new Intent(Intent.ACTION_CALL);\n" +
            "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
            "        // This one will only be flagged if we have framework metadata on Intent.ACTION_CALL\n" +
            "        /*Missing permissions required by intent Intent.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivity(intent)/**/;\n" +
            "    }\n" +
            "\n" +
            "    public static void activities2(Activity activity) {\n" +
            "        Intent intent = new Intent(ACTION_CALL);\n" +
            "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivity(intent)/**/;\n" +
            "    }\n" +
            "\n" +
            "    public static void activities3(Activity activity) {\n" +
            "        Intent intent;\n" +
            "        intent = new Intent(ACTION_CALL);\n" +
            "        intent.setData(Uri.parse(\"tel:1234567890\"));\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivity(intent)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivity(intent, null)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivityForResult(intent, 0)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivityFromChild(activity, intent, 0)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivityIfNeeded(intent, 0)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startActivityFromFragment(null, intent, 0)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/activity.startNextMatchingActivity(intent)/**/;\n" +
            "        startActivity(\"\"); // Not an error!\n" +
            "    }\n" +
            "\n" +
            "    public static void broadcasts(Context context) {\n" +
            "        Intent intent;\n" +
            "        intent = new Intent(ACTION_CALL);\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/context.sendBroadcast(intent)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/context.sendBroadcast(intent, \"\")/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/context.sendBroadcastAsUser(intent, null)/**/;\n" +
            "        /*Missing permissions required by intent X.ACTION_CALL: android.permission.CALL_PHONE*/context.sendStickyBroadcast(intent)/**/;\n" +
            "    }\n" +
            "\n" +
            "    public static void contentResolvers(Context context, ContentResolver resolver) {\n" +
            "        // read\n" +
            "        /*Missing permissions required to read X.BOOKMARKS_URI: com.android.browser.permission.READ_HISTORY_BOOKMARKS*/resolver.query(BOOKMARKS_URI, null, null, null, null)/**/;\n" +
            "\n" +
            "        // write\n" +
            "        /*Missing permissions required to write X.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.insert(BOOKMARKS_URI, null)/**/;\n" +
            "        /*Missing permissions required to write X.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.delete(BOOKMARKS_URI, null, null)/**/;\n" +
            "        /*Missing permissions required to write X.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.update(BOOKMARKS_URI, null, null, null)/**/;\n" +
            "\n" +
            "        // Framework (external) annotation\n" +
            "        /*Missing permissions required to write Browser.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.update(android.provider.Browser.BOOKMARKS_URI, null, null, null)/**/;\n" +
            "\n" +
            "        // URI manipulations\n" +
            "        /*Missing permissions required to write X.BOOKMARKS_URI: com.android.browser.permission.WRITE_HISTORY_BOOKMARKS*/resolver.insert(COMBINED_URI, null)/**/;\n" +
            "    }\n" +
            "\n" +
            "    public static void startActivity(Object other) {\n" +
            "        // Unrelated\n" +
            "    }\n" +
            "}\n");
  }

  public void testWrongThread() {
    doCheck("import android.support.annotation.MainThread;\n" +
            "import android.support.annotation.UiThread;\n" +
            "import android.support.annotation.WorkerThread;\n" +
            "\n" +
            "public class X {\n" +
            "    public AsyncTask testTask() {\n" +
            "\n" +
            "        return new AsyncTask() {\n" +
            "            final CustomView view = new CustomView();\n" +
            "\n" +
            "            @Override\n" +
            "            protected void doInBackground(Object... params) {\n" +
            "                /*Method onPreExecute must be called from the main thread, currently inferred thread is worker*/onPreExecute()/**/; // ERROR\n" +
            "                /*Method paint must be called from the UI thread, currently inferred thread is worker*/view.paint()/**/; // ERROR\n" +
            "                publishProgress(); // OK\n" +
            "            }\n" +
            "\n" +
            "            @Override\n" +
            "            protected void onPreExecute() {\n" +
            "                /*Method publishProgress must be called from the worker thread, currently inferred thread is main*/publishProgress()/**/; // ERROR\n" +
            "                onProgressUpdate(); // OK\n" +
            "            }\n" +
            "        };\n" +
            "    }\n" +
            "\n" +
            "    @UiThread\n" +
            "    public static class View {\n" +
            "        public void paint() {\n" +
            "        }\n" +
            "    }\n" +
            "    @some.pkg.UnrelatedNameEndsWithThread\n" +
            "    public static void test1(View view) {\n" +
            "        view.paint();\n" +
            "    }\n" +
            "\n" +
            "    @UiThread\n" +
            "    public static void test2(View view) {\n" +
            "        test1(view);\n" +
            "    }\n" +
            "\n" +
            "    @UiThread\n" +
            "    public static void test3(View view) {\n" +
            "        TestClass.test4();\n" +
            "    }\n" +
            "\n" +
            "    @some.pkg.UnrelatedNameEndsWithThread\n" +
            "    public static class TestClass {\n" +
            "        public static void test4() {\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static class CustomView extends View {\n" +
            "    }\n" +
            "\n" +
            "    public static abstract class AsyncTask {\n" +
            "        @WorkerThread\n" +
            "        protected abstract void doInBackground(Object... params);\n" +
            "\n" +
            "        @MainThread\n" +
            "        protected void onPreExecute() {\n" +
            "        }\n" +
            "\n" +
            "        @MainThread\n" +
            "        protected void onProgressUpdate(Object... values) {\n" +
            "        }\n" +
            "\n" +
            "        @WorkerThread\n" +
            "        protected final void publishProgress(Object... values) {\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  /**
   * Test that the parent class annotations are not inherited by the static methods declared in a child class. In the example below,
   * android.view.View is annotated with the UiThread annotation. The test checks that workerThreadMethod does not inherit that annotation.
   */
  public void testStaticWrongThread() {
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.content.Context;\n" +
            "import android.os.AsyncTask;\n" +
            "import android.support.annotation.WorkerThread;\n" +
            "import android.view.View;\n" +
            "\n" +
            "public class X extends View {\n" +
            "    public X(Context context) {\n" +
            "        super(context);\n" +
            "    }\n" +
            "\n" +
            "    class MyAsyncTask extends AsyncTask<Long, Void, Boolean> {\n" +
            "        @Override\n" +
            "        protected Boolean doInBackground(Long... sizes) {\n" +
            "            return workedThreadMethod();\n" +
            "        }\n" +
            "\n" +
            "        @Override\n" +
            "        protected void onPostExecute(Boolean isEnoughFree) {\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static boolean workedThreadMethod() {\n" +
            "        return true;\n" +
            "    }\n" +
            "}");
  }

  public void testAnyThread() {
    // Tests support for the @AnyThread annotation as well as fixing bugs
    // suppressing AndroidLintWrongThread and
    // 207313: Class-level threading annotations are not overrideable
    // 207302: @WorkerThread cannot call View.post

    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.support.annotation.BinderThread;\n" +
            "import android.support.annotation.MainThread;\n" +
            "import android.support.annotation.UiThread;\n" +
            "import android.support.annotation.WorkerThread;\n" +
            "\n" +
            "@SuppressWarnings({\"WeakerAccess\", \"unused\"})\n" +
            "public class X {\n" +
            "    @UiThread\n" +
            "    static class AnyThreadTest {\n" +
            "        //    @AnyThread\n" +
            "        static void threadSafe() {\n" +
            "            /*Method worker must be called from the worker thread, currently inferred thread is UI*/worker()/**/; // ERROR\n" +
            "        }\n" +
            "\n" +
            "        @WorkerThread\n" +
            "        static void worker() {\n" +
            "            /*Method threadSafe must be called from the UI thread, currently inferred thread is worker*/threadSafe()/**/; // OK\n" +
            "        }\n" +
            "\n" +
            "        // Multi thread test\n" +
            "        @UiThread\n" +
            "        @WorkerThread\n" +
            "        private static void calleee() {\n" +
            "        }\n" +
            "\n" +
            "        @WorkerThread\n" +
            "        private static void call1() {\n" +
            "            calleee(); // OK - context is included in target\n" +
            "        }\n" +
            "\n" +
            "        @BinderThread\n" +
            "        @WorkerThread\n" +
            "        private static void call2() {\n" +
            "            /*Method calleee must be called from the UI or worker thread, currently inferred thread is binder and worker*/calleee()/**/; // Not ok: thread could be binder thread, not supported by target\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static AsyncTask testTask() {\n" +
            "\n" +
            "        return new AsyncTask() {\n" +
            "            final CustomView view = new CustomView();\n" +
            "\n" +
            "            @Override\n" +
            "            protected void doInBackground(Object... params) {\n" +
            "                /*Method onPreExecute must be called from the main thread, currently inferred thread is worker*/onPreExecute()/**/; // ERROR\n" +
            "                /*Method paint must be called from the UI thread, currently inferred thread is worker*/view.paint()/**/; // ERROR\n" +
            "                publishProgress(); // OK\n" +
            "            }\n" +
            "\n" +
            "            @Override\n" +
            "            protected void onPreExecute() {\n" +
            "                /*Method publishProgress must be called from the worker thread, currently inferred thread is main*/publishProgress()/**/; // ERROR\n" +
            "                onProgressUpdate(); // OK\n" +
            "                // Suppressed via older Android Studio inspection id:\n" +
            "                //noinspection ResourceType\n" +
            "                publishProgress(); // SUPPRESSED\n" +
            "                // Suppressed via new lint id:\n" +
            "                //noinspection WrongThread\n" +
            "                publishProgress(); // SUPPRESSED\n" +
            "                // Suppressed via Studio inspection id:\n" +
            "                //noinspection AndroidLintWrongThread\n" +
            "                publishProgress(); // SUPPRESSED\n" +
            "            }\n" +
            "        };\n" +
            "    }\n" +
            "\n" +
            "    @UiThread\n" +
            "    public static class View {\n" +
            "        public void paint() {\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public static class CustomView extends View {\n" +
            "        @Override public void paint() {\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public abstract static class AsyncTask {\n" +
            "        @WorkerThread\n" +
            "        protected abstract void doInBackground(Object... params);\n" +
            "\n" +
            "        @MainThread\n" +
            "        protected void onPreExecute() {\n" +
            "        }\n" +
            "\n" +
            "        @MainThread\n" +
            "        protected void onProgressUpdate(Object... values) {\n" +
            "        }\n" +
            "\n" +
            "        @WorkerThread\n" +
            "        protected final void publishProgress(Object... values) {\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  public void testPx() {
    // Test the @Px annotation
    doCheck("\n" +
            "package test.pkg;\n" +
            "\n" +
            "import android.support.annotation.Px;\n" +
            "import android.support.annotation.DimenRes;\n" +
            "\n" +
            "public abstract class X {\n" +
            "    @DimenRes\n" +
            "    public abstract int getDimension1();\n" +
            "    public abstract void setDimension1(@DimenRes int dimension);\n" +
            "    @Px\n" +
            "    public abstract int getDimension2();\n" +
            "    public abstract void setDimension2(@Px int dimension);\n" +
            "\n" +
            "    public void test1() {\n" +
            "        int actualSize = getDimension2();\n" +
            //"        setDimension1(/*Expected a dimension resource id (R.color.) but received a pixel integer*/actualSize/**/); // ERROR\n" +
            //"        setDimension1(/*Expected a dimension resource id (R.color.) but received a pixel integer*/getDimension2()/**/); // ERROR\n" +
            "        setDimension1(getDimension1()); // OK\n" +
            "    }\n" +
            "    public void test2() {\n" +
            "        int actualSize = getDimension1();\n" +
            //"        setDimension2(/*Should pass resolved pixel dimension instead of resource id here: `getResources().getDimension*(actualSize)`*/actualSize/**/); // ERROR\n" +
            "        setDimension2(/*Should pass resolved pixel dimension instead of resource id here: `getResources().getDimension*(getDimension1())`*/getDimension1()/**/); // ERROR\n" +
            "        setDimension2(getDimension2()); // OK\n" +
            "    }\n" +
            "}\n");
  }

  public void testCombinedIntDefAndIntRange() throws Exception {
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.support.annotation.IntDef;\n" +
            "import android.support.annotation.IntRange;\n" +
            "\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "\n" +
            "@SuppressWarnings({\"UnusedParameters\", \"unused\", \"SpellCheckingInspection\"})\n" +
            "public class X {\n" +
            "\n" +
            "    public static final int UNRELATED = 500;\n" +
            "\n" +
            "    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})\n" +
            "    @IntRange(from = 10)\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    public @interface Duration {}\n" +
            "\n" +
            "    public static final int LENGTH_INDEFINITE = -2;\n" +
            "    public static final int LENGTH_SHORT = -1;\n" +
            "    public static final int LENGTH_LONG = 0;\n" +
            "    public void setDuration(@Duration int duration) {\n" +
            "    }\n" +
            "\n" +
            "    public void test() {\n" +
            "        setDuration(/*Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be \u2265 10 (was 500)*/UNRELATED/**/); /// ERROR: Not right intdef, even if it's in the right number range\n" +
            "        setDuration(/*Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be \u2265 10 (was -5)*/-5/**/); // ERROR (not right int def or value\n" +
            "        setDuration(/*Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG or value must be \u2265 10 (was 8)*/8/**/); // ERROR (not matching number range)\n" +
            "        setDuration(8000); // OK (@IntRange applies)\n" +
            "        setDuration(LENGTH_INDEFINITE); // OK (@IntDef)\n" +
            "        setDuration(LENGTH_LONG); // OK (@IntDef)\n" +
            "        setDuration(LENGTH_SHORT); // OK (@IntDef)\n" +
            "    }\n" +
            "}\n");
  }

  public void testConstrainedIntRanges() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=188351
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.support.annotation.IntRange;\n" +
            "\n" +
            "public class X {\n" +
            "    public int forcedMeasureHeight = -1;\n" +
            "\n" +
            "    public void testVariable() {\n" +
            "        int parameter = -1;\n" +
            "        if (parameter >= 0) {\n" +
            "            method(parameter); // OK\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public void testOk1(boolean ok) {\n" +
            "        if (forcedMeasureHeight >= 0) {\n" +
            "            method(forcedMeasureHeight); // OK\n" +
            "        }\n" +
            "        if (ok && forcedMeasureHeight >= 0) {\n" +
            "            method(forcedMeasureHeight); // OK\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public void testError(boolean ok, int unrelated) {\n" +
            "        method(/*Value must be ≥ 0 (was -1)*/forcedMeasureHeight/**/); // ERROR\n" +
            "        if (ok && unrelated >= 0) {\n" +
            "            method(/*Value must be ≥ 0 (was -1)*/forcedMeasureHeight/**/); // ERROR\n" +
            "        }\n" +
            "    }\n" +
            "\n" +
            "    public void method(@IntRange(from=0) int parameter) {\n" +
            "    }\n" +
            "}\n");
  }

  public void testStringDefOnEquals() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=186598
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.support.annotation.StringDef;\n" +
            "\n" +
            "import java.lang.annotation.Retention;\n" +
            "\n" +
            "@SuppressWarnings({\"unused\", \"StringEquality\"})\n" +
            "public class X {\n" +
            "    public static final String SUNDAY = \"a\";\n" +
            "    public static final String MONDAY = \"b\";\n" +
            "\n" +
            "    @StringDef(value = {\n" +
            "            SUNDAY,\n" +
            "            MONDAY\n" +
            "    })\n" +
            "    @Retention(java.lang.annotation.RetentionPolicy.SOURCE)\n" +
            "    public @interface Day {\n" +
            "    }\n" +
            "\n" +
            "    @Day\n" +
            "    public String getDay() {\n" +
            "        return MONDAY;\n" +
            "    }\n" +
            "\n" +
            "    public void test(Object object) {\n" +
            "        boolean ok1 = this.getDay() == /*Must be one of: X.SUNDAY, X.MONDAY*/\"Any String\"/**/;\n" +
            "        boolean ok2 = this.getDay().equals(MONDAY);\n" +
            "        boolean wrong1 = this.getDay().equals(/*Must be one of: X.SUNDAY, X.MONDAY*/\"Any String\"/**/);\n" +
            "    }\n" +
            "}\n");
  }

  public void testColorAndDrawable() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=197411
    doCheck("\n" +
            "package test.pkg;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "import android.support.annotation.ColorRes;\n" +
            "import android.support.annotation.DrawableRes;\n" +
            "import android.widget.TextView;\n" +
            "\n" +
            "public class X extends Activity {\n" +
            "    @ColorRes int getSwipeColor() {\n" +
            "        return android.R.color.black;\n" +
            "    }\n" +
            "\n" +
            "    @DrawableRes int getDrawableIcon() {\n" +
            "        return android.R.drawable.ic_delete;\n" +
            "    }\n" +
            "    \n" +
            "    public void test(TextView view) {\n" +
            "        getResources().getColor(getSwipeColor()); // OK: color to color\n" +
            "        view.setBackgroundResource(getSwipeColor()); // OK: color promotes to drawable\n" +
            "        getResources().getColor(/*Expected resource of type color*/getDrawableIcon()/**/); // Not OK: drawable doesn't promote to color\n" +
            "    }\n" +
            "}\n");
  }

  public void testObtainStyleablesFromArray() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=201882
    // obtainStyledAttributes normally expects a styleable but you can also supply a
    // custom int array
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.app.Activity;\n" +
            "import android.content.Context;\n" +
            "import android.content.res.TypedArray;\n" +
            "import android.graphics.Color;\n" +
            "import android.util.AttributeSet;\n" +
            "\n" +
            "@SuppressWarnings(\"unused\")\n" +
            "public class X {\n" +
            "    public void test1(Activity activity, float[] foregroundHsv, float[] backgroundHsv) {\n" +
            "        TypedArray attributes = activity.obtainStyledAttributes(\n" +
            "                new int[] {\n" +
            "                        R.attr.setup_wizard_navbar_theme,\n" +
            "                        android.R.attr.colorForeground,\n" +
            "                        android.R.attr.colorBackground });\n" +
            "        Color.colorToHSV(attributes.getColor(1, 0), foregroundHsv);\n" +
            "        Color.colorToHSV(attributes.getColor(2, 0), backgroundHsv);\n" +
            "        attributes.recycle();\n" +
            "    }\n" +
            "\n" +
            "    public void test2(Context context, AttributeSet attrs, int defStyle) {\n" +
            "        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BezelImageView,\n" +
            "                defStyle, 0);\n" +
            "        a.getDrawable(R.styleable.BezelImageView_maskDrawable);\n" +
            "        a.recycle();\n" +
            "    }\n" +
            "\n" +
            "    public static class R {\n" +
            "        public static class attr {\n" +
            "            public static final int setup_wizard_navbar_theme = 0x7f01003b;\n" +
            "        }\n" +
            "        public static class styleable {\n" +
            "            public static final int[] BezelImageView = {\n" +
            "                    0x7f01005d, 0x7f01005e, 0x7f01005f\n" +
            "            };\n" +
            "            public static final int BezelImageView_maskDrawable = 0;\n" +
            "        }\n" +
            "    }\n" +
            "}\n");
  }

  public void testSuppressNames() throws Exception {
    doCheck("package test.pkg;\n" +
            "\n" +
            "import android.support.annotation.BinderThread;\n" +
            "import android.support.annotation.CheckResult;\n" +
            "import android.support.annotation.ColorInt;\n" +
            "import android.support.annotation.ColorRes;\n" +
            "import android.support.annotation.FloatRange;\n" +
            "import android.support.annotation.IntDef;\n" +
            "import android.support.annotation.IntRange;\n" +
            "import android.support.annotation.RequiresPermission;\n" +
            "import android.support.annotation.Size;\n" +
            "import android.support.annotation.StringRes;\n" +
            "import android.support.annotation.UiThread;\n" +
            "\n" +
            "import java.lang.annotation.Retention;\n" +
            "import java.lang.annotation.RetentionPolicy;\n" +
            "\n" +
            "import static android.Manifest.permission.ACCESS_COARSE_LOCATION;\n" +
            "import static android.Manifest.permission.ACCESS_FINE_LOCATION;\n" +
            "\n" +
            "@SuppressWarnings(\"unused\")\n" +
            "public class X {\n" +
            "\n" +
            "    @ColorInt private int colorInt;\n" +
            "    @ColorRes private int colorRes;\n" +
            "    @StringRes private int stringRes;\n" +
            "\n" +
            "    @BinderThread\n" +
            "    public void testOk() {\n" +
            "        setColor(colorRes); // OK\n" +
            "        setColorInt(colorInt); // OK\n" +
            "        printBetween(5); // OK\n" +
            "        printBetweenFromInclusiveToInclusive(3.0f); // OK\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9 }); // OK\n" +
            "        int result = checkMe(); // OK\n" +
            "        setDuration(LENGTH_LONG); // OK\n" +
            "    }\n" +
            "\n" +
            "    @BinderThread\n" +
            "    public void testErrors() {\n" +
            "        setColor(/*Expected resource of type color*/colorInt/**/); // ERROR\n" +
            "        setColorInt(/*Should pass resolved color instead of resource id here: `getResources().getColor(colorRes)`*/colorRes/**/); // ERROR\n" +
            "        printBetween(/*Value must be ≥ 4 and ≤ 7 (was 1)*/1/**/); // ERROR\n" +
            "        printBetweenFromInclusiveToInclusive(/*Value must be ≥ 2.5 and ≤ 5.0 (was 1.0f)*/1.0f/**/); // ERROR\n" +
            "        printMinMultiple(/*Size must be at least 4 and a multiple of 3 (was 8)*/new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }/**/); // ERROR\n" +
            "        /*The result of 'checkMe' is not used*/checkMe()/**/; // ERROR\n" +
            "        /*Missing permissions required by X.requiresPermission: android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION*/requiresPermission()/**/; // ERROR\n" +
            "        /*Method requiresUiThread must be called from the UI thread, currently inferred thread is binder*/requiresUiThread()/**/; // ERROR\n" +
            "        setDuration(/*Must be one of: X.LENGTH_INDEFINITE, X.LENGTH_SHORT, X.LENGTH_LONG*/5/**/); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    @BinderThread\n" +
            "    public void testSuppressedViaComment() {\n" +
            "        //noinspection ResourceType\n" +
            "        setColor(colorInt); // ERROR\n" +
            "        //noinspection ResourceAsColor\n" +
            "        setColorInt(colorRes); // ERROR\n" +
            "        //noinspection Range\n" +
            "        printBetween(1); // ERROR\n" +
            "        //noinspection Range\n" +
            "        printBetweenFromInclusiveToInclusive(1.0f); // ERROR\n" +
            "        //noinspection Range\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }); // ERROR\n" +
            "        //noinspection CheckResult\n" +
            "        checkMe(); // ERROR\n" +
            "        //noinspection MissingPermission\n" +
            "        requiresPermission(); // ERROR\n" +
            "        //noinspection WrongThread\n" +
            "        requiresUiThread(); // ERROR\n" +
            "        //noinspection WrongConstant\n" +
            "        setDuration(5); // ERROR\n" +
            "    }\n" +
            "\n" +
            "    @BinderThread\n" +
            "    public void testSuppressedViaOldInspectionName() {\n" +
            "        //noinspection ResourceType\n" +
            "        setColor(colorInt); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        setColorInt(colorRes); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        printBetween(1); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        printBetweenFromInclusiveToInclusive(1.0f); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        checkMe(); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        requiresPermission(); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        requiresUiThread(); // SUPPRESSED\n" +
            "        //noinspection ResourceType\n" +
            "        setDuration(5); // SUPPRESSED\n" +
            "    }\n" +
            "\n" +
            "    @SuppressWarnings({\"ResourceAsColor\", \"Range\", \"CheckResult\", \"MissingPermission\", \"WrongThread\", \"WrongConstant\"})\n" +
            "    @BinderThread\n" +
            "    public void testSuppressedViaAnnotation() {\n" +
            "        setColorInt(colorRes); // SUPPRESSED\n" +
            "        printBetween(1); // SUPPRESSED\n" +
            "        printBetweenFromInclusiveToInclusive(1.0f); // SUPPRESSED\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }); // SUPPRESSED\n" +
            "        checkMe(); // SUPPRESSED\n" +
            "        requiresPermission(); // SUPPRESSED\n" +
            "        requiresUiThread(); // SUPPRESSED\n" +
            "        setDuration(5); // SUPPRESSED\n" +
            "    }\n" +
            "\n" +
            "    @SuppressWarnings(\"ResourceType\")\n" +
            "    @BinderThread\n" +
            "    public void testSuppressedViaOldAnnotation() {\n" +
            "        setColorInt(colorRes); // SUPPRESSED\n" +
            "        printBetween(1); // SUPPRESSED\n" +
            "        printBetweenFromInclusiveToInclusive(1.0f); // SUPPRESSED\n" +
            "        printMinMultiple(new int[] { 1, 2, 3, 4, 5, 6, 7, 8 }); // SUPPRESSED\n" +
            "        checkMe(); // SUPPRESSED\n" +
            "        requiresPermission(); // SUPPRESSED\n" +
            "        requiresUiThread(); // SUPPRESSED\n" +
            "        setDuration(5); // SUPPRESSED\n" +
            "    }\n" +
            "\n" +
            "\n" +
            "    private void setColor(@ColorRes int color) { }\n" +
            "    private void setColorInt(@ColorInt int color) { }\n" +
            "    public void printBetween(@IntRange(from=4,to=7) int arg) { }\n" +
            "    public void printMinMultiple(@Size(min=4,multiple=3) int[] arg) { }\n" +
            "    public void printBetweenFromInclusiveToInclusive(@FloatRange(from=2.5,to=5.0) float arg) { }\n" +
            "    @CheckResult\n" +
            "    public int checkMe() { return 0; }\n" +
            "\n" +
            "    @RequiresPermission(anyOf = {ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION})\n" +
            "    public void requiresPermission() { }\n" +
            "\n" +
            "    @UiThread\n" +
            "    public void requiresUiThread() { }\n" +
            "\n" +
            "    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})\n" +
            "    @Retention(RetentionPolicy.SOURCE)\n" +
            "    public @interface Duration {}\n" +
            "\n" +
            "    public static final int LENGTH_INDEFINITE = -2;\n" +
            "    public static final int LENGTH_SHORT = -1;\n" +
            "    public static final int LENGTH_LONG = 0;\n" +
            "    public void setDuration(@Duration int duration) {\n" +
            "    }\n" +
            "}\n");
  }

  @Override
  protected String[] getEnvironmentClasses() {
    @Language("JAVA")
    String header = "package android.support.annotation;\n" +
                    "\n" +
                    "import java.lang.annotation.Documented;\n" +
                    "import java.lang.annotation.Retention;\n" +
                    "import java.lang.annotation.Target;\n" +
                    "\n" +
                    "import static java.lang.annotation.ElementType.ANNOTATION_TYPE;\n" +
                    "import static java.lang.annotation.ElementType.CONSTRUCTOR;\n" +
                    "import static java.lang.annotation.ElementType.FIELD;\n" +
                    "import static java.lang.annotation.ElementType.LOCAL_VARIABLE;\n" +
                    "import static java.lang.annotation.ElementType.METHOD;\n" +
                    "import static java.lang.annotation.ElementType.PARAMETER;\n" +
                    "import static java.lang.annotation.ElementType.TYPE;\n" +
                    "import static java.lang.annotation.RetentionPolicy.SOURCE;\n" +
                    "import static java.lang.annotation.RetentionPolicy.CLASS;\n" +
                    "\n";

    List<String> classes = Lists.newArrayList();
    @Language("JAVA")
    String floatRange = "@Retention(CLASS)\n" +
                        "@Target({CONSTRUCTOR,METHOD,PARAMETER,FIELD,LOCAL_VARIABLE})\n" +
                        "public @interface FloatRange {\n" +
                        "    double from() default Double.NEGATIVE_INFINITY;\n" +
                        "    double to() default Double.POSITIVE_INFINITY;\n" +
                        "    boolean fromInclusive() default true;\n" +
                        "    boolean toInclusive() default true;\n" +
                        "}";
    classes.add(header + floatRange);

    @Language("JAVA")
    String intRange = "@Retention(CLASS)\n" +
                      "@Target({CONSTRUCTOR,METHOD,PARAMETER,FIELD,LOCAL_VARIABLE,ANNOTATION_TYPE})\n" +
                      "public @interface IntRange {\n" +
                      "    long from() default Long.MIN_VALUE;\n" +
                      "    long to() default Long.MAX_VALUE;\n" +
                      "}";
    classes.add(header + intRange);

    @Language("JAVA")
    String size = "@Retention(CLASS)\n" +
                  "@Target({PARAMETER, LOCAL_VARIABLE, METHOD, FIELD})\n" +
                  "public @interface Size {\n" +
                  "    long value() default -1;\n" +
                  "    long min() default Long.MIN_VALUE;\n" +
                  "    long max() default Long.MAX_VALUE;\n" +
                  "    long multiple() default 1;\n" +
                  "}";
    classes.add(header + size);

    @Language("JAVA")
    String permission = "@Retention(SOURCE)\n" +
                        "@Target({ANNOTATION_TYPE,METHOD,CONSTRUCTOR,FIELD})\n" +
                        "public @interface RequiresPermission {\n" +
                        "    String value() default \"\";\n" +
                        "    String[] allOf() default {};\n" +
                        "    String[] anyOf() default {};\n" +
                        "    boolean conditional() default false;\n" +
                        "    @Target(FIELD)\n" +
                        "    @interface Read {\n" +
                        "        RequiresPermission value();\n" +
                        "    }\n" +
                        "    @Target(FIELD)\n" +
                        "    @interface Write {\n" +
                        "        RequiresPermission value();\n" +
                        "    }\n" +
                        "}\n";
    classes.add(header + permission);

    @Language("JAVA")
    String uiThread = "@Retention(SOURCE)\n" +
                      "@Target({METHOD,CONSTRUCTOR,TYPE})\n" +
                      "public @interface UiThread {\n" +
                      "}";
    classes.add(header + uiThread);

    @Language("JAVA")
    String mainThread = "@Retention(SOURCE)\n" +
                        "@Target({METHOD,CONSTRUCTOR,TYPE})\n" +
                        "public @interface MainThread {\n" +
                        "}";
    classes.add(header + mainThread);

    @Language("JAVA")
    String workerThread = "@Retention(SOURCE)\n" +
                          "@Target({METHOD,CONSTRUCTOR,TYPE})\n" +
                          "public @interface WorkerThread {\n" +
                          "}";
    classes.add(header + workerThread);

    @Language("JAVA")
    String binderThread = "@Retention(SOURCE)\n" +
                          "@Target({METHOD,CONSTRUCTOR,TYPE})\n" +
                          "public @interface BinderThread {\n" +
                          "}";
    classes.add(header + binderThread);

    @Language("JAVA")
    String colorInt = "@Retention(SOURCE)\n" +
                      "@Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE})\n" +
                      "public @interface ColorInt {\n" +
                      "}";
    classes.add(header + colorInt);

    @Language("JAVA")
    String px = "@Retention(SOURCE)\n" +
                "@Target({METHOD, PARAMETER, FIELD, LOCAL_VARIABLE})\n" +
                "public @interface Px {\n" +
                "}";
    classes.add(header + px);

    @Language("JAVA")
    String intDef = "@Retention(SOURCE)\n" +
                    "@Target({ANNOTATION_TYPE})\n" +
                    "public @interface IntDef {\n" +
                    "    long[] value() default {};\n" +
                    "    boolean flag() default false;\n" +
                    "}\n";
    classes.add(header + intDef);

    @Language("JAVA")
    String stringDef = "@Retention(SOURCE)\n" +
                       "@Target({ANNOTATION_TYPE})\n" +
                       "public @interface StringDef {\n" +
                       "    String[] value() default {};\n" +
                       "}\n";
    classes.add(header + stringDef);

    for (ResourceType type : ResourceType.values()) {
      if (type == ResourceType.FRACTION || type == ResourceType.PUBLIC) {
        continue;
      }
      @Language("JAVA")
      String resourceTypeAnnotation = "@Documented\n" +
                                      "@Retention(SOURCE)\n" +
                                      "@Target({METHOD, PARAMETER, FIELD})\n" +
                                      "public @interface " + StringUtil.capitalize(type.getName()) + "Res {\n" +
                                      "}";
      classes.add(header + resourceTypeAnnotation);
    }
    String anyRes = "@Documented\n" +
                    "@Retention(SOURCE)\n" +
                    "@Target({METHOD, PARAMETER, FIELD})\n" +
                    "public @interface AnyRes {\n" +
                    "}";
    classes.add(header + anyRes);

    @Language("JAVA")
    String unrelatedThread = "package some.pkg;\n" +
                             "import java.lang.annotation.*;\n" +
                             "import static java.lang.annotation.ElementType.*;\n" +
                             "import static java.lang.annotation.RetentionPolicy.*;\n" +
                             "@Retention(SOURCE)\n" +
                             "@Target({METHOD,CONSTRUCTOR,TYPE})\n" +
                             "public @interface UnrelatedNameEndsWithThread {\n" +
                             "}";
    classes.add(unrelatedThread);

    @Language("JAVA")
    String checkResult = "@Retention(SOURCE)\n" +
                       "@Target({METHOD})\n" +
                       "public @interface CheckResult {\n" +
                       "}\n";
    classes.add(header + checkResult);


    return ArrayUtil.toStringArray(classes);
  }

  // Like doTest in parent class, but uses <error> instead of <warning>
  protected final void doCheck(@Language("JAVA") @NotNull @NonNls String classText) {
    @NonNls final StringBuilder newText = new StringBuilder();
    int start = 0;
    int end = classText.indexOf("/*");
    while (end >= 0) {
      newText.append(classText, start, end);
      start = end + 2;
      end = classText.indexOf("*/", end);
      if (end < 0) {
        throw new IllegalArgumentException("invalid class text");
      }
      final String warning = classText.substring(start, end);
      if (warning.isEmpty()) {
        newText.append("</error>");
      }
      else {
        newText.append("<error descr=\"").append(warning).append("\">");
      }
      start = end + 2;
      end = classText.indexOf("/*", end + 1);
    }
    newText.append(classText, start, classText.length());

    // Now delegate to the real test implementation (it won't find comments to replace with <warning>)
    super.doTest(newText.toString());
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ResourceTypeInspection();
  }
}
