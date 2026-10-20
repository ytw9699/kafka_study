package com.ytw.kafkastudy.fraudservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 이상거래 탐지. 6단계에서 나중에 처음 기동해 과거 전체를 재생한다. */
@SpringBootApplication
class FraudServiceApplication

fun main(args: Array<String>) {
    runApplication<FraudServiceApplication>(*args)
}
