package com.contextsmith.email.exporter;

import com.contextsmith.api.service.NewsFeederRequest;
import com.contextsmith.api.service.Source;
import com.contextsmith.email.provider.exchange.ExchangeServiceProvider;
import com.contextsmith.email.provider.exchange.MimeMessageProducer;
import com.contextsmith.utils.Args;
import com.martiansoftware.validation.Hope;
import microsoft.exchange.webservices.data.core.ExchangeService;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Created by beders on 6/3/17.
 */
public class EmailExporter {
    Source source;
    Path dir;

    public EmailExporter(String... args) throws Exception {
        source = new Source();
        Args.match(args)
                .on("--kind", value -> source.kind = NewsFeederRequest.Provider.valueOf(value))
                .on("--email", value -> source.email = value)
                .on("--password", value -> source.password = value)
                .on("--url", value -> source.url = value)
                .on("--dir", value -> dir = Paths.get(value));

        Hope.that(source.kind).isNotNull();
        Hope.that(dir).named("Destination").isNotNull().isTrue(p -> p.toFile().isDirectory());
        export();
    }

    private void export() throws Exception {
        ExchangeService exchangeService = new ExchangeServiceProvider().connectAsUser(source.email, source.password.toCharArray(), source.url);
        new MimeMessageProducer(exchangeService).asFlux().map(msg -> toPath(msg)).collectList().block();
    }

    private Path toPath(MimeMessage msg) {
        Path path;
        try {
            Instant sentDate = msg.getSentDate().toInstant()
                    .truncatedTo(ChronoUnit.DAYS); // one dir per day
            path = dir.resolve(sentDate.toString());
        } catch (MessagingException e) {
            e.printStackTrace();
            path = Paths.get(".", "invalid_sent_date");
        }
        path.toFile().mkdirs();
        try {
            path = path.resolve(msg.getSentDate().getTime() + "_" + msg.getMessageID() + ".txt");
        } catch (MessagingException e) {
            e.printStackTrace();
            path = path.resolve("" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + ".txt");
        }
        try (OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            msg.writeTo(os);
            os.close();
        } catch (IOException | MessagingException e) {
            e.printStackTrace();
        }
        return path;
    }


    public static void main(String... args) throws Exception {
        EmailExporter emailExporter = new EmailExporter(args);
    }

    static class Switch {
        String arg;

        static Switch match(String arg) {
            Switch aSwitch = new Switch();
            aSwitch.arg = arg;
            return aSwitch;
        }

        Switch on(String comparison, Runnable handler) {
            if (arg.equalsIgnoreCase(comparison)) {
                handler.run();
            }
            return this;
        }
    }
}
