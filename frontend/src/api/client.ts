import axios from 'axios'
import type { ApiError } from '../types'

// axios は XSRF-TOKEN Cookie を自動的に X-XSRF-TOKEN ヘッダへ転記する(同一オリジン)
export const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

/** axiosエラーからバックエンドの正規化済みエラー情報を取り出す。 */
export function toApiError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as Partial<ApiError> | undefined
    if (data && typeof data.message === 'string') {
      return {
        code: data.code ?? 'UNKNOWN',
        message: data.message,
        retryable: data.retryable,
        fieldErrors: data.fieldErrors,
      }
    }
    if (error.code === 'ECONNABORTED') {
      return { code: 'CLIENT_TIMEOUT', message: '通信がタイムアウトしました。再試行してください', retryable: true }
    }
    return { code: 'NETWORK_ERROR', message: 'サーバに接続できません', retryable: true }
  }
  return { code: 'UNKNOWN', message: '予期しないエラーが発生しました' }
}

export function isUnauthorized(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401
}
