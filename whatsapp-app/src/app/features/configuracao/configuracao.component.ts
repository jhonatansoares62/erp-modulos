import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { TagModule } from 'primeng/tag';
import { finalize } from 'rxjs';
import { MetaConfig, MetaConfigUpdate, WhatsAppApiService } from '../../core/whatsapp-api.service';

/**
 * Configuração das credenciais Meta (GET/PUT /api/whatsapp/config). Os secrets nunca
 * voltam em claro — o GET só informa se cada campo está preenchido (booleans); os
 * campos de secret ficam vazios e só são enviados no PUT quando o operador digita
 * algo (atualização parcial: em branco = mantém). phoneNumberId volta em claro.
 */
@Component({
  selector: 'app-configuracao',
  standalone: true,
  imports: [FormsModule, CardModule, InputTextModule, PasswordModule, ButtonModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-card>
      <ng-template #title>
        <div class="titulo-linha">
          <span><i class="pi pi-cog"></i> Credenciais Meta (WhatsApp Cloud)</span>
          @if (config(); as c) {
            @if (c.configurado) {
              <p-tag value="Pronto pra operar" severity="success" icon="pi pi-check-circle" />
            } @else {
              <p-tag value="Falta configurar" severity="warn" icon="pi pi-exclamation-triangle" />
            }
          }
        </div>
      </ng-template>

      @if (config(); as c) {
        <p class="ajuda">
          Os segredos não são exibidos por segurança — cada campo mostra apenas se já está
          preenchido. Deixe em branco para <strong>manter</strong> o valor atual; digite para trocar.
          @if (c.atualizadoEm) {
            <br />Última atualização: {{ fmtData(c.atualizadoEm) }}.
          }
        </p>

        <form class="form" (ngSubmit)="salvar()">
          <div class="field">
            <label for="phoneNumberId">Phone Number ID</label>
            <input pInputText id="phoneNumberId" name="phoneNumberId" [(ngModel)]="phoneNumberId"
                   placeholder="Ex.: 123456789012345" autocomplete="off" />
            <small>Identificador público do número (não é secret).</small>
          </div>

          <div class="field">
            <label for="accessToken">
              Access Token
              <p-tag [value]="c.accessTokenConfigurado ? 'preenchido' : 'vazio'"
                     [severity]="c.accessTokenConfigurado ? 'success' : 'danger'" />
            </label>
            <p-password inputId="accessToken" name="accessToken" [(ngModel)]="accessToken" [feedback]="false"
                        [toggleMask]="true" autocomplete="off" placeholder="Novo token (deixe vazio p/ manter)"
                        styleClass="w-full" inputStyleClass="w-full" />
          </div>

          <div class="field">
            <label for="appSecret">
              App Secret
              <p-tag [value]="c.appSecretConfigurado ? 'preenchido' : 'vazio'"
                     [severity]="c.appSecretConfigurado ? 'success' : 'danger'" />
            </label>
            <p-password inputId="appSecret" name="appSecret" [(ngModel)]="appSecret" [feedback]="false"
                        [toggleMask]="true" autocomplete="off" placeholder="Novo secret (deixe vazio p/ manter)"
                        styleClass="w-full" inputStyleClass="w-full" />
          </div>

          <div class="field">
            <label for="verifyToken">
              Verify Token
              <p-tag [value]="c.verifyTokenConfigurado ? 'preenchido' : 'vazio'"
                     [severity]="c.verifyTokenConfigurado ? 'success' : 'danger'" />
            </label>
            <p-password inputId="verifyToken" name="verifyToken" [(ngModel)]="verifyToken" [feedback]="false"
                        [toggleMask]="true" autocomplete="off" placeholder="Novo verify token (deixe vazio p/ manter)"
                        styleClass="w-full" inputStyleClass="w-full" />
          </div>

          <div class="acoes">
            <p-button type="submit" label="Salvar" icon="pi pi-save" [loading]="salvando()" [disabled]="!alterou()" />
            <p-button type="button" label="Recarregar" icon="pi pi-refresh" severity="secondary" [text]="true"
                      [loading]="loading()" (onClick)="carregar()" />
          </div>
        </form>
      } @else if (loading()) {
        <p class="ajuda">Carregando configuração…</p>
      } @else {
        <p class="ajuda">Não foi possível carregar a configuração.
          <p-button label="Tentar de novo" icon="pi pi-refresh" [text]="true" (onClick)="carregar()" />
        </p>
      }
    </p-card>
  `,
  styles: [`
    :host { display: block; }
    .titulo-linha { display: flex; align-items: center; justify-content: space-between; gap: 1rem; flex-wrap: wrap; }
    .titulo-linha > span { display: inline-flex; align-items: center; gap: .5rem; }
    .ajuda { color: var(--text-color-secondary); font-size: .9rem; margin-bottom: 1.25rem; line-height: 1.5; }
    .form { display: flex; flex-direction: column; gap: 1.1rem; max-width: 34rem; }
    .field { display: flex; flex-direction: column; gap: .4rem; }
    .field label { font-size: .85rem; font-weight: 500; display: inline-flex; align-items: center; gap: .5rem; }
    .field small { color: var(--text-color-secondary); font-size: .78rem; }
    .field input { width: 100%; }
    .acoes { display: flex; gap: .75rem; align-items: center; margin-top: .25rem; }
    :host ::ng-deep .w-full { width: 100%; }
    :host ::ng-deep .p-password { width: 100%; }
  `],
})
export class ConfiguracaoComponent implements OnInit {
  private api = inject(WhatsAppApiService);
  private destroyRef = inject(DestroyRef);
  private msg = inject(MessageService);

  loading = signal(false);
  salvando = signal(false);
  config = signal<MetaConfig | null>(null);

  phoneNumberId = '';
  accessToken = '';
  appSecret = '';
  verifyToken = '';

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.loading.set(true);
    this.api.obterConfig().pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading.set(false))).subscribe({
      next: (c) => {
        this.config.set(c);
        this.phoneNumberId = c.phoneNumberId ?? '';
        this.accessToken = '';
        this.appSecret = '';
        this.verifyToken = '';
      },
      error: () => {
        this.config.set(null);
        this.msg.add({ severity: 'error', summary: 'Configuração', detail: 'Falha ao carregar as credenciais Meta.' });
      },
    });
  }

  /** Habilita salvar só se algum campo mudou (phone diferente ou algum secret digitado). */
  alterou(): boolean {
    const atual = this.config();
    const phoneMudou = (this.phoneNumberId ?? '').trim() !== (atual?.phoneNumberId ?? '');
    return phoneMudou || !!this.accessToken.trim() || !!this.appSecret.trim() || !!this.verifyToken.trim();
  }

  salvar(): void {
    if (!this.alterou()) {
      return;
    }
    const body: MetaConfigUpdate = {};
    const phone = this.phoneNumberId.trim();
    if (phone !== (this.config()?.phoneNumberId ?? '')) {
      body.phoneNumberId = phone;
    }
    if (this.accessToken.trim()) {
      body.accessToken = this.accessToken.trim();
    }
    if (this.appSecret.trim()) {
      body.appSecret = this.appSecret.trim();
    }
    if (this.verifyToken.trim()) {
      body.verifyToken = this.verifyToken.trim();
    }

    this.salvando.set(true);
    this.api.salvarConfig(body).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.salvando.set(false))).subscribe({
      next: (c) => {
        this.config.set(c);
        this.phoneNumberId = c.phoneNumberId ?? '';
        this.accessToken = '';
        this.appSecret = '';
        this.verifyToken = '';
        this.msg.add({
          severity: 'success',
          summary: 'Configuração',
          detail: c.configurado ? 'Credenciais salvas — módulo pronto pra operar.' : 'Credenciais salvas (ainda faltam campos).',
        });
      },
      error: (err) => this.msg.add({
        severity: 'error',
        summary: 'Configuração',
        detail: err?.status === 400 ? 'Dados inválidos — confira os campos.' : 'Falha ao salvar as credenciais.',
      }),
    });
  }

  fmtData(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    return isNaN(d.getTime()) ? iso : d.toLocaleString('pt-BR');
  }
}
