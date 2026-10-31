export interface CurrentUser {
  authenticated: boolean
  username?: string
  displayName?: string
  role?: 'ADMIN' | 'USER'
}

export interface ApiError {
  code: string
  message: string
  retryable?: boolean
  fieldErrors?: Record<string, string>
}

export interface AppUser {
  id: number
  username: string
  displayName: string
  email: string
  status: 'ACTIVE' | 'DISABLED'
  role: 'ADMIN' | 'USER'
  createdAt: string
  updatedAt: string
}

export interface RoleHistory {
  id: number
  oldRole: string | null
  newRole: string
  changedBy: string
  changedAt: string
}

export interface TransactionSummary {
  transactionId: string
  partnerName: string
  amount: number
  transactionDate: string
  status: 'COMPLETED' | 'PENDING' | 'CANCELLED'
}

export interface TransactionPage {
  content: TransactionSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TransactionLine {
  lineNo: number
  itemName: string
  quantity: number
  unitPrice: number
}

export interface TransactionHistory {
  occurredAt: string
  event: string
  operator: string
}

export interface TransactionDetail extends TransactionSummary {
  note: string
  lines: TransactionLine[]
  histories: TransactionHistory[]
}

export interface RegisterResult {
  receiptNumber: string
  transactionId: string
  status: string
}

export interface ApplicationRequest {
  applicationType: string
  nameKanji: string
  nameKana: string
  nameAlphabet: string
  employeeNumber: string
  email: string
  phoneNumber: string
  websiteUrl: string
  birthDate: string
  desiredDate: string
  startTime: string
  endTime: string
  notificationAt: string
  periodStart: string
  periodEnd: string
  postalCode: string
  prefecture: string
  address: string
  buildingName: string
  quantity: number | null
  amount: number | null
  percentage: number | null
  rating: number | null
  gender: string
  languages: string[]
  contactMethod: string
  priority: number | null
  themeColor: string
  attachmentFileName: string
  subject: string
  content: string
  confidentiality: string
  termsAgreed: boolean
  marketingOptIn: boolean
}

export interface ApplicationResponse {
  id: number
  status: string
  createdAt: string
}
