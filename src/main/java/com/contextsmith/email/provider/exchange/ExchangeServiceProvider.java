package com.contextsmith.email.provider.exchange;

import microsoft.exchange.webservices.data.core.ExchangeService;
import microsoft.exchange.webservices.data.core.enumeration.misc.ExchangeVersion;
import microsoft.exchange.webservices.data.credential.ExchangeCredentials;
import microsoft.exchange.webservices.data.credential.WebCredentials;

import java.net.URI;
import java.util.Arrays;

/**
 * Provides instances of ExchangeService for users/token.
 *
 * Created by beders on 4/26/17.
 */
public class ExchangeServiceProvider {
    static final int CONNECTION_TIMEOUT = 45_000;

    /** Return a connected instance of ExchangeService.
     *
     * @param username username (i.e. e-mail address)
     * @param password password (char[] will be deleted after calling this)
     * @param url optional: endpoint to connect to. If not specified autodiscovery will be used
     * @return an exchange service instance
     * @throws Exception
     */
    public ExchangeService connectAsUser(String username, char[] password, String url) throws Exception {
        ExchangeService service = new ExchangeService(ExchangeVersion.Exchange2010_SP2);
        service.setTimeout(CONNECTION_TIMEOUT);
        ExchangeCredentials credentials = new WebCredentials(username, new String(password) /* TODO: remove again when EWS supports char */);
        service.setCredentials(credentials);
        if (url != null) {
            service.setUrl(URI.create(url));
        } else {
            service.autodiscoverUrl(username, redirectionUrl -> {
                return redirectionUrl.toLowerCase().startsWith("https://");
            });
        }
        Arrays.fill(password, '\0');
        System.out.println("Connected. URL to use for this account " + service.getUrl());
        return service;
    }
}
