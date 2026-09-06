package com.archseoul.kafkastudy.balanceworker.config

import com.archseoul.kafkastudy.balanceworker.consume.AssignmentTracker
import com.archseoul.kafkastudy.common.LedgerEvent
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
class KafkaConfig {

    /**
     * 리스너 컨테이너에 [AssignmentTracker] 를 붙인다.
     *
     * Spring Boot 가 만들어주는 기본 팩토리를 그대로 쓰되
     * `ConsumerRebalanceListener` 만 얹는다.
     */
    @Bean
    fun kafkaListenerContainerFactory(
        configurer: ConcurrentKafkaListenerContainerFactoryConfigurer,
        consumerFactory: ConsumerFactory<Any, Any>,
        tracker: AssignmentTracker,
    ): ConcurrentKafkaListenerContainerFactory<Any, Any> {
        val factory = ConcurrentKafkaListenerContainerFactory<Any, Any>()
        configurer.configure(factory, consumerFactory)
        factory.containerProperties.setConsumerRebalanceListener(tracker)
        return factory
    }
}
