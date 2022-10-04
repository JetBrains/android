package p1.p2;

import android.annotation.SuppressLint;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@SuppressWarnings({"UnusedParameters", "unused", "SpellCheckingInspection", "RedundantCast"})
public class MissingIntDefSwitch {
    @IntDef({LENGTH_INDEFINITE, LENGTH_SHORT, LENGTH_LONG})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Duration {
    }

    public static final int LENGTH_INDEFINITE = -2;
    public static final int LENGTH_SHORT = -1;
    public static final int LENGTH_LONG = 0;

    public void setDuration(@Duration int duration) {
    }

    @Duration
    public static int getDuration() {
        return LENGTH_INDEFINITE;
    }

    public static void testOk(@Duration int duration) {
        switch (duration) {
            case LENGTH_SHORT:
            case LENGTH_LONG:
            case LENGTH_INDEFINITE:
                break;
        }
    }

    public static void testMissingWithDefault(@Duration int duration) {
        switch (duration) {
            case LENGTH_SHORT:
            case LENGTH_LONG:
            default:
                break;
        }
    }

    public static void testLiteral(@Duration int duration) {
        switch (duration) {
            case LENGTH_SHORT:
            case <warning descr="Don't use a constant here; expected one of: `LENGTH_INDEFINITE`, `LENGTH_LONG`, `LENGTH_SHORT`">5</warning>:
            case LENGTH_INDEFINITE:
                break;
        }
    }

    public static void testParameter(@Duration int duration) {
        <warning descr="Switch statement on an `int` with known associated constant missing case `LENGTH_LONG`">switch</warning> (duration) {
            case LENGTH_SHORT:
            case LENGTH_INDEFINITE:
                break;
        }
    }

    public static void testMissingAll(@Duration int duration) {
        // We don't flag these; let the IDE's normal "empty switch" check flag it
        <warning descr="Switch statement on an `int` with known associated constant missing case `LENGTH_INDEFINITE`, `LENGTH_LONG`, `LENGTH_SHORT`">switch</warning> (duration) {
        }
    }

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static void testLocalVariableFlow() {
        int intermediate = getDuration();
        int duration = intermediate;

        // Missing LENGTH_SHORT
        switch (duration) {
            case LENGTH_LONG:
            case LENGTH_INDEFINITE:
                break;
            case LENGTH_SHORT:
                break;
        }
    }

    public static void testMethodCall() {
        // Missing LENGTH_SHORT
        <warning descr="Switch statement on an `int` with known associated constant missing case `LENGTH_SHORT`">switch</warning> ((int)getDuration()) {
            case LENGTH_LONG:
            case LENGTH_INDEFINITE:
                break;
        }
    }

    @SuppressWarnings("ConstantConditionalExpression")
    public static void testInline() {
        // Missing LENGTH_SHORT
        <warning descr="Switch statement on an `int` with known associated constant missing case `LENGTH_SHORT`">swi<caret>tch</warning> (true ? getDuration() : 0) {
            case LENGTH_LONG:
            case LENGTH_INDEFINITE:
                break;
        }
    }

    @SuppressLint("SwitchIntDef")
    public static void testSuppressAnnotation(@Duration int duration) {
        switch (duration) {
            case LENGTH_SHORT:
            case LENGTH_INDEFINITE:
                break;
        }
    }

    public static void testSuppressComment(@Duration int duration) {
        //noinspection AndroidLintSwitchIntDef
        switch (duration) {
            case LENGTH_SHORT:
            case LENGTH_INDEFINITE:
                break;
        }
    }
}