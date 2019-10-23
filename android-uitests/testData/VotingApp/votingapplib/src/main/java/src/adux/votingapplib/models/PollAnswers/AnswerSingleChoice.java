package src.adux.votingapplib.models.PollAnswers;

/**
 * Created by axap on 3/13/18.
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class AnswerSingleChoice {

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
    private Integer response;

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

    public Integer getResponse() {
        return response;
    }

    public void setResponse(Integer response) {
        this.response = response;
    }

}