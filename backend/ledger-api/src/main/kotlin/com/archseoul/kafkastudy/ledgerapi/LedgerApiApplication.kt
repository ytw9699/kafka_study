package com.archseoul.kafkastudy.ledgerapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 거래 발행 + SSE 게이트웨이. DB 를 갖지 않는다 — Kafka 가 원장의 원본이다. */
@SpringBootApplication
class LedgerApiApplication

fun main(args: Array<String>) {
    runApplication<LedgerApiApplication>(*args)
}
