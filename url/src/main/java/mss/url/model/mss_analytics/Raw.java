package mss.url.model.mss_analytics;

import java.sql.Timestamp;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "raw", schema = "mss_analytics")
public class Raw {

    @Id
    private int id;
    private String url_id;
    private Timestamp hit_time;
    private String ip_address;

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUrl_id() {
        return url_id;
    }

    public void setUrl_id(String url_id) {
        this.url_id = url_id;
    }

    public Timestamp getHit_time() {
        return hit_time;
    }

    public void setHit_time(Timestamp hit_time) {
        this.hit_time = hit_time;
    }

    public String getIp_address() {
        return ip_address;
    }

    public void setIp_address(String ip_address) {
        this.ip_address = ip_address;
    }

}
