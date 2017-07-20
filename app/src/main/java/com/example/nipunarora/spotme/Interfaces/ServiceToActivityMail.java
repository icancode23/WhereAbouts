package com.example.nipunarora.spotme.Interfaces;

/**
 * Created by nipunarora on 20/07/17.
 */

public interface ServiceToActivityMail {
    void onReceiveServiceMail(String Action,Object... attachments);
}
