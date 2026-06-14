package ai.corporatedroneagent.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class KnowledgeScanJobTests {

    @Test
    void scheduledScanRunsAtQuarterHourBoundaries() throws NoSuchMethodException {
        Method method = KnowledgeScanJob.class.getDeclaredMethod("scanKnowledgeRoots");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0/15 * * * *");
    }
}
