import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { API_BASE } from './api.config';

export interface Contador {
  email: string;
  nome: string;
  empresa: string;
}

interface LoginResponse extends Contador {
  token: string;
}

const TOKEN_KEY = 'contabil.token';
const USER_KEY = 'contabil.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  private _token = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private _user = signal<Contador | null>(this.lerUser());

  readonly user = this._user.asReadonly();
  readonly autenticado = computed(() => this._token() !== null);

  token(): string | null {
    return this._token();
  }

  login(email: string, senha: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${API_BASE}/v1/auth/login`, { email, senha }).pipe(
      tap((res) => this.guardarSessao(res)),
    );
  }

  logout(): void {
    this._token.set(null);
    this._user.set(null);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  private guardarSessao(res: LoginResponse): void {
    const user: Contador = { email: res.email, nome: res.nome, empresa: res.empresa };
    this._token.set(res.token);
    this._user.set(user);
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  private lerUser(): Contador | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as Contador) : null;
  }
}
