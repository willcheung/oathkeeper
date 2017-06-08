package com.contextsmith.api.service;

import com.contextsmith.api.data.Contact;
import com.google.gson.annotations.SerializedName;

/**
 * Used to hold result of an authentication attempt.
 * Created by beders on 5/16/17.
 */
public class AuthResult {
    @SerializedName("logged_in")
    public final boolean loggedIn;
    public final String message;
    public final String url;
    public final Contact contact;

    public AuthResult(boolean loggedIn, String message, String url) {
        this(loggedIn, message, url, null);
    }
    public AuthResult(boolean loggedIn, String message, String url, Contact contact) {
        this.loggedIn = loggedIn;
        this.message = message;
        this.url = url;
        this.contact = contact;
    }

}
