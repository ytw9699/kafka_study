package com.ytw.kafkastudy.balanceworker.config

import com.ytw.kafkastudy.balanceworker.consume.PermanentFailureException
import com.ytw.kafkastudy.balanceworker.consume.UserQuarantinedException
import com.ytw.kafkastudy.balanceworker.domain.QuarantineService
import com.ytw.kafkastudy.common.LedgerEvent
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafkaRetryTopic
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.listener.RetryListener
import org.springframework.kafka.retrytopic.RetryTopicConfiguration
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder
import org.springframework.kafka.retrytopic.SameIntervalTopicReuseStrategy
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.util.backoff.FixedBackOff

/**
 * 처리에 실패한 메시지를 어떻게 할 것인가 — **7단계(KS-35)의 실험 장치.**
 *
 * `app.error-mode`(`APP_ERROR_MODE`)로 세 가지를 바꿔 가며 비교한다.
 *
 * | 모드 | 동작 | 순서 | 실패한 거래 |
 * |---|---|---|---|
 * | `DEFAULT` | Spring Kafka 기본값. 즉시 10번 다시 하고 **로그만 남기고 넘어간다** | 지켜짐 | **조용히 사라진다** |
 * | `NON_BLOCKING` | 재시도 토픽으로 보내고 **다음 거래를 먼저 처리** | **깨진다** | 나중에 처리되거나 DLT |
 * | `BLOCKING` | 그 자리에서 기다리며 다시 시도. 다시 해도 안 되면 DLT + **사용자 보류** | 지켜짐 | DLT 에 보관 |
 */
enum class ErrorMode { DEFAULT, NON_BLOCKING, BLOCKING }

@Configuration
class ErrorHandlingConfig {

    /**
     * DLT(Dead Letter Topic) — 처리하지 못한 거래를 보관하는 토픽.
     *
     * 원래 파티션 번호 그대로 보내므로 원본과 **파티션 수가 같아야** 한다.
     * 브로커의 토픽 자동 생성을 꺼 두었으므로 여기서 명시적으로 만든다.
     */
    @Bean
    fun deadLetterTopic(@Value("\${app.topic.ledger-events}") topic: String): NewTopic =
        TopicBuilder.name("$topic.DLT").partitions(3).replicas(1).build()

    /**
     * 실패한 거래를 재시도 토픽 · DLT 로 다시 보내는 데 쓰는 프로듀서.
     *
     * 값이 두 종류다. 정상적으로 읽힌 거래는 `LedgerEvent`(JSON 으로),
     * **읽지도 못한 깨진 메시지는 원래 바이트 그대로** 보낸다.
     */
    @Bean
    fun failureKafkaTemplate(properties: KafkaProperties): KafkaTemplate<String, Any> {
        val json = JsonSerializer<Any>().apply { isAddTypeInfo = false }
        @Suppress("UNCHECKED_CAST")
        val value = DelegatingByTypeSerializer(
            mapOf<Class<*>, Serializer<*>>(
                ByteArray::class.java to ByteArraySerializer(),
                LedgerEvent::class.java to json,
            ),
            true,
        ) as Serializer<Any>
        val factory = DefaultKafkaProducerFactory<String, Any>(
            properties.buildProducerProperties(null),
            StringSerializer(),
            value,
        )
        return KafkaTemplate(factory)
    }
}

/**
 * `NON_BLOCKING` — Spring Kafka 가 권장하는 **재시도 토픽** 방식.
 *
 * 실패한 거래를 `ledger-events-retry` 로 보내고 **원래 파티션은 다음 거래로 넘어간다.**
 * 처리량은 지켜지지만, 같은 사용자의 뒤 거래가 먼저 처리되어 **순서가 깨진다.** (실험 E)
 */
@Configuration
@EnableKafkaRetryTopic
@ConditionalOnProperty(name = ["app.error-mode"], havingValue = "NON_BLOCKING")
class NonBlockingRetryConfig {

    @Bean
    fun retryTopicConfiguration(
        failureKafkaTemplate: KafkaTemplate<String, Any>,
        @Value("\${app.topic.ledger-events}") topic: String,
    ): RetryTopicConfiguration =
        RetryTopicConfigurationBuilder.newInstance()
            .fixedBackOff(2_000)
            .maxAttempts(10)
            // 간격이 같으면 재시도 토픽을 하나(ledger-events-retry)만 쓴다. 기본값은 시도마다 토픽을 하나씩 만든다.
            .sameIntervalTopicReuseStrategy(SameIntervalTopicReuseStrategy.SINGLE_TOPIC)
            .includeTopic(topic)
            .notRetryOn(PermanentFailureException::class.java)
            .autoCreateTopicsWith(3, 1)
            .create(failureKafkaTemplate)
}

/**
 * `BLOCKING` — 그 자리에서 기다리며 다시 시도한다. **파티션이 멈추는 대신 순서가 지켜진다.**
 *
 * - 일시 실패: 2초 간격으로 최대 30번(약 1분) 다시 시도
 * - 영구 실패 · 이미 보류된 사용자: 다시 하지 않고 **바로 DLT**
 * - DLT 로 보낼 때 그 사용자를 **보류**한다 — 뒤 거래도 DLT 로 보내 건너뛴 잔액이 생기지 않게
 */
@Configuration
@ConditionalOnProperty(name = ["app.error-mode"], havingValue = "BLOCKING")
class BlockingRetryConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun blockingErrorHandler(
        failureKafkaTemplate: KafkaTemplate<String, Any>,
        quarantine: QuarantineService,
    ): DefaultErrorHandler {
        val toDlt = DeadLetterPublishingRecoverer(failureKafkaTemplate)
        val recoverer = ConsumerRecordRecoverer { record, ex ->
            // 깨진 메시지는 값이 없다 — 누구의 거래인지 모르므로 사용자를 보류하지 않는다.
            (record.value() as? LedgerEvent)?.let { quarantine.quarantine(it.userId, rootMessage(ex)) }
            log.warn("DLT 로 보냄 p{}#{} key={} 이유={}", record.partition(), record.offset(), record.key(), rootMessage(ex))
            toDlt.accept(record, ex)
        }
        return DefaultErrorHandler(recoverer, FixedBackOff(2_000L, 30L)).apply {
            addNotRetryableExceptions(
                PermanentFailureException::class.java,
                UserQuarantinedException::class.java,
            )
            setRetryListeners(
                RetryListener { record, ex, attempt ->
                    log.warn("재시도 {}회째 p{}#{} key={} 이유={}", attempt, record.partition(), record.offset(), record.key(), rootMessage(ex))
                },
            )
        }
    }

    private fun rootMessage(ex: Throwable): String {
        var e: Throwable = ex
        while (e.cause != null && e.cause !== e) e = e.cause!!
        return "${e.javaClass.simpleName}: ${e.message}"
    }
}
