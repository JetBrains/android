package p1.p2;

import android.app.Activity;

public class Plurals extends Activity {
    public void test(int quantity) <fold text='{...}' expand='true'>{
        String s = <fold text='Hello!' expand='false'>getResources().getQuantityString(R.plurals.plural1, quantity)</fold>;
        String t = <fold text='Quantity One' expand='false'>getResources().getQuantityString(R.plurals.plural2, quantity)</fold>;
        String u = <fold text='{3} song found from {25}.' expand='false'>getResources().getQuantityString(R.plurals.numberOfSongsAvailable, quantity, 3, 25)</fold>;
    }</fold>
}

