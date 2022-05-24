package p1.p2;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

public class WrongCastActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Button button = (Button) findViewById(R.id.My_Inflated_Id);
    }

    private static class R {
        private static class id {
            public static final int My_Inflated_Id = 1;
        }
        private static class layout {
            public static final int casts = 2;
        }
    }
}


