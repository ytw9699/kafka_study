package com.archseoul.kafkastudy.balanceworker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 잔액 계산 컨슈머. 인스턴스를 여러 개 띄워 순서·병렬·리밸런싱을 관찰한다. */
@SpringBootApplication
class BalanceWorkerApplication

fun main(args: Array<String>) {
    runApplication<BalanceWorkerApplication>(*args)
}
