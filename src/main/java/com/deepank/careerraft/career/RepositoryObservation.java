package com.deepank.careerraft.career;
import java.util.List;
public record RepositoryObservation(String repository,String provider,String defaultBranch,List<String> files,List<String> technologies,List<String> signals,String sourceUrl){public RepositoryObservation{files=List.copyOf(files);technologies=List.copyOf(technologies);signals=List.copyOf(signals);}}
