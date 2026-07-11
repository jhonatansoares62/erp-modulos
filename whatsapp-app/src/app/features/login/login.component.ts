import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule, ButtonModule, InputTextModule, PasswordModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="login-wrap">
      <form class="login-card" (ngSubmit)="entrar()">
        <div class="brand">
          <i class="pi pi-whatsapp"></i>
          <div>
            <h1>WhatsApp</h1>
            <span>ERPKit</span>
          </div>
        </div>

        <label class="field">
          <span>E-mail</span>
          <input pInputText type="email" name="email" [(ngModel)]="email" autocomplete="username"
                 placeholder="atendente@erpkit.local" required />
        </label>

        <label class="field">
          <span>Senha</span>
          <p-password name="senha" [(ngModel)]="senha" [feedback]="false" [toggleMask]="true"
                      autocomplete="current-password" placeholder="Sua senha" styleClass="w-full" inputStyleClass="w-full" />
        </label>

        <p-button type="submit" label="Entrar" icon="pi pi-sign-in" [loading]="loading()"
                  styleClass="w-full" [disabled]="!email || !senha" />
      </form>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .login-wrap { min-height: 100vh; display: grid; place-items: center; padding: 1.5rem;
      background: linear-gradient(160deg, var(--color-slate) 0%, var(--color-slate-mid) 100%); }
    .login-card { width: 100%; max-width: 22rem; display: flex; flex-direction: column; gap: 1.1rem;
      background: var(--surface-card); border: 1px solid var(--surface-border);
      border-radius: var(--radius-xl); padding: 2rem 1.75rem;
      box-shadow: 0 18px 40px rgba(0,0,0,.28); }
    .brand { display: flex; align-items: center; gap: .8rem; margin-bottom: .4rem; }
    .brand i { font-size: 1.9rem; color: var(--primary-color); }
    .brand h1 { font-family: var(--font-display); font-size: 1.5rem; line-height: 1; margin: 0;
      color: var(--text-color); text-transform: uppercase; letter-spacing: .02em; }
    .brand span { font-size: .8rem; color: var(--text-color-secondary); letter-spacing: .18em; text-transform: uppercase; }
    .field { display: flex; flex-direction: column; gap: .35rem; }
    .field span { font-size: .8rem; color: var(--text-color-secondary); }
    .field input { width: 100%; }
    :host ::ng-deep .w-full { width: 100%; }
    :host ::ng-deep .p-password { width: 100%; }
  `],
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);
  private msg = inject(MessageService);

  email = '';
  senha = '';
  loading = signal(false);

  entrar(): void {
    if (!this.email || !this.senha) {
      return;
    }
    this.loading.set(true);
    this.auth.login(this.email, this.senha).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: () => this.router.navigate(['/conversas']),
      error: (err) => this.msg.add({
        severity: 'error',
        summary: 'Falha no login',
        detail: err?.status === 401 ? 'E-mail ou senha inválidos.' : 'Não foi possível conectar ao WhatsApp.',
      }),
    });
  }
}
