package com.deepank.careerraft.notifications;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SmtpNotifier {
    public DeliveryReceipt send(EmailMessage message) {
        String host=env("CR_SMTP_HOST"), port=env("CR_SMTP_PORT"),
                user=env("CR_SMTP_USERNAME"), pass=env("CR_SMTP_PASSWORD"),
                from=env("CR_EMAIL_FROM");
        if (List.of(host,port,user,pass,from).stream().anyMatch(String::isBlank)) {
            throw new IllegalStateException("SMTP environment is incomplete");
        }

        Properties props=new Properties();
        props.put("mail.smtp.host",host);
        props.put("mail.smtp.port",port);
        props.put("mail.smtp.auth","true");
        boolean tls=Boolean.parseBoolean(System.getenv().getOrDefault("CR_SMTP_USE_TLS","true"));
        if(tls) props.put("mail.smtp.starttls.enable","true");
        else props.put("mail.smtp.ssl.enable","true");

        Session session=Session.getInstance(props,new Authenticator(){
            protected PasswordAuthentication getPasswordAuthentication(){
                return new PasswordAuthentication(user,pass);
            }
        });

        try {
            MimeMessage mail=new MimeMessage(session);
            mail.setFrom(new InternetAddress(from));
            mail.setRecipients(Message.RecipientType.TO,InternetAddress.parse(message.to()));
            mail.setSubject(message.subject(),"UTF-8");

            MimeMultipart mixed=new MimeMultipart("mixed");
            MimeMultipart alternative=new MimeMultipart("alternative");

            MimeBodyPart textPart=new MimeBodyPart();
            textPart.setText(message.text(),"UTF-8");
            alternative.addBodyPart(textPart);

            if(message.html()!=null&&!message.html().isBlank()) {
                MimeBodyPart htmlPart=new MimeBodyPart();
                htmlPart.setContent(message.html(),"text/html; charset=UTF-8");
                alternative.addBodyPart(htmlPart);
            }

            MimeBodyPart body=new MimeBodyPart();
            body.setContent(alternative);
            mixed.addBodyPart(body);

            for(Path p:message.attachments()) {
                if(!Files.isRegularFile(p)) throw new IllegalArgumentException("Attachment missing: "+p);
                MimeBodyPart a=new MimeBodyPart();
                a.attachFile(p.toFile());
                mixed.addBodyPart(a);
            }

            mail.setContent(mixed);
            Transport.send(mail);
            return new DeliveryReceipt("smtp",message.to(),message.subject());
        } catch(Exception e) {
            throw new IllegalStateException("SMTP delivery failed",e);
        }
    }

    private String env(String k){return System.getenv().getOrDefault(k,"");}
    public record DeliveryReceipt(String provider,String recipient,String subject){}
}
