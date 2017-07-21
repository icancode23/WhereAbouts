package com.example.nipunarora.spotme.DataModels;

/**
 * Created by nipunarora on 21/06/17.
 */

public class LocationData {
    public Double lat,lng;
    public String address,timestamp;

    public LocationData() {
    }

    public LocationData(Double latitude, Double longitude,String address,String timestamp) {
        this.lat = latitude;
        this.lng = longitude;
        this.address=address;
        this.timestamp=timestamp;
    }
}
