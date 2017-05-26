package com.contextsmith.api.service;

import com.contextsmith.utils.StringUtil;

/**
 * Data class for returning error messages.
 * Created by beders on 5/16/17.
 */
public class JsonError {
    final String error;

    JsonError(String error) {
        this.error = error;
    }

    public String toJson() {
        return StringUtil.toJson(this);
    }


    public static JsonError error(Throwable t) {
        return new JsonError(t.getMessage());
    }
}
