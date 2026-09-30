package mss.url.model.mss_transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "url", schema = "mss_transaction")
public class Url {

    @Id
    @Column(name = "short_url")
    private String shortUrl;
    @Column(name = "url")
    private String url;
    @Column(name = "user_id")
    private Integer userId;

    public String getShortUrl() {
        return shortUrl;
    }

    public void setShortUrl(String shortUrl) {
        this.shortUrl = shortUrl;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "Url [shortUrl=" + shortUrl + ", url=" + url + ", userId=" + userId + "]";
    }

}
