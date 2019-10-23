package src.adux.votingapplib.models.PollAnswers.AnswerDollars;

/**
 * Created by axap on 3/13/18.
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

import src.adux.votingapplib.models.PollAnswers.AnswerRedGreen.Response;

public class AnswerDollars {

    @SerializedName("uid")
    @Expose
    private String uid;

    @SerializedName("qid")
    @Expose
    private String qid;

    @SerializedName("qtype")
    @Expose
    private String qtype;

    @SerializedName("response")
    @Expose
    private List<Integer> response = null;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getQid() {
        return qid;
    }

    public void setQid(String qid) {
        this.qid = qid;
    }

    public String getQtype() {
        return qtype;
    }

    public void setQtype(String qtype) {
        this.qtype = qtype;
    }

    public List<Integer> getDollars() {
        return response;
    }

    public void setDollars(List<Integer> dollars) {
        this.response = dollars;
    }

}