package p1.p2;

import android.content.Context;
import android.view.ActionProvider;
import android.view.View;

public class MyProvider extends ActionProvider {
    public MyProvider(Context context) {
        super(context);
    }

    @Override
    public View onCreateActionView() {
        return null;
    }
}
