package mss.url.model.mss_transaction;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "url", schema = "mss_transaction")
public class Url {

    @Id
    private String short_url;
    private String url;
    private int user_id;

    public String getShort_url() {
        return short_url;
    }

    public void setShort_url(String short_url) {
        this.short_url = short_url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getUser_id() {
        return user_id;
    }

    public void setUser_id(int user_id) {
        this.user_id = user_id;
    }

    @Override
    public String toString() {
        return "Url [short_url=" + short_url + ", url=" + url + ", user_id=" + user_id + "]";
    }

}
