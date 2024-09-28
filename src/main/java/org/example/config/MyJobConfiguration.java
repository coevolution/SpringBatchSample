package org.example.config;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.configuration.support.DefaultBatchConfiguration;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyJobConfiguration extends DefaultBatchConfiguration {
//    @Bean
//    public Job job(JobRepository jobRepository) {
//        return new JobBuilder("myJob", jobRepository).start();
//    }
}
