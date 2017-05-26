package com.contextsmith.api.service;

import com.contextsmith.utils.InternetAddressUtil;

import javax.mail.internet.InternetAddress;

/**
 * Source data.
 * Created by beders on 5/13/17.
 */
public class Source {
    public NewsFeederRequest.Provider kind;
    public String token;
    public String email;
    public String password;
    public String url;

    public InternetAddress getEmailAddress() {
        return InternetAddressUtil.newIAddress(this.email);
    }
}
