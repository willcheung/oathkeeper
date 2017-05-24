package com.contextsmith.api.service;

import com.martiansoftware.validation.UncheckedValidationException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Handler for friendlier error messages from invalid data.
 * Created by beders on 5/16/17.
 */
@Provider
public class ValidationExceptionHandler implements ExceptionMapper<UncheckedValidationException> {
    public Response toResponse(UncheckedValidationException ex) {
        return Response.status(400).
                entity(JsonError.error(ex).toJson()).
                type("application/json").
                build();
    }
}

