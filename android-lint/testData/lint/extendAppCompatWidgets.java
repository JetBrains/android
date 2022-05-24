package test.pkg;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

public class MyButton extends <error descr="This custom view should extend `android.support.v7.widget.AppCompatButton` instead">Bu<caret>tton</error> implements Runnable {
    public MyButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void run() {
    }
}