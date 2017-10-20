package com.contextsmith.email.provider.exchange;

import com.google.api.services.calendar.model.Event;
import microsoft.exchange.webservices.data.core.ExchangeService;

import java.net.URI;
import java.util.List;

public class OAuthTest {
    public static void main(String... args) throws Exception {
        String token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6IkhIQnlLVS0wRHFBcU1aaDZaRlBkMlZXYU90ZyIsImtpZCI6IkhIQnlLVS0wRHFBcU1aaDZaRlBkMlZXYU90ZyJ9.eyJhdWQiOiIwMDAwMDAwMi0wMDAwLTAwMDAtYzAwMC0wMDAwMDAwMDAwMDAiLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC9kN2Q5MzcxNC05OTY2LTQzNTQtYmI2Mi1mYTVhYWJlMjZmMjkvIiwiaWF0IjoxNTA3NTk1MjY4LCJuYmYiOjE1MDc1OTUyNjgsImV4cCI6MTUwNzU5OTE2OCwiYWNyIjoiMSIsImFpbyI6IlkyVmdZS2oxWnYxUXNMeGM3L1hhenRQVHZlNTNpcG9zZFBydzg0R1l4dFVYZFgzdFM1b0EiLCJhbXIiOlsicHdkIl0sImFwcGlkIjoiZjljN2M5MTgtZTcwMy00Y2ZlLWJlNzAtNDdmOWE4MDM1OWMxIiwiYXBwaWRhY3IiOiIxIiwiZmFtaWx5X25hbWUiOiJCZWRlcnNkb3JmZXIiLCJnaXZlbl9uYW1lIjoiSm9jaGVuIiwiaXBhZGRyIjoiNzMuOTMuMTUzLjEyMCIsIm5hbWUiOiJKb2NoZW4gQmVkZXJzZG9yZmVyIiwib2lkIjoiYWMzMWE2MWMtNDFhYy00ZjQzLWEzY2YtNDdjZmE0ZGEyZTAzIiwicHVpZCI6IjEwMDNCRkZEQTA5OUQwMTAiLCJzY3AiOiJVc2VyLlJlYWQiLCJzdWIiOiJFdmxDRDRnYmNoQk16RHFaTzloQWJQQk1ZVmxsQTZKQU16cU8wUEs2ZjVvIiwidGVuYW50X3JlZ2lvbl9zY29wZSI6Ik5BIiwidGlkIjoiZDdkOTM3MTQtOTk2Ni00MzU0LWJiNjItZmE1YWFiZTI2ZjI5IiwidW5pcXVlX25hbWUiOiJiZWRlcnNAY29udGV4dHNtaXRoLm9ubWljcm9zb2Z0LmNvbSIsInVwbiI6ImJlZGVyc0Bjb250ZXh0c21pdGgub25taWNyb3NvZnQuY29tIiwidXRpIjoicE10VWZDNUFka2VfYU9RNjU2WjRBQSIsInZlciI6IjEuMCJ9.eW3C4_2sxpIUEzRNKa-OcLlogE__FvOzYXDWKkJjb8gcLGnTnTN1nYBVyVqmR2tihH5nOQM7vsakwK71uBzbdSmoU4YSrp9CNUqrob-JRfz68fW-Aam5qzHc03riqfj8YxdCVOGTYs5-kqFcTjFauIfbRrrUy8ye8Oxl9Q-P01tHunF7RbMYDIaVXPMR2ZgKtng0AuWgQ3zSharg4GR8TMNwGbZ_Et7ftKrk-43ZlO4PZJZnJOsWzd6TBHh1bJihPqOlBsfruwOFVExJlZzgJnSff2BtHKux-eY6ZOdJwKSN0q2C1r3dEbZp_K-_8JDocOo-RTjKgAR0TU3bncy1Yg";
        String url = "https://outlook.office365.com/EWS/Exchange.asmx";
        ExchangeService service = new ExchangeService();
        service.setCredentials(new BearerTokenCredentials(token));
        service.setUrl(URI.create(url));
        EventProducer eventProducer = new EventProducer(service);
        eventProducer.maxMessages(10);
        List<Event> events = eventProducer.asFlux().collectList().block();
        System.out.println(events);
    }
}

/*
  [Content-Length: 0, Server: Microsoft-IIS/8.5, request-id: 7d86d893-adc8-4200-b285-bf2bb35644a8, X-CalculatedFETarget: DM5PR13CU002.internal.outlook.com,
  X-BackEndHttpStatus: 401, Set-Cookie: exchangecookie=6ceb8e862ced4cba85704b91d8f0ad1f; expires=Wed, 10-Oct-2018 00:37:59 GMT;
  path=/; HttpOnly, X-FEProxyInfo: DM5PR13CA0042.NAMPRD13.PROD.OUTLOOK.COM, X-CalculatedBETarget: DM5PR18MB1004.namprd18.prod.outlook.com,
  X-BackEndHttpStatus: 401, x-ms-diagnostics: 2000003;reason="The audience claim value is invalid 'aud'.";error_category="invalid_resource",
   X-DiagInfo: DM5PR18MB1004, X-BEServer: DM5PR18MB1004, X-FEServer: DM5PR13CA0042, X-Powered-By: ASP.NET, X-FEServer: MWHPR18CA0031,
    WWW-Authenticate: Bearer client_id="00000002-0000-0ff1-ce00-000000000000",
    trusted_issuers="00000001-0000-0000-c000-000000000000@*",
    token_types="app_asserted_user_v1 service_asserted_app_v1",
     authorization_uri="https://login.windows.net/common/oauth2/authorize", error="invalid_token",Basic Realm="",Basic Realm="",Basic Realm="",
      Date: Tue, 10 Oct 2017 00:37:59 GMT]
 */
