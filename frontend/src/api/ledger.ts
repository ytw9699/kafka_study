import type { BurstResult, PartitionKeyMode, PublishResult } from './types'

async function post<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`${res.status} ${await res.text()}`)
  return res.json() as Promise<T>
}

export function createTx(params: {
  userId: string
  type: 'EARN' | 'SPEND'
  amount: number
  reason?: string
}): Promise<PublishResult> {
  return post<PublishResult>('/api/tx', params)
}

export function burst(params: {
  users: number
  txPerUser: number
}): Promise<BurstResult> {
  return post<BurstResult>('/api/tx/burst', params)
}

export function setKeyMode(mode: PartitionKeyMode): Promise<{ mode: PartitionKeyMode }> {
  return post('/api/config/partition-key-mode', { mode })
}

export async function getKeyMode(): Promise<PartitionKeyMode> {
  const res = await fetch('/api/config/partition-key-mode')
  return (await res.json()).mode
}
