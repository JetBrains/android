package src.adux.votingapplib.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by axap on 3/22/18.
 */

public class Survey implements Serializable{
    @SerializedName("questions")
    @Expose
    private List<Question> questionsList = new ArrayList<>();

    public List<Question> getQuestionList() {
        return questionsList;
    }

    public void setQuestionList(List<Question> questionList) {
        this.questionsList = questionList;
    }
}
