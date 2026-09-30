package mss.url.model.mss_analytics;

import java.sql.Timestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "mss_analytics", name = "raw")
public class Raw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "url_id", nullable = false)
    private String urlId;

    @Column(name = "hit_time", nullable = false)
    private Timestamp hitTime;

    @Column(name = "ip_address")
    private String ipAddress;

    protected Raw() {
    }

    public Raw(String urlId, Timestamp hitTime, String ipAddress) {
        this.urlId = urlId;
        this.hitTime = hitTime;
        this.ipAddress = ipAddress;
    }

    public int getId() {
        return id;
    }

    public String getUrlId() {
        return urlId;
    }

    public Timestamp getHitTime() {
        return hitTime;
    }

    public String getIpAddress() {
        return ipAddress;
    }

}
