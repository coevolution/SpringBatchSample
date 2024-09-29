package org.example.rpw;

import org.example.config.DataSourceConfiguration;
import org.example.skip.SkippableExceptionDuringProcess;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Arrays;

@Configuration
@EnableBatchProcessing
@Import(DataSourceConfiguration.class)
public class RpwSampleStarter {
    private final PlatformTransactionManager transactionManager;

    public RpwSampleStarter(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hello world!");
        ApplicationContext context = new AnnotationConfigApplicationContext(RpwSampleStarter.class);
        JobLauncher jobLauncher = context.getBean(JobLauncher.class);
        Job job = context.getBean(Job.class);
        jobLauncher.run(job, new JobParameters());
    }

    @Bean
    public Step step(JobRepository jobRepository) {
        return new StepBuilder("step", jobRepository).<Integer, Integer>chunk(2, this.transactionManager)
                .reader(new JdbcDataReader(Arrays.asList(1, 2, 3, 4, 5, 6)))
                .processor(new SampleProcessor())
                .writer(new SampleWriter())
                .faultTolerant()
                .skip(IllegalArgumentException.class)
                .skipLimit(3)
                .build();
    }

//    @Bean
    public Step step1(JobRepository jobRepository) {
        return new StepBuilder("step", jobRepository).<Integer, Integer>chunk(3, this.transactionManager)
//                .reader(itemReader())
//                .processor(itemProcessor())
//                .writer(itemWriter())
                .faultTolerant()
                .skip(IllegalArgumentException.class)
                .skipLimit(3)
                .build();
    }

    @Bean
    public Job job(JobRepository jobRepository) {
        return new JobBuilder("job1", jobRepository).start(step(jobRepository)).build();
    }
}
