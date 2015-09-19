<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.OperationApplicationException;
import android.os.Build;

public class Class {
    private void method() {
        try {
            willThrow();
        } catch (<error descr="Class requires API level 5 (current min is 1): OperationApplicationException">OperationApplicationException</error> e) {
            e.printStackTrace();
        }
    }

    private void willThrow() throws OperationApplicationException {
        throw <error descr="Call requires API level 5 (current min is 1): android.content.OperationApplicationException#OperationApplicationException">new OperationApplicationException()</error>;
    }

    private class MyException extends <error descr="Class requires API level 5 (current min is 1): OperationApplicationException">OperationApplicationException</error> {
    }

    @SuppressLint("NewApi")
    private void suppressed() {
        try {
            willThrow();
        } catch (OperationApplicationException e) {
            e.printStackTrace();
        }
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private void targetApi() {
        try {
            willThrow();
        } catch (OperationApplicationException e) {
            e.printStackTrace();
        }
    }
}