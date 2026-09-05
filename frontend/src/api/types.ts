/** `ledger-events` 토픽의 이벤트. SSE 로 받는 형태. */
export interface LedgerEvent {
  eventId: string
  eventType: 'POINT_EARNED' | 'POINT_SPENT'
  occurredAt: string
  userId: string
  amount: number
  reason: string
  /** 이 이벤트가 적재된 파티션. 같은 userId 면 고정되어야 한다. */
  partition: number
  offset: number
}

export interface PublishResult {
  eventId: string
  partition: number
  offset: number
  keyMode: PartitionKeyMode
  partitionKey: string
}

export type PartitionKeyMode = 'USER_ID' | 'RANDOM'

export interface BurstResult {
  published: number
  keyMode: PartitionKeyMode
  byPartition: Record<string, number>
}
