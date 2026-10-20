package com.ytw.kafkastudy.balanceworker.domain

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 보류 사용자 관리. [QuarantinedUser] 참조. */
@Service
class QuarantineService(
    private val repository: QuarantinedUserRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun isQuarantined(userId: String): Boolean = repository.existsById(userId)

    /** 이미 보류된 사용자면 처음 이유를 유지한다. */
    @Transactional
    fun quarantine(userId: String, reason: String) {
        if (repository.existsById(userId)) return
        repository.save(QuarantinedUser(userId = userId, reason = reason.take(500)))
        log.warn("사용자 보류 userId={} reason={}", userId, reason)
    }

    @Transactional(readOnly = true)
    fun all(): List<QuarantinedUser> = repository.findAll().sortedBy { it.userId }

    @Transactional
    fun clear() = repository.deleteAll()
}
