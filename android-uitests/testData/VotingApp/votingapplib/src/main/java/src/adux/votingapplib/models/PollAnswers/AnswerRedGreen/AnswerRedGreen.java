package src.adux.votingapplib.models.PollAnswers.AnswerRedGreen;

/**
 * Created by axap on 3/13/18.
 */

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class AnswerRedGreen {

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
    private Response response;

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

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

}