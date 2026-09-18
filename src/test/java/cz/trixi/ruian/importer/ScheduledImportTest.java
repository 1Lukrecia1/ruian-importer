package cz.trixi.ruian.importer;

import cz.trixi.ruian.PostgisContainerConfig;
import cz.trixi.ruian.download.DownloadException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.test.context.ActiveProfiles;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgisContainerConfig.class)
class ScheduledImportTest {

    @Autowired
    private List<ScheduledTaskHolder> taskHolders;

    @Value("${importer.schedule.cron}")
    private String cron;

    @Value("${importer.schedule.zone}")
    private String zone;

    @Test
    void importIsScheduledWithConfiguredCron() {
        List<String> cronExpressions = taskHolders.stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(ScheduledTask::getTask)
                .filter(CronTask.class::isInstance)
                .map(task -> ((CronTask) task).getExpression())
                .toList();

        assertThat(cronExpressions).containsExactly(cron);
    }

    @Test
    void defaultCronRunsOnceAMonth() {
        ZoneId prague = ZoneId.of(zone);
        CronExpression expression = CronExpression.parse(cron);

        ZonedDateTime first = expression.next(ZonedDateTime.of(2026, 9, 15, 12, 0, 0, 0, prague));
        ZonedDateTime second = expression.next(first);

        assertThat(first).isEqualTo(ZonedDateTime.of(2026, 10, 2, 3, 0, 0, 0, prague));
        assertThat(second).isEqualTo(ZonedDateTime.of(2026, 11, 2, 3, 0, 0, 0, prague));
    }

    @Test
    void failedScheduledImportDoesNotPropagate() {
        RuianImportService importService = mock(RuianImportService.class);
        when(importService.importFromDefaultSource(ImportTrigger.SCHEDULED))
                .thenThrow(new DownloadException("unreachable"));

        new ScheduledImport(importService).runScheduledImport();

        verify(importService).importFromDefaultSource(ImportTrigger.SCHEDULED);
    }
}
