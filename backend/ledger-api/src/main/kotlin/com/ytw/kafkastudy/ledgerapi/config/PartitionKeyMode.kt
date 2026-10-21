package com.ytw.kafkastudy.ledgerapi.config

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 발행 시 어떤 값을 파티션 키로 쓸지 결정한다.
 *
 * **이 스위치는 프로덕션에 없을 학습 장치다.** 4단계 실험 A 의 조작 장치이며,
 * 설정 한 줄로 "순서가 안 지켜지면 잔액이 틀어진다"를 재현하고 되돌릴 수 있다.
 */
enum class PartitionKeyMode {
    /**
     * 정상. 키 = `userId`.
     *
     * 같은 사용자의 거래가 항상 같은 파티션에 들어간다.
     * Kafka 는 **파티션 내부에서만** 순서를 보장하고 **한 파티션은 한 워커만** 처리하므로,
     * 이것이 "같은 사용자는 순서대로, 다른 사용자는 병렬로"를 성립시킨다.
     */
    USER_ID,

    /**
     * 실험용. 키 = 매번 새로운 UUID.
     *
     * 같은 사용자의 거래가 여러 파티션에 흩어진다.
     * → 병렬 워커가 순서를 뒤바꿔 처리한다
     * → 잔액 부족 판정이 달라진다
     * → **잔액이 틀어진다**
     */
    RANDOM,
    ;

    fun keyFor(userId: String): String = when (this) {
        USER_ID -> userId
        RANDOM -> UUID.randomUUID().toString()
    }
}

/**
 * 현재 파티션 키 모드. 런타임에 바꿀 수 있다.
 *
 * 4단계 실험 중 서비스를 재기동하지 않고 전환하기 위해 [AtomicReference] 로 들고 있다.
 * 재기동하면 `app.partition-key-mode` 설정값으로 돌아간다.
 */
@Component
@ConfigurationProperties(prefix = "app")
class PartitionKeyModeHolder {

    /** `application.yml` 의 `app.partition-key-mode`. 기본값은 정상 동작인 [PartitionKeyMode.USER_ID]. */
    var partitionKeyMode: PartitionKeyMode = PartitionKeyMode.USER_ID
        set(value) {
            field = value
            current.set(value)
        }

    private val current = AtomicReference(PartitionKeyMode.USER_ID)

    fun get(): PartitionKeyMode = current.get()

    fun set(mode: PartitionKeyMode): PartitionKeyMode {
        current.set(mode)
        return mode
    }
}
