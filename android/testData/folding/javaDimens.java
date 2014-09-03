package p1.p2;

import <fold text='...' expand='false'>android.app.Activity;
import android.app.AlertDialog;</fold>

public class MyActivity extends Activity {
    public void test() <fold text='{...}' expand='true'>{
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(null)
                .setPositiveButton(<fold text='"String 3"' expand='false'>R.string.string3</fold>, null)
                .create();
        float dimension = <fold text='56dip' expand='false'>getResources().getDimension(R.dimen.action_bar_default_height)</fold>;
        int dimension2 = <fold text='4dip' expand='false'>getResources().getDimensionPixelOffset(R.dimen.action_bar_icon_vertical_padding)</fold>;
        int dimension3 = <fold text='40dp' expand='false'>getResources().getDimensionPixelSize(R.dimen.mydimen1)</fold>;
        int[] strings = new int[] {
                <fold text='"String 1"' expand='false'>R.string.string1</fold>,
                <fold text='"String 2"' expand='false'>R.string.string2</fold>,
                <fold text='"String 3"' expand='false'>R.string.string3</fold>
        };
        int[] dimensions = new int[] {
                <fold text='40dp' expand='false'>R.dimen.mydimen1</fold>,
                <fold text='28sp' expand='false'>R.dimen.mydimen2</fold>
        };
        final int maxButtons = <fold text='max_action_buttons: 5' expand='false'>getResources().getInteger(R.integer.max_action_buttons)</fold>;
    }</fold>
}
