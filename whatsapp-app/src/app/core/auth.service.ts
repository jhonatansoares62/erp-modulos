import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { API_BASE } from './api.config';

export interface Atendente {
  email: string;
  nome: string;
  empresa: string;
}

interface LoginResponse extends Atendente {
  token: string;
}

const TOKEN_KEY = 'whatsapp.token';
const USER_KEY = 'whatsapp.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  private _token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private _user = signal<Atendente | null>(this.lerUser());

  readonly user = this._user.asReadonly();
  readonly autenticado = computed(() => this._token() !== null);

  token(): string | null {
    return this._token();
  }

  login(email: string, senha: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_BASE}/api/whatsapp/auth/login`, { email, senha }).pipe(
      tap((res) => this.guardarSessao(res)),
    );
  }

  me(): Observable<Atendente> {
    return this.http.get<Atendente>(`${API_BASE}/api/whatsapp/auth/me`).pipe(
      tap((user) => this._user.set(user)),
    );
  }

  logout(): void {
    this._token.set(null);
    this._user.set(null);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  private guardarSessao(res: LoginResponse): void {
    const user: Atendente = { email: res.email, nome: res.nome, empresa: res.empresa };
    this._token.set(res.token);
    this._user.set(user);
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  private lerUser(): Atendente | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as Atendente) : null;
  }
}
