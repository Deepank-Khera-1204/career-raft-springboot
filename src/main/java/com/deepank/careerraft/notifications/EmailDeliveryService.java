package com.deepank.careerraft.notifications;

import com.deepank.careerraft.domain.*;
import com.deepank.careerraft.documents.ApplicationPackageBuilder;
import com.deepank.careerraft.referrals.ReferralModels;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class EmailDeliveryService {
    private final SmtpNotifier notifier;
    public EmailDeliveryService(SmtpNotifier n){notifier=n;}

    public SmtpNotifier.DeliveryReceipt sendPackage(
            Job job, JobAssessment a, List<ReferralModels.ReferralTarget> referrals,
            ApplicationPackageBuilder.ApplicationPackage p, String recipient) {

        String score = String.format(Locale.ROOT,"%.0f",a.combinedScore());
        String subject = "Aizen-sama — "+job.company()+" · "+job.title()+" · "+score+"/100";

        StringBuilder text=new StringBuilder();
        text.append("Konnichiwa, Aizen-sama.\n\n")
            .append("I have taken the liberty of bringing an opportunity to your attention.\n\n")
            .append(job.title()).append(" — ").append(job.company()).append("\n")
            .append("Location: ").append(Objects.toString(job.location(),"Not specified")).append("\n")
            .append("Work mode: ").append(Objects.toString(job.workMode(),"Not specified")).append("\n")
            .append("Match: ").append(score).append("/100 · ").append(a.nextAction()).append("\n")
            .append("Job: ").append(job.url()).append("\n\nWHY I BROUGHT IT TO YOU\n-----------------------\n");
        for(String r:a.rationale()) text.append("• ").append(r).append("\n");

        text.append("\nMATCHED SKILLS\n--------------\n");
        for(String s:a.matchedSkills()) text.append("• ").append(s).append("\n");
        text.append("\nMISSING / WEAK AREAS\n--------------------\n");
        for(String s:a.missingSkills()) text.append("• ").append(s).append("\n");
        text.append("\nSELECTED PROJECTS\n------------------\n");
        for(String s:a.selectedProjects()) text.append("• ").append(s).append("\n");

        text.append("\nREFERRAL TARGETS\n----------------\n");
        if(referrals.isEmpty()) text.append("• No public referral profiles were surfaced.\n");
        for(var r:referrals) text.append("• [").append(r.category()).append("] ").append(r.name())
                .append(" — ").append(Objects.toString(r.title(),"Title unavailable"))
                .append("\n  LinkedIn: ").append(r.profileUrl())
                .append("\n  Confidence: ").append(r.confidence()).append("\n");

        text.append("\nON THE TABLE\n------------\n• ")
                .append(p.resumePdf().getFileName()).append("\n• ")
                .append(p.resumeTex().getFileName()).append("\n• ")
                .append(p.coverLetterPdf().getFileName()).append("\n\n")
                .append("Everything is prepared for your review. Career Raft does not submit applications or contact people automatically.\n\n")
                .append("Your humble assistant,\nCareer Raft");

        String html="<html><body style='font-family:Arial,sans-serif;line-height:1.5;color:#332f29'>"
                +"<h2>Konnichiwa, Aizen-sama.</h2>"
                +"<h1>"+esc(job.title())+"</h1>"
                +"<p><strong>"+esc(job.company())+"</strong> · "+esc(Objects.toString(job.location(),"Not specified"))+"</p>"
                +"<p><strong>Match: "+score+"/100</strong> · "+esc(String.valueOf(a.nextAction()))+"</p>"
                +"<p><a href='"+escAttr(job.url())+"'>View the opportunity ↗</a></p>"
                +"<h3>Why I brought it to you</h3><ul>"+items(a.rationale())+"</ul>"
                +"<h3>Matched skills</h3><p>"+chips(a.matchedSkills())+"</p>"
                +"<h3>Missing / weak areas</h3><p>"+chips(a.missingSkills())+"</p>"
                +"<h3>Selected projects</h3><p>"+chips(a.selectedProjects())+"</p>"
                +"<h3>Referral targets</h3>"+referralHtml(referrals)
                +"<h3>On the table</h3><p>📎 "+esc(p.resumePdf().getFileName().toString())
                +"<br>📎 "+esc(p.resumeTex().getFileName().toString())
                +"<br>📎 "+esc(p.coverLetterPdf().getFileName().toString())
                +"</p><p>The final decision remains yours. Career Raft does not submit applications or contact people automatically.</p>"
                +"<p>Your humble assistant,<br>Career Raft</p></body></html>";

        return notifier.send(new EmailMessage(recipient,subject,text.toString(),html,
                List.of(p.resumePdf(),p.resumeTex(),p.coverLetterPdf())));
    }

    private String items(List<String> values){
        if(values==null||values.isEmpty()) return "<li>None recorded.</li>";
        StringBuilder b=new StringBuilder();for(String v:values)b.append("<li>").append(esc(v)).append("</li>");return b.toString();
    }
    private String chips(List<String> values){
        if(values==null||values.isEmpty()) return "<span>None surfaced.</span>";
        StringBuilder b=new StringBuilder();for(String v:values)b.append("<span style='display:inline-block;background:#f2efe9;padding:5px 9px;margin:2px;border-radius:12px'>").append(esc(v)).append("</span>");return b.toString();
    }
    private String referralHtml(List<ReferralModels.ReferralTarget> values){
        if(values==null||values.isEmpty()) return "<p>No public referral profiles were surfaced.</p>";
        StringBuilder b=new StringBuilder("<ul>");for(var r:values)b.append("<li><strong>").append(esc(r.name())).append("</strong> — ")
                .append(esc(Objects.toString(r.title(),"Title unavailable"))).append(" · <a href='")
                .append(escAttr(r.profileUrl())).append("'>LinkedIn ↗</a> · confidence ")
                .append(r.confidence()).append("</li>");return b.append("</ul>").toString();
    }
    private String esc(Object v){return java.util.Objects.toString(v,"").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private String escAttr(Object v){return esc(v).replace("'","&#39;").replace("\"", "&quot;");}
}
