package src.adux.votingapplib.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import src.adux.votingapplib.SurveysActivity;
import src.adux.votingapplib.models.PollAnswers.AnswerManager;
import src.adux.votingapplib.SurveyActivity;

public class FragmentEnd extends Fragment {

    private FragmentActivity mContext;
    private TextView mTextViewTitle;
    private String mType = "";
    private Button mFinish;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                src.adux.votingapplib.R.layout.fragment_end, container, false);


        mFinish = (Button) rootView.findViewById(src.adux.votingapplib.R.id.button_finish);
        mTextViewTitle = (TextView) rootView.findViewById(src.adux.votingapplib.R.id.textView_end);
        mType = getArguments().getString("type");


        mFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mType == null)
                    ((SurveyActivity) mContext).event_survey_completed(AnswerManager.getInstance());
                else
                    ((SurveysActivity) mContext).event_survey_completed(AnswerManager.getInstance());

            }
        });

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mContext = getActivity();
        if(mType == null) {
            mTextViewTitle.setText("Click on Submit to record/submit your responses. Thank you.");
            mFinish.setText("Submit");
        }
        else {
            mFinish.setText("Done");
            mTextViewTitle.setText("Thank you for taking the survey.");
        }

    }
}