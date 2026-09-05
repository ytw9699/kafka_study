import { onMounted, onUnmounted, ref } from 'vue'

/**
 * SSE 구독 공통화.
 *
 * ## 주의 — 해제를 빠뜨리면 연결이 누적된다
 *
 * 컴포넌트가 언마운트될 때 `close()` 하지 않으면 서버 쪽 `SseEmitter` 가 계속 쌓인다.
 *
 * ## 한계 — 재연결 중 놓친 이벤트는 복구되지 않는다
 *
 * `EventSource` 는 끊기면 브라우저가 자동으로 재연결하지만, **그 사이의 이벤트는 사라진다.**
 *
 * Kafka 의 offset 은 **컨슈머 서비스가** 어디까지 읽었는지를 기억할 뿐,
 * 브라우저가 어디까지 받았는지는 모른다.
 * 서버-브라우저 구간(SSE)과 서버-서버 구간(Kafka)은 **다른 레이어**다.
 *
 * → 이것이 "Kafka 는 SSE 연결 문제를 해결하는 도구가 아니다"의 실물이다.
 */
export function useSse<T>(url: string, eventName: string, onMessage: (data: T) => void) {
  const connected = ref(false)
  const error = ref<string | null>(null)
  let source: EventSource | null = null

  function open() {
    source = new EventSource(url)

    source.addEventListener('connected', () => {
      connected.value = true
      error.value = null
    })

    source.addEventListener(eventName, (e) => {
      onMessage(JSON.parse((e as MessageEvent).data) as T)
    })

    source.onerror = () => {
      // EventSource 는 스스로 재연결을 시도한다. 여기서는 상태만 표시한다.
      connected.value = false
      error.value = '연결 끊김 — 재연결 시도 중'
    }
  }

  function close() {
    source?.close()
    source = null
    connected.value = false
  }

  onMounted(open)
  onUnmounted(close)

  return { connected, error, close }
}
