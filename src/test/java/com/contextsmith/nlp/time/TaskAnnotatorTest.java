package com.contextsmith.nlp.time;

import com.contextsmith.nlp.annotator.Annotation;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by beders on 5/24/17.
 */
public class TaskAnnotatorTest {

    @Test
    public void testReschedule() {
        List<Annotation> anns = TaskAnnotator.getInstance().annotate("Let me know if it doesn't work and I'll come over tomorrow.");

        assertTrue(anns.size() > 0);
        anns = TaskAnnotator.getInstance().annotate("Let me know if it doesn't work and I'll reschedule tomorrow.");
        assertTrue(anns.size() == 0);

        anns = TaskAnnotator.getInstance().annotate("Let me know if it doesn't work and I'll schedule tomorrow.");
        assertTrue(anns.size() == 0);
    }



}