package com.contextsmith.api.service;

import com.contextsmith.utils.InternetAddressUtil;

import javax.mail.internet.InternetAddress;

/**
 * Created by beders on 4/27/17.
 */
public class Credential {
    private String service;
    private String username;
    private String /*char[]*/ password; // we can't prevent the password from being in memory as a string for now
    private String url;

    public Credential(String service, String username, char[] password) {
        this.service = service;
        this.username = username;
        this.password = new String(password);
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public char[] getPassword() {
        return password.toCharArray();
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public InternetAddress getEmailAddress() {
        return InternetAddressUtil.newIAddress(this.username);
    }
}
