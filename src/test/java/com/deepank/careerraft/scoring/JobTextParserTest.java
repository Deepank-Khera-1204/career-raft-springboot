package com.deepank.careerraft.scoring;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobTextParserTest {
 @Test void parsesExperience(){assertEquals(3.0,JobTextParser.parseRequiredExperience("Requires at least 3 years of experience"));assertEquals(2.0,JobTextParser.parseRequiredExperience("2+ yrs experience"));assertNull(JobTextParser.parseRequiredExperience("Experience preferred"));}
 @Test void parsesLpaRanges(){assertArrayEquals(new double[]{18,24},JobTextParser.parseLpaRange("18-24 LPA"));assertArrayEquals(new double[]{20,20},JobTextParser.parseLpaRange("₹20 LPA"));assertTrue(Double.isNaN(JobTextParser.parseLpaRange("Competitive salary")[0]));}
}
