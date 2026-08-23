import type { ChangePasswordRequest, LoginRequest, SessionResponse } from '../../shared/api/contracts'
import { http } from '../../shared/api/http'

interface SessionWireResponse {
  authenticated: boolean
  username: string | null
  display_name?: string | null
  displayName?: string | null
}

function sessionFromWire(response: SessionWireResponse): SessionResponse {
  return {
    authenticated: response.authenticated,
    username: response.username,
    displayName: response.display_name ?? response.displayName ?? null
  }
}

export async function getSession(): Promise<SessionResponse> {
  return sessionFromWire(await http.get<SessionWireResponse>('/admin/session'))
}

export async function loginSession(credentials: LoginRequest): Promise<SessionResponse> {
  return sessionFromWire(await http.post<SessionWireResponse>('/admin/session', credentials))
}

export function logoutSession(): Promise<void> {
  return http.delete<void>('/admin/session')
}

export function changePassword(request: ChangePasswordRequest): Promise<void> {
  return http.put<void>('/admin/account/password', {
    current_password: request.currentPassword,
    new_password: request.newPassword,
    confirmation: request.confirmation
  })
}
