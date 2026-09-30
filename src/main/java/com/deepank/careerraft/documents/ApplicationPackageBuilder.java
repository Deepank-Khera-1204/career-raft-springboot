package com.deepank.careerraft.documents;

import com.deepank.careerraft.domain.Job;
import com.deepank.careerraft.domain.JobAssessment;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.deepank.careerraft.intelligence.EvidenceCatalog;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import com.deepank.careerraft.config.RuntimePaths;
import java.util.*;

@Service
public class ApplicationPackageBuilder {
    private final ResumeTailor resumeTailor;
    private final CoverLetterBuilder coverLetterBuilder;
    private final CoverLetterRenderer coverLetterRenderer;
    private final LatexPipeline latexPipeline = new LatexPipeline();
    private final EvidenceCatalog catalog;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApplicationPackageBuilder(ResumeTailor resumeTailor, CoverLetterBuilder coverLetterBuilder,
                                     CoverLetterRenderer coverLetterRenderer, EvidenceCatalog catalog) {
        this.resumeTailor = resumeTailor;
        this.coverLetterBuilder = coverLetterBuilder;
        this.coverLetterRenderer = coverLetterRenderer;
        this.catalog = catalog;
    }

    public ApplicationPackage build(Job job, JobAssessment assessment) throws Exception {
        String safeId = job.id().replaceAll("[^A-Za-z0-9_-]", "_");
        Path directory = RuntimePaths.data().resolve("applications").resolve(safeId);
        Files.createDirectories(directory);

        TailoredResume tailored = resumeTailor.build(
                job.description(), 3,
                assessment.semanticEvidenceIds(),
                assessment.selectedProjects()
        );

        Path resumeTex = directory.resolve("resume.tex");
        Files.writeString(resumeTex, tailored.tex());
        Path resumePdf = latexPipeline.compile(resumeTex);
        latexPipeline.validatePageCount(resumePdf);

        CoverLetter letter = coverLetterBuilder.build(
                job, assessment.semanticEvidenceIds()
        );
        Path coverPdf = coverLetterRenderer.render(
                letter, job, directory.resolve("cover_letter.pdf")
        );

        Map<String,Object> manifestData = new LinkedHashMap<>();
        manifestData.put("job", job);
        manifestData.put("assessment", assessment);
        manifestData.put("resume", Map.of(
                "selected_claim_ids", tailored.selectedClaimIds(),
                "selected_project_ids", tailored.selectedProjectIds(),
                "changed_regions", tailored.changedRegions(),
                "evidence_provenance", tailored.selectedClaimIds().stream()
                        .filter(id -> catalog.byId().containsKey(id))
                        .map(id -> {
                            var claim = catalog.get(id);
                            return Map.of("id", claim.id(),
                                    "type", claim.type().name().toLowerCase(Locale.ROOT),
                                    "source", claim.source(),
                                    "title", claim.title());
                        }).toList()
        ));
        manifestData.put("cover_letter_claim_ids", letter.claimIds());

        Path manifestPath = directory.resolve("manifest.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifestData);

        return new ApplicationPackage(directory, resumeTex, resumePdf, coverPdf, manifestPath);
    }

    public record ApplicationPackage(Path directory, Path resumeTex, Path resumePdf,
                                      Path coverLetterPdf, Path manifest) {}
}
