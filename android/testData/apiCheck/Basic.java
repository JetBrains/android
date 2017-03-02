package p1.p2;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;

import android.view.ViewGroup.LayoutParams;
import android.app.Activity;
import android.app.ApplicationErrorReport;
import android.app.ApplicationErrorReport.BatteryInfo;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.widget.Chronometer;
import android.widget.GridLayout;

import java.io.IOException;

public class Class extends Activity {
    public void method(Chronometer chronometer) {
        // Method call
        chronometer.<error descr="Call requires API level 3 (current min is 1): android.widget.Chronometer#getOnChronometerTickListener">getOnChronometerTickListener</error>(); // API 3

        // Inherited method call (from TextView
        chronometer.<error descr="Call requires API level 11 (current min is 1): android.widget.TextView#setTextIsSelectable">setTextIsSelectable</error>(true); // API 11

        // Field access
        int fillParent = LayoutParams.FILL_PARENT; // API 1
        // This is a final int, which means it gets inlined
        int matchParent = LayoutParams.MATCH_PARENT; // API 8
        // Field access: non final
        BatteryInfo batteryInfo = <error descr="Field requires API level 14 (current min is 1): `android.app.ApplicationErrorReport#batteryInfo`">getReport().batteryInfo</error>;

        // Enum access
        Mode mode = <error descr="Field requires API level 11 (current min is 1): `android.graphics.PorterDuff.Mode#OVERLAY`">PorterDuff.Mode.OVERLAY</error>; // API 11
    }

    // Return type
    GridLayout getGridLayout() { // API 14
        return null;
    }

    private ApplicationErrorReport getReport() {
        return null;
    }

    public static class ApiCallTest10 extends View {
        public ApiCallTest10() {
            super(null, null, 0);
        }

        @Override
        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            return super.dispatchPopulateAccessibilityEvent(event);
        }

        @Override
        public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
            super.<error descr="Call requires API level 4 (current min is 1): android.view.View#dispatchPopulateAccessibilityEvent">dispatchPopulateAccessibilityEvent</error>(event); // Error

            super.onPopulateAccessibilityEvent(event); // OK: just calling super on same method
            // Additional override code here:
        }

        @Override
        protected boolean dispatchGenericFocusedEvent(MotionEvent event) {
            return super.dispatchGenericFocusedEvent(event); // OK: just calling super on same method
        }

        protected boolean dispatchHoverEvent(int event) {
            return false;
        }

        public void test1() {
            // Should flag this, because the local method has the wrong signature
            <error descr="Call requires API level 14 (current min is 1): android.view.View#dispatchHoverEvent">dispatchHoverEvent</error>(null);

            // Shouldn't flag this, local method makes it available
            dispatchGenericFocusedEvent(null);
        }
    }

    public static class ApiCallTest11 extends Activity {
        public boolean isDestroyed() {
            return true;
        }

        @SuppressLint("Override")
        public void finishAffinity() {
        }

        private class MyLinear extends LinearLayout {
            private Drawable mDividerDrawable;

            public MyLinear(Context context) {
                super(context);
            }

            public void setDividerDrawable(Drawable dividerDrawable) {
                mDividerDrawable = dividerDrawable;
            }
        }
    }

    public static class ApiCallTest5 extends View {
        public ApiCallTest5(Context context) {
            super(context);
        }

        @SuppressWarnings("unused")
        @Override
        @TargetApi(2)
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int measuredWidth = View.<error descr="Call requires API level 11 (current min is 2): android.view.View#resolveSizeAndState">resolveSizeAndState</error>(widthMeasureSpec,
                    widthMeasureSpec, 0);
            int measuredHeight = <error descr="Call requires API level 11 (current min is 2): android.view.View#resolveSizeAndState">resolveSizeAndState</error>(heightMeasureSpec,
                    heightMeasureSpec, 0);
            View.<error descr="Call requires API level 11 (current min is 2): android.view.View#combineMeasuredStates">combineMeasuredStates</error>(0, 0);
            ApiCallTest5.<error descr="Call requires API level 11 (current min is 2): android.view.View#combineMeasuredStates">combineMeasuredStates</error>(0, 0);
        }
    }

    public static class ApiCallTest6 {
        public void test(Throwable throwable) {
            // IOException(Throwable) requires API 9
            IOException ioException = <error descr="Call requires API level 9 (current min is 1): new java.io.IOException">new IOException</error>(throwable);
        }
    }

    @SuppressWarnings("serial")
    public static class ApiCallTest7 extends IOException {
        public ApiCallTest7(String message, Throwable cause) {
            <error descr="Call requires API level 9 (current min is 1): new java.io.IOException">super</error>(message, cause); // API 9
        }

        public void fun() throws IOException {
            super.toString(); throw <error descr="Call requires API level 9 (current min is 1): new java.io.IOException">new IOException</error>((Throwable) null); // API 9
        }
    }

  /* Temporarily hidden: We need to have a more recent build target for our unit test platform
    public void closeTest(android.database.sqlite.SQLiteDatabase db) throws Exception {
        db.close();
    }
   */
}