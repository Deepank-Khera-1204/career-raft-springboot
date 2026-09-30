package com.deepank.careerraft.documents;

import com.deepank.careerraft.intelligence.*;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import com.deepank.careerraft.config.RuntimePaths;
import java.util.*;
import java.util.regex.*;

@Service
public class ResumeTailor {
    private final Path template = RuntimePaths.resume().resolve("resume.tex");
    private final EvidenceCatalog catalog;
    private final EvidenceMatcher matcher;
    private final LatexPipeline latex;

    public ResumeTailor(EvidenceCatalog catalog) {
        this.catalog = catalog;
        this.matcher = new EvidenceMatcher(catalog);
        this.latex = new LatexPipeline();
    }

    public TailoredResume build(String jobDescription, int topProjects,
                                List<String> semanticEvidenceIds,
                                List<String> semanticProjectIds) throws Exception {
        String source = Files.readString(template);
        int projectCount = resumeProjectCount();
        if (topProjects <= 0) topProjects = projectCount;
        if (topProjects != projectCount) throw new IllegalArgumentException("topProjects must match configured resume project count");

        List<String> preferred = new ArrayList<>(semanticProjectIds == null ? List.of() : semanticProjectIds);
        if (semanticEvidenceIds != null) {
            semanticEvidenceIds.stream().map(this::projectGroup).filter(Objects::nonNull).forEach(preferred::add);
        }
        preferred = preferred.stream().distinct().toList();

        List<String> groups = projectGroups(jobDescription, projectCount, preferred);
        Map<String,List<EvidenceClaim>> projectClaims = projectClaims(jobDescription, groups,
                semanticEvidenceIds == null ? List.of() : semanticEvidenceIds);
        List<EvidenceClaim> experience = experienceClaims(jobDescription,
                semanticEvidenceIds == null ? List.of() : semanticEvidenceIds, 7);

        Map<String,String> placeholders = new LinkedHashMap<>();
        placeholders.put("NAME", escape(identity().get("name")));
        placeholders.put("CONTACT", contactBlock());
        placeholders.put("SKILLS", skillsBlock());
        placeholders.put("EDUCATION", educationBlock());
        placeholders.put("CERTIFICATES", certificatesBlock());

        String filled = latex.replacePlaceholders(source, placeholders);
        String rendered = latex.render(filled, Map.of(
                "EXPERIENCE_BULLETS", experienceBlock(experience),
                "PROJECTS", projectBlock(groups, projectClaims)
        ));

        List<String> claimIds = new ArrayList<>();
        experience.forEach(c -> claimIds.add(c.id()));
        projectClaims.values().forEach(list -> list.forEach(c -> claimIds.add(c.id())));

        return new TailoredResume(rendered, groups, claimIds.stream().distinct().toList(),
                List.of("EXPERIENCE_BULLETS", "PROJECTS"));
    }

    private int resumeProjectCount() {
        Map<String,Object> policy = map(catalog.knowledgeBase().profile().get("application_policy"));
        Map<String,Object> layout = map(policy.get("resume_layout"));
        Object value = layout.getOrDefault("project_count", 3);
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid resume project count", e);
        }
    }

    private Map<String,Object> identity() {
        Object x = catalog.knowledgeBase().profile().get("identity");
        return map(x);
    }

    private List<EvidenceClaim> experienceClaims(String jd, List<String> preferred, int topN) {
        List<Map<String,Object>> roles = catalog.knowledgeBase().roles();
        if (roles.isEmpty()) return List.of();
        Map<String,Object> latest = roles.stream()
                .max(Comparator.comparing(r -> Objects.toString(r.get("start"), "")))
                .orElseThrow();
        Set<String> latestIds = new LinkedHashSet<>();
        for (Object raw : listObjects(latest.get("responsibilities"))) {
            if (raw instanceof Map<?,?> m && "confirmed".equals(Objects.toString(m.get("evidence_status"), ""))) {
                latestIds.add(Objects.toString(m.get("id"), ""));
            }
        }

        Map<String,EvidenceMatch> matches = new HashMap<>();
        for (EvidenceMatch m : matcher.match(jd, Math.max(50, catalog.claims().size()))) matches.put(m.claimId(), m);

        List<String> selected = new ArrayList<>();
        preferred.stream().filter(latestIds::contains).forEach(selected::add);
        matches.entrySet().stream()
                .filter(e -> latestIds.contains(e.getKey()) && e.getValue().score() >= .55)
                .sorted(Map.Entry.<String,EvidenceMatch>comparingByValue(
                        Comparator.comparingDouble(EvidenceMatch::score).reversed()
                ))
                .map(Map.Entry::getKey).forEach(selected::add);
        latestIds.forEach(id -> { if (selected.size() < Math.min(6, latestIds.size()) && !selected.contains(id)) selected.add(id); });

        return selected.stream().distinct().limit(Math.min(topN, latestIds.size())).map(catalog::get).toList();
    }

    private List<String> projectGroups(String jd, int count, List<String> preferred) {
        Map<String,Map<String,Object>> projects = catalog.knowledgeBase().projectsById();
        List<String> eligible = projects.values().stream()
                .filter(p -> Set.of("confirmed","confirmed_from_resume").contains(Objects.toString(p.get("evidence_status"), "")))
                .filter(p -> listObjects(p.get("claims")).stream().anyMatch(x ->
                        x instanceof Map<?,?> m && "confirmed".equals(Objects.toString(m.get("evidence_status"), ""))))
                .map(p -> Objects.toString(p.get("id"), ""))
                .toList();
        if (eligible.size() < count) throw new IllegalStateException("Not enough confirmed resume-ready projects");

        Map<String,Double> scores = new HashMap<>();
        eligible.forEach(id -> scores.put(id, 0d));
        for (EvidenceMatch m : matcher.projectRecommendations(jd, Math.max(50, catalog.claims().size()))) {
            String group = projectGroup(m.claimId());
            if (group != null && scores.containsKey(group)) scores.merge(group, m.score(), Math::max);
        }
        preferred.stream().map(this::projectGroup).filter(Objects::nonNull).filter(scores::containsKey)
                .forEach(g -> scores.merge(g, .20, Double::sum));

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String,Double>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(count).map(Map.Entry::getKey).toList();
    }

    private Map<String,List<EvidenceClaim>> projectClaims(String jd, List<String> groups, List<String> preferred) {
        Map<String,EvidenceMatch> matches = new HashMap<>();
        for (EvidenceMatch m : matcher.match(jd, Math.max(50, catalog.claims().size()))) matches.put(m.claimId(), m);
        Map<String,List<EvidenceClaim>> out = new LinkedHashMap<>();
        for (String group : groups) {
            Map<String,Object> project = catalog.knowledgeBase().projectsById().get(group);
            List<String> confirmedIds = listObjects(project.get("claims")).stream()
                    .filter(x -> x instanceof Map<?,?> m && "confirmed".equals(Objects.toString(m.get("evidence_status"), "")))
                    .map(x -> Objects.toString(((Map<?,?>)x).get("id"), "")).toList();
            List<String> ids = new ArrayList<>();
            preferred.stream().filter(confirmedIds::contains).forEach(ids::add);
            matches.entrySet().stream()
                    .filter(e -> confirmedIds.contains(e.getKey()) && e.getValue().score() >= .45)
                    .sorted(Map.Entry.<String,EvidenceMatch>comparingByValue(
                            Comparator.comparingDouble(EvidenceMatch::score).reversed()))
                    .map(Map.Entry::getKey).forEach(ids::add);
            confirmedIds.forEach(id -> { if (ids.size() < 3 && !ids.contains(id)) ids.add(id); });
            out.put(group, ids.stream().distinct().limit(5).map(catalog::get).toList());
        }
        return out;
    }

    private String skillsBlock() {
        Object raw = catalog.knowledgeBase().skills().stream().findAny().orElse(null);
        // Resume skill groups live in skills.yaml as presentation policy.
        Object yamlGroups = loadDocument("skills").get("resume_groups");
        if (!(yamlGroups instanceof List<?> groups)) return "";
        List<String> lines = new ArrayList<>();
        for (Object g : groups) {
            if (!(g instanceof Map<?,?> m)) continue;
            String label = Objects.toString(m.get("label"), "");
            List<?> skills = listObjects(m.get("skills"));
            if (!label.isBlank() && !skills.isEmpty())
                lines.add("\\textbf{" + escape(label) + "}: "
                        + escape(skills.stream().map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(", "))) + " \\\\");
        }
        return String.join("\n", lines);
    }

    private String educationBlock() {
        Map<String,Object> e = catalog.knowledgeBase().education().stream()
                .filter(x -> "confirmed".equals(Objects.toString(x.get("evidence_status"), "")))
                .findFirst().orElseThrow(() -> new IllegalStateException("Confirmed education record is required"));
        return "\\section{Education}\n\\resumeSubHeadingListStart\n  \\resumeSubheading\n"
                + "    {" + escape(e.get("institution")) + "}{" + escape(e.get("start")) + " -- " + escape(e.get("end")) + "}\n"
                + "    {" + escape(e.get("degree")) + "; \\textbf{CGPA: " + escape(e.get("cgpa")) + "}}{" + escape(e.get("location")) + "}\n"
                + "\\resumeSubHeadingListEnd";
    }

    private String certificatesBlock() {
        Map<String,Object> docs = loadDocument("certifications");
        Map<String,Map<String,Object>> byId = new LinkedHashMap<>();
        for (Object x : listObjects(docs.get("certifications"))) if (x instanceof Map<?,?> m) byId.put(Objects.toString(m.get("id"), ""), map(m));
        List<String> ids = stringList(docs.get("resume_certification_ids"));
        List<Map<String,Object>> selected = ids.stream().filter(byId::containsKey).map(byId::get).toList();
        if (selected.isEmpty()) selected = byId.values().stream().limit(2).toList();
        List<String> items = new ArrayList<>();
        for (Map<String,Object> c : selected) if ("confirmed".equals(Objects.toString(c.get("evidence_status"), ""))) {
            items.add("        \\resumeItem{\\textbf{" + escape(c.get("name")) + "} -- " + escape(c.get("issuer"))
                    + (Objects.toString(c.get("details"), "").isBlank() ? "" : ": " + escape(c.get("details"))) + "}");
        }
        return "\\section{Certificates}\n\\begin{itemize}[leftmargin=0.16in,itemsep=1pt,parsep=0pt,topsep=1pt]\n"
                + String.join("\n", items) + "\n\\end{itemize}";
    }

    private String contactBlock() {
        String email = Objects.toString(identity().get("email"), "");
        String phone = Objects.toString(identity().get("phone"), "");
        String linkedin = Objects.toString(identity().get("linkedin"), "");
        String gitlab = Objects.toString(identity().get("gitlab"), "");
        return escape(phone) + " ~\\textbar{}~ \\href{mailto:" + email + "}{" + escape(email) + "}"
                + " ~\\textbar{}~ \\href{" + escapeUrl(linkedin) + "}{LinkedIn}"
                + " ~\\textbar{}~ \\href{" + escapeUrl(gitlab) + "}{GitLab}";
    }

    private String experienceBlock(List<EvidenceClaim> claims) {
        return String.join("\n", claims.stream().map(c -> "      \\resumeItem{" + experienceText(c.text()) + "}").toList());
    }

    private String projectBlock(List<String> groups, Map<String,List<EvidenceClaim>> claims) {
        List<String> blocks = new ArrayList<>();
        for (String group : groups) {
            Map<String,Object> p = catalog.knowledgeBase().projectsById().get(group);
            String tech = String.join(" \\textbar{} ", listObjects(p.get("technologies")).stream().limit(4).map(Object::toString).map(this::escape).toList());
            String heading = "  \\resumeProjectHeading{\\textbf{" + escape(Objects.toString(p.get("name"), "").replace("—", "--"))
                    + "}\\hfill \\href{" + escapeUrl(Objects.toString(p.get("url"), "")) + "}{\\small [Link]}}{" + tech + "}";
            String bullets = String.join("\n", claims.getOrDefault(group, List.of()).stream()
                    .map(c -> "      \\resumeItem{" + escape(c.text()) + "}").toList());
            blocks.add(heading + "\n    \\resumeItemListStart\n" + bullets + "\n    \\resumeItemListEnd");
        }
        return String.join("\n    \\vspace{-9pt}\n", blocks);
    }

    private String experienceText(String value) {
        return escape(value).replace("twice current account volume", "2$\\times$ current account volume")
                .replace("JUnit, Mockito and Cucumber", "JUnit, Mockito, and Cucumber");
    }

    private String projectGroup(String claimId) {
        for (Map<String,Object> project : catalog.knowledgeBase().projects()) {
            String pid = Objects.toString(project.get("id"), "");
            if (claimId.equals(pid) || claimId.startsWith(pid + "-")
                    || listObjects(project.get("claims")).stream().anyMatch(x ->
                    x instanceof Map<?,?> m && claimId.equals(Objects.toString(m.get("id"), "")))) return pid;
        }
        return null;
    }

    private Map<String,Object> loadDocument(String name) {
        try { return new com.fasterxml.jackson.dataformat.yaml.YAMLMapper()
                .readValue(Files.readString(RuntimePaths.data().resolve(name + ".yaml")),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}); }
        catch(Exception e){ throw new IllegalStateException("Unable to load data/"+name+".yaml",e); }
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(Object x) { return x instanceof Map<?,?> m ? (Map<String,Object>)(Map<?,?>)m : Map.of(); }
    private static List<?> listObjects(Object x) { return x instanceof List<?> l ? l : List.of(); }
    private static List<String> stringList(Object x) { return listObjects(x).stream().map(String::valueOf).toList(); }
    private String escape(Object x){String v=Objects.toString(x,"");return v.replace("\\","\\textbackslash{}").replace("&","\\&").replace("%","\\%").replace("$","\\$").replace("#","\\#").replace("_","\\_").replace("{","\\{").replace("}","\\}");}
    private String escapeUrl(String v){return Objects.toString(v,"").replace("%","\\%").replace("#","\\#").replace("{","\\{").replace("}","\\}");}
}
