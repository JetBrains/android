package src.adux.votingapplib.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import src.adux.votingapplib.SurveyActivity;


public class FragmentStart extends Fragment {

    private FragmentActivity mContext;

    private TextView textView_start;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                src.adux.votingapplib.R.layout.fragment_start, container, false);

        textView_start = (TextView) rootView.findViewById(src.adux.votingapplib.R.id.textview_start);
        Button button_continue = (Button) rootView.findViewById(src.adux.votingapplib.R.id.button_continue);
        button_continue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((SurveyActivity) mContext).go_to_next();
            }
        });

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = getActivity();



    }
}