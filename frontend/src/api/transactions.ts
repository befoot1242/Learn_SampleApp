import { http } from './client'
import type { RegisterResult, TransactionDetail, TransactionPage } from '../types'

export interface TransactionSearchCondition {
  dateFrom?: string
  dateTo?: string
  status?: string
  partnerName?: string
  page: number
  size: number
}

export async function searchTransactions(condition: TransactionSearchCondition): Promise<TransactionPage> {
  const params: Record<string, string | number> = {
    page: condition.page,
    size: condition.size,
  }
  if (condition.dateFrom) params.dateFrom = condition.dateFrom
  if (condition.dateTo) params.dateTo = condition.dateTo
  if (condition.status) params.status = condition.status
  if (condition.partnerName) params.partnerName = condition.partnerName
  const { data } = await http.get<TransactionPage>('/transactions', { params })
  return data
}

export async function fetchTransactionDetail(id: string): Promise<TransactionDetail> {
  const { data } = await http.get<TransactionDetail>(`/transactions/${id}`)
  return data
}

export interface RegisterTransactionInput {
  requestId: string
  partnerName: string
  amount: number
  transactionDate: string
  note: string
}

export async function registerTransaction(input: RegisterTransactionInput): Promise<RegisterResult> {
  const { data } = await http.post<RegisterResult>('/transactions', input)
  return data
}
