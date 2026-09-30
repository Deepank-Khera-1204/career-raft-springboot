package com.deepank.careerraft.discovery;
import com.deepank.careerraft.domain.Job;
import java.util.List;
public interface JobSource {
    String provider();
    List<Job> fetch(SourceDefinition definition);
}
