package p1.p2;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.OperationApplicationException;
import android.os.Build;

public class Class {
    private void method() {
        try {
            willThrow();
        } catch (<error descr="Class requires API level 5 (current min is 1): android.content.OperationApplicationException">OperationApplicationException</error> e) {
            e.printStackTrace();
        }
    }

    private void willThrow() throws OperationApplicationException {
        throw <error descr="Call requires API level 5 (current min is 1): new android.content.OperationApplicationException">new OperationApplicationException</error>();
    }

    private class MyException extends <error descr="Class requires API level 5 (current min is 1): android.content.OperationApplicationException">OperationApplicationException</error> {
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