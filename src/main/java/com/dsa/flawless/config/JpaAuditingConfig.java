package com.dsa.flawless.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity의 @CreatedDate/@LastModifiedDate가 동작하도록 JPA Auditing을 활성화한다.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider") //서버 시간대로 처리되는것을 방지하기 위해 해당 프로바이더를 통해 timezone부여
public class JpaAuditingConfig {
}
