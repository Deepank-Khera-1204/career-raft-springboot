package com.deepank.careerraft.discovery;

import java.time.*;import java.util.*;
public final class JitteredScheduler {
 private final SourcePolicy policy; private final Random random;
 public JitteredScheduler(SourcePolicy policy){this(policy,new Random());}
 public JitteredScheduler(SourcePolicy policy,Random random){if(policy.minHours()<=0||policy.maxHours()<policy.minHours())throw new IllegalArgumentException("Invalid scheduler bounds");this.policy=policy;this.random=random;}
 public Duration nextDelay(){List<Double> candidates=policy.allowedIntervalsHours().stream().filter(x->x>=policy.minHours()&&x<=policy.maxHours()).toList();double base=candidates.isEmpty()?policy.minHours()+random.nextDouble()*(policy.maxHours()-policy.minHours()):candidates.get(random.nextInt(candidates.size()));return Duration.ofMillis(Math.round(base*3600000));}
 public Instant nextRunAt(Instant now){return now.plus(nextDelay());}
}
