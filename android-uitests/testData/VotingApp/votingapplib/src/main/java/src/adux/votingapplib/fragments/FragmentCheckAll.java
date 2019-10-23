package src.adux.votingapplib.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import src.adux.votingapplib.SurveysActivity;
import src.adux.votingapplib.models.PollAnswers.AnswerManager;
import src.adux.votingapplib.SurveyActivity;
import src.adux.votingapplib.models.PollAnswers.AnswerCheckAll;
import src.adux.votingapplib.models.Question;
import com.google.gson.Gson;

import src.adux.votingapplib.models.Session;
import src.nex3z.togglebuttongroup.MultiSelectToggleGroup;
import src.nex3z.togglebuttongroup.PollManager;
import src.nex3z.togglebuttongroup.button.CircularToggle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import httplib.HttpPostAnswer;

public class FragmentCheckAll extends Fragment {

    private static final String LOG_TAG = FragmentCheckAll.class.getSimpleName();
    private String OPTION_NAMES ="ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String SERVER_URL = "https://us-central1-asux-survey.cloudfunctions.net";

    private Question mQuestions;
    private FragmentActivity mContext;
    private Button mButtonContinue;
    private TextView mTextViewSubtitle;
    private TextView mTextViewTitle;
    private MultiSelectToggleGroup mToggleButtonGroup;

    private AnswerCheckAll mAnswerCheckAll;
    private String mQid;
    private String mUid;
    private String mType = "";
    private Session mSession;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                src.adux.votingapplib.R.layout.fragment_check_all, container, false);

        mToggleButtonGroup = (MultiSelectToggleGroup) rootView.findViewById(src.adux.votingapplib.R.id.group_choices);

        mButtonContinue = (Button) rootView.findViewById(src.adux.votingapplib.R.id.button_continue);

        mTextViewTitle = (TextView) rootView.findViewById(src.adux.votingapplib.R.id.textview_q_title);

        mTextViewSubtitle = (TextView) rootView.findViewById(src.adux.votingapplib.R.id.textview_q);

        AnswerManager.getInstance().reset();
        PollManager.getInstance().setFragment(false);
        PollManager.getInstance().setToggleState(true);

        mQid = getArguments().getString("hash");
        mType = getArguments().getString("type");


//        mUid = Settings.Secure.getString(getContext().getContentResolver(),
//                Settings.Secure.ANDROID_ID);
        mSession = new Session(this.getContext());
        mUid = mSession.getUserID();
        mAnswerCheckAll = new AnswerCheckAll();
        mAnswerCheckAll.setQid(mQid);
        mAnswerCheckAll.setUid(mUid);

        mButtonContinue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //index to 0, current index starts from 1.
                indexResponseTo0();
                System.out.println("Answer: "+ new Gson().toJson(mAnswerCheckAll,AnswerCheckAll.class));
                AnswerManager.getInstance().put_answer2(new Gson().toJson(mAnswerCheckAll,AnswerCheckAll.class));
                if(mType == null) {
                 //   ((SurveyActivity) mContext).go_to_next();
                    AlertDialog.Builder builder = new AlertDialog.Builder(FragmentCheckAll.this.getActivity());
                    builder.setTitle(
                            "Submit response?")
                            .setMessage("Do you wish to submit your response?")
                            .setCancelable(false)
                            .setPositiveButton("Ok",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int id) {
                                            ((SurveyActivity) mContext).event_survey_completed(AnswerManager.getInstance());
                                        }
                                    })
                            .setNegativeButton("Cancel",
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int id) {
                                            dialog.cancel();
                                        }
                                    }).create().show();
                }
                else {
                    postAnswer();
                    ((SurveysActivity) mContext).go_to_next();
                }
            }
        });

        return rootView;
    }

    private void indexResponseTo0() {
        List<Integer> choices = new ArrayList<>();
        if (mAnswerCheckAll != null && mAnswerCheckAll.getResponse() != null) {
            for (int i = 0; i < mAnswerCheckAll.getResponse().size(); i++) {
                choices.add(mAnswerCheckAll.getResponse().get(i) - 1);
            }
        }
        mAnswerCheckAll.setResponse(choices);
    }


    private void collect_data(MultiSelectToggleGroup group) {
        ArrayList<Integer> choices;
        choices = sanitize_data(group.getCheckedIds(),group.getChildCount(),group.getChildAt(0).getId());
        mAnswerCheckAll.setResponse(choices);
//        if (mQuestions.getRequired()) {
//            if (group.getCheckedIds().size()>=1) {
//                mButtonContinue.setVisibility(View.VISIBLE);
//            } else {
//                mButtonContinue.setVisibility(View.GONE);
//            }
//        }
    }

    private ArrayList<Integer> sanitize_data(ArrayList<Integer> choices,int max, int firstChild){
        ArrayList<Integer> sanitized  = new ArrayList<>();
        for(Integer i: choices){
            if((i - (firstChild-1)) % max == 0)
                sanitized.add(max);
            else
                sanitized.add((i - (firstChild -1))%(max));
        }
        return sanitized;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mContext = getActivity();
        mQuestions = (Question) getArguments().getSerializable("data");
        mQuestions.setGreenDots(mQuestions.getNumberOfItems());
        mTextViewSubtitle.setText("Check all");
        mTextViewTitle.setText(mQuestions != null ? "Check all that apply" : "");
        mAnswerCheckAll.setQtype(mQuestions.getQuestionType());
//        if (mQuestions.getRequired()) {
//            mButtonContinue.setVisibility(View.GONE);
//        }

//        List<String> qq_data = mQuestions.getChoices();
//        if (mQuestions.getRandomChoices()) {
//            Collections.shuffle(qq_data);
//        }


        GradientDrawable mGreenGradient = (GradientDrawable) ContextCompat.getDrawable(
                getContext(), src.nex3z.togglebuttongroup.R.drawable.bg_circle);
        mGreenGradient.setColor(Color.parseColor("#4AB97F"));
        for(int i = 0; i < mQuestions.getNumberOfItems(); i++){
                CircularToggle toggle = new CircularToggle(mContext);
                toggle.setTextSize(24);
                toggle.setText(Character.toString(OPTION_NAMES.charAt(i)));
                toggle.setCheckedImageDrawable(mGreenGradient);
                mToggleButtonGroup.addView(toggle);
        }


        mToggleButtonGroup.setOnCheckedChangeListener(new MultiSelectToggleGroup.OnCheckedStateChangeListener() {
            @Override
            public void onCheckedStateChanged(MultiSelectToggleGroup group, int checkedId, boolean isChecked) {
                Log.v(LOG_TAG, "onCheckedStateChanged(): group.getCheckedIds() = " + group.getCheckedIds());
                EnableDisableMostSelected(group, mQuestions.getGreenDots());
                collect_data(group);
            }
        });

    }

    private void EnableDisableMostSelected(MultiSelectToggleGroup group,int max){
        if(group.getCheckedIds().size() == max){
            for(int i=0;i<group.getChildCount();i++){
                CircularToggle toggle = (CircularToggle) group.getChildAt(i);
                if(toggle.isChecked() == false)
                    toggle.setClickable(false);
            }
        } else if (group.getCheckedIds().size() == (max - 1)){
            for(int i=0;i<group.getChildCount();i++){
                CircularToggle toggle = (CircularToggle) group.getChildAt(i);
                if(toggle.isChecked() == false)
                    toggle.setClickable(true);
            }
        }
    }

    private void postAnswer(){
        final String answers_json = AnswerManager.getInstance().get_answer();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                HttpPostAnswer httpPostClient = new HttpPostAnswer();
                try {
                    Log.d("****", "****************** WE HAVE ANSWERS ******************");
                    Log.d(LOG_TAG, "Posting data : " + answers_json);
                    Log.d("****", "****************** WE HAVE ANSWERS ******************");
                    httpPostClient.post(SERVER_URL + "/answer", answers_json);
                } catch (IOException e) {
                    e.printStackTrace();
                    DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    };
                    AlertDialog.Builder builder = new AlertDialog.Builder(FragmentCheckAll.this.getActivity());
                    builder.setTitle("Error")
                            .setMessage("Some problem")
                            .setPositiveButton(android.R.string.ok, listener)
                            .show();
                }
            }
        });
        thread.start();
    }
}