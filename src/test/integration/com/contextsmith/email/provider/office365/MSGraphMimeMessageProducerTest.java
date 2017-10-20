package com.contextsmith.email.provider.office365;

import com.contextsmith.utils.MimeMessageUtil;
import com.microsoft.graph.extensions.IGraphServiceClient;
import org.junit.Test;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

import java.util.List;

import static us.monoid.web.Resty.enc;

public class MSGraphMimeMessageProducerTest {


    @Test
    public void getSomeResults() throws Exception {
        String token = getAccessToken();
        IGraphServiceClient client = new Office365ServiceProvider().createClient(token);

        MSGraphMimeMessageProducer producer = new MSGraphMimeMessageProducer(client);
        producer.maxMessages(100);
        List<String> messages = producer.asFlux().map(MimeMessageUtil::toString).collectList().block();
        System.out.println(messages.size() + " received");
        if (messages.size() < 200) {
            System.out.println(messages);
        }
    }

    private static String getAccessToken() throws Exception {
        String clientID = "35bed7bd-6fd6-4dc1-aaa6-579849355012";
        String clientSecret = "jEkMUAj8dxP9bMnxQMbxkko";
        String refreshToken = "OAQABAAAAAAABlDrqfEFlSaui6xnRjX5EwHDi4XbAiZIACVwQ4EAtk0pbDkcXvVGVbycNf5N_DS0Ot9aMRsOw0hmIuQG1hfq61rxHRJ-zEkZ5hGaLehEZ2tqRgGIjpXCndUzjsnYpRtIJ1iwCp9monWMXMegEvRr5moccwMZpQlO091tYruk0Ysqw2e5wWfs13jGBV_8IrxDyscB85ZcA65PT97GoRXyoym99cRkGv05cB3aZ7Q5hdSqInaslJI6gZK8KZBlsHgMugyDmqtVlV2LBLkPHpZUS8xELit59HN2VAiMY15XBHabPRwpwsNz5kfMFezpJmdR2v7twnhscGac45AWcgLp6VkI4BCjKfmzogIAsTsoQ199Qr_8TyVEgQtbtPZxUGSItZS-s26dkRC5XtN9cR0Wab0euHkYsPSoh1CjEc5JuAk-gFfNOQ79WUyiLvBvDrjK9iEfUwxn7GenykAMD7g5wYXk5yP-ZKsp5QsrFe6gWdpXzLNItp70tgM3YUxOSAK0OJCzC8-3a9sgdvxblLY6Fk00ikLxDOSBhdsLIdzBIegWFFB1TvHLFZXXuBYGqVaUfKAn7uABJrLg3kNf6F1v23yqOVi_eE42KF6lu0vjMwFxdMcu98RDM9nLsFzmnMTlpjRYm3XDz8Vl-OBE9y4MLvKEvm0kxvVQKUeDxm7QmTzgoJn9Ybt91xrGfzHM_NBwwhHRcPk-BXAlWcG2h5Y4M6TIw32UiYsFi7NFvtlblGcjZQ5ULgO9kiSHdL_BPJsaJZup3nngHCjfE4RxfDHB1fHcNQRaB_41Vz6-0jL91a_orgxHZPO4Hl7JdVSJ8yoMgAA";
        Resty r = new Resty();
        JSONResource json = r.json("https://login.microsoftonline.com/common/oauth2/v2.0/token", Resty.form("refresh_token=" + refreshToken
                + "&client_id=" + enc(clientID) + "&client_secret=" + enc(clientSecret) + "&grant_type=refresh_token"));
        String token = json.get("access_token").toString();
        return token;
    }
}