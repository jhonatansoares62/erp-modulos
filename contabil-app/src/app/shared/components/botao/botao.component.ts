import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';

export type BotaoTipo = 'primario' | 'secundario' | 'texto' | 'perigo';
export type BotaoSeverity =
  | 'success' | 'info' | 'warn' | 'danger' | 'help' | 'primary' | 'secondary' | 'contrast';

// Botao padrao do sistema. `tipo` define o layout (cobre preenchido, outlined,
// ghost, danger); os overrides severity/outlined/text cobrem variantes pontuais
// (sucesso, alerta, secundario cinza) sem precisar de p-button cru.
@Component({
  selector: 'app-botao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule, Tooltip],
  template: `
    <p-button
      [label]="label()"
      [icon]="icon()"
      [iconPos]="iconPos()"
      [severity]="severityFinal()"
      [outlined]="outlinedFinal()"
      [text]="textFinal()"
      [rounded]="rounded()"
      [disabled]="disabled()"
      [loading]="loading()"
      [size]="size()"
      [type]="type()"
      [styleClass]="styleClass()"
      [pTooltip]="tooltip()"
      [ariaLabel]="ariaLabel() || label()"
      (onClick)="clicado.emit($event)"
    />
  `,
})
export class BotaoComponent {
  tipo = input<BotaoTipo>('primario');
  label = input<string>();
  icon = input<string>();
  iconPos = input<'left' | 'right'>('left');
  disabled = input(false);
  loading = input(false);
  rounded = input(false);
  size = input<'small' | 'large'>();
  type = input<'button' | 'submit'>('button');
  styleClass = input<string>('');
  ariaLabel = input<string>();
  tooltip = input<string>();

  // Overrides pontuais (vencem o default do tipo quando informados)
  severity = input<BotaoSeverity>();
  outlined = input<boolean>();
  text = input<boolean>();

  clicado = output<MouseEvent>();

  severityFinal = computed(() =>
    this.severity() ?? (this.tipo() === 'perigo' ? 'danger' : undefined)
  );
  outlinedFinal = computed(() => this.outlined() ?? this.tipo() === 'secundario');
  textFinal = computed(() => this.text() ?? this.tipo() === 'texto');
}
